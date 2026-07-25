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

    /** 빌드시 주입된 버전·빌드시각(build.properties, Maven 필터링). */
    private static final String VERSION, BUILD_TIME;
    static {
        String v = "dev", b = "-";
        try (var in = ComplianceReportWriter.class.getResourceAsStream("/build.properties")) {
            if (in != null) {
                var p = new java.util.Properties();
                p.load(in);
                v = p.getProperty("version", "dev");
                b = p.getProperty("buildTime", "-").replace("T", " ").replace("Z", " UTC");
            }
        } catch (Exception ignore) { /* 없으면 기본값 */ }
        VERSION = v; BUILD_TIME = b;
    }

    private ComplianceReportWriter() {}

    /** 페이로드로 reportDir/report.html 저장. */
    public static Path write(Path reportDir, Map<String, Object> payload) {
        java.util.Map<String, Object> enriched = new java.util.LinkedHashMap<>(payload);
        enriched.put("screenshots", scanScreenshots(reportDir));
        Path out = reportDir.resolve("report.html");
        try {
            Files.writeString(out, toHtml(enriched));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("report.html 저장 실패: " + out, e);
        }
    }

    /** reports/&lt;&gt;/screenshots/&lt;engine&gt;-&lt;slug&gt;.png 를 slug(화면)별로 엔진 목록으로 묶는다. */
    private static java.util.List<Map<String, Object>> scanScreenshots(Path reportDir) {
        Path dir = reportDir.resolve("screenshots");
        if (!Files.isDirectory(dir)) return java.util.List.of();
        java.util.Map<String, java.util.Map<String, String>> bySlug = new java.util.LinkedHashMap<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted()
                    .forEach(p -> {
                        String f = p.getFileName().toString();
                        String base = f.substring(0, f.length() - 4);
                        int dash = base.indexOf('-');
                        if (dash < 0) return;
                        String engine = base.substring(0, dash), slug = base.substring(dash + 1);
                        bySlug.computeIfAbsent(slug, k -> new java.util.LinkedHashMap<>())
                                .put(engine, "screenshots/" + f);
                    });
        } catch (IOException e) {
            return java.util.List.of();
        }
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        bySlug.forEach((slug, shots) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("slug", slug);
            m.put("shots", shots);
            out.add(m);
        });
        return out;
    }

    public static String toHtml(Map<String, Object> payload) {
        String data;
        try {
            data = JSON.writeValueAsString(payload).replace("</", "<\\/");
        } catch (IOException e) {
            throw new RuntimeException("리포트 직렬화 실패", e);
        }
        return TEMPLATE.replace("/*__DATA__*/", data)
                .replace("__VERSION__", VERSION.replace("\\", "").replace("\"", ""))
                .replace("__BUILD_TIME__", BUILD_TIME.replace("\\", "").replace("\"", ""));
    }

    private static final String TEMPLATE = """
            <!DOCTYPE html>
            <html lang="ko"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>웹접근성·웹호환성 검수 결과보고서</title>
            <style>
            /* 밝은 단일 톤(공공기관 문서) — 다크모드 미사용, 인쇄/화면 동일 */
            :root{--paper:#fff;--bg:#eceff4;--ink:#17202e;--navy:#0f2340;--navy2:#1b3b63;
              --head:#0f2340;--chip:#0f2340;
              --gov:#1f5aa6;--govsoft:#e9f1fb;--gold:#b0894a;--muted:#5a6573;
              --line:#d4dbe4;--line2:#b9c3d0;
              --ok:#1f7a44;--okbg:#e7f4ec;--bad:#c02a2a;--badbg:#fbeaea;--warn:#a9781c;--warnbg:#faf1df;--na:#eef1f5;}
            :root{color-scheme:light}
            *{box-sizing:border-box}
            html{-webkit-print-color-adjust:exact;print-color-adjust:exact}
            body{margin:0 auto;max-width:940px;background:var(--bg);color:var(--ink);
              font-family:"Malgun Gothic","Apple SD Gothic Neo",system-ui,-apple-system,sans-serif;
              line-height:1.6;padding:28px 22px 44px;counter-reset:sec;word-break:keep-all;overflow-wrap:break-word}
            /* 한글은 어절(띄어쓰기) 단위로만 줄바꿈 — 글자 중간에서 한 칸 내려가는 현상 방지.
               text-wrap:pretty 로 마지막 줄에 한 단어만 남는(고아 단어) 현상 완화 */
            td,th,.g,.sub,summary,.docinfo td,h1,h2{word-break:keep-all;overflow-wrap:break-word;text-wrap:pretty}
            /* URL·경로만 강제 분해. 코드(1.2.1 등)는 줄바꿈 금지 */
            .ev,.site,.shoturl{word-break:break-all;overflow-wrap:anywhere}
            code{white-space:nowrap}
            /* 표지 머리글 */
            .cover{background:linear-gradient(180deg,var(--navy),var(--navy2));color:#fff;
              border-top:3px solid var(--gold);padding:26px 28px 22px;border-radius:3px 3px 0 0}
            .cover .ey{font-size:11px;letter-spacing:.22em;color:#c7d4e6;font-weight:600}
            .cover .eyk{font-size:12.5px;color:#aebfd6;margin-top:3px}
            .cover h1{font-size:26px;font-weight:800;
              margin:12px 0 4px;letter-spacing:-.01em;line-height:1.28}
            .cover .site{font-size:12.5px;color:#b8c7dc;font-family:ui-monospace,monospace}
            .cover .basis{margin-top:12px;padding-top:12px;border-top:1px solid rgba(255,255,255,.16);
              font-size:12px;color:#c2d0e3}
            /* 문서 정보 표 */
            .docinfo{width:100%;border-collapse:collapse;margin:0 0 4px;background:var(--paper);
              border:1px solid var(--line);border-top:none;border-radius:0 0 3px 3px;overflow:hidden}
            .docinfo th{width:118px;background:var(--govsoft);color:var(--head);font-weight:700;
              font-size:12.5px;text-align:left;padding:9px 14px;border-bottom:1px solid var(--line);white-space:nowrap}
            .docinfo td{font-size:13px;padding:9px 14px;border-bottom:1px solid var(--line)}
            .docinfo tr:last-child th,.docinfo tr:last-child td{border-bottom:none}
            /* 섹션 제목(자동 번호) */
            h2{counter-increment:sec;font-size:15px;color:var(--head);margin:30px 0 12px;
              padding-bottom:8px;border-bottom:2px solid var(--head);display:flex;align-items:center;gap:10px}
            h2::before{content:counter(sec,decimal-leading-zero);background:var(--chip);color:#fff;
              font-size:11px;font-weight:700;padding:3px 7px;border-radius:2px;letter-spacing:.04em}
            /* 종합 판정 */
            .verdict{background:var(--paper);border:1px solid var(--line2);border-top:4px solid var(--muted);
              border-radius:3px;padding:18px 20px;margin:4px 0;display:flex;align-items:center;gap:18px;flex-wrap:wrap}
            .verdict.적합{border-top-color:var(--ok)}.verdict.부적합{border-top-color:var(--bad)}.verdict.미완료{border-top-color:var(--warn)}
            .verdict .lab{font-size:11px;color:var(--muted);letter-spacing:.14em;font-weight:600}
            .verdict .s{font-size:24px;font-weight:800;letter-spacing:.02em;line-height:1.15;margin-top:2px}
            .verdict.적합 .s{color:var(--ok)}.verdict.부적합 .s{color:var(--bad)}.verdict.미완료 .s{color:var(--warn)}
            .verdict .rt{margin-left:auto;text-align:right;font-size:12.5px;color:var(--muted);line-height:1.9}
            .verdict .rt b{color:var(--ink);font-size:14px;font-variant-numeric:tabular-nums}
            /* 표 공통 */
            table.dt{width:100%;border-collapse:collapse;background:var(--paper);
              border:1px solid var(--line);border-radius:3px;overflow:hidden;margin:2px 0 6px}
            .dt th{background:var(--govsoft);color:var(--head);font-size:11.5px;font-weight:700;
              text-align:left;padding:8px 12px;border-bottom:1px solid var(--line)}
            .dt td{font-size:12.5px;padding:8px 12px;border-bottom:1px solid var(--line);vertical-align:top}
            .dt tbody tr.main:last-child td,.dt tbody tr:last-child td{border-bottom:none}
            .dt .num{text-align:right;font-variant-numeric:tabular-nums;font-family:ui-monospace,monospace;white-space:nowrap}
            .dt .r0{color:var(--muted)}
            .dt tr.main:hover{background:var(--govsoft)}
            .dt tr.totrow td{background:var(--govsoft);border-top:2px solid var(--navy);border-bottom:none}
            /* 항목표 열 폭 — 코드/판정/근거는 줄바꿈 금지, 검사항목·세부기준에 넓게 배분 */
            .items td:nth-child(1),.items th:nth-child(1){width:34px}
            .items td:nth-child(2),.items th:nth-child(2){width:52px;white-space:nowrap}
            .items td:nth-child(3),.items th:nth-child(3){width:24%}
            .items td:nth-child(4),.items th:nth-child(4){width:42%}
            .items td:nth-child(5),.items th:nth-child(5),.items td:nth-child(6),.items th:nth-child(6){white-space:nowrap}
            /* 준수율 바 */
            .bar{height:5px;background:var(--line);border-radius:3px;overflow:hidden;margin-top:5px;min-width:88px}
            .bar>i{display:block;height:100%;background:var(--gov)}
            .bar.ok>i{background:var(--ok)}.bar.no>i{background:var(--bad)}
            /* 판정 배지 */
            .badge{display:inline-block;padding:1px 8px;border-radius:2px;font-size:11px;font-weight:700;
              white-space:nowrap;border:1px solid transparent}
            .badge.적합,.badge.충족{background:var(--okbg);color:var(--ok);border-color:var(--ok)}
            .badge.부적합,.badge.미달{background:var(--badbg);color:var(--bad);border-color:var(--bad)}
            .badge.수동확인필요,.badge.대기,.badge.미완료{background:var(--warnbg);color:var(--warn);border-color:var(--warn)}
            .badge.해당없음{background:var(--na);color:var(--muted);border-color:var(--line2)}
            /* 상세 설명 행 */
            .prov{color:var(--warn);font-size:10px;font-weight:700;border:1px solid var(--warn);border-radius:2px;padding:0 4px;vertical-align:middle}
            .note{font-size:11.5px;color:var(--muted);background:var(--bg);border:1px solid var(--line);border-radius:4px;padding:9px 12px;margin:2px 0 8px}.note b{color:var(--fg)}
            .drow>td{background:var(--bg);padding:0}
            details.d{margin:0}
            details.d>summary{cursor:pointer;color:var(--gov);font-size:12px;font-weight:700;padding:7px 13px;list-style:none}
            details.d>summary::-webkit-details-marker{display:none}
            details.d>summary::before{content:"▸ "}
            details.d[open]>summary::before{content:"▾ "}
            .g{font-size:12px;color:var(--muted);padding:0 15px 12px}
            .g div{margin:4px 0}.g b{color:var(--ink);font-weight:700;margin-right:5px}
            .ev{font-size:11.5px;color:var(--muted);margin-top:5px;font-family:ui-monospace,monospace;word-break:break-all;
              padding-left:9px;border-left:2px solid var(--line2)}
            code{font-family:ui-monospace,monospace;font-size:11.5px;color:var(--navy2)}
            .flash{animation:fl 1.6s ease}@keyframes fl{0%,40%{background:var(--govsoft)}100%{background:transparent}}
            /* 개선 필요 요약 */
            table.sumt{width:100%;border-collapse:collapse;background:var(--paper);
              border:1px solid var(--line);border-radius:3px;overflow:hidden}
            .sumt td{font-size:12.5px;padding:9px 12px;border-bottom:1px solid var(--line);vertical-align:top}
            .sumt tr:last-child td{border-bottom:none}
            .sumt tr{cursor:pointer}.sumt tr:hover{background:var(--govsoft)}
            .sumt .c1{white-space:nowrap;width:1px}
            .sumt .go{color:var(--gov);font-size:11px;font-weight:700;white-space:nowrap}
            /* 스크린샷 */
            .shoturl{font-size:12px;color:var(--head);margin:16px 0 6px;font-family:ui-monospace,monospace;font-weight:700;
              display:flex;align-items:center;gap:10px;flex-wrap:wrap}
            .difbtn{background:var(--paper);color:var(--gov);border:1px solid var(--gov);border-radius:3px;
              padding:3px 10px;font-size:11px;font-weight:700;cursor:pointer}
            .difbtn[aria-pressed=true]{background:var(--gov);color:#fff}
            .difnote{font-size:11px;color:var(--muted);font-family:inherit}
            .shots{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:10px;margin-bottom:10px}
            .shot{border:1px solid var(--line);border-radius:3px;overflow:hidden;background:var(--paper);display:flex;flex-direction:column}
            .shot .cap{font-size:11.5px;font-weight:700;color:var(--head);padding:6px 9px;
              border-bottom:1px solid var(--line);background:var(--govsoft);display:flex;align-items:center;gap:6px}
            .shot .cap .base{font-size:10px;font-weight:700;color:#fff;background:var(--navy);border-radius:2px;padding:0 5px}
            .imgwrap{position:relative;line-height:0}
            .imgwrap img{display:block;width:100%;height:auto;cursor:zoom-in}
            .imgwrap canvas{position:absolute;inset:0;width:100%;height:100%;pointer-events:none}
            /* 라이트박스 */
            #lb{position:fixed;inset:0;background:rgba(8,12,20,.92);display:none;z-index:50;
              align-items:flex-start;justify-content:center;overflow:auto;padding:24px;cursor:zoom-out}
            #lb.on{display:flex}#lb .lbwrap{position:relative;max-width:100%;height:max-content;box-shadow:0 6px 30px rgba(0,0,0,.5)}
            #lb .lbwrap img{display:block;max-width:100%;height:auto}
            #lb .lbwrap canvas{position:absolute;inset:0;width:100%;height:100%;pointer-events:none}
            #lb .lbcap{position:fixed;top:14px;left:50%;transform:translateX(-50%);color:#fff;font-size:12.5px;
              background:rgba(0,0,0,.5);padding:5px 12px;border-radius:3px}
            .sub{color:var(--muted);font-size:12.5px}
            footer{margin-top:30px;padding-top:14px;border-top:1px solid var(--line);color:var(--muted);font-size:11px;line-height:1.75}
            .verstamp{position:fixed;right:12px;bottom:10px;font-size:10.5px;color:var(--muted);
              background:rgba(255,255,255,.88);border:1px solid var(--line);border-radius:3px;padding:3px 8px;z-index:8;font-family:ui-monospace,monospace}
            .pbtn{position:fixed;top:14px;right:16px;background:var(--gov);color:#fff;border:none;border-radius:3px;
              padding:9px 15px;font-size:12.5px;font-weight:700;cursor:pointer;z-index:9;box-shadow:0 2px 8px rgba(0,0,0,.22)}
            .backbtn{position:fixed;top:14px;left:16px;background:var(--paper);color:var(--gov);border:1px solid var(--gov);
              border-radius:3px;padding:9px 14px;font-size:12.5px;font-weight:700;cursor:pointer;z-index:9;
              text-decoration:none;box-shadow:0 2px 8px rgba(0,0,0,.15)}
            @page{size:A4;margin:12mm}
            @media print{
              body{padding:0;max-width:none;font-size:10.5px;line-height:1.45;background:#fff}
              .noprint{display:none!important}.cover{border-radius:0}
              table.dt,.docinfo,.verdict,.sumt,.shot{break-inside:avoid}
              h2{break-after:avoid}a{color:inherit;text-decoration:none}
              details.d>summary{display:none}.g{padding:0 13px 9px;font-size:10px}
              .dt td,.dt th{padding:5px 8px;font-size:10px}.dt .sub{font-size:9.5px}
              .docinfo th,.docinfo td{padding:6px 10px;font-size:10.5px}
              .shot img{max-height:340px;object-fit:contain;object-position:top}
            }
            </style></head><body>
            <a class="backbtn noprint" id="backbtn" href="/#result" style="display:none">← 결과 목록</a>
            <div class="verstamp noprint">v__VERSION__ · __BUILD_TIME__</div>
            <button class="pbtn noprint" onclick="window.print()">🖨 인쇄 / PDF 저장</button>
            <div id="app"></div>
            <div id="lb" class="noprint"><div class="lbcap"></div><div class="lbwrap"><img alt=""><canvas></canvas></div></div>
            <script id="data" type="application/json">/*__DATA__*/</script>
            <script>
            // 콘솔(http)로 열람할 때만 '결과 목록' 돌아가기 버튼 노출 (배포 PDF/file:// 에선 숨김)
            if(location.protocol.indexOf('http')===0){const _b=document.getElementById('backbtn');if(_b)_b.style.display='';}
            const R=JSON.parse(document.getElementById('data').textContent);
            const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
            const WD=['일','월','화','수','목','금','토'];
            const fmtTs=s=>{const m=/^(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})$/.exec(s||'');
              if(!m)return esc(s);const d=new Date(+m[1],+m[2]-1,+m[3],+m[4],+m[5]);
              return `${m[1]}-${m[2]}-${m[3]} (${WD[d.getDay()]}) ${m[4]}:${m[5]}`;};
            const c=R.compliance, vc=c.status, items=R.items||[];
            const basis='웹접근성 KWCAG 2.2 (KS X OT0003:2022) · WCAG 2.1 AA · 웹호환성 3개 엔진 렌더링 비교';
            const cnt=v=>items.filter(it=>it.verdict===v).length;
            let h=`<div class="cover">
                <div class="ey">WEB ACCESSIBILITY &amp; COMPATIBILITY COMPLIANCE REPORT</div>
                <div class="eyk">웹접근성 · 웹호환성 검수 결과보고서</div>
                <h1>${esc(R.displayName||R.site)}</h1>
                <div class="site">${esc(R.site)}</div>
                <div class="basis">검사 기준 · ${esc(basis)}</div>
              </div>
              <table class="docinfo">
                <tr><th>대상 시스템</th><td>${esc(R.displayName||R.site)} <code>(${esc(R.site)})</code></td></tr>
                <tr><th>검사 일시</th><td>${fmtTs(R.timestamp)}</td></tr>
                <tr><th>검사 기준</th><td>KWCAG 2.2 (KS X OT0003:2022) · WCAG 2.1 AA · 3개 엔진 렌더링 비교</td></tr>
                <tr><th>종합 판정</th><td><span class="badge ${esc(vc)}">${esc(vc)}</span>${c.pendingTotal>0?' · 미확정 '+c.pendingTotal+'건':''}</td></tr>
              </table>`;
            // 종합 판정 패널
            h+=`<h2>종합 판정</h2>
              <div class="verdict ${esc(vc)}">
                <div><div class="lab">OVERALL VERDICT</div><div class="s">${esc(vc)}</div></div>
                <div class="rt">전체 <b>${items.length}</b>건 중 · 적합 <b>${cnt('적합')}</b> · 부적합 <b>${cnt('부적합')}</b> · 수동확인 <b>${cnt('수동확인필요')}</b> · 해당없음 <b>${cnt('해당없음')}</b>${c.pendingTotal>0?'<br>미확정 <b>'+c.pendingTotal+'</b>건 — 평가자 확정 후 최종 판정':''}</div>
              </div>`;
            // 카테고리별 준수율
            h+='<h2>카테고리별 준수율</h2><table class="dt"><thead><tr><th>구분</th><th class="num">항목</th><th class="num">적합</th><th class="num">부적합</th><th class="num">해당없음</th><th class="num">대기</th><th>준수율</th><th class="num">기준</th><th>판정</th></tr></thead><tbody>';
            (c.categories||[]).forEach(s=>{
              const st=s.pending>0?'<span class="badge 대기">대기</span>':(s.pass?'<span class="badge 충족">충족</span>':'<span class="badge 미달">미달</span>');
              const bc=s.pending>0?'':(s.pass?'ok':'no');const rate=Math.max(0,Math.min(100,+s.rate||0));
              h+=`<tr><td>${esc(s.label)}</td><td class="num">${s.total}</td><td class="num">${s.적합}</td><td class="num">${s.부적합}</td><td class="num r0">${s.naCount}</td><td class="num">${s.pending}</td>
                <td>${s.pending>0?'<span class="prov">산정 전</span> <span class="r0" style="font-size:11px">대기 '+s.pending+'건</span>':'<b>'+s.rate+'%</b><div class="bar '+bc+'"><i style="width:'+rate+'%"></i></div>'}</td><td class="num r0">${s.threshold}%</td><td>${st}</td></tr>`});
            // 합계 행 — 전체 항목 집계
            const T={total:0,적합:0,부적합:0,na:0,pending:0};
            (c.categories||[]).forEach(s=>{T.total+=s.total;T.적합+=s.적합;T.부적합+=s.부적합;T.na+=s.naCount;T.pending+=s.pending;});
            const trate=(T.적합+T.부적합)>0?(T.적합/(T.적합+T.부적합)*100).toFixed(1):'0.0';
            h+=`<tr class="totrow"><td><b>합계</b></td><td class="num"><b>${T.total}</b></td><td class="num"><b>${T.적합}</b></td><td class="num"><b>${T.부적합}</b></td><td class="num r0">${T.na}</td><td class="num">${T.pending}</td>
              <td>${T.pending>0?'<span class="prov">산정 전</span> <span class="r0" style="font-size:11px">대기 '+T.pending+'건</span>':'<b>'+trate+'%</b>'}</td><td class="num r0">—</td><td></td></tr>`;
            h+='</tbody></table>';
            h+=`<div class="note">※ 준수율 = 적합 ÷ (적합 + 부적합) × 100. <b>해당없음·대기(미확정)는 분모에서 제외</b>합니다.
              <b>대기 항목이 남은 카테고리는 준수율을 산정하지 않고 '산정 전'으로 표기</b>하며 판정은 <b>대기</b>입니다 —
              평가자가 대기 항목을 모두 확정(적합/부적합/해당없음)하면 그때 최종 준수율·판정이 확정됩니다.</div>`;
            if((R.excluded||[]).length)h+=`<div class="note">🚫 <b>검사 제외 항목 ${R.excluded.length}건</b>(관리자 설정) — 이번 검수 범위에서 제외되어 위 집계·준수율에 포함되지 않았습니다: `
              +R.excluded.map(e=>`<code>${esc(e.code)}</code> ${esc(e.title)}`).join(', ')+`</div>`;
            // 카테고리별 항목 표 + 접이식 상세
            (c.categories||[]).forEach(cat=>{
              const its=items.filter(it=>it.category===cat.category&&(it.siClassification==='auto')===cat.siAuto);
              if(!its.length)return;
              h+=`<h2>${esc(cat.label)}</h2><table class="dt items"><thead><tr><th>No</th><th>코드</th><th>검사 항목</th><th>세부 기준</th><th>판정</th><th>근거</th></tr></thead><tbody>`;
              its.forEach(it=>{const g=it.guide||{};
                h+=`<tr class="main" id="it-${esc(it.code)}"><td class="num r0">${it.no}</td><td><code>${esc(it.code)}</code></td><td>${esc(it.title)}</td><td class="sub">${esc(it.criteria)}</td>
                  <td><span class="badge ${esc(it.verdict)}">${esc(it.verdict)}</span></td><td class="sub">${it.source==='assessor'?'평가자 확정':'자동 판정'}</td></tr>`;
                h+=`<tr class="drow"><td colspan="6"><details class="d"><summary>상세 설명 보기</summary>`;
                const rmsg=it.verdict==='적합'?'✓ 자동 검사에서 위반이 발견되지 않아 <b>적합</b>입니다.'
                  :it.verdict==='해당없음'?'검사 대상 요소가 페이지에 없어 <b>해당없음</b>으로 처리했습니다.'
                  :it.verdict==='수동확인필요'?'대상은 있으나 자동 판정이 어려워 <b>평가자 확인</b>이 필요합니다.'
                  :'자동 검사에서 <b>위반</b>이 발견되었습니다. 아래 증거를 확인하세요.';
                h+=`<div class="g"><div><b>검사 결과</b> ${rmsg}</div></div>`;
                if(it.memo)h+=`<div class="g"><div><b>평가자 메모</b>${esc(it.memo)}</div></div>`;
                h+=`<div class="g"><div><b>무엇</b>${esc(g.what)}</div><div><b>왜</b>${esc(g.why)}</div>
                  <div><b>근거</b>${esc(g.basis)}</div><div><b>개선 방법</b>${esc(g.fix)}</div>
                  ${g.limit?'<div><b>검사 한계</b>'+esc(g.limit)+'</div>':''}`;
                (it.evidence||[]).forEach(e=>{h+=`<div class="ev">${esc(e.rule)} — ${esc(e.detail)}${e.url?' ('+esc(e.url)+')':''}</div>`});
                h+='</div></details></td></tr>';
              });
              h+='</tbody></table>';
            });
            // 개선 필요 요약(클릭 → 위 항목표 상세로 이동)
            const detail=items.filter(it=>it.verdict==='부적합'||it.verdict==='수동확인필요')
              .sort((a,b)=>(a.verdict==='부적합'?0:1)-(b.verdict==='부적합'?0:1));
            if(detail.length){const nBad=detail.filter(it=>it.verdict==='부적합').length;
              h+=`<h2>개선 필요 항목 · 부적합 ${nBad} · 수동확인 ${detail.length-nBad}</h2><table class="sumt"><tbody>`;
              detail.forEach(it=>{const fix=(it.guide||{}).fix;
                h+=`<tr data-code="${esc(it.code)}"><td class="c1"><span class="badge ${esc(it.verdict)}">${esc(it.verdict)}</span></td>
                  <td class="c1"><code>${esc(it.code)}</code></td>
                  <td><b>${esc(it.title)}</b>${fix?'<div class="sub">'+esc(fix)+'</div>':''}</td>
                  <td class="c1 go noprint">상세 ▸</td></tr>`});
              h+='</tbody></table>';
            }
            // 브라우저 호환성 화면 비교
            if((R.screenshots||[]).length){h+='<h2>브라우저 호환성 화면 비교 (3개 엔진)</h2>';
              R.screenshots.forEach((sc,i)=>{const engs=Object.keys(sc.shots);const base=sc.shots.chromium?'chromium':engs[0];
                h+=`<div class="shoturl">/${esc(sc.slug)}
                    <button class="difbtn noprint" data-g="${i}" data-base="${esc(base)}" aria-pressed="false">차이 표시 (기준: ${esc(base)})</button>
                    <span class="difnote noprint" data-note="${i}"></span></div><div class="shots">`;
                engs.forEach(eng=>{const isBase=eng===base;
                  h+=`<figure class="shot"><figcaption class="cap">${esc(eng)}${isBase?'<span class="base">기준</span>':''}</figcaption>
                    <div class="imgwrap"><img loading="lazy" data-g="${i}" data-eng="${esc(eng)}" data-slug="${esc(sc.slug)}" src="${esc(sc.shots[eng])}" alt="${esc(sc.slug)} — ${esc(eng)}"></div></figure>`});
                h+='</div>';
              });
            }
            h+=`<footer>web-quality-gate · axe-core + vnu.jar + Playwright(3-engine) + 커스텀 프로브.
              KWCAG 2.2 항목은 자동 검사 결과를 매핑·집계한 것이며, 수동확인 항목은 평가자 확정을 거칩니다. 공식 인증 판정이 아닙니다.
              <div style="margin-top:6px">도구 버전 <b>__VERSION__</b> · 빌드/배포 <b>__BUILD_TIME__</b></div></footer>`;
            document.getElementById('app').innerHTML=h;

            // 개선 필요 요약 → 해당 항목 상세 펼치고 이동
            function jumpTo(code){const row=document.getElementById('it-'+code);if(!row)return;
              const det=row.nextElementSibling&&row.nextElementSibling.querySelector('details');if(det)det.open=true;
              row.scrollIntoView({behavior:'smooth',block:'center'});
              row.classList.remove('flash');void row.offsetWidth;row.classList.add('flash');}
            document.querySelectorAll('.sumt tr[data-code]').forEach(tr=>tr.addEventListener('click',()=>jumpTo(tr.dataset.code)));

            // 라이트박스(화면 크게 보기)
            const lb=document.getElementById('lb'),lbImg=lb.querySelector('img'),lbCap=lb.querySelector('.lbcap'),lbCv=lb.querySelector('canvas');
            document.addEventListener('click',e=>{const img=e.target.closest('.imgwrap img');if(!img)return;
              lbImg.src=img.src;lbCap.textContent=img.alt;
              // 썸네일의 차이(빨강) 오버레이를 라이트박스에도 복제 — 작은/큰 사진 일치
              const srcCv=img.parentNode.querySelector('canvas');
              if(srcCv&&srcCv.width){lbCv.width=srcCv.width;lbCv.height=srcCv.height;
                lbCv.getContext('2d').clearRect(0,0,lbCv.width,lbCv.height);
                lbCv.getContext('2d').drawImage(srcCv,0,0);lbCv.style.display='';}
              else lbCv.style.display='none';
              lb.classList.add('on');});
            lb.addEventListener('click',()=>{lb.classList.remove('on');lbImg.src='';});
            addEventListener('keydown',e=>{if(e.key==='Escape')lb.classList.remove('on');});

            // 잘못된 부분 표기 — chromium 기준 픽셀 차이 오버레이(브라우저 내 계산)
            const load=src=>new Promise((res,rej)=>{const im=new Image();im.onload=()=>res(im);im.onerror=rej;im.src=src;});
            function diffData(a,b){const w=Math.min(a.naturalWidth,b.naturalWidth),hh=Math.min(a.naturalHeight,b.naturalHeight);
              if(!w||!hh)return null;
              const ca=document.createElement('canvas');ca.width=w;ca.height=hh;ca.getContext('2d').drawImage(a,0,0);
              const cb=document.createElement('canvas');cb.width=w;cb.height=hh;cb.getContext('2d').drawImage(b,0,0);
              let A,B;try{A=ca.getContext('2d').getImageData(0,0,w,hh).data;B=cb.getContext('2d').getImageData(0,0,w,hh).data;}
              catch(err){return 'tainted';}
              const out=new ImageData(w,hh),O=out.data;const TH=110;let n=0;
              for(let i=0;i<A.length;i+=4){const d=Math.abs(A[i]-B[i])+Math.abs(A[i+1]-B[i+1])+Math.abs(A[i+2]-B[i+2]);
                if(d>TH){O[i]=224;O[i+1]=45;O[i+2]=42;O[i+3]=140;n++;}else O[i+3]=0;}
              return{w,h:hh,out,ratio:n/(w*hh)};}
            async function toggleDiff(btn){const gi=btn.dataset.g,base=btn.dataset.base,note=document.querySelector(`[data-note="${gi}"]`);
              const on=btn.getAttribute('aria-pressed')==='true';
              const imgs=[...document.querySelectorAll(`.imgwrap img[data-g="${gi}"]`)];
              if(on){btn.setAttribute('aria-pressed','false');note.textContent='';
                imgs.forEach(im=>{const cv=im.parentNode.querySelector('canvas');if(cv)cv.remove();});return;}
              btn.setAttribute('aria-pressed','true');note.textContent='분석 중…';
              try{const baseImg=await load(imgs.find(im=>im.dataset.eng===base).src);let msg=[];
                for(const im of imgs){if(im.dataset.eng===base)continue;
                  const other=await load(im.src);const d=diffData(baseImg,other);
                  if(d==='tainted'){note.textContent='차이 자동표시는 콘솔(http) 열람 시 지원됩니다. 확대 비교를 이용하세요.';btn.setAttribute('aria-pressed','false');return;}
                  if(!d)continue;
                  const cv=document.createElement('canvas');cv.width=d.w;cv.height=d.h;cv.getContext('2d').putImageData(d.out,0,0);
                  im.parentNode.appendChild(cv);msg.push(`${im.dataset.eng} ${(d.ratio*100).toFixed(1)}%`);}
                note.textContent=msg.length?('기준 대비 차이 — '+msg.join(' · ')):'차이 없음';
              }catch(err){note.textContent='화면을 불러오지 못했습니다.';btn.setAttribute('aria-pressed','false');}}
            document.querySelectorAll('.difbtn').forEach(b=>b.addEventListener('click',()=>toggleDiff(b)));
            // 차이 표시 기본 ON — 로드 후 자동 계산(콘솔 http 열람 시). file:// 은 안내 후 자동 해제
            document.querySelectorAll('.difbtn').forEach(b=>toggleDiff(b));

            // 인쇄/PDF 저장 시 모든 상세 설명 펼침
            addEventListener('beforeprint',()=>document.querySelectorAll('details.d').forEach(d=>{d._o=d.open;d.open=true;}));
            addEventListener('afterprint',()=>document.querySelectorAll('details.d').forEach(d=>{d.open=d._o;}));
            </script></body></html>""";
}
