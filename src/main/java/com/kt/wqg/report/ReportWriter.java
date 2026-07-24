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

    /** 리포트 최상위 스키마 (index.json). */
    public record Report(String site, String timestamp, Summary summary, List<CheckResult> results) {}

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
    public static Path write(String outputDir, String site, String timestamp,
                             List<CheckResult> results) {
        Report report = new Report(site, timestamp, summarize(results), results);
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
            <title>web-quality-gate 리포트</title>
            <style>
            :root{--bg:#f6f7f9;--card:#fff;--fg:#1a1d21;--muted:#6b7280;--line:#e5e7eb;
              --pass:#16a34a;--fail:#dc2626;--passbg:#eafbf0;--failbg:#fdecec;--accent:#2563eb;}
            @media(prefers-color-scheme:dark){:root{--bg:#0e1116;--card:#171b22;--fg:#e6e8eb;
              --muted:#9099a5;--line:#262c36;--passbg:#0f2a1a;--failbg:#2a1414;}}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
              font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;
              line-height:1.5;padding:24px;max-width:1100px;margin:0 auto}
            h1{font-size:20px;margin:0 0 4px}.sub{color:var(--muted);font-size:13px;margin-bottom:20px}
            .gate{display:inline-block;padding:2px 10px;border-radius:999px;font-weight:700;font-size:13px}
            .gate.fail{background:var(--failbg);color:var(--fail)}.gate.pass{background:var(--passbg);color:var(--pass)}
            .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:24px}
            .card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px}
            .card .t{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
            .card .n{font-size:26px;font-weight:700;margin-top:4px}
            .card .n small{font-size:14px;font-weight:500;color:var(--muted)}
            .bar{height:6px;border-radius:3px;background:var(--line);margin-top:10px;overflow:hidden}
            .bar>i{display:block;height:100%;background:var(--pass)}
            table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);
              border-radius:12px;overflow:hidden;margin-bottom:24px}
            th,td{text-align:left;padding:10px 14px;border-bottom:1px solid var(--line);font-size:14px}
            th{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
            tr:last-child td{border-bottom:none}
            .badge{display:inline-block;padding:1px 8px;border-radius:6px;font-size:12px;font-weight:600}
            .badge.pass{background:var(--passbg);color:var(--pass)}.badge.fail{background:var(--failbg);color:var(--fail)}
            details{background:var(--card);border:1px solid var(--line);border-radius:12px;margin-bottom:10px;padding:0 16px}
            summary{cursor:pointer;padding:12px 0;font-weight:600;display:flex;justify-content:space-between;gap:12px}
            summary::-webkit-details-marker{display:none}
            .vio{border-top:1px solid var(--line);padding:10px 0;font-size:13px}
            .vio .id{font-family:ui-monospace,monospace;color:var(--accent);font-size:12px}
            .vio .meta{color:var(--muted);font-size:12px;margin-left:6px}
            code{font-family:ui-monospace,monospace;font-size:12px}
            </style></head><body>
            <div id="app"></div>
            <script id="data" type="application/json">/*__DATA__*/</script>
            <script>
            const R=JSON.parse(document.getElementById('data').textContent);
            const TYPES=[['accessibility','접근성'],['markup','마크업'],['compatibility','호환성']];
            const esc=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
            const pct=(p,t)=>t?Math.round(p/t*100):100;
            // url -> {type -> [results]}
            const byUrl={};
            R.results.forEach(r=>{(byUrl[r.url]=byUrl[r.url]||{});(byUrl[r.url][r.checkType]=byUrl[r.url][r.checkType]||[]).push(r)});
            const s=R.summary;
            let h=`<h1>web-quality-gate 리포트 · ${esc(R.site)}</h1>
              <div class="sub">${esc(R.timestamp)} · 전체 ${s.total}건
              <span class="gate ${s.failed?'fail':'pass'}">${s.failed?'실패 '+s.failed:'전체 통과'}</span></div>`;
            h+='<div class="cards">';
            h+=card('전체',s.passed,s.total);
            TYPES.forEach(([k,ko])=>{const b=s.byType[k];if(b)h+=card(ko,b.passed,b.total)});
            h+='</div>';
            // matrix
            h+='<table><thead><tr><th>URL</th>'+TYPES.map(t=>`<th>${t[1]}</th>`).join('')+'</tr></thead><tbody>';
            Object.keys(byUrl).forEach(u=>{
              h+=`<tr><td><code>${esc(new URL(u).pathname)}</code></td>`;
              TYPES.forEach(([k])=>{h+='<td>'+cell(byUrl[u][k])+'</td>'});
              h+='</tr>';
            });
            h+='</tbody></table>';
            // details per failing result
            const fails=R.results.filter(r=>!r.passed);
            if(fails.length){h+='<h1 style="font-size:16px">위반 상세 ('+fails.length+')</h1>';
              fails.forEach(r=>{
                h+=`<details><summary><span>${esc(new URL(r.url).pathname)} · ${typeKo(r.checkType)}${r.engine?' · '+esc(r.engine):''}</span>
                  <span class="badge fail">${r.violations.length}건</span></summary>`;
                r.violations.forEach(v=>{h+=`<div class="vio"><span class="id">${esc(v.id)}</span>
                  <span class="meta">${esc(v.impact||'')}${v.nodes?' · '+v.nodes+'개':''}</span><br>${esc(v.help||'')}</div>`});
                h+='</details>';
              });
            }
            document.getElementById('app').innerHTML=h;
            function card(t,p,tot){return `<div class="card"><div class="t">${t}</div>
              <div class="n">${tot-p}<small> / ${tot} 실패</small></div>
              <div class="bar"><i style="width:${pct(p,tot)}%"></i></div></div>`}
            function cell(rs){if(!rs)return '<span class="meta">-</span>';
              const bad=rs.filter(r=>!r.passed);
              if(!bad.length)return '<span class="badge pass">✓</span>';
              const n=bad.reduce((a,r)=>a+r.violations.length,0);
              return `<span class="badge fail">✗ ${n}</span>`}
            function typeKo(k){const m={accessibility:'접근성',markup:'마크업',compatibility:'호환성'};return m[k]||k}
            </script></body></html>""";
}
