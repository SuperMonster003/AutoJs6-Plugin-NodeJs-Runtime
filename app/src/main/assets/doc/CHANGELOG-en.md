# v1.3.0

###### Unreleased

* `Fix` Fixed growing live-bridge request and response history in resident scripts by retiring completed requests and bounding diagnostics to the latest 32 responses with a size limit
* `Improvement` Reduced live bridge latency with a default JNI/Binder transport and Node event-loop responses, preserving selectable file fallback and pending-call limits
