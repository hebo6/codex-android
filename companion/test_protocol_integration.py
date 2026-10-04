"""Real CLI integration in a temporary CODEX_HOME with no cloud model calls.

Desktop's IPC router is simulated; its owner is a separate real app-server.
This verifies writer separation and same-thread persistence, not Desktop UI E2E.
"""
import asyncio
import json
import os
from pathlib import Path
import shutil
import signal
import tempfile
import unittest
import uuid

from websockets.asyncio.client import connect
from websockets.asyncio.server import serve

from desktop_companion import DesktopCompanion, DISCOVER, START
from test_desktop_companion import IpcFixture, TOKEN


async def start_cli(home):
    return await asyncio.create_subprocess_exec(
        "codex", "app-server", "--listen", "stdio://",
        "-c", 'model_provider="probe"',
        "-c", 'model_providers.probe.name="Offline protocol test"',
        "-c", 'model_providers.probe.base_url="http://127.0.0.1:9/v1"',
        "-c", 'model_providers.probe.wire_api="responses"',
        "-c", "model_providers.probe.request_max_retries=0",
        "-c", "model_providers.probe.stream_max_retries=0",
        "-c", "features.plugins=false",
        "-c", "features.remote_plugin=false",
        "-c", "features.plugin_sharing=false",
        env={**os.environ, "CODEX_HOME": home},
        start_new_session=True,
        stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL)


async def close_cli(process):
    # The isolated CLI may spawn plugin-discovery children. Clean up only this
    # test's new process group before removing its temporary CODEX_HOME.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    if process.returncode is None:
        try:
            await asyncio.wait_for(process.wait(), 5)
        except TimeoutError:
            os.killpg(process.pid, signal.SIGKILL); await process.wait()
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass


class StdioClient:
    def __init__(self, process):
        self.process = process
        self.counter = 0
        self.pending = {}
        self.task = asyncio.create_task(self.read())

    async def read(self):
        async for raw in self.process.stdout:
            message = json.loads(raw)
            future = self.pending.pop(message.get("id"), None)
            if future is not None:
                future.set_result(message)

    async def request(self, method, params):
        self.counter += 1
        request_id = self.counter
        future = asyncio.get_running_loop().create_future()
        self.pending[request_id] = future
        self.process.stdin.write((json.dumps({"id": request_id, "method": method, "params": params}) + "\n").encode())
        await self.process.stdin.drain()
        return await asyncio.wait_for(future, 15)

    async def close(self):
        await close_cli(self.process)
        await self.task


