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
* `수정` 상주 스크립트의 브리지 요청 및 응답 기록이 계속 쌓이던 문제를 수정하고, 완료된 요청을 정리하며 진단에는 크기 제한과 함께 최근 32개 응답만 유지
* `수정` 네이티브 비동기 작업 완료 전에 성공으로 판정하여 오류와 종료 코드가 누락되는 문제 수정; Node 이벤트 루프 최종 종료 시 결과 확정
* `개선` 실시간 브리지에 기본 JNI/Binder 전송과 Node 이벤트 루프 응답을 적용해 지연을 줄이고, 파일 전송 대체 경로와 대기 중인 호출 수 제한 유지
* `개선` Node.js stream, crypto, timers, util, node:test 등의 네이티브 내보내기 복원, 파일 시스템 경계 및 호스트 디렉터리 정책 유지
* `개선` 네이티브 worker 기본 수를 CPU 병렬도 (최대 8개)로 변경하고 실행의 네트워크 및 파일 설정과 요청별 리소스 상한을 적용. 풀 작업의 기본 시간 제한을 제거하고 CPU/WASM 예제를 실제 worker 실행으로 변경
* `개선` 파일 시스템 경계를 유지하도록 원시 WASI를 계속 비활성화하고 비활성 WASI 예제 두 개 제거; 일반 WebAssembly 및 WASM worker는 계속 사용 가능
* `개선` 화면 OCR 예제가 Android 캡처 승인을 요청하고 실제 이미지 핸들을 인식하며, OCR 또는 바코드 플러그인을 사용할 수 없으면 읽기 쉬운 unavailable 오류를 반환하고 인식 실패는 오류로 처리
