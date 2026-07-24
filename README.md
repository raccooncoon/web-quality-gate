# web-quality-gate

폐쇄망 환경의 대상 사이트 5개(React·Vue SPA + 일반 웹 혼재)에 대한
**웹접근성·웹호환성 자동 점검 및 리포트** 도구.

## 검사 매트릭스

사이트 × URL 별로 아래 3종 검사를 수행한다.

| 검사 | 도구 | 비고 |
|---|---|---|
| 접근성 | axe-core (Playwright 통합) | WCAG 2.1 AA 태그 기준, SPA는 렌더링 완료 대기 후 분석 |
| 마크업 표준 | vnu.jar (W3C Nu Html Checker) | MPA: 원본 응답 / SPA: 렌더링 DOM 검증 |
| 호환성 | Playwright 3엔진 | chromium·firefox·webkit 스크린샷 픽셀 diff |

자동 점검의 한계: KWCAG 기준 자동 검출 가능 항목은 일부(약 30~40%)이며,
대체텍스트 적절성·키보드 조작성 등은 수동 점검 필요. 본 도구는 스크리닝 용도.

## 폐쇄망 배포 흐름

```
[외부망] scripts/package-offline.sh
    → dist/wqg-bundle-YYYYMMDD.tar.gz (앱 jar + 브라우저 번들 + vnu.jar)
[반입]
[폐쇄망] scripts/install-offline.sh <bundle> <스모크URL>
    → /opt/wqg 설치 + OS 라이브러리 검증
```

## 실행

```bash
export PLAYWRIGHT_BROWSERS_PATH=/opt/wqg/pw-browsers
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
java -jar /opt/wqg/web-quality-gate.jar config/sites.yaml
# 결과: reports/ 디렉터리
```

로그인 계정은 환경변수로 주입 (`WQG_<SITE>_ID` / `WQG_<SITE>_PW`). 설정 파일에 계정 정보를 커밋하지 않는다.

## 설정

`config/sites.yaml` 참고. 사이트별 핵심 필드:
- `type`: `spa` | `mpa` — 대기 전략·마크업 검증 방식 분기
- `urls[].readySelector`: SPA 렌더링 완료 판정 셀렉터 (SPA 필수)
- `auth`: 로그인 필요 시 form 셀렉터 정의

## 개발 로드맵

- [x] Phase 0: 스택 확정 (Java 단일 스택 — 폐쇄망 반입성)
- [ ] Phase 1: 외부망 패키징 (`package-offline.sh`)
- [ ] Phase 2: 반입·설치 검증 (`install-offline.sh`)
- [ ] Phase 3: 러너 구현 — CheckRunner / AccessibilityCheck / MarkupCheck / CompatibilityCheck / ReportWriter
- [ ] Phase 4: 리포트 — Allure 통합(1차) → 정적 대시보드(필요 시)
- [ ] Phase 5: 스케줄링 — Jenkins 주기 잡 또는 cron + nginx 서빙
