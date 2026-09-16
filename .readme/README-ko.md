<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 용 Node.js 24.21.0 네이티브 런타임 플러그인</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
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

AutoJs6 Node.js Runtime 플러그인은 AutoJs6 에 내장 Node.js 24.21.0 네이티브 런타임을 제공하여 Node.js 스크립트와 플러그인 런타임 작업을 실행합니다. Android 17 이상에서는 이 플러그인의 근처 기기 권한을 허용해야 로컬 네트워크 기기에 연결할 수 있습니다. AutoJs6 권한은 이 플러그인에 적용되지 않습니다. 공용 인터넷 및 루프백 연결에는 이 권한이 필요하지 않습니다.

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
- 멀티콜 터미널 런처 `libnodexe.so`와 npm / corepack 자산을 포함하고 `NODE_CLI_*` manifest meta-data로 선언하여, AutoJs6 터미널 (호스트 6.8.0 이상) 이 자신의 uid shell에서 `node`, `npm`, `npx`, `corepack`, `yarn`, `pnpm`을 실행할 수 있습니다.

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

- 런타임 슬롯: `node24_21`.
- 플러그인 ID: `nodejs`, 엔진: `nodejs`.
- 런타임 서비스 액션: `org.autojs.plugin.nodejs.RUNTIME`.
- 네이티브 런타임 라이브러리: `libnode.so`, `libautojs6-node.so` 및 `libnodexe.so`.
- Intl: 영어 로케일 데이터만 포함한 ICU 78 (`--with-intl=small-icu`); `Intl`, 정규식의 유니코드 속성 이스케이프, Node 자체의 stderr 오류 출력이 AutoJs6 터미널에서 동작하며, `NODE_ICU_DATA` 로 전체 ICU 데이터 파일을 지정할 수 있습니다.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 그리고 `universal`.
- 파일 시스템: Android 권한이 허용하는 기기 경로에 접근할 수 있고 `/proc`, `/sys`, `/dev` 는 엄격한 경계입니다.
- TypeScript: host output을 허용하고 실행 중 생성된 project file에 provider-v3 compilation을 요청할 수 있습니다. direct raw TypeScript는 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`를 반환합니다.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 네이티브 live binding 및 순환 의존성 지원.
- 기능: 동기 스크립트 실행, bundle transport, 네이티브 내장 런타임, 호스트 기능 브로커, host capability live bridge.

******

### 릴리스 기록

******

# v1.5.3

###### 2026/09/16

* `개선` Android 17 (SDK 37) 대응 및 플러그인별 로컬 네트워크 권한 설정과 복구 안내 제공

# v1.5.2

###### 2026/09/15

* `개선` compileSdk 를 37 (Android 17) 로 올리며, targetSdk 는 대상 버전에 의존하는 동작을 검증할 때까지 36 으로 유지

# v1.5.1

###### 2026/09/15

* `수정` Node 자체의 치명적 오류와 경고 텍스트를 Android 에서 logcat 외에 실제 stderr 에도 기록하여, 터미널이 조용히 종료되는 대신 처리되지 않은 예외를 표시합니다
* `개선` `libnode.so` 를 `--with-intl=small-icu` 로 다시 빌드: 영어 로케일 데이터만으로 `Intl` 과 정규식의 유니코드 속성 이스케이프 (`\p{...}`) 를 사용할 수 있으며 (`NODE_ICU_DATA` 로 전체 ICU 데이터 파일 지정 가능), 이에 따라 corepack 이 AutoJs6 터미널에서 pnpm 11 과 Yarn Berry 를 실행할 수 있습니다

##### 더 많은 릴리스 기록

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
