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

# v1.5.4

###### 2026/09/17

* `수정` 내장 실행에서 ExperimentalWarning 출력을 중단하고 warning 이벤트, 일반 경고, 사용 중단 경고 및 오류를 유지; Node 상위 API 안정성과 터미널 기본 동작은 변경되지 않음
* `개선` 파일 스트림, gzip/deflate/Brotli 스트림 및 기본 VM 실행을 정식 지원; 명시적으로 활성화한 Debug Inspector는 localhost 범위에서 정식 지원

# v1.5.3

###### 2026/09/16

* `개선` Android 17 로컬 네트워크 권한 요청을 플러그인 센터의 활성화 흐름과 설정으로 통합하고 런처 권한 페이지 제거; 권한이 없으면 비활성 상태를 유지하고 자동 시작을 조용히 건너뜀
* `개선` Android 17 (SDK 37) 대응 및 플러그인별 로컬 네트워크 권한 설정과 복구 안내 제공

# v1.5.2

###### 2026/09/15

* `개선` compileSdk 를 37 (Android 17) 로 올리며, targetSdk 는 대상 버전에 의존하는 동작을 검증할 때까지 36 으로 유지

# v1.5.1

###### 2026/09/15

* `수정` Node 자체의 치명적 오류와 경고 텍스트를 Android 에서 logcat 외에 실제 stderr 에도 기록하여, 터미널이 조용히 종료되는 대신 처리되지 않은 예외를 표시합니다
* `개선` `libnode.so` 를 `--with-intl=small-icu` 로 다시 빌드: 영어 로케일 데이터만으로 `Intl` 과 정규식의 유니코드 속성 이스케이프 (`\p{...}`) 를 사용할 수 있으며 (`NODE_ICU_DATA` 로 전체 ICU 데이터 파일 지정 가능), 이에 따라 corepack 이 AutoJs6 터미널에서 pnpm 11 과 Yarn Berry 를 실행할 수 있습니다

# v1.5.0

###### 2026/09/14

* `추가` 멀티콜 터미널 런처 `libnodexe.so` (node / npm / npx / corepack / yarn / pnpm) 를 모든 ABI에 포함, `DT_RUNPATH $ORIGIN` 및 16 KB 페이지 정렬 적용
* `추가` 공식 Node.js 24.21.0 배포판에서 가져온 npm 11.19.0 및 corepack 0.36.0 자산, `NODE_CLI_*` manifest meta-data (schema 1) 로 선언하고 `nodeCli` 기능으로 runtimeInfo / PluginInfo에 미러링

# v1.4.2

###### 2026/09/13

* `수정` 설치된 APK에 실제 포함된 네이티브 ABI만 보고
* `수정` 빌드 환경 언어와 관계없이 플러그인 메타데이터의 빌드 날짜를 영어로 통일
* `수정` 릴리스 패키지와 보관 파일의 버전 정보 일치
* `수정` 파일 디스크립터 격리를 유지하면서 Android 7 작업 파일 호환성 개선

# v1.4.1

###### 2026/09/13

* `개선` 64비트 네이티브 라이브러리의 16 KB 페이지 정렬을 빌드 시 검증, manifest 계약 검사 및 JSON 보고서 지원
* `개선` 호스트 활성화, 메타데이터, 다국어 문서 및 서명된 APK 수집을 공통 규칙에 맞게 정리

# v1.4.0

###### 2026/09/10

* `추가` MediaInfo 쿼리는 0부터 시작하는 streamNumber, countGet 스트림 수, 단위와 설명 및 표시 이름을 위한 infoKind를 지원; Rhino와 Node는 첫 스트림의 TEXT 기본 쿼리를 유지하고 플러그인 확장 기능을 확인
* `수정` MediaInfo 및 이미지 파일 경로가 절대 경로, 상위 디렉터리 및 유효한 파일 이름을 지원; 녹음 출력도 업데이트된 호스트에서 동일한 Android 파일 접근 규칙 적용
* `수정` 브리지 기능 오류가 누락된 node.permissions 선언을 안내하며 진단 전용 pro_compat_opt_in 프로필을 요구하지 않도록 수정
* `수정` 프로젝트 검증기가 fs 절대 경로와 상위 디렉터리를 FS_OUTSIDE_SCOPE로 거부하지 않도록 수정; 실제 파일 접근은 Android에서 결정
* `개선` Android 이벤트 및 3초 녹음 독립 프로젝트와 화면 캡처, OCR, 물리 키, MediaInfo의 프로젝트 선언 및 수동 검증 절차 제공
* `개선` 스크린샷 이미지 검색, 화면 OCR 및 3초 AAC 녹음의 실제 기기 수동 검증을 완료하고 해당 예제와 동일한 호출을 사용하는 Pro 호환 코드 조각을 안정 상태로 전환
* `개선` 이벤트 검증 예제에 호스트의 볼륨 높이기 중지 단축키를 잠시 끄는 절차를 추가; 업데이트된 호스트는 project.json의 node.timeoutMs를 읽어 긴 대기가 약 5초 후 종료되는 문제를 해결

# v1.3.0

###### 2026/09/09

