******

### 릴리스 기록

******

# v1.3.0

###### 미출시

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
* `수정` 상주 스크립트의 브리지 요청 및 응답 기록이 계속 쌓이던 문제를 수정하고, 완료된 요청을 정리하며 진단에는 크기 제한과 함께 최근 32개 응답만 유지
* `수정` 네이티브 비동기 작업 완료 전에 성공으로 판정하여 오류와 종료 코드가 누락되는 문제 수정; Node 이벤트 루프 최종 종료 시 결과 확정
* `개선` 실시간 브리지에 기본 JNI/Binder 전송과 Node 이벤트 루프 응답을 적용해 지연을 줄이고, 파일 전송 대체 경로와 대기 중인 호출 수 제한 유지
* `개선` Node.js stream, crypto, timers, util, node:test 등의 네이티브 내보내기 복원, 파일 시스템 경계 및 호스트 디렉터리 정책 유지
* `개선` 네이티브 worker 기본 수를 CPU 병렬도 (최대 8개)로 변경하고 실행의 네트워크 및 파일 설정과 요청별 리소스 상한을 적용. 풀 작업의 기본 시간 제한을 제거하고 CPU/WASM 예제를 실제 worker 실행으로 변경
* `개선` 파일 시스템 경계를 유지하도록 원시 WASI를 계속 비활성화하고 비활성 WASI 예제 두 개 제거; 일반 WebAssembly 및 WASM worker는 계속 사용 가능
* `개선` 화면 OCR 예제가 Android 캡처 승인을 요청하고 실제 이미지 핸들을 인식하며, OCR 또는 바코드 플러그인을 사용할 수 없으면 읽기 쉬운 unavailable 오류를 반환하고 인식 실패는 오류로 처리

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
