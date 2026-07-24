# KWCAG 2.2 컴플라이언스 검수 산출물 — 설계

- 작성일: 2026-07-24
- 대상: web-quality-gate
- 상태: 설계 승인 완료 → 스펙 검토 대기

## 1. 목표

SI 검수 요건(전자정부 웹사이트 품질관리 가이드 · KWCAG 2.2 / KS X OT0003:2022 ·
관련 고시)에 맞춰, 도구가 **웹접근성·웹호환성 37개 검사항목을 최대한 자동 판정**하고,
자동 불가한 소수 항목은 **자동 스크리닝 + 증거로 평가자를 보조**하며,
**항목별 친절 설명(개선방법·근거)**과 **SI 검수결과표(4카테고리 준수율·종합판정)**를
담은 제출용 리포트를 생성한다.

기존 검사 엔진(axe·vnu·3엔진 스크린샷)은 리졸버 뒤에서 재사용한다(폐기 없음).

## 2. 배경 — SI 검수 체계

37개 검사항목이 4개 카테고리로 나뉘고, 카테고리별 준수율 기준을 모두 충족해야 전체 '적합'.

| 카테고리 | 항목 수 | 준수율 기준 |
|---|---|---|
| 웹접근성 자동 | 6 | 95% 이상 |
| 웹접근성 수동 | 27 | 95% 이상 |
| 웹호환성 자동 | 2 | 100% |
| 웹호환성 수동 | 2 | 100% |
| **전체** | **37** | 4개 카테고리 모두 '적합' |

- 접근성 33항목 = KWCAG 2.2 검사항목. 호환성 4항목 = 웹표준/Deprecated/브라우저/해상도·확대.
- 카테고리의 자동/수동 분류는 **SI 분류 고정값**이며, 준수율 버킷·검수표 구조를 결정한다.
  (도구가 어떤 항목을 자동 판정하든, 리포트 버킷은 SI 분류를 따른다.)

## 3. 아키텍처

37항목 카탈로그(데이터)를 축으로, 여러 리졸버가 각 항목을 채우고, 집계·검수·리포트로 수렴한다.

```
KwcagCatalog (37항목 데이터)
      │  각 항목: 코드·카테고리·SI분류·자동화수준·세부기준·guide·resolver
      ▼
Resolver ┬ AxeResolver     (기존 AccessibilityCheck 재사용)
         ├ MarkupResolver  (기존 MarkupCheck 재사용)
         └ ProbeResolver   (신규 커스텀 Playwright 프로브)
      │  → 항목별 ItemResult { 판정, 증거 }  (URL 전체 롤업)
      ▼
ComplianceEvaluator  → 4카테고리 준수율 + 종합판정
      │
AssessmentStore  ← 평가자 확정·오버라이드 (reports/<sys>/<ts>/assessment.json)
      ▼
ComplianceReportWriter → report.html (검수결과표 + 항목별 친절설명 + 증거)
```

### 컴포넌트 (각 단일 책임)

- **KwcagCatalog** — 37항목 로드·조회. 데이터 소유. (`config/kwcag22-catalog.yaml` 또는 리소스)
- **Resolver** — 인터페이스. 항목 → `ItemResult`. 구현체 3종(Axe/Markup/Probe).
- **ComplianceEvaluator** — 항목 판정 집계 → 카테고리 준수율·종합판정. 순수 로직.
- **AssessmentStore** — 평가자 확정·오버라이드 로드/저장. 자동+확정 병합.
- **ComplianceReportWriter** — SI 검수결과표 + 친절설명 + 증거 리포트(자기완결·인쇄최적화).

## 4. 데이터 모델

### 4.1 카탈로그 항목

```yaml
- code: "1.4.3"                 # KWCAG 2.2 항목 코드 (호환성은 c-* 슬러그)
  no: 7                         # SI 표 번호
  category: accessibility       # accessibility | compatibility
  siClassification: manual      # auto | manual  (SI 분류 — 준수율 버킷 결정)
  automation: auto              # auto | screen | manual  (도구의 실제 자동화 수준)
  title: "텍스트 콘텐츠의 명도 대비"
  criteria: "일반 텍스트 4.5:1 이상, 큰 텍스트 3:1 이상"
  resolver: { type: axe, rules: [color-contrast] }   # axe | markup | probe
  guide:
    what:  "텍스트와 배경색의 명암 차이를 검사합니다."
    why:   "저시력·색각 이상 사용자가 글을 읽으려면 충분한 대비가 필요합니다."
    basis: "KWCAG 2.2 1.4.3 · 장애인차별금지법 · KS X OT0003:2022"
    fix:   "명도비 4.5:1 이상. 예) #767676 on #fff = 4.54:1 ✓"
    limit: "자동 검출. 배경 이미지 위 텍스트는 수동 확인 권장."
```

