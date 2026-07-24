# 호환성 완성 + 프로브 인프라 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 호환성 카테고리 4항목을 완성한다 — c-browser 는 기존 3엔진 diff 결과로 자동 판정, c-reflow 는 신규 reflow 프로브(다중 뷰포트·200% 확대 가로 오버플로 검출)로 스크리닝 — 하고, 이후 접근성 DOM 프로브가 얹힐 수 있는 프로브 실행 골격(PageProbe/ProbeCheck)을 만든다.

**Architecture:** (1) `RuleResolver` 를 axe 뿐 아니라 probe 타입도 rule 매칭하도록 일반화하고, `ComplianceMapper` 가 probe finding 을 finding 풀에 합친다. (2) c-browser 는 카탈로그 resolver.type 을 `compat` 으로 바꾸고 매퍼가 compatibility CheckResult 로 판정. (3) `PageProbe` 인터페이스 + `ReflowProbe`(추출 라이브 / 판정 순수) + `ProbeCheck`(로그인된 page 에서 프로브 실행 → Finding). (4) `CheckService` 가 프로브를 돌려 finding 을 매퍼에 넘김.

**Tech Stack:** Java 17, Playwright(기존), 신규 의존성 없음. `com.kt.wqg.compliance` + `com.kt.wqg.check` 에 확장.

## Global Constraints

- Java 17, plain Java(프레임워크·Node 금지), 신규 런타임 의존성 금지.
- 기존 소비: `CatalogItem`(resolver().type()/rules(), automation(), code()), `KwcagCatalog`, `ItemResult`(Evidence(url,rule,detail)), `ItemVerdict`(적합/부적합/수동확인필요/해당없음), `RuleResolver.resolve`, `ComplianceMapper`, `Finding(url,rule,help,target)`, `com.kt.wqg.report.CheckResult`(checkType "accessibility"|"markup"|"compatibility", engine, passed, diffRatio, violations).
- 판정 규칙 유지: automation "na"→해당없음, "auto"→매칭 유무로 적합/부적합, "screen"→수동확인필요(증거 첨부).
- c-browser: automation auto. 어느 엔진 비교라도 실패(passed=false)면 부적합, 전부 통과면 적합. c-reflow: automation screen → 수동확인필요(오버플로 증거 첨부).
- reflow 판정은 임계값 없이 가로 오버플로 유무(이진)로 한다.
- 테스트: `mvn -B test`. 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: RuleResolver·Mapper 를 probe 타입까지 일반화

**Files:**
- Modify: `src/main/java/com/kt/wqg/compliance/RuleResolver.java`
- Modify: `src/main/java/com/kt/wqg/compliance/ComplianceMapper.java`
- Test: `src/test/java/com/kt/wqg/compliance/RuleResolverProbeTest.java`
- Modify test: `src/test/java/com/kt/wqg/compliance/ComplianceMapperTest.java` (probe 오버로드 케이스 추가)

