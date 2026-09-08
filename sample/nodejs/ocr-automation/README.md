# ocr-automation

Requests Android screen capture consent, captures the screen, and recognizes its text through the host ML Kit OCR plugin. Install and enable that plugin, run the example, and allow capture of the entire screen. The sample prints up to 160 characters and releases the image and capture session in `finally`.

- Capabilities: `screen_capture`, `ocr`, `image`
- Expected provider: Android OCR provider over opaque image handles
- Packaged support: model assets and live OCR provider policy are future gated
- Security limitations: raw bitmap, OCR engine, and native model objects are never exposed to Node

Expected output is listed in `expected-output.txt`.

Denied consent or an unavailable recognition plugin produces a readable skip; other failures stop the sample. Known host UI text and QR recognition have device coverage using instrumentation screenshots. The MediaProjection acceptance test still awaits manual consent, so this example remains partial.
