<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 용 Node.js 24.5.0 네이티브 런타임 플러그인</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어 (Languages)

******

현재 README.md 는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### 소개

******

AutoJs6 Node.js Runtime 플러그인은 AutoJs6 에 내장 Node.js 24.5.0 네이티브 런타임을 제공하여 Node.js 스크립트와 플러그인 런타임 작업을 실행합니다.

******

### 기능

******

- `nodejs` 플러그인 서비스를 제공하며 플러그인 ID 는 `nodejs`, 엔진은 `nodejs` 입니다.
- `org.autojs.plugin.nodejs.RUNTIME` 를 통해 동기 스크립트 실행과 런타임 프리웜을 호스트에 제공합니다.
- 대기열과 실행 전체에 적용되는 스크립트 시간 제한을 추가하고, 초과 시 `ERR_AUTOJS6_SCRIPT_TIMEOUT`을 반환합니다. 시간 제한을 지정하지 않으면 계속 무기한 실행할 수 있습니다
- `dgram` (UDP)과 `http2`를 기본으로 활성화하고, `trace_events`에는 명확한 비활성화 오류를 반환합니다
- Debug 빌드에서 명시적으로 활성화하는 로컬 `inspector` 디버깅을 추가했습니다. localhost에서만 수신하고 `adb forward`로 연결합니다
- CommonJS/ESM 소스, 모듈 소스, 작업 디렉터리, 샌드박스 루트, 환경 변수, stdout/stderr 결과 페이로드를 지원합니다.
- ESM 엔트리와 dynamic `import()` 에 V8 native linker 를 사용하여 순환 의존성, 변경 가능한 export, re-export live binding 을 보존합니다; CommonJS `require(esm)` 은 동기 상호 운용 경계를 유지합니다.
- 플러그인 앱의 Android 권한을 경계로 데스크톱과 유사한 파일 시스템 접근을 제공하며 `/proc`, `/sys`, `/dev` 는 항상 거부합니다.
- 호스트가 제공한 TypeScript 출력을 실행하고 실행 중 생성된 project `.ts`/`.mts`/`.cts`에 provider-v3 compilation을 요청할 수 있습니다. runtime 타입 제거 fallback과 호환 스위치가 삭제되어 direct raw dispatch는 항상 fail-closed입니다.
- 호스트 기능 브로커와 live bridge 를 제공하며 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 같은 런타임 모듈을 주입할 수 있습니다.
- `sample/nodejs` 프로젝트와 호스트 API 능력 목록 `docs/HOST-API.md` 를 포함합니다.
- 플러그인 정보, 사용 설명, README, CHANGELOG 는 스페인어/프랑스어/러시아어/아랍어/일본어/한국어/영어/간체 중국어/홍콩 번체/대만 번체를 지원합니다.

******

### 사용 예

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

AutoJs6 플러그인 센터에서 플러그인을 설치하고 활성화한 뒤 `"nodejs";` 지시문으로 Node.js 스크립트를 시작합니다. 더 많은 예제는 `sample/nodejs` 에 있습니다.

******

### 빠른 시작

******

- **설치** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) 에서 ABI 에 맞는 APK 를 내려받아 설치합니다 (모르면 `universal` 선택). 또는 `.\gradlew.bat :app:assembleDebug` 로 로컬 빌드 후 `app/build/outputs/apk/debug/` 에서 설치합니다. 그런 다음 AutoJs6 플러그인 센터에서 이 플러그인을 활성화합니다. Android 11 이상에서 공유 저장소가 필요하면 시스템 설정에서 이 플러그인에 모든 파일 접근 권한을 부여하세요. 권한이 없을 때의 `EACCES` 는 예상 동작입니다.
- **실행** — AutoJs6 편집기에서 첫 줄이 `"nodejs";` 인 스크립트를 만들고 나머지는 데스크톱 Node.js 처럼 작성합니다 (CommonJS/ESM, 순수 JS npm 패키지, 네트워크 내장 모듈 지원). 실행하면 출력이 실시간으로 스트리밍되며 언제든 중지할 수 있습니다. 원시 `.ts`/`.mts`/`.cts` 는 먼저 호스트에서 JavaScript 로 컴파일해야 하며 플러그인에는 `tsc` 가 포함되지 않습니다.
- **오류 발생 시 확인 위치** — 스크립트 실패 시 콘솔에 JS 스택과 한 줄 오류 코드 (예: `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`) 가 표시됩니다. 자세한 내용은 `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin` 으로 플러그인 프로세스 로그를 확인하세요. 호스트 API 가용성은 `docs/HOST-API.md` 를 참조하세요.

******

