# AutoJs6 Node.js Runtime

이 플러그인은 AutoJs6 에 Node.js 24.5.0 네이티브 런타임을 제공합니다.

AutoJs6 플러그인 센터에서 설치하고 활성화한 뒤 다음 지시문으로 스크립트를 시작합니다:

```js
"nodejs";

console.log(process.version);
```

런타임 슬롯은 `node24_5` 입니다. `arm64-v8a`, `armeabi-v7a`, `x86_64` 빌드와 `universal` APK 를 지원합니다.

예제 프로젝트는 `sample/nodejs` 에 있습니다.
