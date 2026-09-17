# packaged-node-apk

Reads the host application's identity through the `app` bridge. The name dates
from an earlier packaged-APK plan; exported APKs do not bundle this runtime, so
run the project from the host entry.

- Capabilities: `app`
- Expected provider: host `app` bridge (`packageName`, `versionName`)
- Packaged support: not applicable

Expected output is listed in `expected-output.txt`.
