# Desktop companion（实验性）

让手机向正在其他 Codex Desktop 客户端中打开的原会话发送文字，保留原 thread ID。普通请求仍交给现有 app-server；发送文字时，通过本机 Desktop IPC 找到 writer，再调用该 writer 的 follower 接收入口。伴随服务不持有原会话的 writer，也不写入 rollout 文件。

这是 codex-android 的可选扩展，**不是 Codex CLI 的官方 RPC，也不是稳定的 Desktop 公共接口**。目前适配本机桌面包中的 Unix IPC：4 字节小端长度 + UTF-8 JSON，`thread-owner-discovery` v1、`thread-follower-start-turn` v2。当前 CLI 协议按 0.159.2 导出的 JSON Schema 核对；Desktop IPC 另行核对已安装桌面包。桌面版本、平台或 IPC 协议变化后需重新验证。

## 使用

需要 Python 3.11+、本机运行的 Codex Desktop，以及已有的 capability-token 认证 app-server。Desktop 和 app-server 必须使用同一个 Codex 历史目录。当前支持 Linux/Unix；不实现 Windows named pipe。

在仓库根目录安装独立依赖环境：

```bash
python3 -m venv companion/.venv
companion/.venv/bin/pip install -r companion/requirements.txt
```

启动代理（将示例 Tailscale 地址替换为电脑的实际地址）：

```bash
companion/.venv/bin/python companion/desktop_companion.py \
  --backend ws://100.100.100.100:8390 \
  --bind 100.100.100.100 --port 8391 \
  --token-file ~/.codex/app-server-token
```

Android 的服务器地址改为 `ws://100.100.100.100:8391`，沿用已有 capability token。原 app-server 的端口保持不变；伴随代理是单独的入口。只允许监听 loopback 或具体 Tailscale IPv4 地址。令牌文件须由当前用户持有、权限为 600，不接受符号链接。Desktop IPC socket 必须是当前用户私有的 socket，Linux 还检查连接对端的用户身份。令牌用于认证代理入口和原 app-server，不出现在日志或参数中。

默认 IPC 为 `${CODEX_HOME:-$HOME/.codex}/ipc/ipc.sock`；需要时可通过 `--ipc` 指定已有 socket。服务不会创建、替换或删除 Desktop socket、writer lock 或历史文件。关闭代理后可以继续使用原 app-server 地址。

## 手机行为

- `thread/resume` 只有遇到 active-writer 冲突才进入 external-writer 模式，通过 `thread/read` 和分页历史查看原对话。
- 检测到 Desktop owner 后显示“发送到原对话”。当前仅支持文字，沿用 Desktop 的模型、权限、工作目录等设置；发送端无法覆盖这些设置。
- 正在处理的会话先等待当前回合结束。此版本不提供跨 Desktop 的排队、打断或 steer；不会伪装成已排队。
- Shell、审批、工具操作和会话设置仍由 Desktop 端处理。子代理 `canAcceptDirectInput=false` 不因伴随服务改变。
- 未安装伴随服务、Desktop 不可用、writer 是独立 CLI/TUI、owner 已断开或协议不兼容时，保留只读查看和草稿。不会自动 Fork。
- 每次发送重新寻找 owner，随后只发一次。发送未确认时保留草稿，请先检查原会话，避免手动重试造成重复消息。
- 历史在 Android 前台轮询更新；writer 释放后正常 `thread/resume` 恢复本地可写状态。“分叉为新对话”仍是独立且明确的选项。

## 扩展协议 v1

使用既有 WS 连接及 JSON-RPC 请求格式。两个方法不属于 CLI 的 `ClientRequest` Schema，代理拦截后处理；其他请求和服务器通知按原样转发。

`codexAndroid/desktop/discover` 参数：`{ "threadId": "<uuid>" }`。
结果：`{ "threadId": "<uuid>", "protocolVersion": 1, "available": true/false }`。

`codexAndroid/desktop/turn/start` 参数：`{ "threadId": "<uuid>", "clientUserMessageId": "<uuid>", "text": "..." }`。
结果：`{ "threadId": "<uuid>", "clientUserMessageId": "<uuid>", "turn": <TurnStartResponse.turn> }`。

发送只接受上述参数及最多 1 MiB 的非空文字。无法用它指定任意 RPC、运行 Shell、代答审批或调整权限。Android 校验确认中的 thread ID、message ID 和 turn ID；发送异常不自动重试。发现操作不可用返回 `available=false`；发送失败返回 `-32070`，非法参数为 `-32602`。

原生 CLI 对未知方法可能返回 `-32601`，0.159.2 实测返回 `-32600` 和精确的 unknown-variant 提示；Android 仅将这一扩展方法的“未知方法”视为未安装服务，其他错误仍上报。

## 验证

```bash
companion/.venv/bin/python -m unittest discover -s companion -p 'test_*.py' -v
```

单元测试验证 Unix IPC framing、owner 路由、认证、RPC 原样转发、不可用状态、参数限制和发送不重试。集成测试需要 PATH 中的 `codex`：在 `/tmp` 下创建隔离的 `CODEX_HOME`，启动两个独立 app-server，使用不可达的本机模型地址，禁用插件发现；验证 active-writer 冲突后经 owner 发送的文字存入原 thread，且手机侧不发送 `turn/start` 或 `thread/fork`。Desktop IPC router 在此集成测试中为模拟实现，不等同于真实手机/Desktop UI 测试。测试结束仅清理自身进程组和临时目录。

本机真实 Desktop 已验证 owner 发现、代理认证和原会话读取，并在专门的测试对话中经 follower v2 提交有效文字、收到回复，确认 thread ID 未改变。手机用户也已确认文字能够发送到原对话。处理过程缓存修复通过了回归测试；包含该修复的最新 APK 尚未进行手机实机验收。当前验证仅覆盖本机 Unix IPC 环境，启用此实验功能后应先用专门的测试对话做设备验收。
