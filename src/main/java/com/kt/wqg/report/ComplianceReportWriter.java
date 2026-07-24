package com.kt.wqg.report;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * SI 검수결과표 report.html 생성기. AssessmentView.build 페이로드(37항목 판정·증거·상세설명 +
 * 4카테고리 준수율)를 자기완결·인쇄최적화 HTML 로 렌더한다.
 * report 는 sink 패키지 — compliance 를 import 하지 않는다. 페이로드는 호출자가 만들어 넘긴다.
 */
public final class ComplianceReportWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ComplianceReportWriter() {}

    /** 페이로드로 reportDir/report.html 저장. */
    public static Path write(Path reportDir, Map<String, Object> payload) {
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
