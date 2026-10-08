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

AutoJs6 Node.js Runtime 플러그인은 AutoJs6 에 내장 Node.js 24.21.0 네이티브 런타임을 제공하여 Node.js 스크립트와 플러그인 런타임 작업을 실행합니다. Android 17 이상에서는 AutoJs6 플러그인 센터에서 이 플러그인을 켜기 전에 주변 기기 권한을 허용하세요. 이 플러그인의 설정 페이지에서도 로컬 네트워크 권한을 관리할 수 있습니다. 권한이 없으면 플러그인이 꺼진 상태로 유지되고 자동 시작은 알림 없이 건너뜁니다. 이 권한은 플러그인에 속하며 AutoJs6 권한과 별개입니다.

******

### 기능

******

- `nodejs` 플러그인 서비스를 제공하며 플러그인 ID 는 `nodejs`, 엔진은 `nodejs` 입니다.
- `org.autojs.plugin.nodejs.RUNTIME` 를 통해 동기 스크립트 실행과 런타임 프리웜을 호스트에 제공합니다.
- 대기열과 실행 전체에 적용되는 스크립트 시간 제한을 추가하고, 초과 시 `ERR_AUTOJS6_SCRIPT_TIMEOUT`을 반환합니다. 시간 제한을 지정하지 않으면 계속 무기한 실행할 수 있습니다
- 모든 Node.js 내장 모듈을 Node 자체의 `module.builtinModules` 목록에서 그대로 반환합니다 (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` 등); 요청 수준 게이트는 `worker_threads`, `child_process`, `inspector` 및 네이티브 네트워크 모듈에만 남습니다 (1.6.0 부터).
- Debug 빌드에서 명시적으로 활성화하는 로컬 `inspector` 디버깅을 추가했습니다. localhost에서만 수신하고 `adb forward`로 연결합니다
- CommonJS/ESM 소스, 모듈 소스, 작업 디렉터리, 샌드박스 루트, 환경 변수, stdout/stderr 결과 페이로드를 지원합니다.
- 모듈을 Node.js 고유 CommonJS / ESM 로더로 실행합니다 (`require`, `import()`, `import.meta`, `require(esm)`, 패키지 exports / imports 는 네이티브); 런타임은 `autojs6:*` 런타임 모듈, 호스트 제공 모듈 소스, 요청 단위 builtin 게이트를 위한 `module.registerHooks` 만 등록합니다 (1.6.0 부터).
- Node.js 고유 fs 모듈을 사용하며 파일 접근 가능 여부는 플러그인 앱의 Android 권한만으로 결정됩니다; 런타임 자체의 `/proc`, `/sys`, `/dev` 거부는 없습니다 (1.6.0 부터).
- 호스트가 제공한 TypeScript 출력을 실행하고 실행 중 생성된 project `.ts`/`.mts`/`.cts`에 provider-v3 compilation을 요청할 수 있습니다. runtime 타입 제거 fallback과 호환 스위치가 삭제되어 direct raw dispatch는 항상 fail-closed입니다.
- 호스트 기능 브로커와 live bridge 를 제공하며 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 같은 런타임 모듈을 주입할 수 있습니다.
- `sample/nodejs` 프로젝트와 호스트 API 능력 목록 `docs/HOST-API.md` 를 포함합니다.
- 플러그인 정보, 사용 설명, README, CHANGELOG 는 스페인어/프랑스어/러시아어/아랍어/일본어/한국어/영어/간체 중국어/홍콩 번체/대만 번체를 지원합니다.
- 멀티콜 터미널 런처 `libnodexe.so`와 npm / corepack 자산을 포함하고 `NODE_CLI_*` manifest meta-data로 선언하여, AutoJs6 터미널 (호스트 6.8.0 이상) 이 자신의 uid shell에서 `node`, `npm`, `npx`, `corepack`, `yarn`, `pnpm`을 실행할 수 있습니다.
- 플러그인 설정에서 동시 실행 슬롯 1-8 개 (기본 2) 및 대기열 한도 (기본 무제한) 설정 지원; 초과 스크립트는 FIFO 로 대기하며 설정 변경 시 수락된 작업을 유지하고 idleExitMs 로 유휴 실행 프로세스 회수

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
- Intl: 전체 로케일 데이터가 포함된 ICU 78 (`--with-intl=full-icu`, 1.6.0 부터; 1.5.1 ~ 1.5.7 은 영어 전용 small-icu); `Intl`, 로케일 서식, 정규식의 Unicode 속성 이스케이프 및 Node 자체의 stderr 오류 출력이 데스크톱 Node 와 일치하며 `NODE_ICU_DATA` 가 필요하지 않습니다.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 그리고 `universal`.
- 파일 시스템: Node.js 고유 fs 모듈, Android 권한이 허용하는 모든 기기 경로에 접근할 수 있으며 런타임 자체의 거부는 없습니다.
- TypeScript: 호스트 출력을 받아들이고 사전 컴파일 스냅샷을 매핑하며 provider v3 컴파일을 요청할 수 있습니다; 디스크상의 raw TypeScript 모듈은 Node 고유의 타입 제거로 로드되고, 직접 디스패치된 raw TypeScript 진입 스크립트는 여전히 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` 를 반환합니다.
- ESM: `module.registerHooks` 를 갖춘 Node.js 고유 로더; 라이브 바인딩, 순환 의존성, `import.meta`, `data:` URL, `require(esm)` 가 데스크톱과 동일합니다.
- 기능: 동기 스크립트 실행, bundle transport, 네이티브 내장 런타임, 호스트 기능 브로커, host capability live bridge.
- 파일 스트림, gzip/deflate/Brotli 스트림 및 기본 VM 실행을 정식 지원; 명시적으로 활성화한 Inspector (Debug/Release)는 localhost 범위에서 정식 지원.
- 내장 실행에서 ExperimentalWarning 출력을 중단하고 warning 이벤트, 일반 경고, 사용 중단 경고 및 오류를 유지; Node 상위 API 안정성과 터미널 기본 동작은 변경되지 않음.
- 네이티브 Worker가 중첩, MessagePort API, stdio, argv, execArgv 및 사전 로드 상속을 지원; 플러그인의 Worker 수와 시작 시간 제한을 제거하고 요청 resourceLimits를 재정의 가능한 기본값으로 변경; WorkerPool은 기본 대기열 및 작업 시간 제한이 없으며 Inspector 세션은 실행 요청과 현재 빌드 지원을 따름
- 메인 스크립트와 Worker에서 Node를 통해 Android 네이티브 애드온을 로드 가능; process.dlopen과 .node는 기본 동작을 사용하며 별도 서명 또는 해시 허용 목록이 없음; native-addon은 3개 ABI의 16 KB 정렬 Node-API 바이너리와 오프라인 NDK/CMake 빌드를 제공; ABI 불일치나 손상은 ERR_DLOPEN_FAILED 등의 Node 오류로 반환되며 기기용 NDK 또는 Python는 포함하지 않음
- 호스트의 명시적 허용으로 Debug 및 Release 빌드에서 Inspector 사용 가능; inspector와 inspector/promises는 Node 기본 모듈 객체를 유지하고 메인 스크립트와 Worker에서 Session을 지원하며, 수신 주소는 127.0.0.1로 제한되고 블로킹 대기는 지원하지 않음; 허용되지 않으면 url()은 undefined를 반환하고 활성화 및 Session 생성은 비활성화됨
- package_manager의 install/update/remove가 npm 패키지 사양을 받아 작업 공간에서 내장 npm 실행; npm.cli/exec/runScript/pack은 실제 CLI를 사용하고 registry/프록시/수명 주기 옵션은 npm 및 프로젝트 .npmrc를 따르며, 출력을 스트리밍하고 npm 오류 코드를 유지; CLI 파일은 최초 사용 시 준비하고 객체 요청은 기존 호스트 로컬 저장소 계약을 유지

******

### 릴리스 기록

******

# v1.6.0

###### 2026/10/08

* `추가` 플러그인 설정에서 동시 실행 슬롯 1-8 개 (기본 2) 및 대기열 한도 (기본 무제한) 설정 지원; 초과 스크립트는 FIFO 로 대기하며 설정 변경 시 수락된 작업을 유지하고 idleExitMs 로 유휴 실행 프로세스 회수
* `개선` fs, fs/promises 및 node:sqlite 가 Node.js 고유 모듈이 되었습니다: 런타임 자체의 /proc, /sys, /dev 거부와 경로 검증 래퍼를 제거하여 파일 접근 가능 여부는 Android 권한만으로 결정되고, 비동기 파일 API 는 이벤트 루프를 막지 않고 libuv 스레드 풀에서 실행되며, 경로 오류는 Node.js 고유 코드를 반환합니다 (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH 및 ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED 는 더 이상 파일 / 데이터베이스 작업에서 발생하지 않음)
* `개선` process.cwd 와 process.chdir 가 Node.js 고유 동작이 되어 실행 시작 시 실제 chdir 로 작업 디렉터리에 진입하고 상대 경로는 Node.js 가 직접 해석합니다
* `개선` node:sqlite 의 ATTACH (바인드 매개변수 및 표현식 파일명 포함), VACUUM INTO, 디렉터리 PRAGMA 및 setAuthorizer 는 SQLite 고유 동작이며, enableLoadExtension 과 allowExtension 은 Node.js 고유 상태 검사를 따릅니다
* `개선` Node.js 런타임 라이브러리를 전체 ICU 데이터로 빌드 (--with-intl=full-icu) 하여 Intl 및 로케일 서식이 데스크톱 Node.js 와 일치하고 NODE_ICU_DATA 가 더 이상 필요하지 않습니다
* `개선` crypto.createPrivateKey 및 서명 API 가 file: URL 형식의 OpenSSL STORE 키 입력을 지원합니다 (Android 전용 비활성화 패치 제거)
* `개선` 모든 Node.js 내장 모듈 (cluster, domain, repl, trace_events, wasi, constants, node:sea 등) 을 Node 자체의 module.builtinModules 목록에서 그대로 반환하여 require, require.resolve, import, process.getBuiltinModule, module.isBuiltin 이 데스크톱과 동일하게 동작합니다; 요청 수준 게이트는 worker_threads, child_process, inspector 및 네이티브 네트워크 모듈에만 남고 node:wasi 는 /proc 경계 제거와 함께 개방되었습니다 (tracing 없이 빌드된 현재 libnode 에서는 trace_events 가 Node 와 동일하게 ERR_TRACE_EVENTS_UNAVAILABLE 을 던집니다)
* `개선` process는 exit의 수명주기 처리만 유지; dlopen, abort, kill, umask, binding, report, features 및 POSIX ID 호출은 Node 기본 구현을 사용; os는 네이티브이며 homedir과 tmpdir은 HOME/TMPDIR을 따르고 userInfo는 Android 앱 사용자를 반환
* `개선` module 내장 모듈은 createRequire 만 재정의한 Node.js 고유 객체가 되어 registerHooks, enableCompileCache, findPackageJSON 등을 사용할 수 있습니다; 네이티브 네트워크 비활성화 시에만 도달하던 http, https, net, tls, dns 제한 구현을 제거하고 해당 모드에서는 ERR_AUTOJS6_BUILTIN_DISABLED 를 직접 반환합니다
* `개선` 플러그인 정보 (INFO) 와 런타임 진단의 supportedAbis는 이제 빌드 시 매니페스트에 기록되는 org.autojs.plugin.contract.SUPPORTED_ABIS meta-data (AutoJs6-Gradle-Platform-Versions 1.9.0 이상의 nativeAlignment 플러그인) 에서 읽으며, 설치된 split이 아니라 플러그인이 빌드된 모든 ABI를 의미; meta-data가 없으면 설치된 APK에 패키징된 ABI로 되돌아가고 수기로 작성된 ABI 상수는 제거
* `개선` 모듈 로딩이 Node.js 고유 CommonJS / ESM 로더로 바뀌어 require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta, require(esm) 가 모두 네이티브 동작이며, 진입 스크립트는 Node 의 메인 모듈로 실행 (require.main === module, __filename / process.argv[1] 은 realpath); 런타임은 module.registerHooks 만 등록하여 autojs6:* 런타임 모듈과 호환 shim (autojs6: scheme, 같은 이름의 설치된 npm 패키지가 여전히 shim 보다 우선), 요청 단위 builtin 게이트, 요청 첨부 / provider 실체화 / TypeScript 스냅샷 모듈 소스와 진입 스크립트의 인라인 소스를 제공; vm.SourceTextModule 자체 링커, 자체 resolver, 동적 import() 재작성, eval / Function 가드는 모두 제거되고 esmEnabled / dynamicImportEnabled 요청 키는 더 이상 영향을 주지 않음
* `개선` 네이티브 Worker가 중첩, MessagePort API, stdio, argv, execArgv 및 사전 로드 상속을 지원; 플러그인의 Worker 수와 시작 시간 제한을 제거하고 요청 resourceLimits를 재정의 가능한 기본값으로 변경; WorkerPool은 기본 대기열 및 작업 시간 제한이 없으며 Inspector 세션은 실행 요청과 현재 빌드 지원을 따름
* `개선` Worker와 중첩 Worker에서 부모 MessagePort를 통해 autojs6:bridge.callAutoJs를 사용 가능; 호스트 broker의 권한 검사, 오류, 시간 초과 및 취소를 유지하며 worker-host-bridge 예제로 실제 호스트 기기 정보 조회를 제공; 브리지 API의 TypeScript 선언도 제공
* `개선` 원본 .ts / .mts / .cts 진입점과 가져온 모듈에는 여전히 호스트 Compiler가 생성한 JavaScript가 필요합니다. 스냅샷 매핑과 provider v3의 요청 시 컴파일을 유지하고, 기존 AutoJs6 ESM 및 동적 가져오기 오류 코드를 Node 기본 오류로 대체했습니다.
* `개선` 메인 스크립트와 Worker에서 Node를 통해 Android 네이티브 애드온을 로드 가능; process.dlopen과 .node는 기본 동작을 사용하며 별도 서명 또는 해시 허용 목록이 없음; native-addon은 3개 ABI의 16 KB 정렬 Node-API 바이너리와 오프라인 NDK/CMake 빌드를 제공; ABI 불일치나 손상은 ERR_DLOPEN_FAILED 등의 Node 오류로 반환되며 기기용 NDK 또는 Python는 포함하지 않음
* `개선` 호스트의 명시적 허용으로 Debug 및 Release 빌드에서 Inspector 사용 가능; inspector와 inspector/promises는 Node 기본 모듈 객체를 유지하고 메인 스크립트와 Worker에서 Session을 지원하며, 수신 주소는 127.0.0.1로 제한되고 블로킹 대기는 지원하지 않음; 허용되지 않으면 url()은 undefined를 반환하고 활성화 및 Session 생성은 비활성화됨
* `개선` package_manager의 install/update/remove가 npm 패키지 사양을 받아 작업 공간에서 내장 npm 실행; npm.cli/exec/runScript/pack은 실제 CLI를 사용하고 registry/프록시/수명 주기 옵션은 npm 및 프로젝트 .npmrc를 따르며, 출력을 스트리밍하고 npm 오류 코드를 유지; CLI 파일은 최초 사용 시 준비하고 객체 요청은 기존 호스트 로컬 저장소 계약을 유지

# v1.5.7

###### 2026/10/05

* `개선` 플러그인 센터 아이콘에 Icon Studio에서 조정한 크기, 위치, 밝은 이미지와 어두운 이미지 및 원형 배경을 적용하고 재생성 가능한 원본과 매개변수를 유지
* `개선` Android 앱 정보 아이콘에 Icon Studio의 그림과 밝은 배경 및 어두운 배경을 사용하고 플러그인 센터의 투명 그림과 기존 런처 옵션을 유지

# v1.5.6

###### 2026/09/19

* `추가` 런타임 슬롯에서 Node.js 치명적 오류 (JavaScript 힙 메모리 부족, 내부 검사 실패) 가 발생하면 이제 Node 자체 진단 보고서를 플러그인 캐시에 남깁니다 (crash/<executionId>.json, 중단 전에 기록, 시그널 핸들러는 설치하지 않음). 실패 결과는 slotExit.crash 와 오류 메시지에 이벤트, 힙 사용량, JS 스택, 보고서 경로를 담고 (예: CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), getRuntimeInfo 는 최신 보고서를 lastCrash 로 노출합니다 (디스패처 재시작 후에도 읽힘). 새 요청 옵션 maxOldGenerationSizeMb 는 한 번의 실행에 대한 JS 힙 상한 (V8 old generation, MB 단위) 을 설정하며 회귀 테스트도 이를 통해 해당 크래시를 재현합니다
* `수정` 큰 CommonJS 모듈을 require 할 때 런타임 슬롯이 V8 힙 메모리 부족으로 중단되던 (CRASH_NATIVE / SIGABRT 로 보고) 문제를 수정했습니다. 로더의 주석 및 문자열 마스킹이 한 문자씩 이어 붙여 22 MiB 모듈에 약 700 MB 의 힙이 필요했으나, 이제 동일한 결과를 연속 구간 단위로 생성하고 (약 44 MB) CommonJS 의 import / export 스캔도 모든 문자를 검사하는 대신 후보 위치 사이를 건너뜁니다
* `수정` 공유 빌드 플러그인 1.8.3을 통해 AGP 9.1의 SDK XML v4 파싱 경고 및 JVM 단위 테스트 조립 작업에서 APK 네이티브 라이브러리 정렬 검사가 잘못 실행되는 문제 해결

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
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
