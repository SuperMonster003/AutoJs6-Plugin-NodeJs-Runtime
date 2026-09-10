# v1.4.0

###### 2026/09/10

* `추가` MediaInfo 쿼리는 0부터 시작하는 streamNumber, countGet 스트림 수, 단위와 설명 및 표시 이름을 위한 infoKind를 지원; Rhino와 Node는 첫 스트림의 TEXT 기본 쿼리를 유지하고 플러그인 확장 기능을 확인
* `수정` MediaInfo 및 이미지 파일 경로가 절대 경로, 상위 디렉터리 및 유효한 파일 이름을 지원; 녹음 출력도 업데이트된 호스트에서 동일한 Android 파일 접근 규칙 적용
* `수정` 브리지 기능 오류가 누락된 node.permissions 선언을 안내하며 진단 전용 pro_compat_opt_in 프로필을 요구하지 않도록 수정
* `수정` 프로젝트 검증기가 fs 절대 경로와 상위 디렉터리를 FS_OUTSIDE_SCOPE로 거부하지 않도록 수정; 실제 파일 접근은 Android에서 결정
* `개선` Android 이벤트 및 3초 녹음 독립 프로젝트와 화면 캡처, OCR, 물리 키, MediaInfo의 프로젝트 선언 및 수동 검증 절차 제공
