# v1.3.0

###### 미출시

* `추가` Node.js 브리지 구독에서 기존 콜백으로 센서, WebSocket, UI, 오버레이 및 입력 이벤트를 전달하며 on/once/off, 크기가 제한된 큐 및 drainEvents 호환성 지원
* `추가` Node.js 런타임에 선택적 idleExitMs 유휴 종료 및 다음 스크립트 재연결 지원, idleForMs 진단 제공 및 기본 상주 동작 유지
* `추가` 전역 fetch, Request, Response, Headers, FormData, WebSocket에 Node 네이티브 Web API 사용; autojs6:fetch 및 autojs6:websocket은 호스트 네트워크 스택 제공
* `수정` 상주 스크립트의 브리지 요청 및 응답 기록이 계속 쌓이던 문제를 수정하고, 완료된 요청을 정리하며 진단에는 크기 제한과 함께 최근 32개 응답만 유지
* `수정` 네이티브 비동기 작업 완료 전에 성공으로 판정하여 오류와 종료 코드가 누락되는 문제 수정; Node 이벤트 루프 최종 종료 시 결과 확정
* `개선` 실시간 브리지에 기본 JNI/Binder 전송과 Node 이벤트 루프 응답을 적용해 지연을 줄이고, 파일 전송 대체 경로와 대기 중인 호출 수 제한 유지
* `개선` Node.js stream, crypto, timers, util, node:test 등의 네이티브 내보내기 복원, 파일 시스템 경계 및 호스트 디렉터리 정책 유지
