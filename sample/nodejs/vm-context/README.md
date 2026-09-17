# vm-context

Run this directory as a Node project from AutoJs6. The script evaluates a compiled
script in a separate context, verifies context isolation, interrupts an infinite
loop with a VM timeout, and checks that the context remains usable afterward.

Basic VM execution is a stable plugin capability. Node's `vm` module is not a
security boundary for untrusted code. `vm.Module`, `vm.SourceTextModule` and
`vm.SyntheticModule` retain their upstream experimental status; this example uses
the stable script and context APIs. Exported APKs cannot currently bundle this runtime.