### 4.2 항목 판정 상태

`적합 | 부적합 | 수동확인필요 | 해당없음`

- **auto** 리졸버가 확정 가능 → 적합/부적합
- **screen**(반자동) → 스크리닝 결과 + `수동확인필요` (증거 첨부)
- **manual** → `수동확인필요` (증거만)
- 평가자 확정 시 저장값이 자동값을 오버라이드

### 4.3 ItemResult (항목별 결과)

```
ItemResult {
  code, verdict,            # 판정 상태
  source,                   # "auto" | "assessor"
  violations: [ {rule, help, target, html, url} ],   # 위반 증거(롤업)
  screenshots: [ {label, path} ],                    # 증거 스크린샷(호환성 등)
  memo                      # 평가자 메모
}
```

### 4.4 assessment.json (실행 디렉터리 동봉)

```json
{ "items": {
    "1.4.3": {"verdict":"적합","by":"assessor","memo":"","at":"20260724-1700"},
    "2.5.1": {"verdict":"부적합","by":"assessor","memo":"제스처 대체 없음"} } }
```

## 5. 리졸버별 자동화 매핑 (37항목 전체)

자동화 수준: ● 완전자동(auto) / ◐ 반자동(screen) / ○ 수동(manual) / ◌ 해당없음(N/A).

### 웹접근성 (33)

| 코드 | 항목 | SI분류 | 자동화 | 리졸버 |
|---|---|---|---|---|
| 1.1.1 | 적절한 대체 텍스트 | 자동 | ● | axe: image-alt, area-alt, svg-img-alt, role-img-alt, object-alt, input-image-alt |
| 1.2.1 | 자막 제공 | 수동 | ◐ | probe: media-captions (track 없는 video/audio) |
| 1.3.1 | 표의 구성 | 수동 | ● | axe: td-headers-attr, th-has-data-cells, scope-attr-valid, table-fake-caption |
| 1.3.2 | 콘텐츠의 선형 구조 | 수동 | ○ | probe(보조): 스타일 제거 DOM 순서 스냅샷 |
| 1.3.3 | 명확한 지시사항 | 수동 | ○ | 증거: 스크린샷 |
| 1.4.1 | 색에 무관한 인식 | 수동 | ◐ | axe: link-in-text-block + 증거 |
| 1.4.2 | 자동 재생 금지 | 수동 | ● | axe: no-autoplay-audio + probe: autoplay 속성 |
| 1.4.3 | 텍스트 명도 대비 | 수동 | ● | axe: color-contrast |
| 1.4.4 | 콘텐츠 간의 구분 | 수동 | ○ | 증거: 스크린샷 |
| 2.1.1 | 키보드 사용 보장 | 수동 | ◐ | probe: 마우스전용 핸들러·비포커서블 컨트롤 |
| 2.1.2 | 초점 이동과 표시 | 수동 | ◐ | axe: focus-order-semantics + probe: :focus-visible |
| 2.1.3 | 조작 가능(타깃 크기) | 수동 | ● | axe: target-size |
| 2.1.4 | 문자 단축키 | 수동 | ○ | probe(보조): accesskey 검출 |
| 2.2.1 | 응답시간 조절 | 수동 | ◐ | probe: meta-refresh·자동 타이머 |
| 2.2.2 | 정지 기능 제공 | 수동 | ◐ | probe: 자동재생 캐러셀·무한 애니메이션 |
| 2.3.1 | 깜빡임·번쩍임 제한 | 수동 | ◐ | probe: blink/marquee·고빈도 CSS 애니메이션 |
| 2.4.1 | 반복 영역 건너뛰기 | 수동 | ● | axe: bypass + probe: skip-link 실동작 |
| 2.4.2 | 제목 제공 | 자동 | ● | axe: document-title, frame-title |
| 2.4.3 | 적절한 링크 텍스트 | 수동 | ● | axe: link-name |
| 2.4.4 | 고정된 참조 위치 | 수동 | ◌ | 전자출판 전용 → 웹 기본 해당없음 |
| 2.5.1 | 단일 포인터 입력 | 수동 | ○ | 증거: 스크린샷 |
| 2.5.2 | 포인터 입력 취소 | 수동 | ○ | 증거: 스크린샷 |
| 2.5.3 | 레이블과 네임 | 수동 | ● | axe: label-content-name-mismatch |
| 2.5.4 | 동작 기반 작동 | 수동 | ○ | probe(보조): devicemotion 리스너 |
| 3.1.1 | 기본 언어 표시 | 자동 | ● | axe: html-has-lang, html-lang-valid, valid-lang |
| 3.2.1 | 사용자 요구에 따른 실행 | 자동 | ◐ | probe: target=_blank·window.open·onchange 자동이동 |
| 3.2.2 | 찾기 쉬운 도움 정보 | 수동 | ○ | 증거: 스크린샷 |
| 3.3.1 | 오류 정정 | 수동 | ○ | probe(보조): form 검증 메시지 |
| 3.3.2 | 레이블 제공 | 자동 | ● | axe: label, select-name, button-name, aria-input-field-name |
| 3.3.3 | 접근 가능한 인증 | 수동 | ○ | 증거: 스크린샷 |
| 3.3.4 | 반복 입력 정보 | 수동 | ◐ | axe: autocomplete-valid |
| 4.1.1 | 마크업 오류 방지 | 자동 | ● | vnu markup |
| 4.2.1 | 웹 애플리케이션 접근성(ARIA) | 수동 | ● | axe: aria-required-attr, aria-valid-attr, aria-valid-attr-value, aria-roles, aria-allowed-attr, aria-allowed-role, aria-hidden-focus, aria-required-children, aria-required-parent |

