# scheduled-node-task

WorkManager scheduled Node task example.

- Capabilities: `work_manager`
- Expected provider: AndroidX WorkManager scheduled runner
- Packaged support: project entry metadata is supported; full packaged scheduled-task UI flow is future gated
- Security limitations: task paths are scoped; raw scheduler authority and arbitrary file paths are denied

Run directly, the script prints its lifecycle policy (`one_shot/script`), schedules itself
once with a 60 s delay and cancels the task. Create an empty `keep-scheduled.txt` next to
`main.cjs` before running to keep the task; about 60 s later the host WorkManager runner
launches `main.cjs` with `executionMode=scheduled` / `launchSurface=scheduled_runner`, and
that run prints `sample.scheduled-node-task.launchedByRunner=true` in the AutoJs6 log. The
scheduled run is never retried implicitly. The manual acceptance steps are in
[MANUAL-ACCEPTANCE.md](../../../docs/nodejs/MANUAL-ACCEPTANCE.md).

Expected output is listed in `expected-output.txt`.
