# ocr-automation

Requests Android screen capture consent, captures the screen, and recognizes its text through an OCR plugin chosen by the host: the first enabled one in ML Kit OCR, Paddle OCR, Rapid OCR order (hosts before AutoJs6 6.8.0 always use ML Kit OCR). Install and enable at least one of them, run the example, and allow capture of the entire screen. The sample prints up to 160 characters and releases the image and capture session in `finally`.

- Capabilities: `screen_capture`, `ocr`, `image`
- Expected provider: Android OCR provider over opaque image handles
- Packaged support: exported APK integration is tracked by Roadmap M24
- Security limitations: raw bitmap, OCR engine, and native model objects are never exposed to Node

Expected output is listed in `expected-output.txt`.

Denied consent or an unavailable recognition plugin produces a readable skip; other failures stop the sample. Known host UI text and QR recognition have device coverage using instrumentation screenshots. A real screen-capture run requires user consent and is accepted only when the expected capture and recognition results are produced; a skipped run is not acceptance evidence.
