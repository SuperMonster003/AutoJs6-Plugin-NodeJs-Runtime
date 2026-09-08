# v1.2.0

###### 2026/08/29

* `추가` `mediainfo` facade에 `capabilities()`를 추가하고 호출 가능한 진입점과 `read()`에서 플러그인 snapshot v1/v2를 명시적으로 선택하도록 지원; schema를 생략하면 기존 호스트 소유 Node v1 snapshot을 계속 반환
* `추가` 실행 중 생성된 `.ts/.mts/.cts`를 byte count와 SHA-256으로 고정된 PFD를 통해 on-demand compile하는 module-source provider v3를 추가하고, 독립적인 30 s compilation budget, 안정적인 path escape/symlink/ambiguity 거부, host TypeScript diagnostics 및 Source Map stack mapping을 제공
* `추가` Android 앱 권한 범위에서 데스크톱과 유사한 파일 시스템 접근을 활성화하고 `/proc`, `/sys`, `/dev` 는 계속 거부
* `추가` 전용 기능 `accessibility.gesture` 아래에 `accessibility.swipe` 와 `accessibility.gesture` 추가
* `추가` 대기열과 실행 전체에 적용되는 스크립트 시간 제한을 추가하고, 초과 시 `ERR_AUTOJS6_SCRIPT_TIMEOUT`을 반환합니다. 시간 제한을 지정하지 않으면 계속 무기한 실행할 수 있습니다
* `추가` `dgram` (UDP)과 `http2`를 기본으로 활성화하고, `trace_events`에는 명확한 비활성화 오류를 반환합니다
* `추가` Debug 빌드에서 명시적으로 활성화하는 로컬 `inspector` 디버깅을 추가했습니다. localhost에서만 수신하고 `adb forward`로 연결합니다
* `수정` 호스트가 컴파일러 출력을 제공하지 않은 원시 TypeScript 를 fail-closed 로 변경하고 snapshot 동적 import 매핑과 생성/가져온 스택 프레임 정규화 추가
* `수정` snapshot 기반 partial ESM adapter 를 V8 native linker 로 교체하여 순환 re-export 에서 변경 가능한 export 가 갱신되지 않던 문제 수정
* `수정` 설치된 npm 패키지 우선순위를 유지하면서 AutoJs6 호환 퍼사드의 ESM 가져오기와 존재하지 않는 TypeScript 확장자 탐색을 수정
* `개선` regex 기반 legacy TypeScript 타입 제거 fallback과 request switch를 삭제하여 raw `.ts/.mts/.cts`가 항상 host compiler output을 요구하도록 변경
* `개선` 호스트/플러그인 v2 계약, 기능 매니페스트 및 plugin-only 런타임 책임 경계를 정렬
* `개선` Node.js 샘플, TypeScript 타입 선언, 프로젝트 마법사, 런타임 기본값 및 호스트 정렬 검사를 플러그인 저장소로 통합하고 호스트 측 Gradle 스위치와 중복 개발 자산을 제거
* `개선` 검증된 npm 패키지를 15개로 확대하고 axios, express 및 ESM-only 패키지 nanoid, p-limit, yocto-queue를 추가했습니다
* `개선` 요청의 `executionMode` 필드가 수명 주기 모드를 결정하도록 하고, 효과가 없는 `runtimeAdapter` 필드를 사용 중단 예정으로 표시했습니다
