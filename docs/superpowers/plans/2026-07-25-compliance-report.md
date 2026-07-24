# 컴플라이언스 리포트 (SI 검수결과표) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** report.html 을 URL×검사유형 대시보드에서 37항목 SI 검수결과표(표지·종합판정·4카테고리 준수율·항목별 판정/근거/상세설명/증거)로 전환하고, 검사 실행 시와 평가자 확정 시 모두 재생성하며, 남은 위생 수정을 정리한다.

**Architecture:** `ComplianceReportWriter` 가 `AssessmentView.build(reportDir, catalog)` 페이로드로 자기완결·인쇄최적화 report.html 을 생성한다. `CheckService` 가 검사 후 이를 호출(기존 URL 중심 report.html 대체), `WebConsole` 의 assessment POST 가 확정 후 이를 재호출해 정적 리포트를 최신 상태로 유지한다.

**Tech Stack:** Java 17, plain Java(문자열 템플릿), 신규 의존성 없음.

## Global Constraints

- Java 17, plain Java(프레임워크·Node 금지), 신규 런타임 의존성 금지.
- **패키지 의존 방향**: report·check·web 는 compliance 에만 의존한다(역방향·순환 금지). 그래서 `AssessmentStore`/`AssessmentView` 를 web→compliance 로 이동한다(Task 1).
- 소비: `com.kt.wqg.compliance.AssessmentView.build(Path, KwcagCatalog)` → Map{items:[{no,code,category,siClassification,automation,title,criteria,guide{what,why,basis,fix,limit},verdict,source,memo,evidence:[{url,rule,detail}]}], compliance:{status,pendingTotal,categories:[{label,category,siAuto,total,적합,부적합,naCount,pending,rate,threshold,pass}]}, site, displayName, timestamp}. `KwcagCatalog`, `ComplianceStore`, `ItemVerdict`.
- report.html 은 자기완결(데이터·CSS·JS 인라인, 외부 리소스 0), 인쇄/PDF 최적화(@media print), 라이트/다크(화면).
- 검사 기준 표기: "웹접근성 KWCAG 2.2 (KS X OT0003:2022) · WCAG 2.1 AA · 웹호환성 3개 엔진".
- 컴플라이언스 정보를 담지 않은 리포트 디렉터리(compliance.json 없음)에서는 report.html 생성을 건너뛴다.
- 테스트: `mvn -B test`. 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: AssessmentStore·AssessmentView 를 compliance 패키지로 이동

**목적:** report·check 가 이들을 참조해야 하는데 web 에 있으면 순환 의존이 생긴다. 컴플라이언스 도메인 로직이므로 compliance 로 옮긴다.

**Files:**
- Move: `src/main/java/com/kt/wqg/web/AssessmentStore.java` → `src/main/java/com/kt/wqg/compliance/AssessmentStore.java` (package `com.kt.wqg.compliance`)
- Move: `src/main/java/com/kt/wqg/web/AssessmentView.java` → `src/main/java/com/kt/wqg/compliance/AssessmentView.java` (package `com.kt.wqg.compliance`)
- Move: `src/test/java/com/kt/wqg/web/AssessmentStoreTest.java` → `src/test/java/com/kt/wqg/compliance/AssessmentStoreTest.java`
- Move: `src/test/java/com/kt/wqg/web/AssessmentViewTest.java` → `src/test/java/com/kt/wqg/compliance/AssessmentViewTest.java`
- Modify: `src/main/java/com/kt/wqg/web/WebConsole.java` (import `com.kt.wqg.compliance.AssessmentView`)

- [ ] **Step 1: 파일 이동 + package 선언 변경**

각 파일의 `package com.kt.wqg.web;` → `package com.kt.wqg.compliance;` 로 바꾸고 디렉터리를 옮긴다. AssessmentStore/AssessmentView 는 이제 같은 패키지의 `ComplianceStore`, `ComplianceDoc`, `ItemResult`, `ItemVerdict`, `KwcagCatalog`, `ComplianceEvaluator`, `CatalogItem` 을 쓰므로 해당 import 는 불필요해진다(제거). 테스트도 package 를 `com.kt.wqg.compliance` 로 바꾸고, `ItemResult`/`ItemVerdict` 등 import 는 같은 패키지이므로 제거한다.

- [ ] **Step 2: WebConsole import 정정**

