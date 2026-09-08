# live-websocket

Controlled WebSocket example using `require("autojs6:websocket")`. The global `WebSocket` uses Node's native network stack; this explicit module uses the AutoJs6 host stack.

- Capabilities: `network`
- Expected provider: Android controlled WebSocket provider
- Packaged support: not enabled by default; packaged apps need explicit network capability and INTERNET mapping
- Security limitations: host connection, queue, and message-size limits apply; native networking is enabled by default and can be disabled per request

Expected output is listed in `expected-output.txt`.
