# long-running-service

Interactive long-running service example with lifecycle checkpoint hooks.

- Capabilities: `interactive_long_running`, `lifecycle_checkpoint`
- Expected provider: explicit interactive long-running launch (`node.executionMode=interactive_long_running`)
- Packaged support: not applicable; exported APKs cannot bundle or launch this runtime, the mode runs from the host project entry
- Security limitations: no automatic restart; checkpoints are JSON-only and project-scoped

Expected output is listed in `expected-output.txt`.