* `추가` Node.js 브리지 구독에서 기존 콜백으로 센서, WebSocket, UI, 오버레이 및 입력 이벤트를 전달하며 on/once/off, 크기가 제한된 큐 및 drainEvents 호환성 지원
* `추가` Node.js 런타임에 선택적 idleExitMs 유휴 종료 및 다음 스크립트 재연결 지원, idleForMs 진단 제공 및 기본 상주 동작 유지
* `추가` 전역 fetch, Request, Response, Headers, FormData, WebSocket에 Node 네이티브 Web API 사용; autojs6:fetch 및 autojs6:websocket은 호스트 네트워크 스택 제공
* `추가` 파일 경계를 검사하는 네이티브 node:sqlite로 CRUD, 트랜잭션 및 백업을 지원. 네이티브 node:test 리포터와 실행 가능한 예제를 제공하고 오프라인 npm corpus에 zod, cheerio, date-fns, mqtt, ws를 추가
* `추가` process.stdin / readline 콘솔 입력과 autojs6:host 실행별 JSON 메시지 지원; v3 postMessage는 기존 호스트와 호환
* `추가` Node.js 화면 캡처 세션에 Android 승인과 기존 포그라운드 서비스를 연결하고 이미지 핸들, PNG/JPEG/WebP 저장 및 중지나 스크립트 종료 시 정리를 지원
* `추가` Node.js 이미지 핸들이 호스트 이미지 처리 기능을 통한 자르기, 크기 조절, 회색조, 임계값 처리, 템플릿 매칭 및 색상 검색을 지원하고 독립된 출력 이미지와 실행 종료 시 정리를 제공
* `추가` Node.js image.toBytes가 파일 디스크립터로 PNG 또는 RGBA 픽셀을 네이티브 Buffer에 전달하고 JNI 및 파일 브리지를 지원하며 사용 후, 시간 초과 또는 실행 종료 시 첨부 데이터를 정리
* `추가` Node device API가 실시간 빌드, 화면, 배터리 및 메모리 정보, 밝기 제어와 시간 제한 화면 켜짐 유지를 제공하며, media는 Android 권한에 따라 오디오 스트림 볼륨 설정을 지원
* `추가` autojs6:events가 명시적 권한과 구독 자동 정리로 Android 알림, 외부 Toast, 접근성 키, 화면 및 배터리 이벤트를 푸시 콜백으로 관찰하며 Node 기본 events 호환성을 유지
* `추가` Node app이 Android 서비스 시작, 브로드캐스트 전송 및 설치된 앱 조회를 지원; dialogs는 텍스트 입력, 단일 및 다중 선택과 스크립트 종료 시 닫히는 진행 창을 제공하고 keys는 접근성 시스템 동작을 제공
* `추가` Node recorder가 마이크 권한, 포그라운드 알림, 시간 제한과 스크립트 종료 시 정리를 적용한 AAC 녹음을 지원; ui.overlay는 선언형 속성 갱신, 끌기 및 이벤트 전송을 지원
* `추가` Node 스크립트를 두 개의 독립 런타임 프로세스에서 동시에 실행하며 FIFO 대기열을 공유하고 실행별 취소와 입력 전달을 지원
* `수정` 상주 스크립트의 브리지 요청 및 응답 기록이 계속 쌓이던 문제를 수정하고, 완료된 요청을 정리하며 진단에는 크기 제한과 함께 최근 32개 응답만 유지
* `수정` 네이티브 비동기 작업 완료 전에 성공으로 판정하여 오류와 종료 코드가 누락되는 문제 수정; Node 이벤트 루프 최종 종료 시 결과 확정
* `수정` OpenSSL STORE 키 URL이 파일 시스템 제한을 우회하는 문제 (node:fs로 키 바이트를 읽어서 사용)
* `개선` 실시간 브리지에 기본 JNI/Binder 전송과 Node 이벤트 루프 응답을 적용해 지연을 줄이고, 파일 전송 대체 경로와 대기 중인 호출 수 제한 유지
* `개선` Node.js stream, crypto, timers, util, node:test 등의 네이티브 내보내기 복원, 파일 시스템 경계 및 호스트 디렉터리 정책 유지
* `개선` 네이티브 worker 기본 수를 CPU 병렬도 (최대 8개)로 변경하고 실행의 네트워크 및 파일 설정과 요청별 리소스 상한을 적용. 풀 작업의 기본 시간 제한을 제거하고 CPU/WASM 예제를 실제 worker 실행으로 변경
* `개선` 파일 시스템 경계를 유지하도록 원시 WASI를 계속 비활성화하고 비활성 WASI 예제 두 개 제거; 일반 WebAssembly 및 WASM worker는 계속 사용 가능
* `개선` 화면 OCR 예제가 Android 캡처 승인을 요청하고 실제 이미지 핸들을 인식하며, OCR 또는 바코드 플러그인을 사용할 수 없으면 읽기 쉬운 unavailable 오류를 반환하고 인식 실패는 오류로 처리
* `개선` Node 스크립트는 협상된 비동기 시작을 지원하고, 장기 실행 중 Binder 스레드를 해제하며, 종료 후 작업 공간 변경 사항을 반환하고 기존 동기 호스트와 호환
* `의존성` Node.js 24.5.0 → 24.21.0 업그레이드, 세 ABI 모두 소스에서 빌드한 Android 라이브러리 사용

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