`WebConsole.java` 에서 `AssessmentView` 는 이제 `com.kt.wqg.compliance.AssessmentView`. 기존에 같은 패키지(web)라 import 가 없었다면 `import com.kt.wqg.compliance.AssessmentView;` 를 추가한다. `assessment(...)` 핸들러의 `AssessmentView.build/confirm` 호출은 그대로.

- [ ] **Step 3: Build + full suite**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, 이동한 테스트(AssessmentStoreTest, AssessmentViewTest) 포함 전체 PASS. 컴파일 에러가 나면 남은 web→Assessment 참조를 compliance import 로 바꾼다.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: AssessmentStore·AssessmentView 를 compliance 패키지로 이동(순환 의존 제거)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ComplianceReportWriter — 검수결과표 HTML 생성

**Files:**
- Create: `src/main/java/com/kt/wqg/report/ComplianceReportWriter.java`
- Test: `src/test/java/com/kt/wqg/report/ComplianceReportWriterTest.java`

**Interfaces:**
- Produces:
  - `class ComplianceReportWriter`:
    - static `String toHtml(Map<String,Object> payload)` — AssessmentView.build 페이로드 → 자기완결 report.html 문자열.
    - static `Path write(Path reportDir, KwcagCatalog catalog)` — `AssessmentView.build(reportDir, catalog)` 로 페이로드를 만들고 `toHtml` 결과를 `reportDir/report.html` 에 저장, 경로 반환.

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.report;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceReportWriterTest {

    private Map<String, Object> payload() {
        Map<String, Object> item = Map.of(
                "no", 7, "code", "1.4.3", "category", "accessibility", "siClassification", "manual",
                "automation", "auto", "title", "텍스트 콘텐츠의 명도 대비", "criteria", "4.5:1 이상",
                "guide", Map.of("what", "명암 대비 검사", "why", "저시력 사용자", "basis", "KWCAG 2.2 1.4.3",
                        "fix", "명도비 4.5:1 이상", "limit", "자동 검출"),
                "verdict", "부적합", "source", "auto", "memo", "",
                "evidence", List.of(Map.of("url", "https://x/a", "rule", "color-contrast", "detail", "명도비 부족")));
        Map<String, Object> cat = Map.of("label", "웹접근성 수동", "category", "accessibility", "siAuto", false,
                "total", 27, "적합", 25, "부적합", 1, "naCount", 1, "pending", 0,
                "rate", 96.2, "threshold", 95.0, "pass", true);
        return Map.of("items", List.of(item),
                "compliance", Map.of("status", "부적합", "pendingTotal", 0, "categories", List.of(cat)),
                "site", "defect-kwater", "displayName", "결함관리시스템", "timestamp", "20260725-0006");
    }

    @Test
    void 자기완결이고_검수결과표_핵심을_담는다() {
        String html = ComplianceReportWriter.toHtml(payload());
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertFalse(html.contains("<script src") || html.contains("<link "), "외부 리소스 없음");
        assertTrue(html.contains("결함관리시스템"), "시스템명");
        assertTrue(html.contains("KWCAG 2.2"), "검사 기준");
        assertTrue(html.contains("종합 판정") && html.contains("부적합"), "종합판정");
        assertTrue(html.contains("웹접근성 수동") && html.contains("96.2"), "카테고리 준수율");
        assertTrue(html.contains("1.4.3") && html.contains("텍스트 콘텐츠의 명도 대비"), "항목");
        assertTrue(html.contains("명도비 4.5:1 이상"), "상세설명 개선방법");
        assertTrue(html.contains("color-contrast"), "증거");
    }

    @Test
    void 미확정_있으면_미완료_안내() {
        Map<String, Object> p = new java.util.HashMap<>(payload());
        p.put("compliance", Map.of("status", "미완료", "pendingTotal", 5, "categories", List.of()));
        String html = ComplianceReportWriter.toHtml(p);
        assertTrue(html.contains("미완료"));
        assertTrue(html.contains("5"), "미확정 건수");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ComplianceReportWriterTest`
Expected: FAIL — `cannot find symbol: class ComplianceReportWriter`

- [ ] **Step 3: Write implementation**

`ComplianceReportWriter.java` — 페이로드를 JSON 으로 `<script id=data>` 에 인라인하고, 인라인 JS 가 아래를 렌더한다. `write` 는 `AssessmentView.build` 로 페이로드를 만든다.

```java
package com.kt.wqg.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.wqg.compliance.AssessmentView;
import com.kt.wqg.compliance.KwcagCatalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * SI 검수결과표 report.html 생성기. AssessmentView.build 페이로드(37항목 판정·증거·상세설명 +
 * 4카테고리 준수율)를 자기완결·인쇄최적화 HTML 로 렌더한다.
 */
public final class ComplianceReportWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ComplianceReportWriter() {}

    /** AssessmentView.build 로 페이로드를 만들어 reportDir/report.html 저장. */
    public static Path write(Path reportDir, KwcagCatalog catalog) {
        Map<String, Object> payload = AssessmentView.build(reportDir, catalog);
        Path out = reportDir.resolve("report.html");
        try {
            Files.writeString(out, toHtml(payload));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("report.html 저장 실패: " + out, e);
        }
    }

    public static String toHtml(Map<String, Object> payload) {
        String data;
        try {
            data = JSON.writeValueAsString(payload).replace("</", "<\\/");
        } catch (IOException e) {
            throw new RuntimeException("리포트 직렬화 실패", e);
        }
        return TEMPLATE.replace("/*__DATA__*/", data);
    }

    private static final String TEMPLATE = """
            <!DOCTYPE html>
            <html lang="ko"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>웹접근성·호환성 검수 결과</title>
            <style>
            :root{--bg:#f6f7f9;--card:#fff;--fg:#1a1d21;--muted:#6b7280;--line:#e5e7eb;
              --ok:#16a34a;--bad:#dc2626;--warn:#d97706;--okbg:#eafbf0;--badbg:#fdecec;--warnbg:#fdf4e7;--accent:#2563eb;}
            @media(prefers-color-scheme:dark){:root{--bg:#0e1116;--card:#171b22;--fg:#e6e8eb;--muted:#9099a5;
              --line:#262c36;--okbg:#0f2a1a;--badbg:#2a1414;--warnbg:#2a2110;}}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
              font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;
              line-height:1.55;padding:32px 24px;max-width:1000px;margin:0 auto}
            h1{font-size:21px;margin:0 0 4px}h2{font-size:16px;margin:26px 0 10px}
            .sub{color:var(--muted);font-size:13px}.meta{color:var(--muted);font-size:12.5px;margin-top:6px}.meta b{color:var(--fg)}
            .verdict{border-radius:14px;padding:16px 20px;margin:16px 0;border:1px solid var(--line)}
            .verdict.적합{background:var(--okbg);border-color:var(--ok)}.verdict.부적합{background:var(--badbg);border-color:var(--bad)}
            .verdict.미완료{background:var(--warnbg);border-color:var(--warn)}
            .verdict .s{font-size:19px;font-weight:800}
            .verdict.적합 .s{color:var(--ok)}.verdict.부적합 .s{color:var(--bad)}.verdict.미완료 .s{color:var(--warn)}
            table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);border-radius:10px;overflow:hidden;margin:6px 0 8px}
            th,td{text-align:left;padding:8px 12px;border-bottom:1px solid var(--line);font-size:13px}
            th{font-size:11.5px;color:var(--muted)}tr:last-child td{border-bottom:none}
            .badge{display:inline-block;padding:1px 8px;border-radius:6px;font-size:11.5px;font-weight:700}
            .badge.적합{background:var(--okbg);color:var(--ok)}.badge.부적합{background:var(--badbg);color:var(--bad)}
            .badge.수동확인필요{background:var(--warnbg);color:var(--warn)}.badge.해당없음{background:var(--line);color:var(--muted)}
            .sec{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px;margin-bottom:10px}
            .sec.부적합{border-left:4px solid var(--bad)}.sec.수동확인필요{border-left:4px solid var(--warn)}
            .sh{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.sh b{flex:1}
            .g{font-size:12.5px;color:var(--muted);margin-top:6px}.g div{margin:3px 0}.g b{color:var(--fg)}
            .ev{font-size:12px;color:var(--muted);margin-top:4px;font-family:ui-monospace,monospace;word-break:break-all}
            code{font-family:ui-monospace,monospace;font-size:12px}
            footer{margin-top:28px;padding-top:12px;border-top:1px solid var(--line);color:var(--muted);font-size:11.5px}
            @media print{body{padding:0;max-width:none;font-size:11.5px}.sec,table,.verdict{break-inside:avoid;box-shadow:none}
              h2{break-after:avoid}a{color:inherit;text-decoration:none}}
            </style></head><body><div id="app"></div>
            <script id="data" type="application/json">/*__DATA__*/</script>
            <script>
            const R=JSON.parse(document.getElementById('data').textContent);
            const esc=s=>String(s==null?'':s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
            const c=R.compliance, vc=c.status;
            let h=`<h1>웹접근성·웹호환성 검수 결과</h1>
              <div class="sub">${esc(R.displayName||R.site)} <code>(${esc(R.site)})</code></div>
              <div class="meta">검사일시 <b>${esc(R.timestamp)}</b> · 기준 <b>웹접근성 KWCAG 2.2 (KS X OT0003:2022) · WCAG 2.1 AA · 웹호환성 3개 엔진</b></div>
              <div class="verdict ${esc(vc)}"><div class="s">종합 판정 · ${esc(vc)}</div>
                <div class="sub">미확정 ${c.pendingTotal}건${c.pendingTotal>0?' — 평가자 확정 후 최종 판정됩니다':''}</div></div>`;
            h+='<h2>카테고리별 준수율</h2><table><thead><tr><th>구분</th><th>항목</th><th>적합</th><th>부적합</th><th>해당없음</th><th>대기</th><th>준수율</th><th>기준</th><th>판정</th></tr></thead><tbody>';
            (c.categories||[]).forEach(s=>{const st=s.pending>0?'<span class="badge 수동확인필요">대기</span>':(s.pass?'<span class="badge 적합">충족</span>':'<span class="badge 부적합">미달</span>');
              h+=`<tr><td>${esc(s.label)}</td><td>${s.total}</td><td>${s.적합}</td><td>${s.부적합}</td><td>${s.naCount}</td><td>${s.pending}</td><td>${s.rate}%</td><td>${s.threshold}%</td><td>${st}</td></tr>`});
            h+='</tbody></table>';
            // 카테고리별 항목 표 + 상세
            (c.categories||[]).forEach(cat=>{
              const items=R.items.filter(it=>it.category===cat.category&&(it.siClassification==='auto')===cat.siAuto);
              if(!items.length)return;
              h+=`<h2>${esc(cat.label)}</h2><table><thead><tr><th>No</th><th>코드</th><th>검사 항목</th><th>세부 기준</th><th>판정</th><th>근거</th></tr></thead><tbody>`;
              items.forEach(it=>{h+=`<tr><td>${it.no}</td><td><code>${esc(it.code)}</code></td><td>${esc(it.title)}</td><td>${esc(it.criteria)}</td>
                <td><span class="badge ${esc(it.verdict)}">${esc(it.verdict)}</span></td><td>${it.source==='assessor'?'평가자':'자동'}</td></tr>`});
              h+='</tbody></table>';
            });
            // 부적합·수동확인 상세(상세설명+증거)
            const detail=R.items.filter(it=>it.verdict==='부적합'||it.verdict==='수동확인필요');
            if(detail.length){h+='<h2>상세 — 부적합 · 수동확인 항목</h2>';
              detail.forEach(it=>{const g=it.guide||{};
                h+=`<div class="sec ${esc(it.verdict)}"><div class="sh"><span class="sub">${it.no}.</span>
                  <b>${esc(it.title)}</b> <code>${esc(it.code)}</code>
                  <span class="badge ${esc(it.verdict)}">${esc(it.verdict)}</span></div>`;
                if(it.memo)h+=`<div class="g"><b>평가자 메모</b> ${esc(it.memo)}</div>`;
                h+=`<div class="g"><div><b>무엇</b> ${esc(g.what)}</div><div><b>왜</b> ${esc(g.why)}</div>
                  <div><b>근거</b> ${esc(g.basis)}</div><div><b>개선 방법</b> ${esc(g.fix)}</div>
                  ${g.limit?'<div><b>검사 한계</b> '+esc(g.limit)+'</div>':''}</div>`;
                (it.evidence||[]).forEach(e=>{h+=`<div class="ev">${esc(e.rule)} — ${esc(e.detail)}${e.url?' ('+esc(e.url)+')':''}</div>`});
                h+='</div>';
              });
            }
            h+=`<footer>web-quality-gate · axe-core + vnu.jar + Playwright(3-engine) + 커스텀 프로브.
              KWCAG 2.2 항목은 자동 검사 결과를 매핑·집계한 것이며, 수동확인 항목은 평가자 확정을 거칩니다. 공식 인증 판정이 아닙니다.</footer>`;
            document.getElementById('app').innerHTML=h;
            </script></body></html>""";
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=ComplianceReportWriterTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Full suite + commit**

Run: `mvn -B test` → 전체 PASS
```bash
git add src/main/java/com/kt/wqg/report/ComplianceReportWriter.java src/test/java/com/kt/wqg/report/ComplianceReportWriterTest.java
git commit -m "feat: ComplianceReportWriter — SI 검수결과표 HTML 생성

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: CheckService 리포트 전환 + STANDARD 정정

**Files:**
- Modify: `src/main/java/com/kt/wqg/check/CheckService.java`
- Test: (실검증은 컨트롤러)

**Interfaces:**
- CheckService: 컴플라이언스 산출·compliance.json 저장 뒤 `ComplianceReportWriter.write(reportDir, catalog)` 로 report.html 생성(기존 `ReportWriter.write` 의 report.html 대체). `ReportWriter.write` 는 index.json 을 계속 쓰되 report.html 은 컴플라이언스 리포트가 최종본이 되도록 그 다음에 호출. STANDARD 문자열 KWCAG 2.2 로 정정.

- [ ] **Step 1: STANDARD 정정 + 리포트 생성 연결**

`CheckService.STANDARD` 를 다음으로 바꾼다:
```java
    public static final String STANDARD =
            "웹접근성 KWCAG 2.2 (KS X OT0003:2022) · WCAG 2.1 AA · 웹호환성 3개 엔진 렌더링 비교";
```
`runSite(...)` 에서 `ComplianceStore.write(...)` 호출 뒤, 그리고 기존 `ReportWriter.write(...)` 호출 뒤에 컴플라이언스 리포트로 report.html 을 덮어쓴다:
```java
            com.kt.wqg.compliance.ComplianceStore.write(reportDir,
                    new com.kt.wqg.compliance.ComplianceDoc(site.name(), site.label(), timestamp, ordered));
            // index.json (기존) 유지
            ReportWriter.write(config.output().dir(), site.name(), site.label(), timestamp,
                    STANDARD, engines, results);
            // report.html 을 SI 검수결과표로 최종 생성(기존 URL 중심 리포트 대체)
            com.kt.wqg.report.ComplianceReportWriter.write(reportDir, catalog);
            return reportDir.resolve("report.html");
```

- [ ] **Step 2: Build + full suite**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, 전체 PASS(리포트 writer 단위 테스트 포함).

- [ ] **Step 3: 실검증 (컨트롤러)**

defect-kwater 1회 실행 → report.html 이 SI 검수결과표(종합판정·카테고리 준수율표·항목표·상세)로 열리는지 브라우저 확인. 표지 기준 문자열에 "KWCAG 2.2" 표기 확인.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/kt/wqg/check/CheckService.java
git commit -m "feat: report.html 을 SI 검수결과표로 전환 + STANDARD KWCAG 2.2 정정

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: 확정 시 리포트 재생성 + 위생 수정

**Files:**
- Modify: `src/main/java/com/kt/wqg/web/WebConsole.java`
- Modify: `src/main/java/com/kt/wqg/compliance/AssessmentView.java` (Task 1 에서 이동됨)
- Test: `src/test/java/com/kt/wqg/web/AssessmentPathTest.java` (빈 문자열 케이스 추가), `src/test/java/com/kt/wqg/compliance/AssessmentViewTest.java` (병합-재평가 회귀 단언 강화, Task 1 에서 이동됨)

**Interfaces:**
- WebConsole.assessment: POST 확정 뒤 `ComplianceReportWriter.write(dir, catalog)` 로 report.html 재생성. 잘못된 verdict 문자열은 400(내부 클래스명 미노출). reportDirOf 는 빈 문자열도 거부.
- AssessmentView.build: 최상위 Map 의 site/timestamp null 가드.

- [ ] **Step 1: Write/strengthen the failing tests**

`AssessmentPathTest` 에 추가:
```java
    @Test
    void 빈_문자열_site_ts_는_거부() {
        assertThrows(IllegalArgumentException.class, () -> WebConsole.reportDirOf("", "x"));
        assertThrows(IllegalArgumentException.class, () -> WebConsole.reportDirOf("s", ""));
    }
```
`AssessmentViewTest.병합뷰는_...` 에 병합-재평가 회귀 단언을 강화(1.4.3 을 적합 확정 후 웹접근성 수동 카테고리 적합 수가 확정 반영되는지):
```java
        // 확정이 재평가에 반영됐는지 — 확정 전/후 적합 수 비교
        Map<String,Object> view2 = AssessmentView.build(dir, catalog);
        @SuppressWarnings("unchecked")
        Map<String,Object> comp = (Map<String,Object>) view2.get("compliance");
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> cats = (List<Map<String,Object>>) comp.get("categories");
        Map<String,Object> a11yManual = cats.stream()
                .filter(s -> s.get("category").equals("accessibility") && Boolean.FALSE.equals(s.get("siAuto")))
                .findFirst().orElseThrow();
        assertTrue(((Number) a11yManual.get("적합")).intValue() >= 1, "확정된 1.4.3 이 적합으로 집계");
```

- [ ] **Step 2: Run tests to verify new ones fail (empty-string) / pass (assertion)**

Run: `mvn -B test -Dtest=AssessmentPathTest,AssessmentViewTest`
Expected: `빈_문자열_site_ts_는_거부` FAIL(현재 빈 문자열 미거부), 강화된 AssessmentViewTest 는 이미 통과할 수도 있음(현재 병합 재평가가 맞으므로) — 회귀 가드로 유지.

- [ ] **Step 3: Implement fixes**

`WebConsole.reportDirOf` 빈 문자열 거부(조건에 `site.isEmpty() || ts.isEmpty()` 추가):
```java
        if (site == null || ts == null || site.isEmpty() || ts.isEmpty()
                || site.contains("/") || site.contains("\\") || site.contains("..")
                || ts.contains("/") || ts.contains("\\") || ts.contains("..")) {
            throw new IllegalArgumentException("잘못된 site/ts");
        }
```
`WebConsole.assessment` POST 처리: verdict 유효성 검사 + 확정 후 리포트 재생성:
```java
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            JsonNode b = JSON.readTree(ex.getRequestBody());
            com.kt.wqg.compliance.ItemVerdict v;
            try {
                v = com.kt.wqg.compliance.ItemVerdict.valueOf(b.path("verdict").asText());
            } catch (IllegalArgumentException badVerdict) {
                send(ex, 400, "application/json; charset=utf-8",
                        "{\"error\":\"허용되지 않은 판정값\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            AssessmentView.confirm(dir, b.path("code").asText(), v, b.path("memo").asText(""));
            com.kt.wqg.report.ComplianceReportWriter.write(dir, catalog); // 정적 리포트 최신화
        }
        sendJson(ex, AssessmentView.build(dir, catalog));
```
`AssessmentView.build` 최상위 반환에서 site/timestamp null 가드(`n(...)` 적용):
```java
        return Map.of("items", items,
                "compliance", Map.of("status", c.status(), "pendingTotal", c.pendingTotal(), "categories", cats),
                "site", n(doc.site()), "displayName", n(doc.displayName()), "timestamp", n(doc.timestamp()));
```

- [ ] **Step 4: Build + full suite**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, AssessmentPathTest(빈문자열 포함) + AssessmentViewTest 통과, 전체 PASS.

- [ ] **Step 5: 실검증 (컨트롤러)**

콘솔에서 검수 화면 한 항목 확정 → 해당 리포트 report.html 을 다시 열어 그 항목이 '평가자' 근거로 바뀌고 종합판정/준수율이 갱신됐는지 확인.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/kt/wqg/web/WebConsole.java src/main/java/com/kt/wqg/web/AssessmentView.java src/test/java/com/kt/wqg/web/AssessmentPathTest.java src/test/java/com/kt/wqg/web/AssessmentViewTest.java
git commit -m "feat: 확정 시 리포트 재생성 + 위생 수정(verdict 400·빈문자열·null가드·회귀단언)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준 (계획 5)

- report.html 이 SI 검수결과표(표지·종합판정·4카테고리 준수율표·항목표·부적합/수동확인 상세+상세설명+증거)
- 검사 실행 시와 평가자 확정 시 모두 report.html 재생성
- STANDARD 문자열 KWCAG 2.2 표기, 위생 수정(verdict 400·빈문자열 거부·null 가드·회귀 단언) 반영
- `mvn -B test` 전체 통과 + defect-kwater 실검증(리포트 열람·확정 후 재생성)

## 후속 계획

- 계획 3b — 접근성 DOM 프로브(media-captions·new-window·meta-refresh·blink-flash) 스크리닝 증거 추가