### 런타임 프로필

******

- 런타임 슬롯: `node24_5`.
- 플러그인 ID: `nodejs`, 엔진: `nodejs`.
- 런타임 서비스 액션: `org.autojs.plugin.nodejs.RUNTIME`.
- 네이티브 런타임 라이브러리: `libnode.so` 및 `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 그리고 `universal`.
- 파일 시스템: Android 권한이 허용하는 기기 경로에 접근할 수 있고 `/proc`, `/sys`, `/dev` 는 엄격한 경계입니다.
- TypeScript: host output을 허용하고 실행 중 생성된 project file에 provider-v3 compilation을 요청할 수 있습니다. direct raw TypeScript는 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`를 반환합니다.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 네이티브 live binding 및 순환 의존성 지원.
- 기능: 동기 스크립트 실행, bundle transport, 네이티브 내장 런타임, 호스트 기능 브로커, host capability live bridge.

******

### 릴리스 기록

******

# v1.2.0

###### 2026/08/29

* `추가` `mediainfo` facade에 `capabilities()`를 추가하고 호출 가능한 진입점과 `read()`에서 플러그인 snapshot v1/v2를 명시적으로 선택하도록 지원; schema를 생략하면 기존 호스트 소유 Node v1 snapshot을 계속 반환
* `추가` 실행 중 생성된 `.ts/.mts/.cts`를 byte count와 SHA-256으로 고정된 PFD를 통해 on-demand compile하는 module-source provider v3를 추가하고, 독립적인 30 s compilation budget, 안정적인 path escape/symlink/ambiguity 거부, host TypeScript diagnostics 및 Source Map stack mapping을 제공
* `추가` Android 앱 권한 범위에서 데스크톱과 유사한 파일 시스템 접근을 활성화하고 `/proc`, `/sys`, `/dev` 는 계속 거부
* `추가` 전용 기능 `accessibility.gesture` 아래에 `accessibility.swipe` 와 `accessibility.gesture` 추가
* `추가` 대기열과 실행 전체에 적용되는 스크립트 시간 제한을 추가하고, 초과 시 `ERR_AUTOJS6_SCRIPT_TIMEOUT`을 반환합니다. 시간 제한을 지정하지 않으면 계속 무기한 실행할 수 있습니다
* `추가` `dgram` (UDP)과 `http2`를 기본으로 활성화하고, `trace_events`에는 명확한 비활성화 오류를 반환합니다
* `추가` Debug 빌드에서 명시적으로 활성화하는 로컬 `inspector` 디버깅을 추가했습니다. localhost에서만 수신하고 `adb forward`로 연결합니다
* `추가` 호스트 플러그인 권한으로 보호되는 화면 없는 활성화 진입점을 추가하고 플러그인 센터 설명을 보완했으며 앱 데이터 백업을 비활성화했습니다
* `수정` 호스트가 컴파일러 출력을 제공하지 않은 원시 TypeScript 를 fail-closed 로 변경하고 snapshot 동적 import 매핑과 생성/가져온 스택 프레임 정규화 추가
* `수정` snapshot 기반 partial ESM adapter 를 V8 native linker 로 교체하여 순환 re-export 에서 변경 가능한 export 가 갱신되지 않던 문제 수정
* `수정` 설치된 npm 패키지 우선순위를 유지하면서 AutoJs6 호환 퍼사드의 ESM 가져오기와 존재하지 않는 TypeScript 확장자 탐색을 수정
* `개선` regex 기반 legacy TypeScript 타입 제거 fallback과 request switch를 삭제하여 raw `.ts/.mts/.cts`가 항상 host compiler output을 요구하도록 변경
* `개선` 호스트/플러그인 v2 계약, 기능 매니페스트 및 plugin-only 런타임 책임 경계를 정렬
* `개선` Node.js 샘플, TypeScript 타입 선언, 프로젝트 마법사, 런타임 기본값 및 호스트 정렬 검사를 플러그인 저장소로 통합하고 호스트 측 Gradle 스위치와 중복 개발 자산을 제거
* `개선` 검증된 npm 패키지를 15개로 확대하고 axios, express 및 ESM-only 패키지 nanoid, p-limit, yocto-queue를 추가했습니다
* `개선` 요청의 `executionMode` 필드가 수명 주기 모드를 결정하도록 하고, 효과가 없는 `runtimeAdapter` 필드를 사용 중단 예정으로 표시했습니다
* `개선` 고정 상태만 출력하던 기존 예제 10개를 제거하고 화면 캡처, 이미지 분석, 녹음에 호스트 provider가 없음을 타입 선언에 명시했습니다

# v1.1.0

