# worker-cpu

Runs the same prime-sum calculation four times sequentially, then in four native
workers. Main and worker functions receive the same warm-up. The output reports
both computation speedup and speedup including worker startup; actual results
depend on available CPUs, Android scheduling and temperature.

Workers are enabled by default. The default limit is `os.availableParallelism()`,
capped at eight. This example needs at least four worker slots; a lower request
limit produces a readable worker-limit error. Filesystem and network operations
inherit the execution's switches. AutoJs bridge modules and inspector remain
unavailable inside workers.

Expected output is listed in `expected-output.txt`.
