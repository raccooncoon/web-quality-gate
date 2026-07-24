# 검수 저장 + 콘솔 검수 탭 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 검사 실행 시 37항목 자동 판정 결과를 compliance.json 으로 저장하고, 콘솔의 '검수' 화면에서 평가자가 수동확인필요/오버라이드 항목을 적합/부적합/해당없음으로 확정(assessment.json 저장)하며, 자동+확정을 병합해 4카테고리 준수율·종합판정을 실시간으로 보여준다.

**Architecture:** CheckService 가 컴플라이언스 결과를 `compliance.json` 으로 저장 → `AssessmentStore` 가 평가자 확정을 `assessment.json` 으로 저장 → `AssessmentView` 가 compliance.json + assessment.json + 카탈로그를 병합해 화면·API 페이로드(37항목 메타+판정+증거 + 준수율)를 만든다 → `WebConsole` 이 GET/POST /api/assessment 로 노출, 결과 탭에서 '검수 열기'. 정적 리포트 재생성은 계획 5.

**Tech Stack:** Java 17, Jackson(기존), JDK HttpServer(기존). 신규 의존성 없음.

## Global Constraints

- Java 17, plain Java(프레임워크·Node 금지), 신규 런타임 의존성 금지.
- 기존 소비: `com.kt.wqg.compliance.{KwcagCatalog, CatalogItem, ItemResult, ItemVerdict, ComplianceEvaluator, ComplianceMapper}`, `com.kt.wqg.web.{WebConsole, WebConsoleUI, ConfigStore, CredentialStore, JobRunner}`, `com.kt.wqg.check.CheckService`.
- 판정 상태: `적합|부적합|수동확인필요|해당없음`(ItemVerdict). 병합 시 평가자 확정값이 자동값을 오버라이드하며 source 는 "assessor".
- 준수율/종합판정 규칙은 계획 1 `ComplianceEvaluator` 그대로 사용(병합된 맵으로 재평가).
- 저장 위치: `reports/<site>/<timestamp>/compliance.json`, `assessment.json`. 계정·비밀은 저장하지 않음.
- 콘솔은 127.0.0.1 바인딩 유지.
- 테스트: `mvn -B test`. 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: 컴플라이언스 결과 compliance.json 저장

**Files:**
- Create: `src/main/java/com/kt/wqg/compliance/ComplianceDoc.java`
- Create: `src/main/java/com/kt/wqg/compliance/ComplianceStore.java`
- Modify: `src/main/java/com/kt/wqg/check/CheckService.java`
- Test: `src/test/java/com/kt/wqg/compliance/ComplianceStoreTest.java`