###### 2026/08/18

* `추가` stdout/stderr 실시간 스트리밍과 `node::Stop` 기반 협력 취소 추가
* `추가` BUSY 즉시 거부를 최대 3개 대기의 제한된 직렬 큐로 교체하고 상주 장기 실행 스크립트 수명 주기 지원 추가
* `추가` Node 네이티브 네트워크 내장 모듈, `worker_threads`, `child_process` 를 기본 활성화하고 인기 순수 JavaScript npm 패키지 10개 검증
* `개선` direct-run 워크스페이스와 v1..v2 모듈 소스 provider 관대한 협상을 추가하고 간결한 오류 코드와 JavaScript 스택으로 정리

# v1.0.0

###### 2026/07/18

* `추가` 플러그인 ID `nodejs`, 엔진 `nodejs`, 런타임 슬롯 `node24_5` 를 가진 Node.js 런타임 플러그인 서비스를 추가
* `추가` `libnode.so` 및 `libautojs6-node.so` 를 통해 Node.js 24.5.0 네이티브 런타임을 제공
* `추가` Node.js 런타임을 독립 상주 프로세스에서 실행하고 프로세스 전역 Node/V8 상태를 재사용하면서 실행마다 새로운 isolate와 Environment 생성
* `추가` `org.autojs.plugin.INFO` 를 통한 플러그인 정보 검색과 `org.autojs.plugin.nodejs.RUNTIME` 를 통한 런타임 호출을 추가
* `추가` CommonJS/ESM 소스, 모듈 소스, 작업 디렉터리, 샌드박스 루트, 환경 변수, stdout/stderr 결과 페이로드, 런타임 프리웜을 지원
* `추가` 요청 범위 워크스페이스 아카이브 전송 v2에서 명시적 입력 매핑, 플러그인 전용 워크스페이스 실행, 출력 쓰기 반영, 삭제 tombstone 매니페스트를 지원하며 호스트 샌드박스를 스캔하지 않음
* `추가` 단일 활성 제로 큐 승인 제어에서 `ERR_AUTOJS6_NODE_PLUGIN_BUSY` 백프레셔를 반환하고 프로세스 재시작 방식 취소 지원
* `추가` `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 같은 런타임 모듈을 사용하는 호스트 기능 브로커와 live bridge 를 추가
* `추가` `arm64-v8a`, `armeabi-v7a`, `x86_64`, 그리고 `universal` APK 용 ABI 분할 빌드를 추가
* `추가` 분할 및 `universal` APK의 Node.js 런타임 라이브러리와 함께 `libc++_shared.so` 패키징
* `추가` `sample/nodejs` 프로젝트, Node resolver 진단 도구, 런타임 빌드 계획 검증 도구를 추가
* `추가` 스페인어/프랑스어/러시아어/아랍어/일본어/한국어/영어/간체 중국어/홍콩 번체/대만 번체용 플러그인 정보, 사용 설명, README, CHANGELOG 리소스를 추가
* `수정` 프로세스 재시작 방식 취소 중 런타임 프로세스를 회수할 때 불완전한 워크스페이스 스냅샷이 커밋될 수 있는 문제
* `수정` 요청 계약 검증 또는 워크스페이스 생성 실패 시 모든 종료 경로에서 소유권 처리가 완료되지 않아 워크스페이스 아카이브 파일 디스크립터가 누수될 수 있는 문제
* `개선` ABI/기능/상주 런타임 상태/승인/취소/독립 프로세스 귀속을 포괄하는 R5 계약 메타데이터 및 진단 정비
* `개선` 실행 소스 구성/부트스트랩/스크립트 실행/결과 생성과 조회/실행별 정리에 단조 시계 기반 단계별 진단을 추가하고 건너뜀과 해당 없음 상태를 구분

##### 더 많은 릴리스 기록

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-ko.md)

******

### 빌드

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 빌드:

```powershell
.\gradlew.bat :app:assembleRelease
```

빌드 매개변수는 `version.properties` 에서 가져오며 현재 최소 SDK 는 24, 대상 SDK 는 36 입니다.

******

### 리소스 구조

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` 은 현지화된 플러그인 설명을 제공합니다; `plugin_instruction.md` 는 호스트에 표시되는 사용 설명을 제공합니다. README 와 CHANGELOG 는 `.python/generate_markdown.py` 가 JSON 소스에서 생성합니다.

******

### 관련 링크

******

- AutoJs6 문서: https://docs.autojs6.com
- Node.js 공식 프로젝트: https://github.com/nodejs/node
- Node.js 런타임 빌드 계획: tools/nodejs/runtime-build/README.md
