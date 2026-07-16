# v1.0.0

###### 2026/07/04

* `추가` 플러그인 ID `nodejs`, 엔진 `nodejs`, 런타임 슬롯 `node24_5` 를 가진 Node.js 런타임 플러그인 서비스를 추가
* `추가` `libnode.so` 및 `libautojs6-node.so` 를 통해 Node.js 24.5.0 네이티브 런타임을 제공
* `추가` `org.autojs.plugin.INFO` 를 통한 플러그인 정보 검색과 `org.autojs.plugin.nodejs.RUNTIME` 를 통한 런타임 호출을 추가
* `추가` CommonJS/ESM 소스, 모듈 소스, 작업 디렉터리, 샌드박스 루트, 환경 변수, stdout/stderr 결과 페이로드, 런타임 프리웜을 지원
* `추가` `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 같은 런타임 모듈을 사용하는 호스트 기능 브로커와 live bridge 를 추가
* `추가` `arm64-v8a`, `armeabi-v7a`, `x86_64`, 그리고 `universal` APK 용 ABI 분할 빌드를 추가
* `추가` `sample/nodejs` 프로젝트, Node resolver 진단 도구, 런타임 빌드 계획 검증 도구를 추가
* `추가` 스페인어/프랑스어/러시아어/아랍어/일본어/한국어/영어/간체 중국어/홍콩 번체/대만 번체용 플러그인 정보, 사용 설명, README, CHANGELOG 리소스를 추가
