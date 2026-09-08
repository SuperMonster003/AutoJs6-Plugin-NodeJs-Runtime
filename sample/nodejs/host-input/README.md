# host-input

Open this project in a host with Node stdin support and run it. The console
input bar appears for each `readline.question()`. Enter a name and another line;
the script echoes both and exits. Stop cancels a pending question.

The example intentionally has no deadline while waiting for a person. A host
that only implements runtime contract v2 can still run scripts, but cannot
send input to this example.

For structured host messages, use `require('autojs6:host').once('message', value => ...)`.
The host sends a JSON value through the v3 `postMessage` transaction. Message
listeners keep the script alive until removed; `unref()` opts out of this.
