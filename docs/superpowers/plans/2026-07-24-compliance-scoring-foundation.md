# KWCAG 컴플라이언스 스코어링 기반 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 37항목 KWCAG 2.2 카탈로그를 데이터로 로드하고, 항목별 판정을 URL 전체로 롤업해 4카테고리 준수율·종합판정을 산출하는 순수 스코어링 엔진을 만든다.

**Architecture:** 카탈로그(YAML 리소스) → `KwcagCatalog` 로더 → 항목별 `ItemResult` → `ComplianceEvaluator`가 카테고리 버킷(SI 자동/수동 분류 고정)으로 집계해 준수율(적합/(적합+부적합), N/A 제외)과 종합판정(4카테고리 임계값+미확정 0)을 산출. 이 계획은 브라우저·기존 검사와 무관한 순수 로직만 다룬다(리졸버 연결은 계획 2).

**Tech Stack:** Java 17, Jackson YAML(기존 의존성), JUnit 5(기존).

## Global Constraints

- Java 17, Maven, shade 단일 jar. 프레임워크 금지(Spring 도입 금지). Node 도구 금지.
- 함수형 스타일 선호, 오버엔지니어링 금지, plain Java 유지.
- 카테고리 버킷은 SI 분류(자동/수동) 고정: 웹접근성 자동 6 / 웹접근성 수동 27 / 웹호환성 자동 2 / 웹호환성 수동 2.
- 준수율 임계값: 웹접근성 95% 이상, 웹호환성 100%. 준수율 = 적합/(적합+부적합)×100, 해당없음(N/A) 분모 제외.
- 종합 '적합' ⟺ 4개 카테고리 모두 임계값 충족 그리고 미확정 0건. 미확정 ≥1 → '미완료'. 카테고리 미달 → '부적합'.
- 테스트 실행: `mvn -B test`. 커밋 메시지 말미에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: 항목 판정 상태 · 결과 모델

