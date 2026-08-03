# web-quality-gate

폐쇄망 내 5개 사이트의 웹접근성·웹호환성 자동 점검 및 리포트 도구.
대상: React/Vue SPA + 일반 서버렌더링(MPA) 혼재.

## 스택 (변경 금지 — 폐쇄망 반입성 기준으로 확정됨)
- Java 17, Maven, shade 플러그인으로 의존성 포함 단일 jar 빌드
- Playwright Java (chromium/firefox/webkit), com.deque.html.axe-core:playwright
- 마크업 검증: vnu.jar 외부 프로세스 실행 (경로: sites.yaml checks.markup.vnuJarPath)
- 설정: Jackson YAML → config/SitesConfig record 바인딩 (구현 완료)
- Node 기반 도구(pa11y, Lighthouse) 도입 금지 — 폐쇄망 반입 불가 판정

## 핵심 설계 규칙
- 검사 매트릭스: 사이트 × URL × (접근성 | 마크업 | 호환성), 결과는 reports/에 JSON 저장
- SPA(type: spa): navigate 후 urls[].readySelector 대기 + NETWORKIDLE → 그 다음 검사.
  빈 화면 검사로 위반 0건이 나오는 오탐 방지가 목적
- MPA(type: mpa): load 이벤트 후 검사
- 마크업 검증 분기: MPA는 원본 HTTP 응답 본문, SPA는 page.content() 렌더링 DOM을
  임시파일로 저장해 vnu.jar --format json 실행
- 접근성: AxeBuilder.withTags(sites.yaml checks.accessibility.tags) 사용
- 호환성: 3개 엔진에서 동일 URL 스크린샷 → 픽셀 diff (threshold: pixelDiffThreshold).
  캡처 전 CSS 주입으로 animation/transition 강제 비활성화
- 로그인(auth.type: sso): 환경변수 WQG_<SITE명 대문자>_ID / _PW 주입.
  계정 정보를 코드·설정에 절대 커밋하지 않는다
- 브라우저는 PLAYWRIGHT_BROWSERS_PATH 오프라인 번들 사용,
  PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 전제. 런타임 다운로드 코드 금지

## 코드 스타일
- 함수형 스타일 선호, 단순 로직 오버엔지니어링 금지, 클린 코드 원칙
- 프레임워크 없는 plain Java 유지 (Spring 도입 금지 — CLI 러너임)
- 응답·작업은 한 번에 하나의 단위로 진행

## 구현 순서 (Phase 3 — 이 순서대로 하나씩)
1. CheckRunner: 사이트별 BrowserContext 생성, form 로그인, URL 순회 골격
2. AccessibilityCheck: axe 분석 → violations JSON 저장
3. MarkupCheck: MPA/SPA 분기 → vnu.jar 실행 → errors JSON 저장
4. CompatibilityCheck: 3엔진 스크린샷 + 픽셀 diff → 결과 JSON 저장
5. ReportWriter: reports/<site>/<yyyyMMdd-HHmm>/ 구조로 집계 index.json 생성
이후 Phase 4: Allure 통합(1차), 필요 시 정적 HTML 대시보드(2차)

## 리포트 JSON 최소 스키마
{ "site", "url", "checkType", "timestamp", "engine"(호환성만),
  "passed": bool, "violations": [ { "id", "impact", "help", "nodes": n } ] }

## 검증
- mvn clean package 성공 + java -jar 로 sites.yaml 로드 출력 확인이 최소 기준
- 폐쇄망 반입 전 외부망에서 scripts/package-offline.sh 산출물로 통합 검증
