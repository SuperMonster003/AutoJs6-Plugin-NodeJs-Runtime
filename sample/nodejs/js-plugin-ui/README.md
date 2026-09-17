# js-plugin-ui

Local JS plugin that exposes a UI layout descriptor, which the entry then shows
through the host `ui` bridge.

- Capabilities: `plugins`, `plugin.org.example.sample_ui`, `ui`
- Expected provider: JS plugin manager plus the host UI provider behind the `ui` permission
- Packaged support: not applicable
- Security limitations: no plugin download, native archive, or marketplace; UI handles stay opaque; without the host UI provider the `ui` step prints `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` and the sample still completes

Expected output is listed in `expected-output.txt`.