**Interfaces:**
- Produces:
  - `record ComplianceDoc(String site, String displayName, String timestamp, List<ItemResult> items)`
  - `class ComplianceStore` static `void write(Path dir, ComplianceDoc doc)` → `<dir>/compliance.json`; static `ComplianceDoc read(Path dir)`.
  - CheckService: 컴플라이언스 산출 후 `ComplianceStore.write(reportDir, doc)` 호출(리포트 저장 근처). doc.items 는 카탈로그 순서(catalog.items())로 정렬한 ItemResult.

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceStoreTest {
    @Test
    void doc_를_저장하고_되읽는다(@TempDir Path dir) {
        ComplianceDoc doc = new ComplianceDoc("defect-kwater", "결함관리", "20260724-1500",
                List.of(new ItemResult("1.4.3", ItemVerdict.부적합, "auto",
                        List.of(new ItemResult.Evidence("https://x/a", "color-contrast", "명도비")), null)));
        ComplianceStore.write(dir, doc);

        ComplianceDoc back = ComplianceStore.read(dir);
        assertEquals("defect-kwater", back.site());
        assertEquals(1, back.items().size());
        assertEquals(ItemVerdict.부적합, back.items().get(0).verdict());
        assertEquals("color-contrast", back.items().get(0).evidence().get(0).rule());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ComplianceStoreTest`
Expected: FAIL — `cannot find symbol: class ComplianceDoc / ComplianceStore`

- [ ] **Step 3a: ComplianceDoc**

```java
// ComplianceDoc.java
package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** compliance.json 스키마: 실행 1회의 자동 판정 결과(항목 순서 유지). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplianceDoc(String site, String displayName, String timestamp, List<ItemResult> items) {}
```

`ItemResult`, `ItemResult.Evidence`, `ItemVerdict` 에 Jackson 역직렬화가 되도록 각 record/enum 에 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 추가한다(직렬화는 record 기본, 역직렬화는 canonical 생성자 사용). ItemVerdict enum 은 이름(적합 등)으로 직렬화된다.

- [ ] **Step 3b: ComplianceStore**

```java
// ComplianceStore.java
package com.kt.wqg.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** compliance.json 저장/로드. */
public final class ComplianceStore {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ComplianceStore() {}

    public static void write(Path dir, ComplianceDoc doc) {
        try {
            Files.createDirectories(dir);
            JSON.writeValue(dir.resolve("compliance.json").toFile(), doc);
        } catch (IOException e) {
            throw new UncheckedIOException("compliance.json 저장 실패: " + dir, e);
        }
    }

    public static ComplianceDoc read(Path dir) {
        try {
            return JSON.readValue(dir.resolve("compliance.json").toFile(), ComplianceDoc.class);
        } catch (IOException e) {
            throw new UncheckedIOException("compliance.json 로드 실패: " + dir, e);
        }
    }
}
```

- [ ] **Step 3c: CheckService 저장 연결**

`runSite(...)` 의 컴플라이언스 산출부에서, 맵을 구한 뒤 카탈로그 순서 ItemResult 리스트로 doc 을 만들어 저장한다. 기존:
```java
            com.kt.wqg.compliance.KwcagCatalog catalog = com.kt.wqg.compliance.KwcagCatalog.load();
            var itemMap = new com.kt.wqg.compliance.ComplianceMapper(catalog).map(results, probeFindings);
            com.kt.wqg.compliance.ComplianceEvaluator.Compliance compliance =
                    new com.kt.wqg.compliance.ComplianceEvaluator(catalog).evaluate(itemMap);
            progress.accept("컴플라이언스: " + compliance.status()
                    + " (미확정 " + compliance.pendingTotal() + "건)");
            java.util.List<com.kt.wqg.compliance.ItemResult> ordered = catalog.items().stream()
                    .map(it -> itemMap.get(it.code())).toList();
            com.kt.wqg.compliance.ComplianceStore.write(reportDir,
                    new com.kt.wqg.compliance.ComplianceDoc(site.name(), site.label(), timestamp, ordered));
```
(기존 인라인 map/evaluate 호출을 위처럼 itemMap 변수로 분리 후 저장 추가.)

- [ ] **Step 4: Build + tests**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, 전체 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/kt/wqg/compliance/ComplianceDoc.java src/main/java/com/kt/wqg/compliance/ComplianceStore.java src/main/java/com/kt/wqg/compliance/ItemResult.java src/main/java/com/kt/wqg/compliance/ItemVerdict.java src/main/java/com/kt/wqg/check/CheckService.java src/test/java/com/kt/wqg/compliance/ComplianceStoreTest.java
git commit -m "feat: 컴플라이언스 결과 compliance.json 저장

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: AssessmentStore — 평가자 확정 저장·병합

**Files:**
- Create: `src/main/java/com/kt/wqg/web/AssessmentStore.java`
- Test: `src/test/java/com/kt/wqg/web/AssessmentStoreTest.java`

**Interfaces:**
- Produces:
  - `class AssessmentStore`:
    - `AssessmentStore(Path dir)` — `<dir>/assessment.json`
    - `void save(String code, ItemVerdict verdict, String memo)` — 확정값 기록(파일 저장)
    - `List<ItemResult> merge(List<ItemResult> auto)` — 각 auto 항목에 확정값이 있으면 verdict·memo 오버라이드, source "assessor"; 없으면 원본 유지. 순서 보존.

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.web;

import com.kt.wqg.compliance.ItemResult;
import com.kt.wqg.compliance.ItemVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentStoreTest {
    private ItemResult auto(String code, ItemVerdict v) {
        return new ItemResult(code, v, "auto", List.of(), null);
    }

    @Test
    void 확정값이_자동값을_오버라이드하고_파일에_남는다(@TempDir Path dir) {
        AssessmentStore store = new AssessmentStore(dir);
        store.save("2.5.1", ItemVerdict.부적합, "제스처 대체 없음");

        // 새 인스턴스로 로드해도 유지
        List<ItemResult> merged = new AssessmentStore(dir).merge(List.of(
                auto("2.5.1", ItemVerdict.수동확인필요),
                auto("1.4.3", ItemVerdict.적합)));

        ItemResult a = merged.stream().filter(r -> r.code().equals("2.5.1")).findFirst().orElseThrow();
        assertEquals(ItemVerdict.부적합, a.verdict());
        assertEquals("assessor", a.source());
        assertEquals("제스처 대체 없음", a.memo());
        // 확정 안 한 항목은 그대로
        ItemResult b = merged.stream().filter(r -> r.code().equals("1.4.3")).findFirst().orElseThrow();
        assertEquals(ItemVerdict.적합, b.verdict());
        assertEquals("auto", b.source());
    }

    @Test
    void 확정없으면_원본그대로(@TempDir Path dir) {
        List<ItemResult> merged = new AssessmentStore(dir).merge(List.of(auto("x", ItemVerdict.적합)));
        assertEquals("auto", merged.get(0).source());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=AssessmentStoreTest`
Expected: FAIL — `cannot find symbol: class AssessmentStore`

- [ ] **Step 3: Write implementation**

```java
// AssessmentStore.java
package com.kt.wqg.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kt.wqg.compliance.ItemResult;
import com.kt.wqg.compliance.ItemVerdict;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 평가자 확정을 assessment.json 에 저장하고 자동 판정과 병합한다. */
public final class AssessmentStore {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String verdict, String memo) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Doc(Map<String, Entry> items) {}

    private final Path file;
    private final Map<String, Entry> entries;

    public AssessmentStore(Path dir) {
        this.file = dir.resolve("assessment.json");
        this.entries = load();
    }

    private Map<String, Entry> load() {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            Doc doc = JSON.readValue(file.toFile(), Doc.class);
            return doc.items() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(doc.items());
        } catch (IOException e) {
            throw new UncheckedIOException("assessment.json 로드 실패: " + file, e);
        }
    }

    public void save(String code, ItemVerdict verdict, String memo) {
        entries.put(code, new Entry(verdict.name(), memo));
        try {
            Files.createDirectories(file.getParent());
            JSON.writeValue(file.toFile(), new Doc(entries));
        } catch (IOException e) {
            throw new UncheckedIOException("assessment.json 저장 실패: " + file, e);
        }
    }

    public List<ItemResult> merge(List<ItemResult> auto) {
        List<ItemResult> out = new ArrayList<>();
        for (ItemResult r : auto) {
            Entry e = entries.get(r.code());
            if (e != null) {
                out.add(new ItemResult(r.code(), ItemVerdict.valueOf(e.verdict()),
                        "assessor", r.evidence(), e.memo()));
            } else {
                out.add(r);
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=AssessmentStoreTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/kt/wqg/web/AssessmentStore.java src/test/java/com/kt/wqg/web/AssessmentStoreTest.java
git commit -m "feat: AssessmentStore — 평가자 확정 저장·병합(오버라이드)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: AssessmentView — 병합 뷰·재평가 페이로드

**Files:**
- Create: `src/main/java/com/kt/wqg/web/AssessmentView.java`
- Test: `src/test/java/com/kt/wqg/web/AssessmentViewTest.java`

**Interfaces:**
- Consumes: `ComplianceStore`, `AssessmentStore`, `KwcagCatalog`, `ComplianceEvaluator`
- Produces:
  - `class AssessmentView` static `Map<String,Object> build(Path reportDir, KwcagCatalog catalog)` — compliance.json 로드 → assessment 병합 → 재평가. 반환 맵:
    - `"items"`: List of `{no, code, category, siClassification, automation, title, criteria, guide, verdict, source, memo, evidence:[{url,rule,detail}]}` (카탈로그 순서)
    - `"compliance"`: `{status, pendingTotal, categories:[{label, category, siAuto, total, 적합, 부적합, naCount, pending, rate, threshold, pass}]}`
  - static `void confirm(Path reportDir, String code, ItemVerdict verdict, String memo)` — AssessmentStore.save 위임.

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.web;

import com.kt.wqg.compliance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentViewTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();

    @Test
    void 병합뷰는_항목메타_판정_준수율을_담고_확정이_반영된다(@TempDir Path dir) {
        // 자동 결과 저장: 전 항목 수동확인필요(간단화 위해 1.4.3 만 명시, 나머지는 카탈로그 기반)
        List<ItemResult> ordered = catalog.items().stream()
                .map(it -> ItemResult.of(it.code(), ItemVerdict.수동확인필요)).toList();
        ComplianceStore.write(dir, new ComplianceDoc("s", "S", "t", ordered));

        // 평가자가 1.4.3 을 적합으로 확정
        AssessmentView.confirm(dir, "1.4.3", ItemVerdict.적합, "확인함");

        Map<String, Object> view = AssessmentView.build(dir, catalog);
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> items = (List<Map<String,Object>>) view.get("items");
        assertEquals(37, items.size());
        Map<String,Object> row = items.stream().filter(m -> m.get("code").equals("1.4.3")).findFirst().orElseThrow();
        assertEquals("적합", row.get("verdict"));
        assertEquals("assessor", row.get("source"));
        assertNotNull(row.get("title"));
        assertNotNull(row.get("guide"));

        @SuppressWarnings("unchecked")
        Map<String,Object> compliance = (Map<String,Object>) view.get("compliance");
        assertNotNull(compliance.get("status"));
        assertTrue(((Number) compliance.get("pendingTotal")).intValue() >= 1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=AssessmentViewTest`
Expected: FAIL — `cannot find symbol: class AssessmentView`

- [ ] **Step 3: Write implementation**

```java
// AssessmentView.java
package com.kt.wqg.web;

import com.kt.wqg.compliance.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 자동 판정(compliance.json) + 평가자 확정(assessment.json) + 카탈로그를 병합한 검수 뷰. */
public final class AssessmentView {

    private AssessmentView() {}

    public static void confirm(Path reportDir, String code, ItemVerdict verdict, String memo) {
        new AssessmentStore(reportDir).save(code, verdict, memo);
    }

    public static Map<String, Object> build(Path reportDir, KwcagCatalog catalog) {
        ComplianceDoc doc = ComplianceStore.read(reportDir);
        List<ItemResult> merged = new AssessmentStore(reportDir).merge(doc.items());

        Map<String, ItemResult> byCode = new LinkedHashMap<>();
        merged.forEach(r -> byCode.put(r.code(), r));

        List<Map<String, Object>> items = new ArrayList<>();
        for (CatalogItem c : catalog.items()) {
            ItemResult r = byCode.getOrDefault(c.code(), ItemResult.of(c.code(), ItemVerdict.수동확인필요));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("no", c.no());
            m.put("code", c.code());
            m.put("category", c.category());
            m.put("siClassification", c.siClassification());
            m.put("automation", c.automation());
            m.put("title", c.title());
            m.put("criteria", c.criteria());
            m.put("guide", Map.of("what", n(c.guide().what()), "why", n(c.guide().why()),
                    "basis", n(c.guide().basis()), "fix", n(c.guide().fix()), "limit", n(c.guide().limit())));
            m.put("verdict", r.verdict().name());
            m.put("source", r.source());
            m.put("memo", r.memo());
            m.put("evidence", r.evidence().stream().map(e -> Map.of(
                    "url", n(e.url()), "rule", n(e.rule()), "detail", n(e.detail()))).toList());
            items.add(m);
        }

        ComplianceEvaluator.Compliance c = new ComplianceEvaluator(catalog).evaluate(byCode);
        List<Map<String, Object>> cats = c.categories().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", s.label()); m.put("category", s.category()); m.put("siAuto", s.siAuto());
            m.put("total", s.total()); m.put("적합", s.적합()); m.put("부적합", s.부적합());
            m.put("naCount", s.naCount()); m.put("pending", s.pending());
            m.put("rate", s.rate()); m.put("threshold", s.threshold()); m.put("pass", s.pass());
            return m;
        }).toList();

        return Map.of("items", items,
                "compliance", Map.of("status", c.status(), "pendingTotal", c.pendingTotal(), "categories", cats),
                "site", doc.site(), "displayName", n(doc.displayName()), "timestamp", doc.timestamp());
    }

    private static String n(String s) { return s == null ? "" : s; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=AssessmentViewTest`
Expected: PASS

- [ ] **Step 5: Full suite + commit**

Run: `mvn -B test` → 전체 PASS
```bash
git add src/main/java/com/kt/wqg/web/AssessmentView.java src/test/java/com/kt/wqg/web/AssessmentViewTest.java
git commit -m "feat: AssessmentView — 자동+확정+카탈로그 병합 검수 뷰·재평가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: WebConsole API + 검수 UI 탭

**Files:**
- Modify: `src/main/java/com/kt/wqg/web/WebConsole.java`
- Modify: `src/main/java/com/kt/wqg/web/WebConsoleUI.java`
- Test: `src/test/java/com/kt/wqg/web/AssessmentPathTest.java`

**Interfaces:**
- Produces:
  - WebConsole 라우트: `GET /api/assessment?site=<s>&ts=<t>` → `AssessmentView.build(reports/<s>/<t>, catalog)` JSON. `POST /api/assessment?site=<s>&ts=<t>` 바디 `{code, verdict, memo}` → `AssessmentView.confirm(...)` 후 갱신된 build 반환.
  - 경로 안전: reports 루트 밖 접근 차단(기존 serveReportFile 패턴 재사용). site/ts 정규화 후 `reports/<site>/<ts>` 존재 확인.
  - UI: 결과 탭 각 리포트 행에 "검수 열기" → 37항목 인터랙티브 화면(카테고리별, 준수율 바, 수동확인/오버라이드 토글+메모, 저장).

- [ ] **Step 1: Write the failing test (경로 파싱·안전성 순수부)**

WebConsole 에 정적 헬퍼를 추가해 site/ts 파라미터로부터 안전한 reportDir 을 만드는 부분을 순수 테스트한다.

```java
package com.kt.wqg.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentPathTest {
    @Test
    void 정상_site_ts_는_reports_하위_경로() {
        Path p = WebConsole.reportDirOf("defect-kwater", "20260724-1500");
        assertTrue(p.endsWith(Path.of("reports", "defect-kwater", "20260724-1500")));
    }

    @Test
    void 상위경로_탈출_시도는_거부() {
        assertThrows(IllegalArgumentException.class,
                () -> WebConsole.reportDirOf("../etc", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> WebConsole.reportDirOf("s", "../../x"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=AssessmentPathTest`
Expected: FAIL — `cannot find symbol: method reportDirOf`

- [ ] **Step 3a: WebConsole — reportDirOf + assessment 라우트**

`WebConsole` 에 정적 헬퍼 추가:
```java
    /** site/ts 파라미터로부터 reports 하위 안전 경로를 만든다(경로 탈출 차단). */
    static Path reportDirOf(String site, String ts) {
        if (site == null || ts == null || site.contains("/") || site.contains("\\")
                || site.contains("..") || ts.contains("/") || ts.contains("\\") || ts.contains("..")) {
            throw new IllegalArgumentException("잘못된 site/ts");
        }
        return Path.of("reports", site, ts);
    }
```
`route(...)` 디스패치에 추가(쿼리 파싱은 `ex.getRequestURI().getQuery()` → site/ts 추출; 간단 파서):
```java
            if (path.equals("/api/assessment")) { assessment(ex); return; }
```
핸들러:
```java
    private void assessment(HttpExchange ex) throws IOException {
        Map<String, String> q = queryParams(ex.getRequestURI().getRawQuery());
        Path dir = reportDirOf(q.get("site"), q.get("ts"));
        if (!Files.isRegularFile(dir.resolve("compliance.json"))) {
            send(ex, 404, "application/json; charset=utf-8",
                    "{\"error\":\"compliance.json 없음\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        KwcagCatalog catalog = KwcagCatalog.load();
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            JsonNode b = JSON.readTree(ex.getRequestBody());
            AssessmentView.confirm(dir, b.path("code").asText(),
                    com.kt.wqg.compliance.ItemVerdict.valueOf(b.path("verdict").asText()),
                    b.path("memo").asText(""));
        }
        sendJson(ex, AssessmentView.build(dir, catalog));
    }

    private static Map<String, String> queryParams(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(java.net.URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }
```
필요한 import: `com.kt.wqg.compliance.KwcagCatalog`, `java.nio.file.Files`. `JsonNode`/`JSON`/`send`/`sendJson` 는 기존 존재.

- [ ] **Step 3b: WebConsoleUI — 결과 탭에 "검수 열기" + 검수 화면**

`WebConsoleUI.HTML` 의 `loadResult()` 에서 각 리포트 행에 검수 버튼을 추가하고, 클릭 시 `/api/assessment?site=&ts=` 를 불러 37항목을 카테고리별로 렌더한다. 요구사항(기존 UI 패턴·CSS 재사용):
- 리포트 행: 기존 "리포트 열기" 옆에 `<button onclick="openAssessment('<site>','<ts>')">검수</button>`.
- `openAssessment(site, ts)`: GET → 상단에 종합판정+카테고리 준수율 바(기존 pill/bar 스타일), 이어서 카테고리별 항목 목록. 각 항목: 코드·제목·판정 배지·근거(자동/평가자)·증거(있으면)·친절설명 펼치기(guide). `verdict` 가 `수동확인필요` 이거나 사용자가 오버라이드하려는 항목에 `[적합][부적합][해당없음]` 버튼 + 메모 input, 클릭 시 `POST /api/assessment?site=&ts=` `{code,verdict,memo}` 후 응답으로 화면·준수율 갱신.
- 저장/확정 시 서버가 갱신된 build 를 반환하므로 그 데이터로 다시 렌더.
- 외부 리소스 없이 기존 인라인 스타일 사용.

> 구현 자유도: UI 세부 마크업은 기존 WebConsoleUI 의 카드·배지·pill 패턴을 따르되, 위 상호작용(조회→확정→준수율 갱신)이 동작하면 된다.

- [ ] **Step 4: Build + tests**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, AssessmentPathTest PASS, 전체 PASS.

- [ ] **Step 5: 실검증 (컨트롤러) — 콘솔에서 검수 흐름**

컨트롤러가 수행: defect-kwater 를 1회 실행해 compliance.json 생성 → 콘솔 기동 → `GET /api/assessment?site=defect-kwater&ts=<ts>` 가 37항목+준수율 반환 확인 → `POST` 로 한 항목 확정 → 응답 준수율·판정이 갱신되는지 → 브라우저에서 결과 탭 "검수 열기" 화면 캡처.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/kt/wqg/web/WebConsole.java src/main/java/com/kt/wqg/web/WebConsoleUI.java src/test/java/com/kt/wqg/web/AssessmentPathTest.java
git commit -m "feat: 콘솔 검수 API + 검수 탭(37항목 확정·준수율 실시간)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준 (계획 4)

- 검사 실행 시 `compliance.json` 저장
- `AssessmentStore` 가 평가자 확정을 `assessment.json` 에 저장·병합(오버라이드)
- `AssessmentView` 가 자동+확정+카탈로그를 병합해 37항목 메타·판정·준수율 페이로드 생성
- 콘솔 `GET/POST /api/assessment` + 검수 탭에서 확정 시 준수율·종합판정 실시간 갱신
- `mvn -B test` 전체 통과 + defect-kwater 실검증(조회→확정→갱신)

## 후속 계획

- 계획 5 — ComplianceReportWriter(SI 검수결과표 + 친절설명 HTML) + 저장 시 리포트 재생성 + STANDARD 문자열 KWCAG 2.2 정정
- 계획 3b — 접근성 DOM 프로브(media-captions·new-window·meta-refresh·blink-flash) 스크리닝 증거
