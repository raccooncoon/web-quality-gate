package com.kt.wqg.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검사 결과를 reports/&lt;site&gt;/&lt;yyyyMMdd-HHmm&gt;/index.json 으로 집계 저장한다.
 */
public final class ReportWriter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ReportWriter() {}

    /** 타입별 통과/실패 집계. */
    public record TypeSummary(int total, int passed, int failed) {}

    /** 전체 및 타입별 집계. */
    public record Summary(int total, int passed, int failed, Map<String, TypeSummary> byType) {}

    /** 검사에 사용한 브라우저 엔진과 버전. */
    public record EngineInfo(String name, String version) {}

    /** 종합 게이트 판정과 총평. */
    public record Verdict(String status, String summary,
                          int a11yCritical, int a11ySerious, int markupErrors, int compatDiffs) {}

    /** 리포트 최상위 스키마 (index.json). */
    public record Report(String site, String displayName, String timestamp, String standard,
                         List<EngineInfo> engines, Verdict verdict,
                         Summary summary, List<CheckResult> results) {}

    /** 결과로부터 종합 게이트 판정을 산출한다. */
    public static Verdict verdict(List<CheckResult> results) {
        int critical = countImpact(results, "accessibility", "critical");
        int serious = countImpact(results, "accessibility", "serious");
        int markup = results.stream().filter(r -> r.checkType().equals("markup"))
                .mapToInt(r -> r.violations().size()).sum();
        int compat = (int) results.stream()
                .filter(r -> r.checkType().equals("compatibility") && !r.passed()).count();

        String status;
        if (critical > 0 || serious > 0 || markup > 0) {
            status = "부적합";
        } else if (results.stream().allMatch(CheckResult::passed)) {
            status = "적합";
        } else {
            status = "개선 권고";
        }
        String summary = String.format(
                "접근성 위반 심각 %d · 중대 %d, 마크업 오류 %d, 호환성 차이 %d 검출",
                critical, serious, markup, compat);
        return new Verdict(status, summary, critical, serious, markup, compat);
    }

    private static int countImpact(List<CheckResult> results, String type, String impact) {
        return (int) results.stream()
                .filter(r -> r.checkType().equals(type))
                .flatMap(r -> r.violations().stream())
                .filter(v -> impact.equals(v.impact()))
                .count();
    }

    /** 결과 목록을 전체·타입별로 집계한다. */
    public static Summary summarize(List<CheckResult> results) {
        int passed = (int) results.stream().filter(CheckResult::passed).count();

        Map<String, int[]> acc = new LinkedHashMap<>();  // type -> [total, passed]
        for (CheckResult r : results) {
            int[] c = acc.computeIfAbsent(r.checkType(), k -> new int[2]);
            c[0]++;
            if (r.passed()) c[1]++;
        }
        Map<String, TypeSummary> byType = new LinkedHashMap<>();
        acc.forEach((type, c) -> byType.put(type, new TypeSummary(c[0], c[1], c[0] - c[1])));

        return new Summary(results.size(), passed, results.size() - passed, byType);
    }

    /**
     * reports/&lt;site&gt;/&lt;timestamp&gt;/ 에 index.json 과 report.html 을 쓰고 index.json 경로를 반환한다.
     *
     * @param outputDir 최상위 출력 디렉터리 (sites.yaml output.dir)
     */
    public static Path write(String outputDir, String site, String displayName, String timestamp,
                             String standard, List<EngineInfo> engines, List<CheckResult> results) {
        Report report = new Report(site, displayName, timestamp, standard, engines, verdict(results),
                summarize(results), results);
        Path dir = Path.of(outputDir, site, timestamp);
        Path index = dir.resolve("index.json");
        try {
            Files.createDirectories(dir);
            JSON.writeValue(index.toFile(), report);
            Files.writeString(dir.resolve("report.html"), toHtml(report));
            return index;
        } catch (IOException e) {
            throw new RuntimeException("리포트 저장 실패: " + index, e);
        }
    }

    /**
     * 데이터·CSS·JS 가 모두 인라인된 자기완결 HTML 대시보드를 만든다.
     * 외부 리소스를 전혀 참조하지 않아 폐쇄망에서 더블클릭으로 열린다.
     */
    public static String toHtml(Report report) {
        String data;
        try {
            data = JSON.writeValueAsString(report);
        } catch (IOException e) {
            throw new RuntimeException("리포트 직렬화 실패", e);
        }
        // 스크립트 태그 조기 종료 방지
        data = data.replace("</", "<\\/");
        return HTML_TEMPLATE.replace("/*__DATA__*/", data);
    }

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>웹 접근성·호환성 검사 리포트</title>
            <style>
            :root{--bg:#f6f7f9;--card:#fff;--fg:#1a1d21;--muted:#6b7280;--line:#e5e7eb;
              --pass:#16a34a;--fail:#dc2626;--warn:#d97706;--passbg:#eafbf0;--failbg:#fdecec;--warnbg:#fdf4e7;--accent:#2563eb;}
            @media(prefers-color-scheme:dark){:root{--bg:#0e1116;--card:#171b22;--fg:#e6e8eb;
              --muted:#9099a5;--line:#262c36;--passbg:#0f2a1a;--failbg:#2a1414;--warnbg:#2a2110;}}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
              font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;
              line-height:1.55;padding:32px 24px;max-width:1080px;margin:0 auto}
            h1{font-size:22px;margin:0 0 4px}h2{font-size:17px;margin:28px 0 12px;padding-top:8px}
            h3{font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:.05em;margin:16px 0 8px}
            .sub{color:var(--muted);font-size:13px}
            .meta-line{color:var(--muted);font-size:12.5px;margin-top:6px}
            .meta-line b{color:var(--fg);font-weight:600}
            .verdict{border-radius:14px;padding:18px 20px;margin:18px 0 8px;border:1px solid var(--line)}
            .verdict.부적합{background:var(--failbg);border-color:var(--fail)}
            .verdict.개선{background:var(--warnbg);border-color:var(--warn)}
            .verdict.적합{background:var(--passbg);border-color:var(--pass)}
            .verdict .status{font-size:20px;font-weight:800}
            .verdict.부적합 .status{color:var(--fail)}.verdict.개선 .status{color:var(--warn)}.verdict.적합 .status{color:var(--pass)}
            .verdict .desc{font-size:13.5px;margin-top:4px}
            .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:16px 0 8px}
            .card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px}
            .card .t{font-size:12px;color:var(--muted)}
            .card .n{font-size:24px;font-weight:700;margin-top:4px}
            .card .n small{font-size:13px;font-weight:500;color:var(--muted)}
            .bar{height:6px;border-radius:3px;background:var(--line);margin-top:10px;overflow:hidden}
            .bar>i{display:block;height:100%;background:var(--pass)}
            table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);
              border-radius:12px;overflow:hidden;margin:8px 0 8px}
            th,td{text-align:left;padding:9px 14px;border-bottom:1px solid var(--line);font-size:13.5px}
            th{font-size:12px;color:var(--muted)}tr:last-child td{border-bottom:none}
            .badge{display:inline-block;padding:1px 8px;border-radius:6px;font-size:12px;font-weight:600}
            .badge.pass{background:var(--passbg);color:var(--pass)}.badge.fail{background:var(--failbg);color:var(--fail)}
            .kwcag{display:inline-block;background:var(--accent);color:#fff;padding:0 6px;border-radius:5px;font-size:11px;font-weight:600}
            .sec{background:var(--card);border:1px solid var(--line);border-radius:12px;margin-bottom:12px;overflow:hidden}
            .sec>summary{cursor:pointer;padding:12px 16px;font-weight:600;display:flex;justify-content:space-between;
              align-items:center;gap:12px;list-style:none}
            .sec>summary::-webkit-details-marker{display:none}
            .sec .body{padding:4px 16px 14px}
            .vio{border-top:1px solid var(--line);padding:10px 0;font-size:13px}
            .vio:first-child{border-top:none}
            .vio .id{font-family:ui-monospace,monospace;color:var(--accent);font-size:12px;font-weight:600}
            .vio .impact{font-size:11px;font-weight:700;text-transform:uppercase;margin-left:6px}
            .impact.critical,.impact.error{color:var(--fail)}.impact.serious{color:var(--warn)}
            .impact.moderate,.impact.minor,.impact.visual{color:var(--muted)}
            .vio .help{margin-top:3px}
            .el{margin-top:6px;padding:6px 8px;background:var(--bg);border-radius:6px;font-size:11.5px;overflow-x:auto}
            .el .tgt{color:var(--accent);font-family:ui-monospace,monospace}
            .el pre{margin:3px 0 0;white-space:pre-wrap;word-break:break-all;font-family:ui-monospace,monospace;color:var(--muted)}
            .shots{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:8px}
            .shot{border:1px solid var(--line);border-radius:8px;overflow:hidden;background:var(--bg)}
            .shot .cap{font-size:12px;padding:6px 8px;border-bottom:1px solid var(--line);font-weight:600}
            .shot img{display:block;width:100%;height:auto}
            code{font-family:ui-monospace,monospace;font-size:12px}
            footer{margin-top:32px;padding-top:14px;border-top:1px solid var(--line);color:var(--muted);font-size:11.5px}
            @media print{
              body{padding:0;max-width:none;font-size:12px}
              .sec,.card,.verdict,table{break-inside:avoid;box-shadow:none}
              .sec>summary{list-style:none}details.sec{border-color:#ccc}
              h2{break-after:avoid}.shot img{max-height:420px;object-fit:contain;object-position:top}
              a{color:inherit;text-decoration:none}
            }
            </style></head><body>
            <div id="app"></div>
            <script id="data" type="application/json">/*__DATA__*/</script>
            <script>
            const R=JSON.parse(document.getElementById('data').textContent);
            const TYPES=[['accessibility','접근성'],['markup','마크업'],['compatibility','호환성']];
            const esc=s=>String(s==null?'':s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
            const pct=(p,t)=>t?Math.round(p/t*100):100;
            const path=u=>{try{return new URL(u).pathname}catch(e){return u}};
            const typeKo=k=>({accessibility:'접근성',markup:'마크업',compatibility:'호환성'}[k]||k);
            const baseline=R.engines&&R.engines.length?R.engines[0].name:null;
            const byUrl={};
            R.results.forEach(r=>{(byUrl[r.url]=byUrl[r.url]||{});(byUrl[r.url][r.checkType]=byUrl[r.url][r.checkType]||[]).push(r)});

            const s=R.summary,v=R.verdict;
            const vClass=v.status==='적합'?'적합':v.status==='부적합'?'부적합':'개선';
            let h=`<h1>웹 접근성·호환성 검사 리포트</h1>
              <div class="sub">${esc(R.displayName||R.site)} <code>(${esc(R.site)})</code></div>
              <div class="meta-line">검사일시 <b>${esc(R.timestamp)}</b> · 기준 <b>${esc(R.standard)}</b></div>
              <div class="meta-line">검사 브라우저 ${(R.engines||[]).map(e=>`<b>${esc(e.name)} ${esc(e.version)}</b>`).join(' · ')||'-'}</div>
              <div class="verdict ${vClass}"><div class="status">종합 판정 · ${esc(v.status)}</div>
                <div class="desc">${esc(v.summary)}</div></div>`;

            h+='<div class="cards">'+card('전체',s.passed,s.total);
            TYPES.forEach(([k,ko])=>{const b=s.byType[k];if(b)h+=card(ko,b.passed,b.total)});
            h+='</div>';

            h+='<h2>검사 매트릭스</h2><table><thead><tr><th>URL</th>'
              +TYPES.map(t=>`<th>${t[1]}</th>`).join('')+'</tr></thead><tbody>';
            Object.keys(byUrl).forEach(u=>{
              h+=`<tr><td><code>${esc(path(u))}</code></td>`;
              TYPES.forEach(([k])=>{h+='<td>'+cell(byUrl[u][k])+'</td>'});
              h+='</tr>';
            });
            h+='</tbody></table>';

            h+='<h2>URL별 상세</h2>';
            Object.keys(byUrl).forEach(u=>{
              const g=byUrl[u];
              const nfail=Object.values(g).flat().filter(r=>!r.passed).length;
              h+=`<details class="sec" ${nfail?'open':''}><summary><span><code>${esc(path(u))}</code></span>
                <span class="badge ${nfail?'fail':'pass'}">${nfail?nfail+' 항목 실패':'통과'}</span></summary><div class="body">`;
              // accessibility
              (g.accessibility||[]).forEach(r=>{h+=a11yBlock(r)});
              // markup
              (g.markup||[]).forEach(r=>{h+=markupBlock(r)});
              // compatibility
              const comp=g.compatibility||[];
              if(comp.length)h+=compatBlock(u,comp);
              h+='</div></details>';
            });

            h+=`<footer>web-quality-gate · axe-core + vnu.jar + Playwright(3-engine).
              KWCAG 2.1 항목은 WCAG 결과를 참고용으로 자동 매핑한 것으로, 공식 인증 판정이 아닙니다.</footer>`;
            document.getElementById('app').innerHTML=h;

            function card(t,p,tot){return `<div class="card"><div class="t">${t}</div>
              <div class="n">${tot-p}<small> / ${tot} 실패</small></div>
              <div class="bar"><i style="width:${pct(p,tot)}%"></i></div></div>`}
            function cell(rs){if(!rs)return '<span class="sub">-</span>';
              const bad=rs.filter(r=>!r.passed);
              if(!bad.length)return '<span class="badge pass">✓</span>';
              const n=bad.reduce((a,r)=>a+r.violations.length,0);
              return `<span class="badge fail">✗ ${n}</span>`}
            function a11yBlock(r){let x='<h3>접근성 (WCAG 2.1 AA / KWCAG 2.1)</h3>';
              if(r.passed)return x+'<div class="vio">위반 없음 ✓</div>';
              r.violations.forEach(v=>{
                x+=`<div class="vio"><span class="id">${esc(v.id)}</span>
                  <span class="impact ${esc(v.impact)}">${esc(v.impact||'')}</span>
                  ${v.kwcag?`<span class="kwcag">KWCAG ${esc(v.kwcag)}</span>`:''}
                  <span class="sub"> · ${v.nodes}개 요소</span>
                  <div class="help">${esc(v.help)}</div>`;
                (v.elements||[]).forEach(el=>{x+=`<div class="el"><span class="tgt">${esc(el.target||'')}</span>
                  ${el.html?`<pre>${esc(el.html)}</pre>`:''}</div>`});
                x+='</div>';
              });
              return x}
            function markupBlock(r){let x='<h3>마크업 유효성 (vnu.jar)</h3>';
              if(r.passed)return x+'<div class="vio">오류 없음 ✓</div>';
              r.violations.forEach(v=>{x+=`<div class="vio"><span class="impact error">error</span>
                <div class="help">${esc(v.help)}</div></div>`});
              return x}
            function compatBlock(u,comp){let x='<h3>브라우저 호환성 (엔진 렌더링 비교)</h3>';
              const base=comp[0].screenshots?comp[0].screenshots[baseline]:null;
              comp.forEach(r=>{
                const eng=r.engine, other=r.screenshots?r.screenshots[eng]:null;
                const dp=(r.diffRatio*100).toFixed(2);
                x+=`<div class="vio"><b>${esc(baseline)} ↔ ${esc(eng)}</b>
                  <span class="badge ${r.passed?'pass':'fail'}">${r.passed?'차이 허용범위':'차이 '+dp+'%'}</span>
                  <div class="shots">
                    <div class="shot"><div class="cap">${esc(baseline)} (기준)</div>${base?`<img loading="lazy" src="${esc(base)}">`:''}</div>
                    <div class="shot"><div class="cap">${esc(eng)}</div>${other?`<img loading="lazy" src="${esc(other)}">`:''}</div>
                  </div></div>`;
              });
              return x}
            </script></body></html>""";
}
