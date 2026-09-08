# v1.3.0

###### Unreleased

* `Feature` Node.js bridge subscriptions push sensor, WebSocket, UI, overlay and input events through existing callbacks, with on/once/off listeners, bounded queues and compatible drainEvents
* `Feature` Optional idleExitMs releases an idle Node.js runtime process and reconnects for the next script, with idleForMs diagnostics and resident behavior preserved by default
* `Feature` Native global fetch, Request, Response, Headers, FormData and WebSocket use Node web APIs by default; explicit autojs6:fetch and autojs6:websocket modules retain the host network stack
* `Feature` Added native node:sqlite with filesystem checks, CRUD, transactions and backups; native node:test reporters and the runnable test example are available, with zod, cheerio, date-fns, mqtt and ws added to the offline npm corpus
* `Feature` Native stdin and readline accept console input, with execution-scoped JSON messages through autojs6:host and a backward-compatible v3 postMessage transaction
* `Feature` Node.js screen capture sessions use Android consent and the existing foreground service, with image handles, PNG/JPEG/WebP saving and cleanup when stopped or the script exits
* `Feature` Node.js image handles support clipping, resizing, grayscale, thresholding, template matching and color searches through the host image backend, with independent output handles and execution cleanup
* `Feature` Node.js image.toBytes transfers PNG or RGBA pixels through file descriptors into native Buffers, supports JNI and file bridge modes, and closes attachments after use, timeout or execution exit
* `Feature` Node device APIs expose live build, screen, battery and memory information, brightness control and timed screen wake locks; media supports setting stream volume, with Android permissions enforced
* `Feature` autojs6:events observes Android notifications, external Toast messages, accessibility keys and screen or battery broadcasts through push callbacks, with explicit permissions and automatic cleanup; native Node events remains unchanged
* `Feature` Node app supports Android service starts, broadcasts and installed package queries; dialogs supports text input, choices and progress windows with script cleanup, and keys provides accessibility system actions
* `Feature` Node recorder supports AAC recording with microphone permission, foreground disclosure, duration limits and script cleanup; ui.overlay supports declarative property updates, dragging and pushed events
* `Feature` Node scripts can run concurrently in two independent runtime processes, with a shared FIFO queue and execution-specific cancellation and input routing
* `Fix` Fixed growing live-bridge request and response history in resident scripts by retiring completed requests and bounding diagnostics to the latest 32 responses with a size limit
* `Fix` Fixed prematurely successful results losing native async errors and late exit codes; completion now follows the final Node event-loop exit
* `Improvement` Reduced live bridge latency with a default JNI/Binder transport and Node event-loop responses, preserving selectable file fallback and pending-call limits
* `Improvement` Restored native Node.js builtin exports for streams, crypto, timers, utilities, node:test and related modules while preserving filesystem boundaries and host directory policy
* `Improvement` Native workers now default to CPU parallelism (up to eight), inherit execution network/filesystem switches, accept request resource caps and have no default pool task deadline; CPU and WASM examples run real workers
* `Improvement` Kept raw WASI disabled to preserve filesystem boundaries and removed the two disabled WASI examples; ordinary WebAssembly and WASM workers remain available
* `Improvement` Screen OCR examples request Android capture consent and recognize real image handles; unavailable OCR or barcode plugins return readable unavailable errors, while recognition failures remain errors
* `Improvement` Node scripts support negotiated asynchronous startup, releasing Binder threads during long-running execution and returning workspace changes after terminal completion, with legacy synchronous host compatibility