@unittest.skipUnless(shutil.which("codex"), "Codex CLI is needed for isolated integration")
class ProtocolIntegrationTest(unittest.IsolatedAsyncioTestCase):
    async def test_external_writer_messages_persist_in_original_thread(self):
        with tempfile.TemporaryDirectory(prefix="codex-android-same-thread-test-") as home:
            owner = StdioClient(await start_cli(home))
            backend_requests = []
            backend_tasks = set()
            try:
                initialize = {"clientInfo": {"name": "codex-android-probe", "version": "1"},
                              "capabilities": {"experimentalApi": True}}
                self.assertIn("result", await owner.request("initialize", initialize))
                owner.process.stdin.write(b'{"method":"initialized"}\n')
                await owner.process.stdin.drain()
                thread = (await owner.request("thread/start", {"cwd": home, "historyMode": "paginated"}))["result"]["thread"]["id"]
                first = (await owner.request("turn/start", {"threadId": thread, "input": [{"type": "text", "text": "initial offline fixture"}]}))["result"]
                # turn/start acknowledges admission before the runtime has an
                # interruptible task. Cancel this test turn once that task exists.
                for _ in range(60):
                    interrupted = await owner.request("turn/interrupt", {"threadId": thread, "turnId": first["turn"]["id"]})
                    if "result" in interrupted: break
                    self.assertIn("no active turn to interrupt", interrupted["error"]["message"])
                    await asyncio.sleep(0.1)
                # Wait for the initial turn to settle before the second send.
                for _ in range(60):
                    read = await owner.request("thread/read", {"threadId": thread, "includeTurns": True})
                    turns = read.get("result", {}).get("thread", {}).get("turns", [])
                    if turns and turns[-1]["status"] != "inProgress": break
                    await asyncio.sleep(0.1)
                self.assertTrue(turns)
                self.assertNotEqual("inProgress", turns[-1]["status"])

                async def backend_handler(ws):
                    backend_tasks.add(asyncio.current_task())
                    process = await start_cli(home)
                    async def input_reader():
                        async for raw in ws:
                            message = json.loads(raw)
                            backend_requests.append(message.get("method"))
                            process.stdin.write((raw + "\n").encode())
                            await process.stdin.drain()
                    async def output_reader():
                        async for raw in process.stdout:
                            await ws.send(raw.decode().strip())
                    tasks = [asyncio.create_task(input_reader()), asyncio.create_task(output_reader())]
                    try:
                        await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                    finally:
                        for task in tasks: task.cancel()
                        await asyncio.gather(*tasks, return_exceptions=True)
                        await close_cli(process)
                        backend_tasks.discard(asyncio.current_task())

                async with IpcFixture().listen() as fixture:
                    async def deliver(params):
                        response = await owner.request("turn/start", params)
                        self.assertIn("result", response)
                        return response["result"]
                    fixture.deliver = deliver
                    async with serve(backend_handler, "127.0.0.1", 0) as backend:
                        backend_url = f"ws://127.0.0.1:{backend.sockets[0].getsockname()[1]}"
                        companion = DesktopCompanion(backend_url, TOKEN, fixture.path)
                        async with companion.listen("127.0.0.1", 0) as server:
                            url = f"ws://127.0.0.1:{server.sockets[0].getsockname()[1]}"
                            async with connect(url, additional_headers={"Authorization": "Bearer " + TOKEN}, proxy=None) as phone:
                                counter = 0
                                async def request(method, params):
                                    nonlocal counter
                                    counter += 1
                                    await phone.send(json.dumps({"id":counter,"method":method,"params":params}))
                                    async with asyncio.timeout(15):
                                        while True:
                                            response = json.loads(await phone.recv())
                                            if response.get("id") == counter: return response
                                self.assertIn("result", await request("initialize", initialize))
                                await phone.send('{"method":"initialized"}')
                                conflict = await request("thread/resume", {"threadId": thread})
                                self.assertEqual(-32600, conflict["error"]["code"])
                                self.assertIn("already has an active writer", conflict["error"]["message"])
                                self.assertIn("result", await request("thread/read", {"threadId": thread,"includeTurns": False}))
                                capability = await request(DISCOVER, {"threadId": thread})
                                self.assertTrue(capability["result"]["available"])
                                message_id = str(uuid.uuid4())
                                sent = await request(START, {"threadId": thread,"clientUserMessageId":message_id,"text":"phone message in original thread"})
                                self.assertEqual(thread, sent["result"]["threadId"])
                                self.assertEqual(message_id, sent["result"]["clientUserMessageId"])
                                for _ in range(60):
                                    snapshot = (await request("thread/read", {"threadId":thread,"includeTurns":True}))["result"]["thread"]
                                    if "phone message in original thread" in json.dumps(snapshot): break
                                    await asyncio.sleep(0.1)
                                await owner.request("turn/interrupt", {"threadId": thread,"turnId": sent["result"]["turn"]["id"]})
                                self.assertEqual(thread, snapshot["id"])
                                self.assertIn("phone message in original thread", json.dumps(snapshot))
                                self.assertNotIn("turn/start", backend_requests)
                                self.assertNotIn("thread/fork", backend_requests)
                                self.assertEqual(1, sum(m["method"] == "thread-follower-start-turn" for m in fixture.messages))
                                sessions = list(Path(home).glob("sessions/**/*.jsonl"))
                                self.assertEqual(1, len(sessions))
            finally:
                if backend_tasks:
                    await asyncio.gather(*backend_tasks, return_exceptions=True)
                await owner.close()


if __name__ == "__main__":
    unittest.main()