**Interfaces:**
- Produces:
  - `RuleResolver.resolve` 는 resolver.type ∈ {"axe","probe"} 를 rule 매칭으로 처리(기존 axe 동작 불변).
  - `ComplianceMapper.map(List<CheckResult> results, List<Finding> probeFindings)` 오버로드 추가. 기존 `map(results)` 는 `map(results, List.of())` 로 위임.

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleResolverProbeTest {
    private CatalogItem probe(String code, String automation, String... rules) {
        return new CatalogItem(0, code, "compatibility", "manual", automation, code, "",
                new CatalogItem.Resolver("probe", List.of(rules)),
                new CatalogItem.Guide("w","y","b","f","l"));
    }

    @Test
    void probe_screen_은_매칭_증거와_함께_수동확인필요() {
        List<Finding> f = List.of(new Finding("https://x/a", "reflow-zoom", "가로 오버플로 40px", null));
        ItemResult r = RuleResolver.resolve(probe("c-reflow", "screen", "reflow-zoom"), f);
        assertEquals(ItemVerdict.수동확인필요, r.verdict());
        assertEquals(1, r.evidence().size());
        assertEquals("reflow-zoom", r.evidence().get(0).rule());
    }

    @Test
    void probe_auto_매칭없으면_적합_있으면_부적합() {
        CatalogItem p = probe("x", "auto", "some-probe");
        assertEquals(ItemVerdict.적합, RuleResolver.resolve(p, List.of()).verdict());
        List<Finding> f = List.of(new Finding("u", "some-probe", "h", null));
        assertEquals(ItemVerdict.부적합, RuleResolver.resolve(p, f).verdict());
    }
}
```

`ComplianceMapperTest` 에 추가:

```java
    @Test
    void probe_finding_이_probe항목에_반영된다() {
        // c-reflow(probe, screen) 는 probeFindings 로 수동확인필요+증거
        java.util.List<Finding> pf = java.util.List.of(new Finding("https://x/a", "reflow-zoom", "가로 오버플로", null));
        Map<String, ItemResult> m = mapper.map(List.of(), pf);
        ItemResult r = m.get("c-reflow");
        assertEquals(ItemVerdict.수동확인필요, r.verdict());
        assertFalse(r.evidence().isEmpty());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=RuleResolverProbeTest`
Expected: FAIL — probe 타입이 아직 수동확인필요(증거 없음)로 처리됨 → 증거 assert 실패. (또는 map 오버로드 없음 컴파일 실패)

- [ ] **Step 3a: Generalize RuleResolver**

`RuleResolver.resolve` 의 타입 가드를 axe→{axe,probe} 로 바꾼다:

```java
        if (!"axe".equals(item.resolver().type()) && !"probe".equals(item.resolver().type())) {
            return new ItemResult(item.code(), ItemVerdict.수동확인필요, "auto", List.of(), null);
        }
```
(나머지 매칭·automation 분기는 그대로.)

- [ ] **Step 3b: Add mapper overload consuming probe findings**

`ComplianceMapper` 에서:

```java
    public Map<String, ItemResult> map(List<CheckResult> results) {
        return map(results, List.of());
    }

    public Map<String, ItemResult> map(List<CheckResult> results, List<Finding> probeFindings) {
        List<Finding> axeFindings = results.stream()
                .filter(r -> "accessibility".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), v.id(), v.help(), firstTarget(v))))
                .toList();
        List<Finding> pool = new java.util.ArrayList<>(axeFindings);
        pool.addAll(probeFindings);
        List<Finding> markupErrors = results.stream()
                .filter(r -> "markup".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), "markup", v.help(), null)))
                .toList();

        Map<String, ItemResult> out = new LinkedHashMap<>();
        for (CatalogItem item : catalog.items()) {
            ItemResult res = "markup".equals(item.resolver().type())
                    ? resolveMarkup(item, markupErrors)
                    : RuleResolver.resolve(item, pool);
            out.put(item.code(), res);
        }
        return out;
    }
```
(기존 단일-인자 map 본문을 위 2-인자 버전으로 이동, 단일 인자는 위임.)

- [ ] **Step 4: Run tests**

Run: `mvn -B test -Dtest=RuleResolverProbeTest,ComplianceMapperTest,RuleResolverTest`
Expected: PASS (기존 케이스 불변 + 신규 probe 케이스).

- [ ] **Step 5: Full suite + commit**

Run: `mvn -B test` → 전체 PASS
```bash
git add src/main/java/com/kt/wqg/compliance/RuleResolver.java src/main/java/com/kt/wqg/compliance/ComplianceMapper.java src/test/java/com/kt/wqg/compliance/RuleResolverProbeTest.java src/test/java/com/kt/wqg/compliance/ComplianceMapperTest.java
git commit -m "feat: RuleResolver·Mapper 를 probe 타입 rule 매칭으로 일반화

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: c-browser 를 호환성 결과로 자동 판정

**Files:**
- Modify: `src/main/resources/kwcag22-catalog.yaml` (c-browser resolver.type probe→compat)
- Modify: `src/main/java/com/kt/wqg/compliance/ComplianceMapper.java` (compat 처리)
- Modify test: `src/test/java/com/kt/wqg/compliance/ComplianceMapperTest.java`

**Interfaces:**
- Produces: 매퍼가 resolver.type "compat" 항목(c-browser)을 compatibility CheckResult 로 판정. 어느 엔진 비교라도 passed=false → 부적합(증거: engine·url·diffRatio), 전부 통과 → 적합.

- [ ] **Step 1: Write the failing test (ComplianceMapperTest 에 추가)**

```java
    private CheckResult compat(String url, String engine, boolean passed, double ratio) {
        return new CheckResult("s", url, "compatibility", "t", engine, passed,
                List.of(), null, ratio);
    }

    @Test
    void 호환성_실패가_있으면_c_browser_부적합() {
        Map<String, ItemResult> m = mapper.map(List.of(
                compat("https://x/a", "firefox", false, 0.08)));
        assertEquals(ItemVerdict.부적합, m.get("c-browser").verdict());
        assertFalse(m.get("c-browser").evidence().isEmpty());
    }

    @Test
    void 호환성_전부통과면_c_browser_적합() {
        Map<String, ItemResult> m = mapper.map(List.of(
                compat("https://x/a", "firefox", true, 0.0),
                compat("https://x/a", "webkit", true, 0.0)));
        assertEquals(ItemVerdict.적합, m.get("c-browser").verdict());
    }

    @Test
    void 호환성_결과없으면_c_browser_적합_처리() {
        // 호환성 미실행 시 위반 근거 없음 → 적합(자동)
        assertEquals(ItemVerdict.적합, mapper.map(List.of()).get("c-browser").verdict());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ComplianceMapperTest`
