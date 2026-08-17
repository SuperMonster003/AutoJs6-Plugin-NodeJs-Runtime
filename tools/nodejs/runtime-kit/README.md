# AutoJs6 Node Plugin Runtime Kit

The Node plugin Runtime Kit is the versioned description of the runtime payload
carried by the **release-universal and per-ABI Node plugin APKs**. It is intentionally
different from the host's generic AutoJs6/APK Builder Runtime Kit.

The canonical manifest is embedded at
`assets/nodejs/node-plugin-runtime-kit.json`. The immutable release directory
contains only a byte-identical manifest and lock; it does not duplicate the
roughly 300 MiB unstripped Node libraries or the upstream archive. The plugin
APK is the payload delivery artifact.

The current canonical release is `1.0.1`. The immutable `1.0.0` manifest and
lock remain retained as historical evidence and are never overwritten.

The manifest pins:

- runtime slot, Node version, contract range, capability-catalog identity;
- the three supported Android ABIs;
- release-APK paths, sizes, SHA-256 values, and applicable Build IDs for the
  complete nine-entry native closure: `libnode.so`, `libautojs6-node.so`, and
  `libc++_shared.so` for every supported ABI;
- the capability-catalog asset;
- the upstream Node Android archive and runtime-build/materializer provenance;
- the distinction between ready pinned-binary materialization and the still
  `bootstrap_only` source build.

Stage or verify the release control plane:

```powershell
.\gradlew.bat :app:stageNodePluginRuntimeKitRelease
.\gradlew.bat :app:verifyNodePluginRuntimeKitRelease
```

The release-control-plane task verifies the immutable manifest/lock and the
stripped native payload sources. It deliberately reports APK carrier
verification as `not requested`; it is not APK packaging evidence.

Build and inspect the actual universal and per-ABI release APKs:

```powershell
.\gradlew.bat :app:verifyNodePluginRuntimeKitGate
```

The full gate checks that the embedded manifest is byte-identical in all four
APKs, validates all ten declared entries in the universal carrier, validates
the exact four-entry ABI subset in each split carrier, and rejects foreign-ABI
native entries. Its verifier mode rejects zero, incomplete, or duplicate APK
carrier sets. On AGP 9.0.1 it disables only this
invocation's app `lintVital` tasks because lint crashes while resolving applied
Kotlin scripts; normal release lint configuration is unchanged.

This is S/A evidence. It does not prove installation, Binder behavior, Android
loader behavior, generated-APK execution, or device compatibility.
