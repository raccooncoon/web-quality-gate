# 리졸버 파이프라인 (axe + markup) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 검사가 만드는 `List<CheckResult>`(axe·vnu·compat)를 순수 변환으로 카탈로그 37항목의 `Map<code, ItemResult>`에 매핑해, axe·markup 리졸버가 커버하는 항목은 실제 적합/부적합을 판정하고 나머지는 수동확인필요로 남긴 뒤, `ComplianceEvaluator`로 준수율·종합판정을 산출한다.

**Architecture:** `RuleResolver`(axe rule 매칭 + automation별 판정, 순수) + `ComplianceMapper`(CheckResult→Finding 변환, axe는 RuleResolver·markup은 전용 로직·나머지는 수동확인필요, 순수) → `CheckService`가 자신이 이미 모은 결과에 이를 적용해 `Compliance`를 산출(통합 + defect-kwater 실검증). 브라우저 신규 코드 없음.

**Tech Stack:** Java 17, 기존 의존성만(JUnit 5). 계획 1의 `com.kt.wqg.compliance` 패키지에 이어 붙인다.

## Global Constraints

- Java 17, plain Java(프레임워크·Node 금지), 신규 런타임 의존성 금지.
- 계획 1 산출물을 소비: `CatalogItem`(resolver().type()/rules(), automation()), `KwcagCatalog`(items()/byCode()), `ItemResult`(of(), Evidence(url,rule,detail)), `ItemVerdict`(적합/부적합/수동확인필요/해당없음), `ComplianceEvaluator`.
- 기존 `com.kt.wqg.report.CheckResult` 구조를 소비: `CheckResult(site,url,checkType,timestamp,engine,passed,List<Violation> violations,Map screenshots,Double diffRatio)`, `Violation(id,impact,help,nodes,kwcag,List<Element> elements)`, `Element(target,html)`. checkType 값: "accessibility" | "markup" | "compatibility".
- 판정 규칙:
  - automation "na" → 해당없음.
  - resolver.type "axe": 해당 항목 rules 에 매칭되는 axe finding 이 있으면(automation auto) 부적합, 없으면 적합. automation "screen"이면 무조건 수동확인필요(스크리닝 증거 첨부).
  - resolver.type "markup": 4.1.1·c-webstandard 는 모든 마크업 오류 대상, c-deprecated 는 obsolete/deprecated 문구 오류 대상. automation auto 면 오류 유무로 적합/부적합, screen(c-webstandard)이면 수동확인필요.
  - resolver.type "probe"/"none"(및 그 외) → 수동확인필요(계획 3에서 처리).
- 테스트: `mvn -B test`. 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: RuleResolver — axe rule 매칭 + automation별 판정

**Files:**
- Create: `src/main/java/com/kt/wqg/compliance/Finding.java`
- Create: `src/main/java/com/kt/wqg/compliance/RuleResolver.java`
- Test: `src/test/java/com/kt/wqg/compliance/RuleResolverTest.java`

