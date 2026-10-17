# host-api-coverage

Runs the public ESM Pinyin/Pinyin4j wrappers, current-display coordinate conversion and Android console window controls through AutoJs6.

Install the Pinyin and Pinyin4j providers and grant AutoJs6 overlay permission first. Open this project in AutoJs6 and run `main.mjs`. The project declares `device`, `console`, `pinyin` and `pinyin4j`. All host calls are awaited, including the console controls and `rows.compact()`.

The result prints `sample.host-api-coverage=PASS` with the current display snapshot. Run once in portrait and once in landscape. Keep the orientation unchanged during one run so that assertions compare the same display. The sample sets both scale bases once, verifies a repeated setter fails and checks explicit pixel bases and the `16:9` aspect ratio. The console window appears for 1.5 seconds before closing.

Provider failures are reported as errors. This sample does not replace missing Pinyin dictionaries with local test data.
