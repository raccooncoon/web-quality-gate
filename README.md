# web-quality-gate

폐쇄망 내 시스템들(React·Vue SPA + 서버렌더링 MPA 혼재)에 대한
**웹접근성·웹호환성 자동 점검 및 리포트** 도구.
CLI 와 **로컬 웹 콘솔**(설정·실행·결과) 두 방식으로 쓸 수 있고, 외부 의존성 없이 오프라인에서 동작한다.

## 검사 매트릭스

시스템 × URL 별로 아래 3종 검사를 수행한다.

| 검사 | 도구 | 비고 |
|---|---|---|
| 접근성 | axe-core (Playwright 통합) | WCAG 2.1 AA 기준, **KWCAG 2.1 병기**. SPA는 렌더 완료 대기 후 분석 |
| 마크업 표준 | vnu.jar (W3C Nu Html Checker) | MPA: 원본 응답 / SPA: 렌더링 DOM 검증 |
| 호환성 | Playwright 3엔진 | chromium·firefox·webkit 스크린샷 픽셀 diff + 엔진 버전 기록 |

리포트는 `reports/<system>/<yyyyMMdd-HHmm>/` 에 `index.json`(기계용)과
`report.html`(사람용, 자기완결·인쇄/PDF 최적화 — 종합 게이트 판정, KWCAG 병기,
위반 요소 상세, 3엔진 스크린샷 비교)로 저장된다.

> 자동 점검의 한계: KWCAG 자동 검출 가능 항목은 일부이며 대체텍스트 적절성·키보드
> 조작성 등은 수동 점검 필요. 본 도구는 스크리닝 + 산출물 초안 용도.
> 리포트의 KWCAG 항목은 axe(WCAG) 결과를 참고용으로 자동 매핑한 것으로 공식 인증 판정이 아니다.

## 두 가지 사용 방식

### A. 웹 콘솔 (권장 — 윈도우 로컬 PC)

```
start.bat 더블클릭  →  http://localhost:8080 자동 오픈
```

- **① 설정** — 시스템별 baseUrl·로그인 셀렉터·계정·검사 경로를 폼으로 입력(YAML 손편집 불필요).
  계정은 이 PC 로컬 파일(`credentials.properties`)에만 저장(git·리포트 제외), 없으면 환경변수 폴백.
- **② 실행** — 시스템별 "검사" 버튼, 진행 상태 실시간 표시.
- **③ 결과** — 과거 리포트 목록 → 클릭해 리포트 열기.

수동 기동: `java -jar web-quality-gate.jar serve [포트]` (127.0.0.1 에만 바인딩).

### B. CLI

```bash
export PLAYWRIGHT_BROWSERS_PATH=/opt/wqg/pw-browsers
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
export WQG_DEFECT_KWATER_ID=... WQG_DEFECT_KWATER_PW=...   # form 로그인 시스템

java -jar web-quality-gate.jar config/sites.yaml            # 전체
java -jar web-quality-gate.jar config/sites.yaml <시스템명>  # 하나씩
```

로그인 후 점검 경로 자동 수집:

```bash
java -cp web-quality-gate.jar com.kt.wqg.check.DiscoverMain config/sites.yaml <시스템명>
```

## 시스템 하나 셋업 순서

1. baseUrl + (로그인 필요 시) 로그인 셀렉터·계정 입력 — 웹 콘솔 ① 설정 또는 `config/sites.yaml`
2. 점검 경로 지정 — 직접 입력하거나 `DiscoverMain` 으로 수집해 채움
3. 검사 실행 — 웹 콘솔 ② 실행 또는 CLI

## 폐쇄망 배포 흐름

### 윈도우 로컬 PC (JRE 포함, 완전 오프라인)

```
[외부망 윈도우 PC] scripts\win\package-offline.bat   (JDK17 + Maven + 인터넷 필요)
    → dist\wqg-win\  (start.bat + jar + vnu.jar + jre + pw-browsers + config)
[폴더째 압축 → 반입]
[폐쇄망 윈도우 PC] 압축 해제 → start.bat 더블클릭
```

### 리눅스 서버

```
[외부망] scripts/package-offline.sh  → dist/wqg-bundle-YYYYMMDD.tar.gz
[폐쇄망] scripts/install-offline.sh <bundle> <스모크URL>  → /opt/wqg 설치 + ldd 검증
```

## 설정 (`config/sites.yaml`)

시스템별 핵심 필드:
- `displayName`: 리포트·콘솔 표기용 한글명
- `type`: `spa` | `mpa` — 대기 전략·마크업 검증 방식 분기
- `urls[].readySelector`: SPA 렌더 완료 판정 셀렉터 (SPA 권장)
- `auth`: 로그인 필요 시 form 셀렉터. 계정은 커밋하지 않는다(환경변수/로컬 계정 파일).
- `checks.markup.vnuJarPath`: vnu.jar 경로. 환경변수 `WQG_VNU_JAR` 로 덮어쓸 수 있다(윈도우 패키지 start.bat 이 자동 주입).

## 개발 상태

- [x] Phase 0: 스택 확정 (Java 단일 스택 — 폐쇄망 반입성)
- [x] Phase 3: 러너 — CheckRunner / AccessibilityCheck / MarkupCheck / CompatibilityCheck / ReportWriter
- [x] Phase 4: 리포트 — 자기완결 HTML 대시보드(판정·KWCAG·스크린샷·인쇄 최적화)
- [x] 웹 콘솔 — 설정·실행·결과 (내장 HttpServer, 프레임워크 없음)
- [x] 윈도우 오프라인 패키지 (`scripts/win/`)
- [ ] 리눅스 패키징·설치 스크립트 실검증
- [ ] 커버리지 확장 — 역할별 계정 다중 검사
