# live-fetch

Controlled live fetch example using `require("autojs6:fetch")`. The global `fetch` uses Node's native network stack; this explicit module uses the AutoJs6 host stack.

- Capabilities: `network`
- Expected provider: Android controlled fetch provider
- Packaged support: not enabled by default; packaged apps need explicit network capability and INTERNET mapping
- Security limitations: controlled `fetch` still applies its own limits; raw Node network builtins are a separate default-on surface and can be disabled per request

Expected output is listed in `expected-output.txt`.
