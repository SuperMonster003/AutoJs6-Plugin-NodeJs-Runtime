# node-test-project

Runs two tests using native Node 24.5 `node:test`, with its default reporter on
stdout and a failing process exit code if an assertion fails. Custom reporters
are available through `node:test/reporters`.

- Capabilities: `node:test`
- Expected provider: native Node test runner
- Packaged support: the script can be executed as an ordinary Node entry
- File, network and process policies are the same as other code in this execution

Expected output is listed in `expected-output.txt`.
