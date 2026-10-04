import asyncio
from contextlib import asynccontextmanager
import json
from pathlib import Path
import struct
import tempfile
import unittest
import uuid

from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import InvalidStatus

from desktop_companion import (CompanionError, DesktopCompanion, DesktopIpc,
                               DISCOVER, START, private_file, validate_bind)

THREAD = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"
MESSAGE = "bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb"
OWNER = "cccccccc-cccc-4ccc-cccc-cccccccccccc"
TOKEN = "a-test-capability-token"


class IpcFixture:
    """Desktop router framing and follower responses, without user conversations."""
    def __init__(self):
        self.messages = []
        self.owner_available = True
        self.delivery_error = None
        self.confirmation_owner = OWNER
        self.deliver = None
        self.tasks = set()

    async def handle(self, reader, writer):
        self.tasks.add(asyncio.current_task())
        try:
            while True:
                size = struct.unpack("<I", await reader.readexactly(4))[0]
                message = json.loads(await reader.readexactly(size))
                self.messages.append(message)
                method = message["method"]
                response = {"type": "response", "requestId": message["requestId"],
                            "resultType": "success", "method": method, "handledByClientId": OWNER}
                if method == "initialize":
                    response["result"] = {"clientId": str(uuid.uuid4())}
                elif method == "thread-owner-discovery":
                    if not self.owner_available:
                        response.update(resultType="error", error="no-client-found")
                    else:
                        response["result"] = {"supportsUntrustedAppInput": True}
                elif method == "thread-follower-start-turn":
                    response["handledByClientId"] = self.confirmation_owner
                    if self.delivery_error:
                        response.update(resultType="error", error=self.delivery_error)
                    else:
                        params = message["params"]["turnStart"]["request"]
                        result = (await self.deliver(params) if self.deliver else
                                  {"turn": {"id": "desktop-turn", "status": "inProgress", "items": []}})
                        response["result"] = {"result": result}
                else:
                    response.update(resultType="error", error="no-handler-for-request")
                body = json.dumps(response).encode()
                # Exercise split length prefixes and payloads, as on real streams.
                frame = struct.pack("<I", len(body)) + body
                writer.write(frame[:2]); await writer.drain()
                writer.write(frame[2:]); await writer.drain()
        except (asyncio.IncompleteReadError, ConnectionError):
            pass
        finally:
            writer.close()
            await writer.wait_closed()
            self.tasks.discard(asyncio.current_task())

    @asynccontextmanager
    async def listen(self):
        with tempfile.TemporaryDirectory(prefix="codex-android-ipc-test-") as directory:
            self.path = Path(directory) / "ipc.sock"
            server = await asyncio.start_unix_server(self.handle, path=self.path)
            self.path.chmod(0o600)
            try:
                yield self
            finally:
                server.close(); await server.wait_closed()
                for task in list(self.tasks):
                    task.cancel()
                await asyncio.gather(*self.tasks, return_exceptions=True)


