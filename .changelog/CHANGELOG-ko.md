******

### 릴리스 기록

******

# v1.2.0

###### 2026/08/26

* `추가` Android 앱 권한 범위에서 데스크톱과 유사한 파일 시스템 접근을 활성화하고 `/proc`, `/sys`, `/dev` 는 계속 거부
* `추가` 전용 기능 `accessibility.gesture` 아래에 `accessibility.swipe` 와 `accessibility.gesture` 추가
* `수정` 호스트가 컴파일러 출력을 제공하지 않은 원시 TypeScript 를 fail-closed 로 변경하고 snapshot 동적 import 매핑과 생성/가져온 스택 프레임 정규화 추가
* `개선` 호스트/플러그인 v2 계약, 기능 매니페스트 및 plugin-only 런타임 책임 경계를 정렬
* `개선` Node.js 샘플, TypeScript 타입 선언, 프로젝트 마법사, 런타임 기본값 및 호스트 정렬 검사를 플러그인 저장소로 통합하고 호스트 측 Gradle 스위치와 중복 개발 자산을 제거

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
