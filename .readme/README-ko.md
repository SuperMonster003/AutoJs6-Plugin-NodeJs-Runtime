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
- 파일 스트림, gzip/deflate/Brotli 스트림 및 기본 VM 실행을 정식 지원; 명시적으로 활성화한 Debug Inspector는 localhost 범위에서 정식 지원.
- 내장 실행에서 ExperimentalWarning 출력을 중단하고 warning 이벤트, 일반 경고, 사용 중단 경고 및 오류를 유지; Node 상위 API 안정성과 터미널 기본 동작은 변경되지 않음.

******

### 릴리스 기록

******

# v1.5.5

###### 2026/09/17

* `수정` runtimeInfo가 보고하는 기능 카탈로그 버전과 다이제스트를 runtime kit에서 파생하도록 변경하여 1.5.4에 남아 있던 1.5.0 이전 값을 수정
* `수정` worker_threads가 Node와 같이 new Worker(code, { eval: true })를 지원하며 코드 문자열을 스크립트 경로로 취급하지 않음
* `개선` 호스트 TypeScript 컴파일 경로를 기능 카탈로그에서 stable로 전환하고 퇴역한 legacy stripping 메타데이터를 제거; 수명주기 카탈로그는 실행 가능한 모드만 나열하며 packaged_long_running 실행 표면과 node_sandboxed / worker_computation 예약 이름을 제거; 전송, 수락 및 취소 메타데이터를 실제 런타임에 맞게 정렬
* `개선` autojs6:profile 진단 객체가 과거의 partial / reserved / deferred 표기 대신 실제 런타임을 설명: Android 권한에 따른 파일 접근, process 부분 집합, worker 채널, 프로세스 풀 및 WASI / native addon 결정
* `개선` 샘플 점검: packaged-esm, packaged-dynamic-import, require-esm, wasm-basic, wasm-plugin, desktop-parity-suite를 stable로 승격. 두 parity 스위트는 카탈로그 문구를 출력하는 대신 스니펫을 실제로 실행. compile-cache는 해당 없음으로 표시하고 메타데이터만 출력하던 package-install 샘플 삭제
* `개선` 모듈 로딩이 Node처럼 Android 파일 접근을 따름: require, import, Worker가 절대 경로, 상위 디렉터리 대상, file: URL을 허용 (/proc, /sys, /dev는 계속 거부). node_modules 탐색은 워크스페이스에 고정
* `개선` Worker 메시지와 fs 감시자가 Node 네이티브 제한을 따름: 64 KB 메시지 / 32개 대기열 상한과 16개 감시자 / 초당 64 이벤트 할당량 제거. Android 메모리만이 한계
* `개선` worker 내 process.exit()가 Node처럼 해당 worker 스레드만 종료하고, process.getBuiltinModule()이 worker builtin 허용 목록을 따름 (일괄 비활성화 해제)
* `개선` worker 내 bare 패키지 지정자를 Node처럼 워크스페이스 node_modules에서 해석 (exports 조건, main, index, 자기 참조). 일괄 거부 해제
* `개선` worker 내 동적 import()를 worker의 partial ESM 로더로 처리 (상대/절대 경로, file: URL, 워크스페이스 패키지, 허용 builtin, with { type: "json" }). 거부 해제
* `개선` 실물 키와 알림 접근 수동 검수 통과 후 host-events 샘플을 stable로 승격
* `개선` scheduled-node-task 샘플이 수명주기 정책을 출력하고 WorkManager 실행을 위해 작업을 유지할 수 있음; scheduled 실행 모드에 플러그인 측 회귀 추가
* `개선` 호스트 WorkManager 예약 러너가 플러그인을 통해 실제 실행된 후 scheduled 실행 모드를 기능 카탈로그에서 available로 승격
* `개선` media.play()는 호스트 스크립트 음악 서비스로 재생하는 세션 객체 (pause/resume/seekTo/stop/status)를 반환하고, 새 media_store 모듈은 media.playback / media.library / media.library.mutate 권한으로 보호되는 제한된 MediaStore capabilities/query/get/insert/update/delete/scanFile/exportFile을 제공; autojs6:compat.media에 Rhino 스타일 playMusic 계열 별칭 추가; 모두 대응하는 호스트 빌드 필요
* `개선` media-playback / media-library 샘플이 병합된 호스트 미디어 provider로 수동 검수를 통과해 stable로 승격; 기능 카탈로그 스냅샷 1.5.5를 releases/nodejs-capability-catalog에 게시하고 호스트 정합 작업이 이를 참조
* `개선` fs 래퍼의 자체 옵션 제한 제거: 스트림의 fs / 상속된 fd / flags 옵션, watch({ recursive: true }), 비동기 cp filter (cpSync는 Node의 ERR_INVALID_RETURN_VALUE 유지), 절대 경로 / 상위 디렉터리 / 리터럴 '!' glob 패턴과 exclude 배열, readableWebStream의 type / encoding, Stats / Dirent / Dir 생성자가 모두 Node 24 네이티브 동작을 따름; filesystemProfile.advancedApis.recursiveWatch는 native를 보고
* `개선` 재귀 readdir / opendir를 네이티브에 위임 (4096 항목 상한과 항목별 realpath 검사 제거; readdir('/')는 Node처럼 proc/sys/dev 이름을 나열), readlink / chmod / chown / utimes가 절대 경로를 허용하고 chmod는 심볼릭 링크를 따라감, fs 정책 오류 코드는 ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (하드 경계, loader와 공유) / ERR_AUTOJS6_FS_SCOPED_PATH로 수렴하고 일반 fs 실패는 Node 코드만 유지
* `개선` 런타임 자체 모듈 소스 예산 (모듈당 16 MiB, 총 64 MiB, 8192개 모듈, provider 요청 수) 제거, CommonJS 진입점의 __filename / require.main.filename / process.argv[1]이 Node처럼 절대 경로가 되고 require.main.id는 '.', fs.mkdtemp*는 네이티브에 위임: 호출자의 접두사 표기에 접미사를 붙여 요청한 인코딩으로 반환하고 부모 디렉터리가 심볼릭 링크여도 거부하지 않음
* `개선` node:sqlite가 SQL 파일 작업을 네이티브 SQLite에 맡김: 리터럴 파일명의 ATTACH (file: URI 포함)는 SQL 텍스트를 훑는 대신 SQLite authorizer로 /proc, /sys, /dev 경계를 검사하고, VACUUM INTO 대상은 SQLite 내부 ATTACH를 통해 같은 경계 검사를 거치고, 디렉터리 PRAGMA는 더 이상 가로채지 않으며, file: URI 문자열과 빈 임시 데이터베이스를 열 수 있고, setAuthorizer()는 경계 검사와 결합됨 (바인드 매개변수나 식으로 파일명을 주는 ATTACH만 계속 거부)
* `개선` 브리지 할당량을 호스트에 맡김: autojs6:bridge-limits의 maxPendingBridgeCalls 창을 넘는 호출은 ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT로 실패하지 않고 선입선출로 대기하며, 런타임은 이미지 핸들 수·동시 제어 fetch 요청 수·제어 WebSocket 연결 수를 더 이상 자체 제한하지 않고 (호스트 broker는 계속 자체 정책을 적용), require('fetch').policy.maxConcurrentRequests와 require('websocket').policy.maxConnections는 폐기됨
* `개선` 브리지 fetch / WebSocket / axios facade의 상한을 호스트에 맡김: 타임아웃·응답 크기·리디렉션 횟수·메시지와 큐 크기·HTTP 메서드를 자르지 않고 호스트 제공자에 전달하며 (호스트 정책 적용), 런타임은 Node처럼 최대 20회 리디렉션을 따르고, policy에서 폐기된 hard*/기본 크기 필드는 limitsEnforcedBy로 대체
* `개선` opendir가 Node 자체의 지연 fs.Dir를 반환 (bufferSize·encoding·recursive는 네이티브 opendir로 전달, ENOENT / ENOTDIR / ERR_DIR_CLOSED는 Node 오류; dir.path와 parentPath만 호출자 표기를 유지). 런타임의 파일 시스템 도달 루트 삭제 방지 가드는 폐기되어 루트의 rm / rmdir도 다른 경로처럼 Node와 Android가 결정 (fs 정책 코드는 FS_NUL_BYTE와 FS_PATH_ESCAPE만 남음)

# v1.5.4

###### 2026/09/17

* `수정` 내장 실행에서 ExperimentalWarning 출력을 중단하고 warning 이벤트, 일반 경고, 사용 중단 경고 및 오류를 유지; Node 상위 API 안정성과 터미널 기본 동작은 변경되지 않음
* `개선` 파일 스트림, gzip/deflate/Brotli 스트림 및 기본 VM 실행을 정식 지원; 명시적으로 활성화한 Debug Inspector는 localhost 범위에서 정식 지원

# v1.5.3

###### 2026/09/16

* `개선` Android 17 로컬 네트워크 권한 요청을 플러그인 센터의 활성화 흐름과 설정으로 통합하고 런처 권한 페이지 제거; 권한이 없으면 비활성 상태를 유지하고 자동 시작을 조용히 건너뜀
* `개선` Android 17 (SDK 37) 대응 및 플러그인별 로컬 네트워크 권한 설정과 복구 안내 제공

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