**Interfaces:**
- Consumes: `CatalogItem`, `ItemResult`, `ItemVerdict`(계획 1)
- Produces:
  - `record Finding(String url, String rule, String help, String target)`
  - `class RuleResolver` static `ItemResult resolve(CatalogItem item, List<Finding> findings)`

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleResolverTest {

    private CatalogItem axe(String code, String automation, String... rules) {
        return new CatalogItem(0, code, "accessibility", "manual", automation, code, "",
                new CatalogItem.Resolver("axe", List.of(rules)),
                new CatalogItem.Guide("w", "y", "b", "f", "l"));
    }

    @Test
    void auto_axe_매칭없으면_적합() {
        ItemResult r = RuleResolver.resolve(axe("1.4.3", "auto", "color-contrast"), List.of());
        assertEquals(ItemVerdict.적합, r.verdict());
        assertEquals("auto", r.source());
    }

    @Test
    void auto_axe_매칭있으면_부적합_증거포함() {
        List<Finding> f = List.of(new Finding("https://x/a", "color-contrast", "명도비 부족", "span.t"));
        ItemResult r = RuleResolver.resolve(axe("1.4.3", "auto", "color-contrast"), f);
        assertEquals(ItemVerdict.부적합, r.verdict());
        assertEquals(1, r.evidence().size());
        assertEquals("color-contrast", r.evidence().get(0).rule());
        assertEquals("https://x/a", r.evidence().get(0).url());
    }

    @Test
    void 다른_rule_은_매칭되지_않는다() {
        List<Finding> f = List.of(new Finding("u", "image-alt", "h", null));
        assertEquals(ItemVerdict.적합, RuleResolver.resolve(axe("1.4.3", "auto", "color-contrast"), f).verdict());
    }

    @Test
    void screen_axe_는_매칭유무와_무관하게_수동확인필요() {
        assertEquals(ItemVerdict.수동확인필요,
                RuleResolver.resolve(axe("3.3.4", "screen", "autocomplete-valid"), List.of()).verdict());
        List<Finding> f = List.of(new Finding("u", "autocomplete-valid", "h", null));
        ItemResult r = RuleResolver.resolve(axe("3.3.4", "screen", "autocomplete-valid"), f);
        assertEquals(ItemVerdict.수동확인필요, r.verdict());
        assertEquals(1, r.evidence().size(), "스크리닝 증거 첨부");
    }

    @Test
    void na_는_해당없음() {
        CatalogItem na = new CatalogItem(0, "2.4.4", "accessibility", "manual", "na", "t", "",
                new CatalogItem.Resolver("none", List.of()), new CatalogItem.Guide("w","y","b","f","l"));
        assertEquals(ItemVerdict.해당없음, RuleResolver.resolve(na, List.of()).verdict());
    }

    @Test
    void probe_나_none_타입은_수동확인필요() {
        CatalogItem probe = new CatalogItem(0, "1.2.1", "accessibility", "manual", "screen", "t", "",
                new CatalogItem.Resolver("probe", List.of("media-captions")), new CatalogItem.Guide("w","y","b","f","l"));
        assertEquals(ItemVerdict.수동확인필요, RuleResolver.resolve(probe, List.of()).verdict());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=RuleResolverTest`
Expected: FAIL — `cannot find symbol: class Finding / RuleResolver`

- [ ] **Step 3: Write minimal implementation**

```java
// Finding.java
package com.kt.wqg.compliance;

/** 한 URL 에서 검출된 검사 신호(axe rule 위반 등) 한 건. */
public record Finding(String url, String rule, String help, String target) {}
```

```java
// RuleResolver.java
package com.kt.wqg.compliance;

import java.util.List;

/**
 * 카탈로그 항목 하나를 finding 목록으로 판정한다(순수).
 * axe 타입만 rule 매칭으로 판정하며, markup/probe/none 은 상위(ComplianceMapper)나
 * 이후 계획에서 처리하도록 수동확인필요로 둔다.
 */
public final class RuleResolver {

    private RuleResolver() {}

    public static ItemResult resolve(CatalogItem item, List<Finding> findings) {
        if ("na".equals(item.automation())) {
            return new ItemResult(item.code(), ItemVerdict.해당없음, "auto", List.of(), null);
        }
        if (!"axe".equals(item.resolver().type())) {
            return new ItemResult(item.code(), ItemVerdict.수동확인필요, "auto", List.of(), null);
        }
        List<String> rules = item.resolver().rules();
        List<ItemResult.Evidence> matched = findings.stream()
                .filter(f -> rules.contains(f.rule()))
                .map(f -> new ItemResult.Evidence(f.url(), f.rule(), f.help()))
                .toList();
        ItemVerdict verdict;
        if ("screen".equals(item.automation())) {
            verdict = ItemVerdict.수동확인필요;
        } else {
            verdict = matched.isEmpty() ? ItemVerdict.적합 : ItemVerdict.부적합;
        }
        return new ItemResult(item.code(), verdict, "auto", matched, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=RuleResolverTest`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/kt/wqg/compliance/Finding.java src/main/java/com/kt/wqg/compliance/RuleResolver.java src/test/java/com/kt/wqg/compliance/RuleResolverTest.java
git commit -m "feat: RuleResolver — axe rule 매칭 + automation별 항목 판정

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ComplianceMapper — CheckResult → Map<code, ItemResult>

**Files:**
- Create: `src/main/java/com/kt/wqg/compliance/ComplianceMapper.java`
- Test: `src/test/java/com/kt/wqg/compliance/ComplianceMapperTest.java`

**Interfaces:**
- Consumes: `KwcagCatalog`, `CatalogItem`, `RuleResolver`, `Finding`, `ItemResult`, `ItemVerdict`, `com.kt.wqg.report.CheckResult`
- Produces:
  - `class ComplianceMapper` with:
    - `ComplianceMapper(KwcagCatalog catalog)`
    - `Map<String, ItemResult> map(List<CheckResult> results)` — 37항목 전체를 채운 맵

**동작:** accessibility CheckResult 의 각 violation → axe `Finding(url, id, help, 첫 element.target)`. markup CheckResult 의 각 violation → 마크업 오류(url, help). 각 카탈로그 항목:
- resolver.type == "markup": 전용 처리 — c-deprecated 는 help 에 "obsolete" 또는 "deprecated"(대소문자 무시) 포함한 오류만, 그 외(4.1.1, c-webstandard)는 모든 마크업 오류. automation auto 면 오류 유무로 적합/부적합(증거 첨부), screen 이면 수동확인필요(증거 첨부).
- 그 외: `RuleResolver.resolve(item, axeFindings)`.

- [ ] **Step 1: Write the failing test**

```java
package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceMapperTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();
    private final ComplianceMapper mapper = new ComplianceMapper(catalog);

    private CheckResult a11y(String url, CheckResult.Violation... vs) {
        return new CheckResult("s", url, "accessibility", "t", null, vs.length == 0, List.of(vs));
    }
    private CheckResult markup(String url, String... helps) {
        List<CheckResult.Violation> vs = java.util.Arrays.stream(helps)
                .map(h -> new CheckResult.Violation("markup-error", "error", h, 1)).toList();
        return new CheckResult("s", url, "markup", "t", null, vs.isEmpty(), vs);
    }
    private CheckResult.Violation v(String id) {
        return new CheckResult.Violation(id, "serious", id + " 위반", 1);
    }

    @Test
    void 결과맵은_37항목을_모두_채운다() {
        assertEquals(37, mapper.map(List.of()).size());
    }

    @Test
    void axe_color_contrast_위반이면_1_4_3_부적합() {
        Map<String, ItemResult> m = mapper.map(List.of(a11y("https://x/a", v("color-contrast"))));
        assertEquals(ItemVerdict.부적합, m.get("1.4.3").verdict());
        assertFalse(m.get("1.4.3").evidence().isEmpty());
    }

    @Test
    void axe_위반없는_auto항목은_적합() {
        Map<String, ItemResult> m = mapper.map(List.of(a11y("https://x/a")));
        assertEquals(ItemVerdict.적합, m.get("1.4.3").verdict());
        assertEquals(ItemVerdict.적합, m.get("1.1.1").verdict());
    }

    @Test
    void 마크업_오류있으면_4_1_1_부적합_c_webstandard는_수동확인() {
        Map<String, ItemResult> m = mapper.map(List.of(markup("https://x/a", "Element X is not allowed")));
        assertEquals(ItemVerdict.부적합, m.get("4.1.1").verdict()); // auto
        assertEquals(ItemVerdict.수동확인필요, m.get("c-webstandard").verdict()); // screen
    }

    @Test
    void c_deprecated는_obsolete_문구만_잡는다() {
        Map<String, ItemResult> only = mapper.map(List.of(markup("u", "The center element is obsolete")));
        assertEquals(ItemVerdict.부적합, only.get("c-deprecated").verdict());
        Map<String, ItemResult> none = mapper.map(List.of(markup("u", "Stray end tag")));
        assertEquals(ItemVerdict.적합, none.get("c-deprecated").verdict());
    }

    @Test
    void na_항목은_해당없음_probe항목은_수동확인필요() {
        Map<String, ItemResult> m = mapper.map(List.of());
        assertEquals(ItemVerdict.해당없음, m.get("2.4.4").verdict());
        assertEquals(ItemVerdict.수동확인필요, m.get("c-browser").verdict()); // probe → 계획 3
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=ComplianceMapperTest`
Expected: FAIL — `cannot find symbol: class ComplianceMapper`

- [ ] **Step 3: Write minimal implementation**

```java
// ComplianceMapper.java
package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기존 검사 결과(List<CheckResult>)를 카탈로그 37항목의 판정 맵으로 변환한다(순수).
 * axe 항목은 RuleResolver, markup 항목은 전용 로직, 그 외(probe/none)는 수동확인필요.
 */
public final class ComplianceMapper {

    private final KwcagCatalog catalog;

    public ComplianceMapper(KwcagCatalog catalog) { this.catalog = catalog; }

    public Map<String, ItemResult> map(List<CheckResult> results) {
        List<Finding> axeFindings = results.stream()
                .filter(r -> "accessibility".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), v.id(), v.help(), firstTarget(v))))
                .toList();
        List<Finding> markupErrors = results.stream()
                .filter(r -> "markup".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), "markup", v.help(), null)))
                .toList();

        Map<String, ItemResult> out = new LinkedHashMap<>();
        for (CatalogItem item : catalog.items()) {
            ItemResult res = "markup".equals(item.resolver().type())
                    ? resolveMarkup(item, markupErrors)
                    : RuleResolver.resolve(item, axeFindings);
            out.put(item.code(), res);
        }
        return out;
    }

    private static ItemResult resolveMarkup(CatalogItem item, List<Finding> markupErrors) {
        List<ItemResult.Evidence> matched = markupErrors.stream()
                .filter(f -> !"c-deprecated".equals(item.code()) || isObsolete(f.help()))
                .map(f -> new ItemResult.Evidence(f.url(), "markup", f.help()))
                .toList();
        ItemVerdict verdict;
        if ("screen".equals(item.automation())) {
            verdict = ItemVerdict.수동확인필요;
        } else {
            verdict = matched.isEmpty() ? ItemVerdict.적합 : ItemVerdict.부적합;
        }
        return new ItemResult(item.code(), verdict, "auto", matched, null);
    }

    private static boolean isObsolete(String help) {
        String h = help == null ? "" : help.toLowerCase(Locale.ROOT);
        return h.contains("obsolete") || h.contains("deprecated");
    }

    private static String firstTarget(CheckResult.Violation v) {
        return v.elements() == null || v.elements().isEmpty() ? null : v.elements().get(0).target();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=ComplianceMapperTest`
Expected: PASS (6 tests). 실패 시 카탈로그의 해당 코드 resolver.type/automation 을 확인해 매핑 로직을 맞춘다.

- [ ] **Step 5: Run full suite + commit**

Run: `mvn -B test`
Expected: 전체 PASS

```bash
git add src/main/java/com/kt/wqg/compliance/ComplianceMapper.java src/test/java/com/kt/wqg/compliance/ComplianceMapperTest.java
git commit -m "feat: ComplianceMapper — CheckResult 를 37항목 판정 맵으로 변환

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: CheckService 통합 + defect-kwater 실검증

**Files:**
- Modify: `src/main/java/com/kt/wqg/check/CheckService.java` (결과 집계 직후 컴플라이언스 산출·로그)
- Test: `src/test/java/com/kt/wqg/compliance/CompliancePipelineTest.java` (매퍼+평가엔진 결합 순수 테스트)

**Interfaces:**
- Consumes: `ComplianceMapper`, `ComplianceEvaluator`(계획 1), `CheckService`의 기존 `List<CheckResult> results`
- Produces: `CheckService` 내부에서 `ComplianceEvaluator.Compliance` 를 산출해 진행 로그로 요약 출력(정식 리포트 반영은 계획 5). 공개 API 변경 없음.

- [ ] **Step 1: Write the failing test (매퍼→평가엔진 결합)**

```java
package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CompliancePipelineTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();
    private final ComplianceMapper mapper = new ComplianceMapper(catalog);
    private final ComplianceEvaluator evaluator = new ComplianceEvaluator(catalog);

    @Test
    void axe위반이_있으면_해당항목_부적합이_준수율에_반영되고_미확정으로_미완료() {
        // color-contrast 위반 1건 → 1.4.3 부적합. 나머지 axe auto 항목은 적합.
        // screen/probe/manual 은 수동확인필요 → 종합 '미완료'
        CheckResult r = new CheckResult("s", "https://x/a", "accessibility", "t", null, false,
                List.of(new CheckResult.Violation("color-contrast", "serious", "명도비", 1)));
        Map<String, ItemResult> items = mapper.map(List.of(r));
        ComplianceEvaluator.Compliance c = evaluator.evaluate(items);

        assertEquals(ItemVerdict.부적합, items.get("1.4.3").verdict());
        assertTrue(c.pendingTotal() > 0, "수동확인필요 항목이 있어 미확정");
        assertEquals("미완료", c.status());
        // 웹접근성 수동 버킷에 1.4.3 부적합이 집계됐는지
        ComplianceEvaluator.CategoryScore a11yManual = c.categories().stream()
                .filter(s -> s.category().equals("accessibility") && !s.siAuto()).findFirst().orElseThrow();
        assertTrue(a11yManual.부적합() >= 1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails or passes**

Run: `mvn -B test -Dtest=CompliancePipelineTest`
Expected: PASS (매퍼·평가엔진은 이미 존재) — 이 테스트는 결합 회귀 가드다. 만약 컴파일 실패면 import·시그니처를 맞춘다.

- [ ] **Step 3: Wire compliance into CheckService**

`CheckService.runSite(...)` 안에서 리포트 저장 직전(`ReportWriter.write(...)` 호출 위치), 이미 모아둔 `List<CheckResult> results` 로 컴플라이언스를 산출해 진행 콜백으로 요약을 남긴다. 아래를 `ReportWriter.write(...)` 호출 바로 위에 추가한다:

```java
            // 컴플라이언스 스코어링(계획 2) — 기존 결과를 37항목 판정으로 변환·집계
            com.kt.wqg.compliance.KwcagCatalog catalog = com.kt.wqg.compliance.KwcagCatalog.load();
            com.kt.wqg.compliance.ComplianceEvaluator.Compliance compliance =
                    new com.kt.wqg.compliance.ComplianceEvaluator(catalog).evaluate(
                            new com.kt.wqg.compliance.ComplianceMapper(catalog).map(results));
            progress.accept("컴플라이언스: " + compliance.status()
                    + " (미확정 " + compliance.pendingTotal() + "건)");
```

> 이 단계는 산출·로그만 한다. Compliance 를 리포트·API 로 노출하는 것은 계획 5/4.

- [ ] **Step 4: Build + full suite**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS, 전체 PASS(신규 13건 추가 전후).

- [ ] **Step 5: 실사이트 실검증 (defect-kwater)**

패키징 후 웹 콘솔이나 CLI 로 defect-kwater 를 1회 실행해, 진행 로그에 `컴플라이언스: 미완료 (미확정 N건)` 이 찍히고, axe 로 실제 검출되는 항목(예: color-contrast→1.4.3, label→3.3.2)이 매핑되는지 확인한다. 검증 방법(이 저장소 관행):

```bash
SP=<scratchpad>   # vnu.jar 위치
sed "s#/opt/wqg/vnu.jar#$SP/vnu.jar#" config/sites.yaml > "$SP/sites.local.yaml"
mvn -B -q clean package -DskipTests
set -a; . ./.env; set +a
java -jar target/web-quality-gate-0.1.0-SNAPSHOT.jar "$SP/sites.local.yaml" defect-kwater 2>&1 | grep -E '컴플라이언스|검사 완료'
```

Expected: `[defect-kwater] ... 컴플라이언스: 미완료 (미확정 ...)` 출력. (미확정은 probe/manual 항목 때문 — 계획 3/4에서 감소)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/kt/wqg/check/CheckService.java src/test/java/com/kt/wqg/compliance/CompliancePipelineTest.java
git commit -m "feat: CheckService 에 컴플라이언스 스코어링 연결 + 결합 회귀 테스트

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준 (계획 2)

- `RuleResolver` 가 axe 항목을 automation 규칙대로 판정(auto 적합/부적합, screen 수동확인필요, na 해당없음)
- `ComplianceMapper` 가 `List<CheckResult>` → 37항목 판정 맵(axe rule 매칭 + markup 전용 + 나머지 수동확인필요)
- `CheckService` 가 실행 시 컴플라이언스 상태를 산출·로그, defect-kwater 실검증에서 axe/markup 항목이 실제 판정됨
- `mvn -B test` 전체 통과

## 후속 계획

- 계획 3 — 커스텀 프로브 ~14종(c-browser 는 기존 compat 결과를 probe 층에서 연결, c-reflow·media-captions 등 신규)
- 계획 4 — AssessmentStore + 콘솔 검수 탭 + /api/assessment + 리포트 재생성
- 계획 5 — ComplianceReportWriter(SI 검수결과표 + 친절설명 HTML)
