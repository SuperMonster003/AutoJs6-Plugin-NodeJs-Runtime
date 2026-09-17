# streams-compression

Run this directory as a Node project from AutoJs6. The script writes `out/input.txt`,
compresses it to `out/input.txt.gz` through a file stream, restores it to
`out/restored.txt`, and verifies every byte before printing PASS.

`fs` file streams and native `zlib` streams are stable plugin capabilities.
Android file permissions and the documented `/proc`, `/sys`, `/dev` boundaries
still apply. Exported APKs cannot currently bundle this runtime.