### 웹호환성 (4)

| 코드 | 항목 | SI분류 | 자동화 | 리졸버 |
|---|---|---|---|---|
| c-webstandard | 웹표준 HTML/CSS 준수 | 자동 | ◐ | vnu(HTML 확정) + CSS 스크리닝 한계 명시 |
| c-deprecated | 마크업 오류·Deprecated 태그 | 자동 | ● | vnu: obsolete elements |
| c-browser | 브라우저 호환성 | 수동 | ● | 3엔진 스크린샷 diff (기존 CompatibilityCheck) |
| c-reflow | 해상도·확대 대응 | 수동 | ◐ | probe: 1920×1080·1366×768·200% 캡처 + 가로 오버플로/잘림 검출 |

**집계: 완전자동 15 · 반자동 11 · 수동 10 · 해당없음 1 = 37.**

### 신규 커스텀 프로브 (ProbeResolver)

`media-captions`, `autoplay`, `keyboard-operable`, `focus-visible`, `meta-refresh`,
`auto-animation`, `blink-flash`, `skip-link`(실동작), `new-window`, `accesskey`(보조),
`devicemotion`(보조), `form-validation`(보조), `linear-order`(보조), **`reflow-zoom`**.

각 프로브는 **DOM 추출(라이브)**과 **판정 로직(순수·TDD)**을 분리한다.

## 6. 준수율·판정 로직

1. **집계 단위** — 시스템별. 각 항목은 그 시스템의 모든 검사 URL을 롤업:
   어느 한 URL이라도 위반 → 항목 **부적합**, 전부 통과 → **적합**.
2. **버킷** — SI 자동/수동 분류 고정 (6/27/2/2).
3. **준수율** = `적합 / (적합 + 부적합) × 100` (소수 1자리). 해당없음(N/A)은 분모 제외.
   - 소수 항목 주의: 6항목 95% → 6/6 필요, 27항목 → 26/27 이상(1건 허용).
4. **미확정 처리** — 수동확인필요가 평가자 확정 전이면 미확정.
   미확정 ≥ 1 → **종합판정 '평가 진행 중(미완료)'**. 잠정 준수율 + '확정 대기 N건' 표기.
5. **종합 판정** — `4개 카테고리 모두 임계값 충족 && 미확정 0` ⟺ '적합'.
   한 카테고리라도 미달 → '부적합'. 확정 대기 → '미완료'.

## 7. 콘솔 '검수' 탭