class CompanionTests(unittest.IsolatedAsyncioTestCase):
    async def test_owner_forwarding_keeps_thread_and_desktop_settings(self):
        async with IpcFixture().listen() as fixture:
            companion = DesktopCompanion("ws://unused", TOKEN, fixture.path)
            capability = await companion.extension(DISCOVER, {"threadId": THREAD})
            self.assertTrue(capability["available"])
            result = await companion.extension(START, {"threadId": THREAD, "clientUserMessageId": MESSAGE, "text": "你好"})
            self.assertEqual((THREAD, MESSAGE), (result["threadId"], result["clientUserMessageId"]))
            forwarded = fixture.messages[-1]
            self.assertEqual("thread-follower-start-turn", forwarded["method"])
            self.assertEqual(2, forwarded["version"])
            self.assertEqual(OWNER, forwarded["targetClientId"])
            self.assertEqual(THREAD, forwarded["params"]["conversationId"])
            turn_start = forwarded["params"]["turnStart"]
            self.assertEqual({"inheritThreadSettings": True}, turn_start["context"])
            self.assertEqual({"threadId", "clientUserMessageId", "input"}, set(turn_start["request"]))
            self.assertEqual("你好", turn_start["request"]["input"][0]["text"])
            self.assertEqual(2, sum(m["method"] == "thread-owner-discovery" for m in fixture.messages))

    async def test_absent_owner_is_read_only_and_never_starts_or_forks(self):
        async with IpcFixture().listen() as fixture:
            fixture.owner_available = False
            companion = DesktopCompanion("ws://unused", TOKEN, fixture.path)
            self.assertFalse((await companion.extension(DISCOVER, {"threadId": THREAD}))["available"])
            with self.assertRaisesRegex(CompanionError, "no-client-found"):
                await companion.extension(START, {"threadId": THREAD, "clientUserMessageId": MESSAGE, "text": "hello"})
            self.assertFalse(any("start-turn" in m["method"] for m in fixture.messages))

    async def test_send_failure_is_visible_and_not_retried(self):
        async with IpcFixture().listen() as fixture:
            fixture.delivery_error = "request-timeout"
            companion = DesktopCompanion("ws://unused", TOKEN, fixture.path)
            with self.assertRaisesRegex(CompanionError, "request-timeout"):
                await companion.extension(START, {"threadId": THREAD, "clientUserMessageId": MESSAGE, "text": "hello"})
            self.assertEqual(1, sum(m["method"] == "thread-follower-start-turn" for m in fixture.messages))

    async def test_extension_cannot_select_rpc_shell_approval_or_settings(self):
        companion = DesktopCompanion("ws://unused", TOKEN, "/does/not/exist")
        cases = [
            ("codexAndroid/desktop/shell", {"threadId": THREAD}),
            (START, {"threadId": THREAD, "clientUserMessageId": MESSAGE, "text": "hello", "model": "override"}),
            (START, {"threadId": THREAD, "clientUserMessageId": MESSAGE, "text": " "}),
            (DISCOVER, {"threadId": "invalid"}),
        ]
        for method, params in cases:
            with self.subTest(method=method, params=params):
                with self.assertRaises(CompanionError):
                    await companion.extension(method, params)

    async def test_unavailable_or_insecure_ipc_does_not_enable_send(self):
        companion = DesktopCompanion("ws://unused", TOKEN, "/does/not/exist")
        self.assertFalse((await companion.extension(DISCOVER, {"threadId": THREAD}))["available"])
        async with IpcFixture().listen() as fixture:
            fixture.path.chmod(0o666)
            companion = DesktopCompanion("ws://unused", TOKEN, fixture.path)
            self.assertFalse((await companion.extension(DISCOVER, {"threadId": THREAD}))["available"])
            self.assertEqual([], fixture.messages)

    async def test_ws_auth_and_normal_rpc_transparency(self):
        forwarded = []
        async def backend_handler(client):
            self.assertEqual("Bearer " + TOKEN, client.request.headers["Authorization"])
            async for raw in client:
                forwarded.append(raw)
                await client.send(raw)
        async with IpcFixture().listen() as fixture:
            async with serve(backend_handler, "127.0.0.1", 0) as backend:
                port = backend.sockets[0].getsockname()[1]
                companion = DesktopCompanion(f"ws://127.0.0.1:{port}", TOKEN, fixture.path)
                async with companion.listen("127.0.0.1", 0) as server:
                    address = f"ws://127.0.0.1:{server.sockets[0].getsockname()[1]}"
                    for token in (None, "wrong-token"):
                        with self.assertRaises(InvalidStatus) as rejected:
                            async with connect(address, additional_headers={} if token is None else {"Authorization": "Bearer " + token}, proxy=None):
                                pass
                        self.assertEqual(401, rejected.exception.response.status_code)
                    async with connect(address, additional_headers={"Authorization": "Bearer " + TOKEN}, proxy=None) as phone:
                        for raw in ('{"id":1,"method":"initialize","params":{}}',
                                    '{"method":"initialized"}', '{"id":7,"result":{"decision":"accept"}}'):
                            await phone.send(raw)
                            self.assertEqual(raw, await phone.recv())
                        await phone.send(json.dumps({"id":2,"method":DISCOVER,"params":{"threadId":THREAD}}))
                        self.assertTrue(json.loads(await phone.recv())["result"]["available"])
                        self.assertEqual(3, len(forwarded))
                        await phone.send(json.dumps({"id":3,"method":START,"params":{"threadId":THREAD,"clientUserMessageId":MESSAGE,"text":"hello"}}))
                        self.assertEqual(THREAD, json.loads(await phone.recv())["result"]["threadId"])
                        self.assertEqual(3, len(forwarded))

    async def test_changed_owner_is_not_a_confirmation(self):
        async with IpcFixture().listen() as fixture:
            fixture.confirmation_owner = str(uuid.uuid4())
            async with DesktopIpc(fixture.path) as ipc:
                with self.assertRaisesRegex(CompanionError, "owner changed"):
                    await ipc.start(THREAD, MESSAGE, "hello")

    async def test_missing_turn_confirmation_is_not_success(self):
        async with IpcFixture().listen() as fixture:
            async def bad_delivery(params):
                return {}  # IPC schema change or missing confirmation
            fixture.deliver = bad_delivery
            async with DesktopIpc(fixture.path) as ipc:
                with self.assertRaisesRegex(CompanionError, "did not confirm"):
                    await ipc.start(THREAD, MESSAGE, "hello")

    def test_private_token_file_and_limited_bind(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "token"
            path.write_text(TOKEN + "\n"); path.chmod(0o600)
            self.assertEqual(TOKEN, private_file(path))
            path.chmod(0o644)
            with self.assertRaises(ValueError): private_file(path)
        for host in ("127.0.0.1", "::1", "100.100.100.100"):
            self.assertEqual(host, validate_bind(host))
        for host in ("0.0.0.0", "192.168.1.10", "::", "example.com"):
            with self.assertRaises(ValueError): validate_bind(host)


if __name__ == "__main__":
    unittest.main()
