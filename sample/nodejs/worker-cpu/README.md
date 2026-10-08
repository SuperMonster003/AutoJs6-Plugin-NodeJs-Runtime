# worker-cpu

Runs the same prime-sum calculation four times sequentially, then in four native
workers. Main and worker functions receive the same warm-up. The output reports
both computation speedup and speedup including worker startup; actual results
depend on available CPUs, Android scheduling and temperature.

Workers are enabled by default and have no plugin-imposed count limit. Nested
workers, native ports and Worker constructor options are available. Filesystem
access follows Android and network operations inherit the execution's switch.
Request resource limits are defaults that each Worker may override. Host calls
are available through `autojs6:bridge` and a parent MessagePort, as demonstrated
by `worker-host-bridge`; Inspector sessions follow the execution's Inspector
request and the current build's support.

Expected output is listed in `expected-output.txt`.