- **위치**: 결과 탭의 각 리포트 행에 "검수 열기" → 그 실행(system+ts)의 37항목 인터랙티브 검수 화면.
- **화면**: 상단 4카테고리 준수율 바 + 종합판정(실시간). 카테고리별 항목 행 —
  판정 배지 · 증거(요소·스크린샷) · 친절 설명 펼치기 · 수동확인/오버라이드 토글(적합/부적합/해당없음)+메모.
- **자동 항목 오버라이드 허용**(오탐 대응) — 오버라이드 시 '평가자 확정' 표기.
- **저장**: `assessment.json` 기록 → 자동결과+확정 병합 → `report.html` 재생성 → 갱신 준수율 반환.
- **API**:
  - `GET /api/assessment/<system>/<ts>` → 병합 상태 + 준수율 + 종합판정
  - `POST /api/assessment/<system>/<ts>` → 확정값 저장 → 병합·리포트 재생성

## 8. 리포트 구조 (`report.html`)

축을 URL×검사유형 → **37항목 검수**로 재편(스크린샷·위반요소 데이터 재사용).

1. **표지** — 시스템명·검사일시·검사기준(KWCAG 2.2/KS X OT0003/고시)·브라우저 버전·URL 목록·평가자
2. **종합 판정 (SI 검수 요약표)** — 4카테고리 항목수/적합/부적합/N/A/준수율/기준/판정 + 종합 배너 + 미확정 건수
3. **카테고리별 검수결과표** — 37항목: 번호·코드·항목명·세부기준·판정배지·판정근거(자동/평가자)·위반건수
4. **항목별 상세** — 친절설명(what/why/basis/fix/limit) + 증거(위반요소·URL·스크린샷). 호환성은 3엔진·해상도/200% 캡처
5. **부록** — 검사방식 범례·한계, KWCAG 자동매핑 고지, 미확정 안내
6. **형식** — 자기완결·인쇄/PDF 최적화(카테고리·항목 페이지 분할, 표지·요약표 1페이지), 오프라인

## 9. 테스트 전략

**순수 로직 (TDD)**: KwcagCatalog(개수·필드) · ComplianceEvaluator(준수율·임계값·엣지) ·
항목 URL 롤업 · 리졸버 매핑(rule→code) · 프로브 판정 로직 · AssessmentStore 병합 ·
ComplianceReportWriter.toHtml(요약표·친절설명·자기완결).

**브라우저·외부 (실사이트 defect-kwater)**: 신규 프로브 DOM 추출부 · reflow/zoom 캡처·오버플로 검출 ·
전체 파이프라인(37항목 리포트 생성·준수율·스크린샷).

**콘솔·API (통합)**: /api/assessment 라운드트립·리포트 재생성 · 검수 탭 확정 흐름.

**회귀**: 기존 46개 단위테스트 유지. 기존 검사 3종은 리졸버 뒤에서 동작 불변으로 재사용.

**프로브 분리 예시(reflow)**:
```
추출(라이브): setViewportSize + evaluate → { overflowPx, clippedEls }
판정(순수·TDD): overflowPx>0 || clippedEls>0 ? 부적합 : 적합
```

## 10. 생성·수정 파일 (개요)

**신규**
- `config/kwcag22-catalog.yaml` — 37항목 카탈로그(guide 포함)
- `check/resolver/Resolver.java`, `AxeResolver`, `MarkupResolver`, `ProbeResolver`
- `check/probe/*` — 커스텀 프로브(추출/판정 분리)
- `report/ComplianceEvaluator.java`, `report/ComplianceReportWriter.java`
- `web/AssessmentStore.java`
- 대응 테스트

**수정**
- `check/CheckService` — 리졸버 파이프라인으로 오케스트레이션, 진행보고 유지
- `web/WebConsole` + `WebConsoleUI` — 검수 탭·assessment API
- `report/CheckResult`/`ReportWriter` — 항목 결과·롤업 반영(또는 신규 모델로 이관)

## 11. 범위 밖 (Non-goals)

- KWCAG 공식 인증 획득(자동 매핑은 참고용). 리포트에 고지.
- CSS 표준 완전 검증(vnu는 HTML 중심; CSS는 스크리닝 한계 명시).
- 네이버 웨일 등 Playwright 미지원 브라우저(엣지=chromium 근사, 사파리=webkit 근사).
- 역할별 계정 다중 커버리지(별도 후속).