**Files:**
- Create: `src/main/java/com/kt/wqg/compliance/ItemVerdict.java`
- Create: `src/main/java/com/kt/wqg/compliance/ItemResult.java`
- Test: `src/test/java/com/kt/wqg/compliance/ItemResultTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum ItemVerdict { 적합, 부적합, 수동확인필요, 해당없음 }`
  - `record ItemResult(String code, ItemVerdict verdict, String source, List<ItemResult.Evidence> evidence, String memo)`
    - `record Evidence(String url, String rule, String detail)`
    - static `ItemResult.of(String code, ItemVerdict verdict)` — source="auto", 빈 evidence, null memo

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemResultTest {
    @Test
    void of_는_자동출처_빈증거로_생성한다() {
        ItemResult r = ItemResult.of("1.4.3", ItemVerdict.적합);
        assertEquals("1.4.3", r.code());
        assertEquals(ItemVerdict.적합, r.verdict());
        assertEquals("auto", r.source());
        assertEquals(List.of(), r.evidence());
    }

    @Test
    void 증거와_메모를_담는다() {
        ItemResult r = new ItemResult("2.5.1", ItemVerdict.부적합, "assessor",
                List.of(new ItemResult.Evidence("https://x/a", "gesture", "제스처 대체 없음")), "확인함");
        assertEquals(1, r.evidence().size());
        assertEquals("제스처 대체 없음", r.evidence().get(0).detail());
        assertEquals("확인함", r.memo());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ItemResultTest`
Expected: FAIL — `cannot find symbol: class ItemResult / ItemVerdict`

- [ ] **Step 3: Write minimal implementation**

```java
// ItemVerdict.java
package com.kt.wqg.compliance;

/** 검사 항목 판정 상태. */
public enum ItemVerdict { 적합, 부적합, 수동확인필요, 해당없음 }
```

```java
// ItemResult.java
package com.kt.wqg.compliance;

import java.util.List;

/** 한 시스템 기준의 항목별 검사 결과(모든 URL 롤업 후). */
public record ItemResult(String code, ItemVerdict verdict, String source,
                         List<Evidence> evidence, String memo) {

    /** 항목 위반 증거 한 건. */
    public record Evidence(String url, String rule, String detail) {}

    /** 자동 판정 결과(증거·메모 없음)를 만든다. */
    public static ItemResult of(String code, ItemVerdict verdict) {
        return new ItemResult(code, verdict, "auto", List.of(), null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=ItemResultTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/kt/wqg/compliance/ItemVerdict.java src/main/java/com/kt/wqg/compliance/ItemResult.java src/test/java/com/kt/wqg/compliance/ItemResultTest.java
git commit -m "feat: 컴플라이언스 항목 판정 상태·결과 모델

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 카탈로그 항목 모델

**Files:**
- Create: `src/main/java/com/kt/wqg/compliance/CatalogItem.java`
- Test: `src/test/java/com/kt/wqg/compliance/CatalogItemTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `record CatalogItem(int no, String code, String category, String siClassification, String automation, String title, String criteria, Resolver resolver, Guide guide)`
    - `record Resolver(String type, List<String> rules)` — type: `axe|markup|probe|none`
    - `record Guide(String what, String why, String basis, String fix, String limit)`
    - `boolean isAccessibility()` → `"accessibility".equals(category)`
    - `boolean siAuto()` → `"auto".equals(siClassification)`

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatalogItemTest {
    @Test
    void 카테고리와_SI분류_판별() {
        CatalogItem a = new CatalogItem(7, "1.4.3", "accessibility", "manual", "auto",
                "명도 대비", "4.5:1", new CatalogItem.Resolver("axe", List.of("color-contrast")),
                new CatalogItem.Guide("w", "y", "b", "f", "l"));
        assertTrue(a.isAccessibility());
        assertFalse(a.siAuto());
        assertEquals("color-contrast", a.resolver().rules().get(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=CatalogItemTest`
Expected: FAIL — `cannot find symbol: class CatalogItem`

- [ ] **Step 3: Write minimal implementation**

```java
// CatalogItem.java
package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** KWCAG 2.2 검수 카탈로그의 한 항목(데이터). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogItem(int no, String code, String category, String siClassification,
                          String automation, String title, String criteria,
                          Resolver resolver, Guide guide) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resolver(String type, List<String> rules) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Guide(String what, String why, String basis, String fix, String limit) {}

    public boolean isAccessibility() { return "accessibility".equals(category); }
    public boolean siAuto() { return "auto".equals(siClassification); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=CatalogItemTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/kt/wqg/compliance/CatalogItem.java src/test/java/com/kt/wqg/compliance/CatalogItemTest.java
git commit -m "feat: KWCAG 카탈로그 항목 모델

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 카탈로그 리소스 파일(37항목) + 로더

**Files:**
- Create: `src/main/resources/kwcag22-catalog.yaml`
- Create: `src/main/java/com/kt/wqg/compliance/KwcagCatalog.java`
- Test: `src/test/java/com/kt/wqg/compliance/KwcagCatalogTest.java`

**Interfaces:**
- Consumes: `CatalogItem` (Task 2)
- Produces:
  - `class KwcagCatalog` with:
    - static `KwcagCatalog load()` — 클래스패스 `/kwcag22-catalog.yaml` 로드
    - `List<CatalogItem> items()`
    - `CatalogItem byCode(String code)` — 없으면 `IllegalArgumentException`
    - `List<CatalogItem> byBucket(String category, boolean siAuto)` — 카테고리+SI분류 필터

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KwcagCatalogTest {
    private final KwcagCatalog cat = KwcagCatalog.load();

    @Test
    void 총_37항목_로드() {
        assertEquals(37, cat.items().size());
    }

    @Test
    void SI_카테고리_버킷_개수가_6_27_2_2() {
        assertEquals(6,  cat.byBucket("accessibility", true).size());
        assertEquals(27, cat.byBucket("accessibility", false).size());
        assertEquals(2,  cat.byBucket("compatibility", true).size());
        assertEquals(2,  cat.byBucket("compatibility", false).size());
    }

    @Test
    void 코드로_조회하고_필수필드가_채워져있다() {
        CatalogItem c = cat.byCode("1.4.3");
        assertEquals("텍스트 콘텐츠의 명도 대비", c.title());
        assertEquals("auto", c.automation());
        assertNotNull(c.guide().fix());
        assertFalse(c.guide().what().isBlank());
    }

    @Test
    void 모든_항목이_guide와_resolver를_가진다() {
        for (CatalogItem c : cat.items()) {
            assertNotNull(c.resolver(), "resolver 누락: " + c.code());
            assertNotNull(c.guide(), "guide 누락: " + c.code());
            assertFalse(c.guide().what().isBlank(), "what 누락: " + c.code());
            assertFalse(c.guide().fix().isBlank(), "fix 누락: " + c.code());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=KwcagCatalogTest`
Expected: FAIL — `cannot find symbol: class KwcagCatalog`

- [ ] **Step 3a: Create the catalog resource**

`src/main/resources/kwcag22-catalog.yaml` — 37항목. 각 항목의 구조 필드(no·code·category·siClassification·automation·title·criteria·resolver)는 스펙 §5 매핑 표를 그대로 옮기고, guide(what/why/basis/fix/limit)는 항목별 한국어 설명을 작성한다. 아래는 대표 3항목의 완성 형태이며, 나머지 34항목도 **동일 구조·동일 상세도**로 작성한다(구조 필드는 스펙 §5, guide는 criteria 기준으로 개발자 개선방법+심사 근거 포함).

```yaml
items:
  - no: 1
    code: "1.1.1"
    category: accessibility
    siClassification: auto
    automation: auto
    title: "적절한 대체 텍스트 제공"
    criteria: "의미 있는 이미지에 적절한 대체텍스트, 장식 이미지는 빈 alt"
    resolver: { type: axe, rules: [image-alt, area-alt, svg-img-alt, role-img-alt, object-alt, input-image-alt] }
    guide:
      what: "이미지 등 비텍스트 콘텐츠에 대체 텍스트(alt)가 있는지 검사합니다."
      why: "스크린리더 사용자는 alt 텍스트로 이미지의 의미를 전달받습니다."
      basis: "KWCAG 2.2 1.1.1 · 장애인차별금지법 · KS X OT0003:2022"
      fix: "의미 있는 이미지: alt=\"내용 설명\". 장식 이미지: alt=\"\"(빈 값). 예) <img src=\"logo.png\" alt=\"회사 로고\">"
      limit: "alt 존재는 자동 검출. 대체텍스트의 '적절성'은 수동 확인 권장."

  - no: 7
    code: "1.4.3"
    category: accessibility
    siClassification: manual
    automation: auto
    title: "텍스트 콘텐츠의 명도 대비"
    criteria: "일반 텍스트 4.5:1 이상, 큰 텍스트 3:1 이상"
    resolver: { type: axe, rules: [color-contrast] }
    guide:
      what: "텍스트와 배경색의 명암 대비를 검사합니다."
      why: "저시력·색각 이상 사용자가 글을 읽으려면 충분한 대비가 필요합니다."
      basis: "KWCAG 2.2 1.4.3 · 장애인차별금지법 · KS X OT0003:2022"
      fix: "명도비 4.5:1 이상(큰 텍스트 3:1). 예) #767676 on #ffffff = 4.54:1 ✓"
      limit: "자동 검출. 배경 이미지·그라디언트 위 텍스트는 수동 확인 권장."

  - no: 33
    code: "c-reflow"
    category: compatibility
    siClassification: manual
    automation: screen
    title: "해상도·확대 대응"
    criteria: "1920×1080, 1366×768, 확대 200%에서 UI 깨짐 없이 사용 가능"
    resolver: { type: probe, rules: [reflow-zoom] }
    guide:
      what: "여러 해상도와 200% 확대에서 가로 스크롤·잘림이 없는지 검사합니다."
      why: "저시력 사용자는 화면을 확대해 사용하며, 가로 스크롤이 생기면 읽기 어렵습니다."
      basis: "KWCAG 2.2 대응 · 전자정부 웹사이트 품질관리 가이드 · SIR-004"
      fix: "반응형 레이아웃·상대 단위(%, rem) 사용. 고정 px 폭과 가로 오버플로 제거."
      limit: "가로 오버플로·잘림은 자동 검출. 콘텐츠 가독성 저하는 수동 확인 권장."

  # 나머지 34항목: 스펙 §5 매핑 표(구조 필드)와 위 3항목의 상세도(guide)를 따라 작성.
  # 접근성: 1.2.1 1.3.1 1.3.2 1.3.3 1.4.1 1.4.2 1.4.4 2.1.1 2.1.2 2.1.3 2.1.4
  #        2.2.1 2.2.2 2.3.1 2.4.1 2.4.2 2.4.3 2.4.4 2.5.1 2.5.2 2.5.3 2.5.4
  #        3.1.1 3.2.1 3.2.2 3.3.1 3.3.2 3.3.3 3.3.4 4.1.1 4.2.1
  # 호환성: c-webstandard c-deprecated c-browser
  # automation·resolver·siClassification 은 스펙 §5 표의 값을 그대로 사용.
  # 2.4.4 는 automation: na, resolver: { type: none }.
```

> 구현 시 위 주석의 코드 목록을 스펙 §5 표와 대조해 34항목을 모두 채운다.
> 검증 게이트: KwcagCatalogTest 의 `총_37항목`·`버킷 6/27/2/2`·`모든 항목 guide/resolver` 가 통과해야 완료.

- [ ] **Step 3b: Write the loader**

```java
// KwcagCatalog.java
package com.kt.wqg.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** KWCAG 2.2 검수 카탈로그 로더. 클래스패스 리소스에서 37항목을 읽는다. */
public final class KwcagCatalog {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final List<CatalogItem> items;

    private KwcagCatalog(List<CatalogItem> items) { this.items = items; }

    public static KwcagCatalog load() {
        try (InputStream in = KwcagCatalog.class.getResourceAsStream("/kwcag22-catalog.yaml")) {
            if (in == null) throw new IllegalStateException("kwcag22-catalog.yaml 리소스 없음");
            Wrapper w = YAML.readValue(in, Wrapper.class);
            return new KwcagCatalog(w.items());
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("카탈로그 로드 실패", e);
        }
    }

    public List<CatalogItem> items() { return items; }

    public CatalogItem byCode(String code) {
        return items.stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("항목 코드 없음: " + code));
    }

    public List<CatalogItem> byBucket(String category, boolean siAuto) {
        return items.stream()
                .filter(c -> c.category().equals(category) && c.siAuto() == siAuto)
                .toList();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Wrapper(List<CatalogItem> items) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=KwcagCatalogTest`
Expected: PASS (4 tests). 실패 시 카탈로그 항목 수·버킷 분류·guide 누락을 수정.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/kwcag22-catalog.yaml src/main/java/com/kt/wqg/compliance/KwcagCatalog.java src/test/java/com/kt/wqg/compliance/KwcagCatalogTest.java
git commit -m "feat: KWCAG 2.2 37항목 카탈로그 리소스 + 로더

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: 준수율·종합판정 평가엔진

**Files:**
- Create: `src/main/java/com/kt/wqg/compliance/ComplianceEvaluator.java`
- Test: `src/test/java/com/kt/wqg/compliance/ComplianceEvaluatorTest.java`

**Interfaces:**
- Consumes: `KwcagCatalog`(Task 3), `CatalogItem`(Task 2), `ItemResult`/`ItemVerdict`(Task 1)
- Produces:
  - `record CategoryScore(String label, String category, boolean siAuto, int total, int 적합, int 부적합, int naCount, int pending, double rate, double threshold, boolean pass)`
  - `record Compliance(String status, List<CategoryScore> categories, int pendingTotal)` — status: `적합|부적합|미완료`
  - `class ComplianceEvaluator`:
    - `ComplianceEvaluator(KwcagCatalog catalog)`
    - `Compliance evaluate(Map<String, ItemResult> resultsByCode)` — 카탈로그 37항목을 순회, 결과 없는 항목은 수동확인필요(pending) 취급

**계산 규칙(Global Constraints 재확인):** 준수율 = 적합/(적합+부적합)×100 (분모 0이면 100.0). N/A 제외. 임계값 접근성 95·호환성 100. 카테고리 pass = rate ≥ threshold 그리고 그 버킷 pending 0. status = 미확정(pending) 있으면 "미완료", 아니면 모든 카테고리 pass 시 "적합" 아니면 "부적합".

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceEvaluatorTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();
    private final ComplianceEvaluator eval = new ComplianceEvaluator(catalog);

    /** 모든 항목을 주어진 판정으로 채운 맵. */
    private Map<String, ItemResult> allWith(ItemVerdict v) {
        Map<String, ItemResult> m = new HashMap<>();
        catalog.items().forEach(c -> m.put(c.code(), ItemResult.of(c.code(), v)));
        return m;
    }

    private CategoryScore cat(Compliance c, String category, boolean siAuto) {
        return c.categories().stream()
                .filter(s -> s.category().equals(category) && s.siAuto() == siAuto)
                .findFirst().orElseThrow();
    }

    @Test
    void 전항목_적합이면_종합_적합_준수율100() {
        Compliance c = eval.evaluate(allWith(ItemVerdict.적합));
        assertEquals("적합", c.status());
        assertEquals(0, c.pendingTotal());
        assertEquals(100.0, cat(c, "accessibility", false).rate(), 1e-9);
        assertTrue(cat(c, "compatibility", true).pass());
    }

    @Test
    void 결과없는_항목은_미확정이라_미완료() {
        Compliance c = eval.evaluate(new HashMap<>()); // 아무 결과 없음
        assertEquals("미완료", c.status());
        assertEquals(37, c.pendingTotal());
    }

    @Test
    void 접근성수동_1건_부적합이면_준수율96_2_통과() {
        // 27항목 중 26 적합 / 1 부적합 = 96.3% ≥ 95%
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        String firstManualA11y = catalog.byBucket("accessibility", false).get(0).code();
        m.put(firstManualA11y, ItemResult.of(firstManualA11y, ItemVerdict.부적합));
        Compliance c = eval.evaluate(m);
        CategoryScore s = cat(c, "accessibility", false);
        assertEquals(26, s.적합());
        assertEquals(1, s.부적합());
        assertTrue(s.rate() >= 95.0, "준수율=" + s.rate());
        assertTrue(s.pass());
        assertEquals("부적합", c.status(), "다른 카테고리는 100 이지만 이 카테고리는 통과 → 종합은 적합? 아니오, 이 케이스는 통과이므로 적합");
    }

    @Test
    void 호환성_1건_부적합이면_100미달로_부적합() {
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        String c1 = catalog.byBucket("compatibility", true).get(0).code();
        m.put(c1, ItemResult.of(c1, ItemVerdict.부적합));
        Compliance c = eval.evaluate(m);
        assertFalse(cat(c, "compatibility", true).pass());
        assertEquals("부적합", c.status());
    }

    @Test
    void 해당없음은_준수율_분모에서_제외() {
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        // 접근성 수동 버킷의 한 항목을 해당없음 처리 → 분모 26, 적합 26 → 100%
        String code = catalog.byBucket("accessibility", false).get(0).code();
        m.put(code, ItemResult.of(code, ItemVerdict.해당없음));
        CategoryScore s = cat(eval.evaluate(m), "accessibility", false);
        assertEquals(1, s.naCount());
        assertEquals(26, s.적합());
        assertEquals(100.0, s.rate(), 1e-9);
    }
}
```

> 주의: `접근성수동_1건_부적합` 테스트의 마지막 assert 는 종합 status 를 검증한다.
> 이 케이스는 접근성수동 카테고리가 통과(96.3%≥95)하고 나머지도 100%이므로 종합은 "적합"이어야 한다.
> 아래 구현에 맞춰 그 assert 를 `assertEquals("적합", c.status())` 로 작성한다(위 문자열 메시지는 설명용 — 실제 기대값은 "적합").

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ComplianceEvaluatorTest`
Expected: FAIL — `cannot find symbol: class ComplianceEvaluator`

- [ ] **Step 3: Write minimal implementation**

```java
// ComplianceEvaluator.java
package com.kt.wqg.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 항목 판정을 4카테고리 준수율·종합판정으로 집계한다(순수 로직). */
public final class ComplianceEvaluator {

    private static final double A11Y_THRESHOLD = 95.0;
    private static final double COMPAT_THRESHOLD = 100.0;

    private final KwcagCatalog catalog;

    public ComplianceEvaluator(KwcagCatalog catalog) { this.catalog = catalog; }

    /** 카테고리별 집계. */
    public record CategoryScore(String label, String category, boolean siAuto,
                                int total, int 적합, int 부적합, int naCount, int pending,
                                double rate, double threshold, boolean pass) {}

    /** 종합 판정. status: 적합 | 부적합 | 미완료. */
    public record Compliance(String status, List<CategoryScore> categories, int pendingTotal) {}

    public Compliance evaluate(Map<String, ItemResult> resultsByCode) {
        List<CategoryScore> scores = new ArrayList<>();
        scores.add(score("웹접근성 자동", "accessibility", true, resultsByCode));
        scores.add(score("웹접근성 수동", "accessibility", false, resultsByCode));
        scores.add(score("웹호환성 자동", "compatibility", true, resultsByCode));
        scores.add(score("웹호환성 수동", "compatibility", false, resultsByCode));

        int pendingTotal = scores.stream().mapToInt(CategoryScore::pending).sum();
        String status;
        if (pendingTotal > 0) {
            status = "미완료";
        } else {
            status = scores.stream().allMatch(CategoryScore::pass) ? "적합" : "부적합";
        }
        return new Compliance(status, scores, pendingTotal);
    }

    private CategoryScore score(String label, String category, boolean siAuto,
                                Map<String, ItemResult> results) {
        int adequate = 0, inadequate = 0, na = 0, pending = 0;
        List<CatalogItem> bucket = catalog.byBucket(category, siAuto);
        for (CatalogItem item : bucket) {
            ItemResult r = results.get(item.code());
            ItemVerdict v = r == null ? ItemVerdict.수동확인필요 : r.verdict();
            switch (v) {
                case 적합 -> adequate++;
                case 부적합 -> inadequate++;
                case 해당없음 -> na++;
                case 수동확인필요 -> pending++;
            }
        }
        int denom = adequate + inadequate;
        double rate = denom == 0 ? 100.0 : Math.round(adequate * 1000.0 / denom) / 10.0;
        double threshold = "accessibility".equals(category) ? A11Y_THRESHOLD : COMPAT_THRESHOLD;
        boolean pass = pending == 0 && rate >= threshold;
        return new CategoryScore(label, category, siAuto, bucket.size(),
                adequate, inadequate, na, pending, rate, threshold, pass);
    }
}
```

- [ ] **Step 4: Adjust the test's status assertion, then run**

`ComplianceEvaluatorTest.접근성수동_1건_부적합이면_준수율96_2_통과` 의 마지막 줄을 다음으로 확정한다:

```java
        assertEquals("적합", c.status());
```

Run: `mvn -B test -Dtest=ComplianceEvaluatorTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Run full suite + commit**

Run: `mvn -B test`
Expected: 기존 46 + 신규 12 = 58 전후, 모두 PASS

```bash
git add src/main/java/com/kt/wqg/compliance/ComplianceEvaluator.java src/test/java/com/kt/wqg/compliance/ComplianceEvaluatorTest.java
git commit -m "feat: 준수율·종합판정 평가엔진 (4카테고리, 95/100, 미확정→미완료)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준 (계획 1)

- `KwcagCatalog.load()` 가 37항목(버킷 6/27/2/2)을 로드하고 모든 항목이 guide·resolver 보유
- `ComplianceEvaluator.evaluate()` 가 준수율·종합판정을 규칙대로 산출(엣지 케이스 테스트 통과)
- `mvn -B test` 전체 통과
- 브라우저·기존 검사와 결합 없음(계획 2에서 리졸버로 연결)

## 후속 계획 (별도 문서)

- 계획 2 — 리졸버 파이프라인(Axe/Markup/Probe 인터페이스) + axe 규칙→항목 코드 확장 매핑 + CheckService 가 ItemResult 산출
- 계획 3 — 커스텀 프로브 ~14종(추출/판정 분리)
- 계획 4 — AssessmentStore + 콘솔 검수 탭 + /api/assessment + 리포트 재생성
- 계획 5 — ComplianceReportWriter(SI 검수결과표 + 친절설명 HTML)
