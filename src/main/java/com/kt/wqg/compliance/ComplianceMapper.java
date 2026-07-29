package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기존 검사 결과(List<CheckResult>)를 카탈로그 37항목의 판정 맵으로 변환한다(순수).
 * axe·probe 항목은 RuleResolver로 규칙 매칭, markup 항목은 전용 로직.
 * 그 외(none)는 수동확인필요.
 */
public final class ComplianceMapper {

    private final KwcagCatalog catalog;

    public ComplianceMapper(KwcagCatalog catalog) { this.catalog = catalog; }

    public Map<String, ItemResult> map(List<CheckResult> results) {
        return map(results, List.of());
    }

    /**
     * URL(페이지)별로 항목 판정을 낸다(페이지별 평가표·대상 페이지용).
     * 각 URL의 결과·프로브 findings 만 넣어 map() 을 재사용하므로, 페이지 하나를 검사한 것과 동일하게 판정된다.
     * 반환 맵의 키 순서는 결과에 처음 등장한 URL 순서를 유지한다.
     */
    public Map<String, Map<String, ItemResult>> mapByUrl(List<CheckResult> results, List<Finding> probeFindings) {
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        results.forEach(r -> urls.add(r.url()));
        probeFindings.forEach(f -> urls.add(f.url()));
        Map<String, Map<String, ItemResult>> out = new LinkedHashMap<>();
        for (String url : urls) {
            List<CheckResult> ru = results.stream().filter(r -> url.equals(r.url())).toList();
            List<Finding> fu = probeFindings.stream().filter(f -> url.equals(f.url())).toList();
            out.put(url, map(ru, fu));
        }
        return out;
    }

    public Map<String, ItemResult> map(List<CheckResult> results, List<Finding> probeFindings) {
        List<Finding> axeFindings = results.stream()
                .filter(r -> "accessibility".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        // 위반 요소(대상)마다 Finding 을 만든다 — 한 규칙의 실패 대상 전체를 증적에 남기기 위함
                        .flatMap(v -> targetsOf(v).stream()
                                .map(t -> new Finding(r.url(), v.id(), v.help(), t))))
                .toList();
        List<Finding> pool = new java.util.ArrayList<>(axeFindings);
        pool.addAll(probeFindings);
        List<Finding> markupErrors = results.stream()
                .filter(r -> "markup".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), "markup", v.help(), null)))
                .toList();

        // 규칙별 통과(검사) 요소 수 집계 — 적합 항목의 '무엇을 검사해 정상인지' 근거로 사용
        Map<String, Integer> passByRule = new java.util.HashMap<>();
        results.stream().filter(r -> "accessibility".equals(r.checkType()) && r.passes() != null)
                .forEach(r -> r.passes().forEach(p -> passByRule.merge(p.rule(), p.nodes(), Integer::sum)));

        Map<String, ItemResult> out = new LinkedHashMap<>();
        for (CatalogItem item : catalog.items()) {
            ItemResult res;
            if ("markup".equals(item.resolver().type())) {
                res = resolveMarkup(item, markupErrors);
            } else if ("compat".equals(item.resolver().type())) {
                res = resolveCompat(item, results);
            } else {
                res = RuleResolver.resolve(item, pool);
            }
            out.put(item.code(), withPassEvidence(item, res, passByRule));
        }
        return out;
    }

    /**
     * 적합 판정(axe·markup·compat)에 '무엇을 검사해 정상이었는지' 근거를 채운다.
     * axe 항목에서 대상 요소가 하나도 없었으면(규칙이 통과 집계에 없음 = inapplicable)
     * '적합'이 아니라 '해당없음'으로 정정한다 — 검사할 대상 자체가 없었기 때문.
     * 단, 이 실행에 통과 데이터가 전혀 없으면(axe pass 미수집) 판단 불가로 기존 적합을 유지한다.
     */
    private static ItemResult withPassEvidence(CatalogItem item, ItemResult res, Map<String, Integer> passByRule) {
        if (res.verdict() != ItemVerdict.적합 || !res.evidence().isEmpty()) return res;
        List<ItemResult.Evidence> ev = new java.util.ArrayList<>();
        if ("axe".equals(item.resolver().type()) && item.resolver().rules() != null) {
            List<String> matched = item.resolver().rules().stream()
                    .filter(passByRule::containsKey).toList();
            if (matched.isEmpty() && !passByRule.isEmpty()) {
                // 검사 대상 요소가 없었음 → 해당없음(대상 없음)
                return new ItemResult(res.code(), ItemVerdict.해당없음, res.source(),
                        List.of(new ItemResult.Evidence("전체 화면", item.resolver().rules().get(0),
                                "검사 대상 요소 없음 · 해당없음")), res.memo());
            }
            matched.forEach(rule -> ev.add(new ItemResult.Evidence("전체 화면", rule,
                    passByRule.get(rule) + "개 요소 검사 · 위반 0 · 통과")));
        } else if ("markup".equals(item.resolver().type())) {
            ev.add(new ItemResult.Evidence("전체 화면", "markup", "마크업 표준 검사 · 오류 0건 · 통과"));
        } else if ("compat".equals(item.resolver().type())) {
            ev.add(new ItemResult.Evidence("전체 화면", "cross-browser", "3개 엔진 렌더링 차이 임계값 이내 · 통과"));
        }
        return ev.isEmpty() ? res : new ItemResult(res.code(), res.verdict(), res.source(), ev, res.memo());
    }

    private static ItemResult resolveMarkup(CatalogItem item, List<Finding> markupErrors) {
        // 같은 오류가 여러 화면에서 반복되므로 메시지 기준 중복 제거
        java.util.LinkedHashMap<String, ItemResult.Evidence> uniq = new LinkedHashMap<>();
        markupErrors.stream()
                .filter(f -> !"c-deprecated".equals(item.code()) || isObsolete(f.help()))
                .forEach(f -> uniq.putIfAbsent(f.help(), new ItemResult.Evidence(f.url(), "markup", f.help())));
        List<ItemResult.Evidence> matched = new java.util.ArrayList<>(uniq.values());
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

    /** 위반의 모든 요소 target(대상 selector). 요소가 없으면 [null] 한 건. */
    private static List<String> targetsOf(CheckResult.Violation v) {
        if (v.elements() == null || v.elements().isEmpty()) {
            return java.util.Collections.singletonList(null);
        }
        return v.elements().stream().map(CheckResult.Element::target).toList();
    }
}