Expected: FAIL — c-browser 가 아직 probe(수동확인필요)로 나옴.

- [ ] **Step 3a: Catalog — c-browser resolver.type 을 compat 으로**

`src/main/resources/kwcag22-catalog.yaml` 에서 code "c-browser" 항목의 `resolver` 를 다음으로 바꾼다(automation auto 유지):
```yaml
    resolver: { type: compat, rules: [] }
```

- [ ] **Step 3b: Mapper — compat 처리 추가**

`map(results, probeFindings)` 의 항목 루프에서 분기 추가:
```java
            ItemResult res;
            if ("markup".equals(item.resolver().type())) {
                res = resolveMarkup(item, markupErrors);
            } else if ("compat".equals(item.resolver().type())) {
                res = resolveCompat(item, results);
            } else {
                res = RuleResolver.resolve(item, pool);
            }
            out.put(item.code(), res);
```
그리고 메서드 추가:
```java
    private static ItemResult resolveCompat(CatalogItem item, List<CheckResult> results) {
        List<ItemResult.Evidence> failed = results.stream()
                .filter(r -> "compatibility".equals(r.checkType()) && !r.passed())
                .map(r -> new ItemResult.Evidence(r.url(), "cross-browser-diff",
                        r.engine() + " 렌더링 차이" + (r.diffRatio() == null ? "" :
                                String.format(" %.2f%%", r.diffRatio() * 100))))
                .toList();
        ItemVerdict v = failed.isEmpty() ? ItemVerdict.적합 : ItemVerdict.부적합;
        return new ItemResult(item.code(), v, "auto", failed, null);
    }
```

- [ ] **Step 4: Run tests**

Run: `mvn -B test -Dtest=ComplianceMapperTest`
Expected: PASS. (c-browser 3케이스 + 기존 케이스)

- [ ] **Step 5: Full suite + commit**

