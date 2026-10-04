"""Optional authenticated WS proxy to a Codex app-server and its Desktop owner.

Only the two codexAndroid/desktop extensions use Desktop's private local IPC.
Normal CLI requests, notifications and approval replies pass through unchanged.
No writer is acquired, released or replaced by the IPC adapter.
"""

import argparse
import asyncio
from contextlib import asynccontextmanager
import hmac
from http import HTTPStatus
import ipaddress
import json
import os
from pathlib import Path
import socket
import stat
import struct
import uuid

from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed

DISCOVER = "codexAndroid/desktop/discover"
START = "codexAndroid/desktop/turn/start"
PROTOCOL_VERSION = 1
MAX_FRAME = 64 * 1024 * 1024


class CompanionError(Exception):
    def __init__(self, message, code=-32070):
        super().__init__(message)
        self.code = code


def validate_uuid(value):
    if not isinstance(value, str):
        raise CompanionError("Expected a UUID", -32602)
    try:
        if str(uuid.UUID(value)) != value:
            raise ValueError()
    except ValueError:
        raise CompanionError("Expected a canonical UUID", -32602) from None
    return value


def private_file(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise ValueError("Token file must be a private regular file owned by this user")
    token = path.read_text().strip()
    if not token or "\n" in token or "\r" in token:
        raise ValueError("Invalid token file")
    return token


def validate_bind(host):
    address = ipaddress.ip_address(host)
    tailscale = ipaddress.ip_network("100.64.0.0/10")
    if not address.is_loopback and address not in tailscale:
        raise ValueError("Bind only to loopback or a specific Tailscale IPv4 address")
    return host


class DesktopIpc:
    """Versioned Unix IPC adapter. Never create the Desktop socket or router."""

    def __init__(self, path):
        self.path = Path(path)
        self.client_id = "initializing-client"
        self.reader = self.writer = None

    async def __aenter__(self):
        info, parent = self.path.lstat(), self.path.parent.lstat()
        if (not stat.S_ISSOCK(info.st_mode) or info.st_uid != os.getuid() or
                info.st_mode & 0o077 or not stat.S_ISDIR(parent.st_mode) or
                parent.st_uid != os.getuid() or parent.st_mode & 0o022):
            raise CompanionError("Desktop IPC endpoint is not private to this user")
        try:
            self.reader, self.writer = await asyncio.wait_for(
                asyncio.open_unix_connection(str(self.path)), 3)
            # Linux additionally verifies the connected peer, after pathname checks.
            if hasattr(socket, "SO_PEERCRED"):
                peer = self.writer.get_extra_info("socket")
                _, uid, _ = struct.unpack("3i", peer.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
                if uid != os.getuid():
                    raise CompanionError("Desktop IPC peer belongs to another user")
            result = await self.request("initialize", {"clientType": "codex-android-companion"}, 0)
            self.client_id = result["result"]["clientId"]
            return self
        except BaseException:
            await self.close()
            raise

    async def __aexit__(self, *_):
        await self.close()

    async def close(self):
        if self.writer:
            self.writer.close()
            try:
                await self.writer.wait_closed()
            except (OSError, ConnectionError):
                pass

    async def write(self, message):
        body = json.dumps(message, separators=(",", ":"), ensure_ascii=False).encode()
        if len(body) > MAX_FRAME:
            raise CompanionError("Desktop IPC frame too large")
        self.writer.write(struct.pack("<I", len(body)) + body)
        await self.writer.drain()

    async def request(self, method, params, version, target=None, timeout=8):
        request_id = str(uuid.uuid4())
        message = {"type": "request", "requestId": request_id, "sourceClientId": self.client_id,
                   "version": version, "method": method, "params": params, "timeoutMs": timeout * 1000}
        if target:
            message["targetClientId"] = target
        try:
            async with asyncio.timeout(timeout + 1):
                await self.write(message)
                while True:
                    size = struct.unpack("<I", await self.reader.readexactly(4))[0]
                    if not 0 < size <= MAX_FRAME:
                        raise CompanionError("Invalid Desktop IPC frame length")
                    response = json.loads(await self.reader.readexactly(size))
                    if response.get("type") == "client-discovery-request":
                        await self.write({"type": "client-discovery-response", "requestId": response["requestId"],
                                          "response": {"canHandle": False}})
                    if response.get("type") != "response" or response.get("requestId") != request_id:
                        continue  # Other conversations' broadcasts are neither retained nor forwarded.
                    if response.get("resultType") != "success":
                        raise CompanionError(response.get("error", "Desktop request failed"))
                    if response.get("method") != method:
                        raise CompanionError("Desktop IPC response method mismatch")
                    return response
        except (TimeoutError, asyncio.IncompleteReadError, OSError) as error:
            raise CompanionError("Desktop delivery is unconfirmed; check the original conversation before retrying") from error

    async def owner(self, thread_id):
        response = await self.request("thread-owner-discovery", {"hostId": "local", "conversationId": thread_id}, 1)
        return response["handledByClientId"]

    async def start(self, thread_id, message_id, text):
        # Rediscover on every send: an owner id from an earlier poll may be stale.
        owner = await self.owner(thread_id)
        response = await self.request("thread-follower-start-turn", {
            "conversationId": thread_id,
            "turnStart": {
                "request": {"threadId": thread_id, "clientUserMessageId": message_id,
                            "input": [{"type": "text", "text": text, "text_elements": []}]},
                "context": {"inheritThreadSettings": True},
            },
        }, 2, target=owner, timeout=120)
        if response.get("handledByClientId") != owner:
            raise CompanionError("Desktop owner changed during delivery")
        result = response["result"]["result"]
        if not isinstance(result.get("turn", {}).get("id"), str):
            raise CompanionError("Desktop did not confirm the submitted turn")
        return {"threadId": thread_id, "clientUserMessageId": message_id, "turn": result["turn"]}


class DesktopCompanion:
    def __init__(self, backend_url, token, ipc_path, ipc_factory=DesktopIpc):
        self.backend_url, self.token = backend_url, token
        self.ipc_path, self.ipc_factory = ipc_path, ipc_factory

    def authenticate(self, connection, request):
        try:
            authorization = request.headers.get("Authorization", "")
        except Exception:
            authorization = ""
        if not hmac.compare_digest(authorization.encode(), ("Bearer " + self.token).encode()):
            return connection.respond(HTTPStatus.UNAUTHORIZED, "Unauthorized\n")

    async def extension(self, method, params):
        if method not in (DISCOVER, START):
            raise CompanionError("Unknown companion extension", -32601)
        if not isinstance(params, dict):
            raise CompanionError("Expected request parameters", -32602)
        thread_id = validate_uuid(params.get("threadId"))
        expected = {"threadId"} if method == DISCOVER else {"threadId", "clientUserMessageId", "text"}
        if set(params) != expected:
            raise CompanionError("Unsupported companion parameters", -32602)
        if method == START:
            message_id = validate_uuid(params.get("clientUserMessageId"))
            text = params.get("text")
            if not isinstance(text, str) or not text.strip() or len(text.encode()) > 1024 * 1024:
                raise CompanionError("Expected nonempty text up to 1 MiB", -32602)
        try:
            async with self.ipc_factory(self.ipc_path) as ipc:
                if method == DISCOVER:
                    await ipc.owner(thread_id)
                    return {"threadId": thread_id, "protocolVersion": PROTOCOL_VERSION, "available": True}
                return await ipc.start(thread_id, message_id, text)
        except (CompanionError, OSError, TimeoutError) as error:
            # Read-only capability discovery is allowed to fail closed. A send
            # failure is always visible and is never replayed by this proxy.
            if method == DISCOVER:
                return {"threadId": thread_id, "protocolVersion": PROTOCOL_VERSION, "available": False}
            if isinstance(error, CompanionError):
                raise
            raise CompanionError("Desktop unavailable; message was not confirmed") from error

    async def handle(self, client):
        try:
            async with connect(self.backend_url, additional_headers={"Authorization": "Bearer " + self.token},
                               max_size=MAX_FRAME, compression=None, proxy=None) as backend:
                async def from_client():
                    async for raw in client:
                        if not isinstance(raw, str):
                            await client.close(1003, "Text frames required")
                            return
                        try:
                            message = json.loads(raw)
                        except (ValueError, TypeError):
                            await client.close(1007, "Invalid JSON")
                            return
                        if not isinstance(message, dict):
                            await client.close(1007, "Object required")
                            return
                        method = message.get("method", "")
                        if not isinstance(method, str) or not method.startswith("codexAndroid/desktop/"):
                            await backend.send(raw)
                            continue
                        # The extension is a client request, never a notification
                        # or an approval response with a reused request id.
                        if "id" not in message or "result" in message or "error" in message:
                            await client.close(1007, "Extension request id required")
                            return
                        response = {"id": message["id"]}
                        try:
                            response["result"] = await self.extension(method, message.get("params"))
                        except CompanionError as error:
                            response["error"] = {"code": error.code, "message": str(error)}
                        except (ValueError, TypeError, KeyError):
                            response["error"] = {"code": -32070, "message": "Unsupported Desktop IPC response; delivery unconfirmed"}
                        await client.send(json.dumps(response, ensure_ascii=False))

                async def from_backend():
                    async for raw in backend:
                        await client.send(raw)

                tasks = [asyncio.create_task(from_client()), asyncio.create_task(from_backend())]
                try:
                    done, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                    for task in done:
                        task.result()
                finally:
                    for task in tasks:
                        task.cancel()
                    await asyncio.gather(*tasks, return_exceptions=True)
        except (ConnectionClosed, OSError):
            await client.close(1011, "Companion connection closed")

    @asynccontextmanager
    async def listen(self, host, port):
        validate_bind(host)
        async with serve(self.handle, host, port, process_request=self.authenticate,
                         max_size=MAX_FRAME, compression=None) as server:
            yield server


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8391)
    parser.add_argument("--backend", required=True, help="Existing authenticated Codex app-server WS URL")
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--ipc", type=Path, default=Path(os.environ.get("CODEX_HOME", str(Path.home()/".codex")))/"ipc/ipc.sock")
    args = parser.parse_args()
    token = private_file(args.token_file)
    companion = DesktopCompanion(args.backend, token, args.ipc)
    async with companion.listen(args.bind, args.port):
        print("Codex Android companion ready", flush=True)
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