Run: `mvn -B test` → 전체 PASS
```bash
git add src/main/resources/kwcag22-catalog.yaml src/main/java/com/kt/wqg/compliance/ComplianceMapper.java src/test/java/com/kt/wqg/compliance/ComplianceMapperTest.java
git commit -m "feat: c-browser 를 3엔진 호환성 결과로 자동 판정(resolver type compat)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: PageProbe 인프라 + ReflowProbe

**Files:**
- Create: `src/main/java/com/kt/wqg/check/probe/PageProbe.java`
- Create: `src/main/java/com/kt/wqg/check/probe/ReflowProbe.java`
- Test: `src/test/java/com/kt/wqg/check/probe/ReflowProbeTest.java`

**Interfaces:**
- Produces:
  - `interface PageProbe { String id(); List<Finding> run(Page page, String url); }`
  - `class ReflowProbe implements PageProbe` — id "reflow-zoom". 뷰포트 3종(1920×1080, 1366×768, 200% 확대)에서 가로 오버플로 검출.
  - static `List<Finding> ReflowProbe.evaluate(String url, List<ReflowProbe.Viewport> measured)` — 순수 판정: 오버플로 있는 뷰포트마다 Finding.
  - `record ReflowProbe.Viewport(String label, int scrollWidth, int innerWidth)`

- [ ] **Step 1: Write the failing test (순수 판정부)**

```java
package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReflowProbeTest {
    @Test
    void 가로_오버플로가_있는_뷰포트마다_finding() {
        List<Finding> f = ReflowProbe.evaluate("https://x/a", List.of(
                new ReflowProbe.Viewport("1920x1080", 1920, 1920),   // 오버플로 없음
                new ReflowProbe.Viewport("1366x768", 1500, 1366),    // 134px 초과
                new ReflowProbe.Viewport("확대200%", 2000, 960)));    // 초과
        assertEquals(2, f.size());
        assertTrue(f.get(0).rule().equals("reflow-zoom"));
        assertTrue(f.get(0).help().contains("1366x768"));
        assertTrue(f.get(0).help().contains("134"));
        assertEquals("https://x/a", f.get(0).url());
    }

    @Test
    void 오버플로_없으면_빈목록() {
        assertEquals(List.of(), ReflowProbe.evaluate("u", List.of(
                new ReflowProbe.Viewport("a", 1000, 1000),
                new ReflowProbe.Viewport("b", 900, 1000))));
    }

    @Test
    void id는_reflow_zoom() {
        assertEquals("reflow-zoom", new ReflowProbe().id());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ReflowProbeTest`
Expected: FAIL — `cannot find symbol: class PageProbe / ReflowProbe`

- [ ] **Step 3a: PageProbe 인터페이스**

```java
// PageProbe.java
package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;
import java.util.List;

/** 로그인된 page 에서 특정 검사항목의 신호를 추출하는 커스텀 프로브. */
public interface PageProbe {
    String id();
    List<Finding> run(Page page, String url);
}
```

- [ ] **Step 3b: ReflowProbe (추출 라이브 + 판정 순수)**

```java
// ReflowProbe.java
package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 해상도·확대 대응(c-reflow) 프로브. 1920×1080·1366×768·200% 확대에서
 * 가로 오버플로(문서 폭 > 뷰포트 폭)를 검출한다. 임계값 없이 이진 판정.
 */
public final class ReflowProbe implements PageProbe {

    /** 한 뷰포트의 측정값. scrollWidth > innerWidth 면 가로 오버플로. */
    public record Viewport(String label, int scrollWidth, int innerWidth) {}

    @Override public String id() { return "reflow-zoom"; }

    @Override
    public List<Finding> run(Page page, String url) {
        List<Viewport> measured = new ArrayList<>();
        measured.add(measure(page, url, "1920x1080", 1920, 1080, false));
        measured.add(measure(page, url, "1366x768", 1366, 768, false));
        measured.add(measure(page, url, "확대200%", 1366, 768, true));
        return evaluate(url, measured);
    }

    private Viewport measure(Page page, String url, String label, int w, int h, boolean zoom) {
        page.setViewportSize(w, h);
        if (zoom) {
            page.evaluate("() => { document.documentElement.style.zoom = '2'; }");
        } else {
            page.evaluate("() => { document.documentElement.style.zoom = ''; }");
        }
        Object sw = page.evaluate("() => document.documentElement.scrollWidth");
        Object iw = page.evaluate("() => window.innerWidth");
        page.evaluate("() => { document.documentElement.style.zoom = ''; }");
        return new Viewport(label, ((Number) sw).intValue(), ((Number) iw).intValue());
    }

    /** 순수 판정: 가로 오버플로가 있는 뷰포트마다 Finding 을 만든다. */
    public static List<Finding> evaluate(String url, List<Viewport> measured) {
        List<Finding> out = new ArrayList<>();
        for (Viewport v : measured) {
            int over = v.scrollWidth() - v.innerWidth();
            if (over > 0) {
                out.add(new Finding(url, "reflow-zoom",
                        v.label() + "에서 가로 오버플로 " + over + "px", null));
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=ReflowProbeTest`
Expected: PASS (3 tests). 라이브 `run` 은 실검증에서 확인.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/kt/wqg/check/probe/PageProbe.java src/main/java/com/kt/wqg/check/probe/ReflowProbe.java src/test/java/com/kt/wqg/check/probe/ReflowProbeTest.java
git commit -m "feat: PageProbe 인프라 + ReflowProbe(해상도·200% 가로 오버플로 검출)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: ProbeCheck 실행 + CheckService 연결 + 실검증

**Files:**
- Create: `src/main/java/com/kt/wqg/check/probe/ProbeCheck.java`
- Modify: `src/main/java/com/kt/wqg/check/CheckService.java`
- Test: `src/test/java/com/kt/wqg/check/probe/ProbeCheckTest.java`

**Interfaces:**
- Produces:
  - `class ProbeCheck` static `List<PageProbe> defaults()` → `List.of(new ReflowProbe())`
  - static `List<Finding> run(Page page, String url, List<PageProbe> probes)` — 각 프로브 실행, 예외는 격리(한 프로브 실패가 전체를 막지 않음), finding 누적.

- [ ] **Step 1: Write the failing test (프로브 격리·누적)**

```java
package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProbeCheckTest {
    /** page 를 쓰지 않는 가짜 프로브. */
    private PageProbe fake(String id, Finding... fs) {
        return new PageProbe() {
            public String id() { return id; }
            public List<Finding> run(Page page, String url) { return List.of(fs); }
        };
    }
    private PageProbe boom(String id) {
        return new PageProbe() {
            public String id() { return id; }
            public List<Finding> run(Page page, String url) { throw new RuntimeException("프로브 오류"); }
        };
    }

    @Test
    void 여러_프로브_finding을_누적한다() {
        List<Finding> out = ProbeCheck.run(null, "u",
                List.of(fake("a", new Finding("u", "a", "h", null)),
                        fake("b", new Finding("u", "b", "h", null))));
        assertEquals(2, out.size());
    }

    @Test
    void 한_프로브가_실패해도_나머지는_수집된다() {
        List<Finding> out = ProbeCheck.run(null, "u",
                List.of(boom("x"), fake("b", new Finding("u", "b", "h", null))));
        assertEquals(1, out.size());
        assertEquals("b", out.get(0).rule());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ProbeCheckTest`
Expected: FAIL — `cannot find symbol: class ProbeCheck`

- [ ] **Step 3a: ProbeCheck**

```java
// ProbeCheck.java
package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/** 로그인된 page 에서 등록된 프로브들을 실행해 Finding 을 모은다(프로브 단위 실패 격리). */
public final class ProbeCheck {

    private ProbeCheck() {}

    public static List<PageProbe> defaults() {
        return List.of(new ReflowProbe());
    }

    public static List<Finding> run(Page page, String url, List<PageProbe> probes) {
        List<Finding> all = new ArrayList<>();
        for (PageProbe probe : probes) {
            try {
                all.addAll(probe.run(page, url));
            } catch (RuntimeException e) {
                // 한 프로브 실패가 나머지·전체 검사를 막지 않는다
            }
        }
        return all;
    }
}
```

- [ ] **Step 3b: CheckService 연결**

`runSite(...)` 의 URL 순회 루프(접근성·마크업 다음)에 프로브 실행을 추가한다. 루프 상단에서 프로브 finding 을 모을 리스트를 선언하고(브라우저 블록 안, results 근처):
```java
            List<com.kt.wqg.compliance.Finding> probeFindings = new ArrayList<>();
```
URL 루프 안, 마크업 검사 뒤에:
```java
                    probeFindings.addAll(com.kt.wqg.check.probe.ProbeCheck.run(
                            page, CheckRunner.resolveUrl(site.baseUrl(), url.path()),
                            com.kt.wqg.check.probe.ProbeCheck.defaults()));
```
그리고 컴플라이언스 산출 호출을 2-인자 map 으로 바꾼다:
```java
                            new com.kt.wqg.compliance.ComplianceMapper(catalog).map(results, probeFindings));
```

> 주의: 프로브는 뷰포트를 바꾸므로 반드시 접근성·마크업·(호환성 캡처) 이후 순서. URL 루프 안에서 접근성/마크업 다음에 두면 됨. 프로브가 뷰포트를 바꿔도 다음 URL navigate 에서 재설정되므로 문제 없음.

- [ ] **Step 4: Build + full suite**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, 전체 PASS.

- [ ] **Step 5: 실사이트 실검증 (defect-kwater) — 컨트롤러 수행**

```bash
SP=<scratchpad>
sed "s#/opt/wqg/vnu.jar#$SP/vnu.jar#" config/sites.yaml > "$SP/sites.local.yaml"
mvn -B -q clean package -DskipTests
set -a; . ./.env; set +a
java -jar target/web-quality-gate-0.1.0-SNAPSHOT.jar "$SP/sites.local.yaml" defect-kwater 2>&1 | grep -E '컴플라이언스|검사 완료'
```
Expected: `컴플라이언스: 미완료 (미확정 N건)` — 이전 22보다 감소(c-browser 자동 판정 + c-reflow 는 여전히 screen 이라 수동확인필요지만 증거 확보). 예외 없이 완주.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/kt/wqg/check/probe/ProbeCheck.java src/main/java/com/kt/wqg/check/CheckService.java src/test/java/com/kt/wqg/check/probe/ProbeCheckTest.java
git commit -m "feat: ProbeCheck 실행 + CheckService 연결(reflow 프로브 파이프라인)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준 (계획 3)

- c-browser 가 3엔진 호환성 결과로 자동 적합/부적합
- c-reflow 가 reflow 프로브(가로 오버플로)로 스크리닝 → 수동확인필요+증거
- PageProbe/ProbeCheck 인프라로 프로브를 page 에서 실행, 프로브 단위 실패 격리
- `mvn -B test` 전체 통과 + defect-kwater 실검증 완주(미확정 감소)

## 후속 계획

- 계획 3b — 접근성 DOM 프로브(media-captions 1.2.1, new-window 3.2.1, meta-refresh 2.2.1, blink-flash 2.3.1) 를 인프라에 추가
- 계획 4 — AssessmentStore + 콘솔 검수 탭 + /api/assessment + 리포트 재생성
- 계획 5 — ComplianceReportWriter(SI 검수결과표 + 친절설명 HTML) + STANDARD 문자열 KWCAG 2.2 로 정정
