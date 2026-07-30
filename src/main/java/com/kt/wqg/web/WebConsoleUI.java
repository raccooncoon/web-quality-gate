package com.kt.wqg.web;

/** 웹 콘솔 단일 페이지 UI (인라인 CSS/JS, 외부 리소스 없음). */
final class WebConsoleUI {

    private WebConsoleUI() {}

    private static final String HTML_A = """
            <!DOCTYPE html>
            <html lang="ko"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>web-quality-gate 콘솔</title>
            <style>
            /* 공공기관 문서 톤 — 밝은 단일 테마(다크모드 미사용), 리포트와 동일 팔레트 */
            :root{--bg:#eceff4;--card:#fff;--fg:#17202e;--muted:#5a6573;--line:#d4dbe4;--line2:#b9c3d0;
              --navy:#0f2340;--navy2:#1b3b63;--gov:#1f5aa6;--accent:#1f5aa6;--govsoft:#e9f1fb;--gold:#b0894a;
              --ok:#1f7a44;--bad:#c02a2a;--warn:#a9781c;--okbg:#e7f4ec;--badbg:#fbeaea;--warnbg:#faf1df;
              --auto:#0c7b7b;--autobg:#e2f4f4;color-scheme:light}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);word-break:keep-all;
              font-family:"Malgun Gothic","Apple SD Gothic Neo",system-ui,-apple-system,sans-serif;line-height:1.55}
            .ev,input,textarea,code,small,.sys .nm small{word-break:break-all}
            header{background:linear-gradient(180deg,var(--navy),var(--navy2));border-top:3px solid var(--gold);
              padding:14px 22px;position:sticky;top:0;z-index:5}
            header h1{font-size:16px;margin:0;display:flex;align-items:center;gap:10px;color:#fff}
            header .tag{font-size:11px;color:#aebfd6;font-weight:400}
            nav{display:flex;gap:6px;margin-top:12px}
            nav button{background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.16);color:#cbd8ea;font-size:13.5px;font-weight:700;
              padding:7px 16px;border-radius:4px;cursor:pointer}
            nav button.on{background:#fff;color:var(--navy);border-color:#fff}
            nav{align-items:center}
            .navsp{flex:1 1 auto}.navsp2{flex:0 0 22px}
            /* 매핑표: KIWAC·카도와·웹와치 pill 열은 가운데 정렬 */
            #mapPr td:nth-child(2),#mapPr td:nth-child(3),#mapPr td:nth-child(4),
            #mapPr th:nth-child(2),#mapPr th:nth-child(3),#mapPr th:nth-child(4){text-align:center}
            #mapPr td:nth-child(2) .pill,#mapPr td:nth-child(3) .pill,#mapPr td:nth-child(4) .pill{margin-right:0}
            nav button.util{background:rgba(255,255,255,.05);border-color:rgba(255,255,255,.12);color:#b7c6db;font-weight:600}
            nav button.util.on{background:#fff;color:var(--navy);border-color:#fff;font-weight:700}
            main{max-width:920px;margin:0 auto;padding:22px}
            .notice{background:var(--warnbg);border:1px solid var(--warn);border-radius:4px;
              padding:11px 14px;font-size:13px;margin-bottom:16px}.notice b{color:var(--warn)}
            .tabintro{background:var(--govsoft);border:1px solid var(--gov);border-radius:4px;
              padding:10px 14px;font-size:12.5px;color:var(--fg);margin-bottom:16px;line-height:1.6}.tabintro b{color:var(--navy)}
            .ok-note{background:var(--okbg);border:1px solid var(--ok);border-radius:4px;padding:11px 14px;font-size:13px;margin-bottom:16px}.ok-note b{color:var(--ok)}
            .cardsteps{font-size:11.5px;color:var(--muted);background:var(--bg);border:1px solid var(--line);
              border-radius:4px;padding:6px 10px;margin:2px 0 10px}.cardsteps b{color:var(--navy)}
            .actbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
            .danger{margin-top:14px;padding-top:10px;border-top:1px dashed var(--line2)}
            .danger .del{color:var(--bad);border-color:var(--bad)}
            .sys{background:var(--card);border:1px solid var(--line);border-radius:6px;margin-bottom:14px;overflow:hidden}
            .sys>.hd{padding:12px 16px;display:flex;justify-content:space-between;align-items:center;cursor:pointer;gap:12px}
            .sys .nm{font-weight:700}.sys .nm small{color:var(--muted);font-weight:400;font-family:ui-monospace,monospace;margin-left:6px;font-size:12px}
            .sys .body{padding:6px 16px 16px;border-top:1px solid var(--line);display:none}
            .sys.open .body{display:block}
            .pill{font-size:11px;font-weight:700;padding:2px 8px;border-radius:3px;border:1px solid transparent}
            .pill.ok{background:var(--okbg);color:var(--ok);border-color:var(--ok)}
            .pill.no{background:var(--badbg);color:var(--bad);border-color:var(--bad)}
            .pill.wait{background:var(--warnbg);color:var(--warn);border-color:var(--warn)}
            input.autoval,select.autoval,textarea.autoval{background:var(--autobg)!important;border-color:var(--auto)!important;box-shadow:inset 3px 0 0 var(--auto)}
            .autoval-chip{background:var(--autobg);color:var(--auto);border:1px solid var(--auto);border-radius:3px;padding:1px 6px;font-weight:700;font-size:11px}
            /* 검사 경로 — 구조화된 행 목록 편집기 */
            .pathhd{display:flex;justify-content:space-between;align-items:baseline;margin:10px 0 4px}
            .pathhd .pathcount{color:var(--muted);font-weight:400;font-size:11.5px}
            .pathlist{border:1px solid var(--line2);border-radius:6px;overflow:hidden;background:var(--card)}
            .pathrow{display:grid;grid-template-columns:1fr 210px 34px;align-items:stretch;border-bottom:1px solid var(--line)}
            .pathrow:last-child{border-bottom:none}
            .pathrow>input{border:0;border-radius:0;background:transparent;box-shadow:none;padding:8px 11px;
              font-family:ui-monospace,monospace;width:100%}
            .pathrow>input:focus{outline:2px solid var(--gov);outline-offset:-2px;background:var(--govsoft)}
            .pathrow .pp{font-size:12.5px;font-weight:600;color:var(--fg)}
            .pathrow .ps{font-size:11.5px;color:var(--muted);border-left:1px solid var(--line)}
            .pathrow .ps::placeholder{color:var(--line2);font-weight:400}
            .pathrow .rm{border:0;border-left:1px solid var(--line);background:transparent;color:var(--muted);
              cursor:pointer;font-size:16px;line-height:1;display:flex;align-items:center;justify-content:center}
            .pathrow .rm:hover{color:var(--bad);background:var(--badbg)}
            .pathrow.justadded{animation:pf 2s ease}@keyframes pf{0%,55%{background:var(--autobg)}100%{background:transparent}}
            .pathempty{padding:14px;text-align:center;color:var(--muted);font-size:12px}
            .pathadd{display:flex;gap:7px;margin-top:7px}
            .pathadd input{font-family:ui-monospace,monospace;font-size:12px}
            .pathadd button{margin-top:0;white-space:nowrap}
            .autolegend{font-size:11.5px;color:var(--muted);margin-top:8px}
            .mapviewbar{display:flex;gap:6px;margin:10px 0}
            .mvtab{width:auto;padding:5px 12px;border:1px solid var(--line2);background:var(--card);border-radius:4px;font-size:12px;font-weight:700;color:var(--muted);cursor:pointer}
            .mvtab.on{background:var(--gov);color:#fff;border-color:var(--gov)}
            label{display:block;font-size:12px;color:var(--muted);margin:10px 0 3px;font-weight:600}
            input,select,textarea{width:100%;padding:8px 10px;border:1px solid var(--line2);border-radius:4px;
              background:#fff;color:var(--fg);font-size:13px;font-family:inherit}
            textarea{min-height:90px;font-family:ui-monospace,monospace;font-size:12px;resize:vertical}
            .row{display:grid;grid-template-columns:1fr 1fr;gap:10px}
            .row3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px}
            .hint{font-size:11px;color:var(--muted);margin-top:3px}
            button.act{background:var(--gov);color:#fff;border:none;border-radius:4px;padding:6px 13px;
              font-size:12.5px;font-weight:700;cursor:pointer;margin-top:9px}
            button.ghost{background:#fff;color:var(--gov);border:1px solid var(--gov)}
            button.run{background:var(--ok);color:#fff;border:none}
            button:disabled,button.act:disabled{cursor:not-allowed;opacity:.5;filter:grayscale(1)}
            .toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:var(--navy);color:#fff;
              padding:9px 18px;border-radius:4px;font-size:13px;opacity:0;transition:.2s;pointer-events:none}
            .toast.show{opacity:1}
            .st{font-size:12px;color:var(--muted)}
            table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);border-radius:6px;overflow:hidden}
            th,td{text-align:left;padding:10px 14px;border-bottom:1px solid var(--line);font-size:13px}
            th{font-size:11px;color:var(--navy);background:var(--govsoft);font-weight:700}tr:last-child td{border-bottom:none}
            a{color:var(--gov)}.auth-only{display:none}.auth-form .auth-only{display:block}
            .storage-only{display:none}.auth-storage .storage-only{display:block}
            .hbox{margin-top:10px;padding:10px 12px;border-radius:4px;font-size:13px;border:1px solid var(--line)}
            .hbox.ok{background:var(--okbg);border-color:var(--ok)}.hbox.no{background:var(--badbg);border-color:var(--bad)}
            .hbox b{display:block;margin-bottom:5px}.hstep{padding:2px 0;font-size:12.5px}
            /* 진행 표시 — 스피너 + 메시지 칩 */
            .busy{margin-top:10px;display:flex;align-items:center;gap:10px;padding:10px 13px;border-radius:6px;
              font-size:13px;color:var(--navy);background:var(--govsoft);border:1px solid var(--gov)}
            .spin{width:15px;height:15px;flex:none;border:2px solid var(--gov);border-right-color:transparent;
              border-radius:50%;animation:sp .7s linear infinite}@keyframes sp{to{transform:rotate(360deg)}}
            .busy .sub{color:var(--muted);font-size:11.5px}
            /* 결과 카드 — 상태 배지 헤더 + 단계 행 */
            .rcard{margin-top:10px;border:1px solid var(--line);border-radius:6px;overflow:hidden;
              animation:rin .25s ease}@keyframes rin{from{opacity:0;transform:translateY(-3px)}to{opacity:1}}
            .rcard .rhd{display:flex;align-items:center;gap:9px;padding:9px 13px;font-size:13px;font-weight:700}
            .rcard.ok .rhd{background:var(--okbg);color:var(--ok)}.rcard.no .rhd{background:var(--badbg);color:var(--bad)}
            .rcard .badge{font-size:11px;font-weight:700;padding:2px 9px;border-radius:20px;border:1px solid currentColor}
            .rcard .rbody{padding:4px 13px 8px;background:var(--card)}
            .sstep{display:flex;align-items:flex-start;gap:9px;padding:7px 0;border-bottom:1px solid var(--line)}
            .sstep:last-child{border-bottom:none}
            .dot{flex:none;width:19px;height:19px;border-radius:50%;display:flex;align-items:center;justify-content:center;
              font-size:11px;font-weight:700;color:#fff;margin-top:1px}
            .sstep.ok .dot{background:var(--ok)}.sstep.no .dot{background:var(--bad)}
            .sstep .snm{font-size:12.5px;font-weight:600;color:var(--fg)}
            .sstep .sdt{font-size:11.5px;color:var(--muted);margin-top:1px}
            .rnote{padding:7px 13px;font-size:11.5px;color:var(--muted);background:var(--card);border-top:1px solid var(--line)}
            .pill.na{background:var(--line);color:var(--muted);border-color:var(--line2)}
            @media print{
              header,nav,.toast,.verstamp,.noprint{display:none!important}
              body{background:#fff;color:#000}
              main{max-width:100%;margin:0;padding:0}
              #audit{display:block!important}
              #printhd{display:block!important}
              table{font-size:11px;border-color:#999}
              th,td{border-color:#bbb}
              th{background:#eee!important;color:#000!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}
              .pill{border:1px solid #999!important;background:#fff!important;color:#000!important}
              a[href]:after{content:''}
              .cardsteps{background:#f4f4f4!important}
            }
            .catbar{background:var(--card);border:1px solid var(--line);border-radius:6px;padding:10px 14px;margin-bottom:8px}
            .cb-top{display:flex;justify-content:space-between;gap:10px;font-size:13px;margin-bottom:6px;flex-wrap:wrap}
            .bar{background:var(--line);border-radius:3px;height:8px;overflow:hidden}
            .bar>i{display:block;height:100%}
            .grp{font-size:13px;color:var(--navy);font-weight:700;margin:18px 0 8px}
            .item{background:var(--card);border:1px solid var(--line);border-radius:6px;padding:12px 14px;margin-bottom:8px}
            .it-hd{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:4px}
            .it-hd b{flex:1}
            .linkbtn{background:none;border:none;color:var(--gov);font-size:12px;cursor:pointer;padding:0;font-weight:700}
            .ev{font-size:12px;color:var(--muted);margin:2px 0}
            /* 증거 — 규칙 캡션 + 각진 표(대상·요소·위치) */
            .evgrp{margin-top:8px;border:1px solid var(--line2);border-radius:6px;padding:8px 10px;background:var(--bg)}
            .evsum{margin-top:7px;font-size:12px;color:var(--fg)}
            .evcap{margin-top:0;font-size:11.5px;color:var(--fg);display:flex;align-items:center;gap:7px;flex-wrap:wrap}
            .evrule{font-family:ui-monospace,monospace;font-size:10px;font-weight:700;color:var(--gov);
              background:var(--govsoft);border:1px solid var(--line2);padding:1px 7px}
            .evt{width:100%;table-layout:fixed;border-collapse:collapse;margin-top:4px;font-size:11.5px;background:#fff;border:1.5px solid var(--navy2)}
            .evt th{background:var(--govsoft);color:var(--navy);text-align:left;padding:5px 9px;
              font-size:10.5px;font-weight:700;border:1px solid var(--line2)}
            .evt td{padding:5px 9px;border:1px solid var(--line);vertical-align:top}
            .evt .evno{width:32px!important;text-align:center;color:var(--muted);font-variant-numeric:tabular-nums}
            .evt .evel{width:auto!important;font-family:ui-monospace,monospace;color:var(--fg);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .evt .evloc{width:88px!important;font-family:ui-monospace,monospace;font-size:10.5px;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .evtwrap.wrap .evel,.evtwrap.wrap .evloc{white-space:normal;word-break:break-all;overflow:visible}
            .evcap .evtx{display:none;margin-left:auto;font-size:10px;font-weight:700;color:var(--gov);background:#fff;
              border:1px solid var(--line2);border-radius:3px;padding:1px 8px;cursor:pointer}
            .evcap .evtx:hover{background:var(--govsoft)}
            .guide{margin-top:6px;font-size:12.5px;color:var(--muted)}
            .guide summary{cursor:pointer;color:var(--gov);font-weight:700}
            .guide div{margin:4px 0}
            .override{margin-top:10px;border-top:1px dashed var(--line2);padding-top:10px}
            .ovbtns{display:flex;gap:8px;margin-top:8px}
            .ovbtns button{flex:1}
            .hd2{display:flex;align-items:center;gap:14px;margin-bottom:14px;flex-wrap:wrap}
            #assessview .body{max-width:920px;margin:0 auto}
            .item.needs{border-color:var(--warn);background:var(--warnbg)}
            .item.done{opacity:.72}
            .filterbar{display:flex;align-items:center;gap:14px;margin:16px 0 4px;flex-wrap:wrap}
            .filterbar label{display:flex;align-items:center;gap:6px;margin:0;color:var(--fg);font-weight:600;cursor:pointer}
            .filterbar input{width:auto}
            .prog{font-size:12px;color:var(--muted)}
            .prog b{color:var(--fg)}
            .grouphd{font-size:13px;color:var(--navy);font-weight:700;margin:20px 0 8px;padding-bottom:5px;border-bottom:2px solid var(--navy)}
            td .pill{margin-right:6px}
            .discovered{margin-top:8px}
            .candtools{display:flex;gap:12px;margin:6px 0}
            .candlist{max-height:220px;overflow:auto;margin:8px 0;border:1px solid var(--line2);border-radius:4px;padding:6px 8px;background:#fff}
            .cand{display:flex;align-items:center;gap:8px;font-size:12.5px;color:var(--fg);font-weight:400;margin:2px 0;font-family:ui-monospace,monospace;cursor:pointer}
            .cand input{width:auto}
            .platform{margin-bottom:20px;overflow-x:auto}
            .platform td:nth-child(2),.platform td:nth-child(3),.platform td:nth-child(4){white-space:nowrap}
            .platform td .runid{white-space:normal}
            .phd{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:14px;margin:0 0 6px;
              padding-bottom:6px;border-bottom:2px solid var(--navy);color:var(--navy)}
            .phd b{color:var(--navy)}.phd small{color:var(--muted);font-weight:400;font-family:ui-monospace,monospace;font-size:12px}
            .newtag{font-size:10px;font-weight:700;color:var(--gov);background:var(--govsoft);border:1px solid var(--gov);border-radius:3px;padding:0 6px;margin-left:6px}
            .runid{font-size:11px;color:var(--muted);margin-top:3px}.runid code{color:var(--gov);font-size:11px}
            tr.flash td{animation:fl 1.8s ease}@keyframes fl{0%,45%{background:var(--govsoft)}100%{background:transparent}}
            .cmpbtn{margin-top:0;font-size:12px;padding:5px 12px}
            .cmphd{display:flex;align-items:center;gap:16px;background:var(--card);border:1px solid var(--line);
              border-radius:6px;padding:14px 18px;margin-bottom:8px}
            .cmphd .cmpcol{flex:1}.cmphd .cmpcol b{font-size:15px}.cmphd .arrow{font-size:22px;color:var(--muted)}
            #compareview .body{max-width:920px;margin:0 auto}
            tr.improve td{background:var(--okbg)}tr.regress td{background:var(--badbg)}
            .cmpchk{width:auto;cursor:pointer}
            .statcell{width:340px;max-width:340px;font-size:12px;color:var(--muted);white-space:normal;word-break:break-word;overflow-wrap:anywhere}
            .statcell .pill{white-space:nowrap}
            .itemchk{width:auto;cursor:pointer}
            .aidraft{margin-top:8px}.aidraft .hbox{background:var(--govsoft);border-color:var(--gov)}
            .aithumbs{display:flex;gap:8px;flex-wrap:wrap;margin:2px 0 4px}
            .aithumbs .cap{font-size:10.5px;color:#5a6b7d;text-align:center;margin-top:2px;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .aithumb-box{display:flex;flex-direction:column;align-items:center}
            .aithumb{width:200px;height:124px;object-fit:contain;object-position:top;border:1px solid var(--gov);border-radius:6px;cursor:zoom-in;background:#eef1f5}
            .aithumb:hover{box-shadow:0 2px 8px rgba(20,40,80,.18);border-color:var(--gov)}
            #lbov{position:fixed;inset:0;background:rgba(10,20,40,.82);display:none;align-items:center;justify-content:center;z-index:9999;cursor:zoom-out;padding:24px}
            #lbov img{max-width:96vw;max-height:94vh;box-shadow:0 8px 40px rgba(0,0,0,.5);border-radius:6px;background:#fff}
            .sparkle{background:linear-gradient(135deg,#4285f4,#9b72cb,#d96570);-webkit-background-clip:text;background-clip:text;
              -webkit-text-fill-color:transparent;color:transparent;font-weight:800;animation:spk 2.4s ease-in-out infinite;display:inline-block}
            @keyframes spk{0%,100%{filter:drop-shadow(0 0 1px rgba(120,120,255,.4)) saturate(1)}
              50%{filter:drop-shadow(0 0 4px rgba(150,110,220,.85)) saturate(1.4);transform:scale(1.12)}}
            .verstamp{position:fixed;right:8px;bottom:6px;font-size:9px;color:var(--muted);z-index:20;opacity:.75;cursor:pointer;
              background:rgba(255,255,255,.9);border:1px solid var(--line);border-radius:3px;padding:1px 5px;font-family:ui-monospace,monospace}
            .verstamp:hover{opacity:1}
            .pwwrap{position:relative}.pwwrap input{padding-right:40px}
            .eye{position:absolute;right:6px;top:50%;transform:translateY(-50%);background:none;border:none;
              cursor:pointer;line-height:0;padding:4px;color:var(--muted)}.eye:hover{color:var(--gov)}
            .eye svg{width:18px;height:18px;display:block}
            </style></head><body>
            <header><h1>🛡️ web-quality-gate <span class="tag">웹접근성·웹호환성 검사 콘솔</span></h1>
            <nav><button data-tab="stdref" class="util">ⓘ 기준·도구</button>
              <span class="navsp2"></span>
              <button data-tab="config" class="on">① 설정</button>
              <button data-tab="run">② 실행</button><button data-tab="result">③ 결과 · 검수</button>
              <span class="navsp"></span>
              <button data-tab="syscfg" class="util">⚙ 시스템 관리</button>
              <button data-tab="items" class="util">✓ 점검 항목</button>
              <button data-tab="aicfg" class="util">✦ AI 설정</button></nav></header>
            <main><div id="config"></div><div id="run" style="display:none"></div>
              <div id="items" style="display:none"></div>
              <div id="syscfg" style="display:none"></div>
              <div id="aicfg" style="display:none"></div>
              <div id="audit" style="display:none"></div>
              <div id="stdref" style="display:none"></div>
              <div id="result" style="display:none"></div>
              <div id="assessview" style="display:none"><div class="body"></div></div>
              <div id="compareview" style="display:none"><div class="body"></div></div></main>
            <div class="toast" id="toast"></div>
            <div class="verstamp" title="클릭하면 복사" onclick="copyVer(this)">v__WQG_VER__ · __WQG_BUILD__</div>
            <script>
            const $=s=>document.querySelector(s);
            const api=(u,m,b)=>fetch(u,{method:m||'GET',headers:{'Content-Type':'application/json'},
              body:b?JSON.stringify(b):undefined}).then(r=>r.json());
            const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
            // 증거: detail 의 '· 예) A / B' 를 대상 요소 목록으로 분리 → 규칙별 각진 표로 렌더
            const evTargets=e=>{const p=(e.detail||'').split('· 예)');
              let t=p.length>1?p[1].split(' / ').map(x=>x.trim()).filter(Boolean):[];
              if(e.target)t.unshift(e.target);
              return {desc:p[0].replace(/\\s+$/,''),targets:t}};
            const urlPath=u=>{try{return new URL(u).pathname||u}catch(_){return u||''}};
            const evTables=evs=>{const byRule=new Map();
              (evs||[]).forEach(e=>{const {desc,targets}=evTargets(e);const loc=e.url?urlPath(e.url):'전체 화면';
                if(!byRule.has(e.rule))byRule.set(e.rule,{rows:[],hasEl:false});
                const g=byRule.get(e.rule);
                if(targets.length){targets.forEach(t=>g.rows.push({el:t,loc}));g.hasEl=true;}
                else g.rows.push({el:desc||'—',loc});});
              let h='';
              byRule.forEach((g,rule)=>{h+=`<div class="evgrp"><div class="evcap"><span class="evrule">${esc(rule)}</span>${g.hasEl?` 검사 대상 <b>${g.rows.length}</b>건`:''}`
                +`<button type="button" class="evtx" onclick="this.parentNode.nextElementSibling.classList.toggle('wrap')">펼쳐보기</button></div>`
                +`<div class="evtwrap"><table class="evt"><thead><tr><th class="evno">대상</th><th>요소</th><th class="evloc">위치</th></tr></thead><tbody>`
                +g.rows.map((r,i)=>`<tr><td class="evno">${i+1}</td><td class="evel" title="${esc(r.el)}">${esc(r.el)}</td><td class="evloc" title="${esc(r.loc)}">${esc(r.loc)}</td></tr>`).join('')
                +`</tbody></table></div></div>`;});
              return h};
            let toastT;function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('show');
              clearTimeout(toastT);toastT=setTimeout(()=>t.classList.remove('show'),1800)}
            // 진행 표시 칩(스피너 + 메시지, 선택적 보조문구)
            function busy(msg,sub){return '<div class="busy"><span class="spin"></span><span>'+esc(msg)
              +(sub?' <span class="sub">'+esc(sub)+'</span>':'')+'</span></div>'}
            // 심플 라인 눈 아이콘(뜬/감은)
            const EYE_OPEN='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/><circle cx="12" cy="12" r="3"/></svg>';
            const EYE_OFF='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.9 17.9A10.1 10.1 0 0 1 12 20C5 20 1 12 1 12a18.5 18.5 0 0 1 5.1-5.9M9.9 4.2A9.1 9.1 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.2 3.2m-6.7-1.1a3 3 0 1 1-4.2-4.2"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';
            function legacyCopy(s){try{const ta=document.createElement('textarea');ta.value=s;
              ta.style.cssText='position:fixed;opacity:0;left:-9999px';document.body.appendChild(ta);ta.focus();ta.select();
              const r=document.execCommand('copy');document.body.removeChild(ta);return r}catch(e){return false}}
            function copyVer(el){const s=el.textContent.trim();const ok=()=>toast('복사됨: '+s);
              if(navigator.clipboard&&navigator.clipboard.writeText)
                navigator.clipboard.writeText(s).then(ok,()=>legacyCopy(s)?ok():toast('복사 실패'));
              else legacyCopy(s)?ok():toast('복사 실패')}
            document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>{
              document.querySelectorAll('nav button').forEach(x=>x.classList.remove('on'));b.classList.add('on');
              $('#assessview').style.display='none';$('#compareview').style.display='none';  // 상단 탭으로 빠져나오기
              ['config','run','items','syscfg','aicfg','audit','stdref','result'].forEach(t=>$('#'+t).style.display=t===b.dataset.tab?'':'none');
              if(b.dataset.tab==='run')loadRun();if(b.dataset.tab==='result')loadResult();if(b.dataset.tab==='items')loadItems();if(b.dataset.tab==='stdref')loadStdRef();
              if(b.dataset.tab==='syscfg')loadSysCfg();if(b.dataset.tab==='aicfg')loadAiCfg();if(b.dataset.tab==='audit')loadAudit()});

            // ── 기준·도구 탭 (정적 참조: 도구 비교 + KWCAG 매핑 + 체크리스트) ──
            async function loadStdRef(){const box=$('#stdref');if(box.dataset.done)return;
              const {items:realItems}=await api('/api/items');
              const P={a:'<span class="pill ok" title="해당 도구가 자동 검출">자동</span>',m:'<span class="pill wait" title="도구에 항목은 있고 사람이 최종 확인">수동</span>',p:'<span class="pill wait" title="부분 지원(제품 버전·설정에 따라 다름)">부분</span>',n:'<span class="pill na" title="해당 도구 미지원 — 항목 자체가 없음(주로 KWCAG 2.2 신설 항목이거나 웹호환성 등 도구 범위 밖)">—</span>'};
              const intro='<div class="tabintro">세 도구 모두 KWCAG를 보지만 <b>목적·엔진·공신력이 달라 대체가 아니라 보완</b>입니다. '
                +'이 사이트의 실제 검사 결과는 <b>③ 결과·검수</b> 탭에서 확인하세요. 아래는 표준 관계·도구 비교·항목 매핑·체크리스트(참조).</div>';
              const std='<div class=grouphd>1. 표준 관계 · WCAG ↔ KWCAG</div>'
                +'<div class="tabintro"><b>WCAG</b>가 국제 원본(W3C 권고), <b>KWCAG</b>는 이를 한국 실정에 맞게 추린·재구성한 국가표준(KS X OT0003)입니다. '
                +'KWCAG 항목은 대부분 WCAG 성공기준(SC)에 대응하지만 <b>1:1이 아니라</b> 여러 WCAG 기준을 하나로 묶거나 일부(주로 AAA)를 제외한 <b>부분집합</b>입니다. '
                +'WQG는 엔진(axe-core)이 내는 <b>WCAG 태그 결과를 KWCAG 33항목으로 매핑·집계</b>합니다(아래 3번 표).</div>'
                +'<table><tr><th style="width:14%">구분</th><th style="width:43%">WCAG</th><th style="width:43%;background:var(--govsoft)">KWCAG</th></tr>'
                +'<tr><td><b>제정</b></td><td>W3C · 국제 권고</td><td>국가표준 KS X OT0003 · TTA</td></tr>'
                +'<tr><td><b>최신</b></td><td>2.0·2.1·2.2</td><td>2.0·2.1·<b>2.2 (2022)</b></td></tr>'
                +'<tr><td><b>구조</b></td><td>4원칙 → 13지침 → 78 성공기준 (A/AA/AAA)</td><td>4원칙 → 14지침 → <b>33 검사항목</b></td></tr>'
                +'<tr><td><b>검사항목 수</b></td><td>2.1: 50 SC · 2.2: 78 SC</td><td><b>2.1: 24항목</b> → <b>2.2(2022): 33항목</b> <span class="pill wait">+9 추가</span><br><small class=st>KIWAC WA 심사표는 2.1 기준 24항목, KWCAG 2.2 개정에서 9항목 신설(문자 단축키·포인터 취소/제스처·레이블과 네임·동작 기반·도움 정보·접근 가능한 인증·반복 입력 정보 등)</small></td></tr>'
                +'<tr><td><b>4원칙</b></td><td colspan=2 style="text-align:center">인식의 용이성 · 운용의 용이성 · 이해의 용이성 · 견고성 <small class=st>(양쪽 동일)</small></td></tr>'
                +'<tr><td><b>법적 근거</b></td><td>직접 의무 아님(원본 기준)</td><td><b>장애인차별금지법 · 지능정보화기본법</b> → 공공기관 의무 · WA 품질인증 심사</td></tr>'
                +'<tr><td><b>WQG에서</b></td><td>측정 엔진(axe-core)의 언어</td><td>판정·리포트의 언어(집계 단위)</td></tr></table>'
                +'<div class="cardsteps" style="margin-top:12px"><b>WCAG 통과 ≈ KWCAG 통과</b>가 대체로 성립하지만, 법·인증이 KWCAG 기준이라 <b>판정은 KWCAG 항목 단위</b>로 합니다. '
                +'아래 매핑의 "33 + 확장 4"에서 <b>33은 KWCAG 표준 항목</b>, <b>+4는 KWCAG 밖 웹호환성</b>(c-browser·c-reflow·c-deprecated·c-webstandard)을 WQG가 얹은 확장입니다.</div>';
              const cmp='<div class=grouphd style="margin-top:22px">2. 도구 성격 비교</div>'
                +'<table><tr><th>항목</th><th>카도와(KADO-WAH)</th><th>웹와치(WebWatch)</th><th style="background:var(--govsoft)">WQG (이 도구)</th></tr>'
                +'<tr><td><b>성격</b></td><td>NIA 무료 공식 도구</td><td>상용 모니터링 솔루션</td><td>자체 제작 인하우스</td></tr>'
                +'<tr><td><b>엔진</b></td><td>KWCAG 네이티브</td><td>자체 상용 엔진</td><td><small style="font-family:ui-monospace,monospace">axe-core+vnu+Playwright</small></td></tr>'
                +'<tr><td><b>방식</b></td><td>단일 페이지·준자동</td><td>사이트 크롤링·스케줄</td><td>로그인 후 다중 페이지 자동+AI 초안</td></tr>'
                +'<tr><td><b>크로스브라우저</b></td><td>'+P.n+'</td><td>'+P.n+'</td><td>'+P.a+' 3엔진 픽셀diff</td></tr>'
                +'<tr><td><b>확대(200%)</b></td><td>'+P.n+'</td><td>'+P.p+'</td><td>'+P.a+' reflow 자동</td></tr>'
                +'<tr><td><b>수동항목</b></td><td>평가자 유도</td><td>평가자</td><td>AI 비전 초안+근거 스크린샷, 평가자 확정</td></tr>'
                +'<tr><td><b>공신력</b></td><td><span class="pill ok">인증·자가진단 표준</span></td><td>상용 모니터링</td><td><span class="pill wait">내부 QA 참고</span></td></tr>'
                +'<tr><td><b>적합 용도</b></td><td>공식 자가진단·인증 준비</td><td>대규모 정기 모니터링</td><td>개발·운영 상시 QA·회귀 방지</td></tr></table>';
              const M=[['P','인식의 용이성 (Perceivable)'],
                ['1.1.1','적절한 대체 텍스트','a','a','axe'],['1.2.1','자막 제공','m','m','axe'],['1.3.1','표의 구성','a','a','axe'],
                ['1.3.2','콘텐츠의 선형 구조','m','m','axe'],['1.3.3','명확한 지시사항','m','m','AI·평가자'],['1.4.1','색에 무관한 인식','m','p','AI·평가자'],
                ['1.4.2','자동 재생 금지','m','a','axe'],['1.4.3','텍스트 명도 대비','a','a','axe'],['1.4.4','콘텐츠 간의 구분','m','m','평가자'],
                ['P','운용의 용이성 (Operable)'],
                ['2.1.1','키보드 사용 보장','m','m','axe'],['2.1.2','초점 이동과 표시','m','m','평가자'],['2.1.3','조작 가능(타깃 크기)','n','p','axe'],
                ['2.1.4','문자 단축키','m','m','axe'],['2.2.1','응답시간 조절','m','m','axe'],['2.2.2','정지 기능','m','a','axe'],['2.3.1','깜빡임 제한','m','a','axe'],
                ['2.4.1','반복 영역 건너뛰기','a','a','axe'],['2.4.2','제목 제공','a','a','axe'],['2.4.3','적절한 링크 텍스트','a','a','axe'],['2.4.4','고정 참조 위치','m','m','axe'],
                ['2.5.1','단일 포인터 입력','m','m','axe'],['2.5.2','포인터 입력 취소','m','m','axe'],['2.5.3','레이블과 네임','m','m','axe'],['2.5.4','동작 기반 작동','m','m','axe'],
                ['P','이해의 용이성 (Understandable)'],
                ['3.1.1','기본 언어 표시','a','a','axe'],['3.2.1','사용자 요구에 따른 실행','m','m','axe'],['3.2.2','찾기 쉬운 도움 정보','m','m','AI·평가자'],
                ['3.3.1','오류 정정','m','m','axe'],['3.3.2','레이블 제공','a','a','axe'],['3.3.3','접근 가능한 인증','m','m','axe'],['3.3.4','반복 입력 정보','m','m','AI·평가자'],
                ['P','견고성 (Robust)'],
                ['4.1.1','마크업 오류 방지','p','a','vnu'],['4.2.1','웹 애플리케이션 접근성(ARIA)','m','m','axe'],
                ['P','웹 호환성'],
                ['c-webstandard','웹표준 HTML/CSS','n','p','vnu·평가자'],['c-deprecated','Deprecated 태그','n','p','vnu'],
                ['c-browser','브라우저 호환성','n','n','Playwright'],['c-reflow','해상도·확대(200%)','n','p','Playwright']];
              // KIWAC(한국웹접근성인증평가원) WA 인증 심사 24 기준 ↔ 우리 코드 매핑. 없으면 대상 아님(KWCAG 2.2 확장·웹호환성).
              const KIWAC={'1.1.1':'1','1.2.1':'2','1.4.1':'3','1.3.3':'4','1.4.3':'5','1.4.2':'6','1.4.4':'7',
                '2.1.1':'8','2.1.2':'9','2.1.3':'10','2.2.1':'11','2.2.2':'12','2.3.1':'13',
                '2.4.1':'14','2.4.2':'15','2.4.3':'16','3.1.1':'17','3.2.1':'18','1.3.2':'19','1.3.1':'20',
                '3.3.2':'21','3.3.1':'22','4.1.1':'23','4.2.1':'24'};
              const kiwacCell=code=>KIWAC[code]
                ?'<span class="pill ok" title="KWCAG 2.1 기준 24개 심사항목(KIWAC WA인증 심사표) '+KIWAC[code]+'번">KIWAC '+KIWAC[code]+'</span>'
                :(String(code)[0]==='c'
                  ?'<span class="pill na" title="KWCAG 밖 · WQG 웹호환성 확장(심사표 외)">웹 호환성</span>'
                  :'<span class="pill wait" title="KWCAG 2.2(2022, KS X OT0003)에서 추가된 항목 — KWCAG 2.1(=KIWAC 24) 밖">2.2 추가</span>');
              const grpHd=l=>'<tr><td colspan=5 style="background:var(--govsoft);color:var(--navy);font-weight:700;font-size:11.5px;letter-spacing:.03em">'+l+'</td></tr>';
              const itRow=r=>'<tr><td><code style="color:var(--gov);font-weight:700;font-size:12px">'+r[0]+'</code> '+r[1]+'</td><td style="white-space:nowrap">'+kiwacCell(r[0])+'</td><td>'+P[r[2]]+'</td><td>'+P[r[3]]+'</td><td><small style="font-family:ui-monospace,monospace;color:var(--muted);font-weight:700">'+r[4]+'</small></td></tr>';
              // 뷰1: 원칙별(KWCAG 4원칙)
              const rowsPr=M.map(r=>r[0]==='P'?grpHd(r[1]):itRow(r)).join('');
              // 뷰2: 자동·수동별 — 웹접근성(자동→수동) → 웹호환성(자동→수동)
              const theadMap='<tr><th style="min-width:190px">검사항목</th><th style="background:var(--govsoft);white-space:nowrap">KIWAC (KWCAG 2.1)</th><th>카도와</th><th>웹와치</th><th style="background:var(--govsoft)">WQG 엔진</th></tr>';
              // 뷰2: 자동·수동별 — 실제 점검 항목(/api/items) 기준으로 그룹핑(④ 점검 항목 탭과 동일 분류)
              const modeCats=[['accessibility',true,'웹접근성 · 자동 판정'],['accessibility',false,'웹접근성 · 수동 확인 (AI 초안·평가자)'],
                ['compatibility',true,'웹호환성 · 자동 판정'],['compatibility',false,'웹호환성 · 수동 확인']];
              let rowsMd='';
              modeCats.forEach(mc=>{const list=realItems.filter(it=>it.category===mc[0]&&(it.siClassification==='auto')===mc[1]);
                if(!list.length)return;
                rowsMd+=grpHd(mc[2]+' ('+list.length+')')
                  +list.map(it=>'<tr><td><code style="color:var(--gov);font-weight:700;font-size:12px">'+esc(it.code)+'</code> '+esc(it.title)+'</td><td class=st style="white-space:nowrap">'+(AUTOLBL[it.automation]||esc(it.automation))+'</td></tr>').join('');});
              const mapping='<div class=grouphd style="margin-top:22px">3. KWCAG 2.2 항목 매핑 (KWCAG 4원칙 + 웹 호환성)</div>'
                +'<div class="cardsteps"><b>KWCAG 버전</b> — <b>KIWAC WA 품질인증 심사표의 24개 심사기준 = KWCAG 2.1 항목</b>(<span class="pill ok">KIWAC N</span>)입니다. '
                +'<b>2022년 개정 KWCAG 2.2(KS X OT0003)</b>에서 항목이 추가되어 총 <b>33개</b>가 됐고, 그 추가분이 <span class="pill wait">2.2 추가</span>(= 2.1/KIWAC 24 밖). '
                +'<span class="pill na">웹 호환성</span>은 KWCAG 밖 WQG 확장(3엔진·확대 등)입니다. 이 구분·코드가 점검 항목·페이지별 평가·준수율의 <b>기준값</b>입니다.'
                +'<br><small class=st>※ 공식 KWCAG 2.2 추가 항목은 <b>9개</b>이며, 표에서도 <span class="pill wait">2.2 추가</span>가 <b>9개</b>로 일치합니다. 표준상 <b>지침 2.5(포인터 입력)는 KWCAG 2.2 신설</b>이라 2.5.1(포인터 궤적)도 2.2 추가로 분류합니다. 다만 KIWAC 심사표는 심사기준 8을 8-1(키보드=2.1.1)/8-2로 나눠 관리하며, 그 8-2가 개념상 이 2.5.1(단순 누르기 대체)을 가리키는 표기 관행이 있습니다.</small></div>'
                +'<table id="mapPr">'+theadMap+rowsPr+'</table>';
              const ck='<div class=grouphd style="margin-top:22px">4. 자동/수동 체크리스트</div>'
                +'<table><tr><th style="width:50%">'+P.a+' 도구가 자동 판정</th><th>'+P.m+' 사람이 최종 확인</th></tr>'
                +'<tr><td style="vertical-align:top">대체텍스트(1.1.1)·표구성(1.3.1)·<b>명도대비(1.4.3)</b>·건너뛰기(2.4.1)·제목(2.4.2)·링크텍스트(2.4.3)·기본언어(3.1.1)·레이블(3.3.2)·마크업오류(4.1.1)·ARIA(4.2.1) + WQG 전용 크로스브라우저·200%확대</td>'
                +'<td style="vertical-align:top">명확한지시(1.3.3)·색무관인식(1.4.1)·콘텐츠구분(1.4.4)·키보드(2.1.1)·초점표시(2.1.2)·도움정보(3.2.2)·오류정정(3.3.1)·반복입력(3.3.4)·접근가능인증(3.3.3)</td></tr></table>';
              const note='<div class="cardsteps" style="margin-top:18px">표시: <span class="pill ok">자동</span> 도구가 자동 검출 · <span class="pill wait">수동</span> 항목은 있고 사람이 확인 · <span class="pill wait">부분</span> 부분 지원 · <span class="pill na">—</span> 해당 도구 미지원. '
                +'카도와·웹와치 표기는 <b>근사치</b>(제품 버전·설정별로 다름)이므로 참고용이며, 실제 대조는 각 도구를 직접 실행하세요. '
                +'<b>WQG "적합"이 공식 인증 통과를 보장하지 않습니다</b> — 납품·인증은 카도와(또는 발주처 지정 도구)+전문가 수동평가로 최종 확인.</div>';
              const engInfo='<div class=grouphd style="margin-top:22px">WQG 검사 엔진</div>'
                +'<table><tr><th style="width:26%">엔진</th><th>무엇을 검사하나</th></tr>'
                +'<tr><td><b>axe-core</b> <small class=st>Deque</small></td><td>접근성(WCAG→KWCAG) 룰 — 대체텍스트·명도대비·레이블·ARIA·표 구성 등 자동 검출</td></tr>'
                +'<tr><td><b>vnu.jar</b> <small class=st>W3C Nu Html Checker</small></td><td><b>마크업 유효성</b> — <code>validator.w3.org/nu</code> 엔진의 오프라인판. 닫히지 않은 태그·중복 id·잘못된 중첩·필수속성 누락·<b>deprecated(폐기) 태그</b> 등 HTML 문법 오류(항목 4.1.1·c-deprecated). MPA는 서버 원본 HTML, SPA는 JS 실행 후 렌더된 DOM을 검사</td></tr>'
                +'<tr><td><b>Playwright</b> <small class=st>3엔진</small></td><td>chromium·firefox·webkit 스크린샷 <b>픽셀 diff</b>(크로스브라우저) + <b>200% 확대 reflow</b>(가로 오버플로)</td></tr></table>';
              box.innerHTML=intro+std+cmp+engInfo+mapping+ck+note;box.dataset.done='1';
              box.querySelectorAll('.mvtab').forEach(b=>b.onclick=()=>mapView(b.dataset.mv));}
            function mapView(v){const pr=$('#mapPr'),md=$('#mapMd');if(!pr||!md)return;
              pr.style.display=v==='pr'?'':'none';md.style.display=v==='md'?'':'none';
              document.querySelectorAll('.mvtab').forEach(b=>b.classList.toggle('on',b.dataset.mv===v))}

            // ── 증적·이력 탭 (검사 이력 대장 + 제출용 인쇄) ──
            const fmtTsFull=ts=>{const m=String(ts).match(/(\\d{4})(\\d\\d)(\\d\\d)-(\\d\\d)(\\d\\d)/);return m?m[1]+'-'+m[2]+'-'+m[3]+' '+m[4]+':'+m[5]:ts};
            function vpillA(v){return '<span class="pill '+(v==='적합'?'ok':v==='부적합'?'no':'wait')+'">'+esc(v)+'</span>'}
            async function loadAudit(){const {reports}=await api('/api/reports');
              const cover='<div class="tabintro noprint">웹접근성·웹호환성을 <b>주기적으로 점검한 증거(증적)</b> 문서입니다. 표지 정보를 채우고 <b>[증적 인쇄 / PDF]</b>를 누르면 제출용 문서로 저장됩니다. 브라우저 인쇄에서 <b>대상: PDF로 저장</b> 선택.</div>'
                +'<div class="row3 noprint"><div><label>기관/부서</label><input id="ac-org" placeholder="K-water ○○부"></div>'
                +'<div><label>대상 시스템</label><input id="ac-sys" value="결함관리시스템(K-water)"></div>'
                +'<div><label>검사자</label><input id="ac-by" placeholder="성명"></div></div>';
              const std='<div class="cardsteps" style="margin-top:12px"><b>검사 기준</b> — KWCAG 2.2(한국형 웹 콘텐츠 접근성 지침) · 전자정부 웹사이트 품질관리 지침 · 장애인차별금지법. <b>검사 도구</b> — WQG(axe-core + vnu.jar + Playwright 3엔진).</div>';
              const rows=(reports||[]).map(r=>'<tr><td>'+fmtTsFull(r.timestamp)+'</td><td>'+esc(r.displayName||r.site)+'</td><td>'+vpillA(r.status)+'</td>'
                +'<td style="text-align:center">'+(r['적합']||0)+'</td><td style="text-align:center">'+(r['부적합']||0)+'</td>'
                +'<td style="text-align:center">'+(r['해당없음']||0)+'</td><td style="text-align:center">'+(r.pendingTotal||0)+'</td>'
                +'<td class=noprint><a href="'+esc(r.path)+'" target="_blank">리포트</a></td></tr>').join('');
              const table='<div class=grouphd style="margin-top:16px">검사 이력 대장 ('+((reports||[]).length)+'회)</div>'
                +'<table><tr><th>검사 일시</th><th>대상</th><th>종합판정</th><th>적합</th><th>부적합</th><th>해당없음</th><th>수동확인</th><th class=noprint>상세</th></tr>'
                +(rows||'<tr><td colspan=8 class=st>검사 이력이 없습니다. ② 실행 탭에서 검사하거나 주기 자동검사를 기다리세요.</td></tr>')+'</table>';
              const printhd='<div id="printhd" style="display:none"><h2 style="margin:0 0 6px;font-size:18px">웹접근성·웹호환성 점검 증적</h2><div class="st" id="printmeta"></div><hr style="margin:10px 0;border:none;border-top:1px solid var(--line)"></div>';
              const btn='<div class="actbar noprint" style="margin-top:14px"><button class="act" onclick="printAudit()">증적 인쇄 / PDF 저장</button> <span class="st">주기 자동검사가 회차를 자동으로 쌓아 수행 증거가 됩니다.</span></div>';
              $('#audit').innerHTML=printhd+cover+std+table+btn;}
            function printAudit(){const g=id=>{const e=$('#'+id);return e?e.value:''};
              const now=new Date().toISOString().slice(0,16).replace('T',' ');
              $('#printmeta').innerHTML='기관/부서: <b>'+esc(g('ac-org')||'—')+'</b> · 대상: <b>'+esc(g('ac-sys')||'—')+'</b> · 검사자: <b>'+esc(g('ac-by')||'—')+'</b> · 출력일: '+esc(now)
                +'<br>기준: KWCAG 2.2 · 전자정부 웹사이트 품질관리 지침 · 도구: WQG(axe-core+vnu+Playwright 3엔진)';
              $('#printhd').style.display='block';window.print();setTimeout(()=>{$('#printhd').style.display='none'},300);}

            // ── 설정 탭 ──
            const isTest=s=>['defect-kwater','defect-local'].includes(s.name)||(s.displayName||'').includes('테스트')||(s.displayName||'').includes('로컬')||(s.baseUrl||'').includes('localhost')||(s.baseUrl||'').includes('dev-');
            const addForm=`<div class="sys" style="border-style:dashed"><div class="body" style="display:block;border:none">
              <div class="grouphd" style="margin-top:0">시스템 추가</div>
              <div class="row3"><div><label>시스템명(영문)</label><input id="ns-name" placeholder="cloud-cmp"></div>
                <div><label>표시 이름</label><input id="ns-disp" placeholder="클라우드포털-CMP"></div>
                <div><label>유형</label><select id="ns-type"><option value="spa">SPA</option><option value="mpa">MPA</option></select></div></div>
              <button class="act" onclick="addSystem()">＋ 추가</button>
              <div class="hint">추가 후 baseUrl·계정·경로를 채우세요. 이름은 영소문자·숫자·하이픈만.</div></div></div>`;
            function llmCard(c){return `<div class="sys" style="border-color:var(--gov)"><div class="body" style="display:block;border:none">
                <div class="grouphd" style="margin-top:0"><span class="sparkle">✦</span> AI(LLM) 설정 <small class=st style="font-weight:400">수동확인 항목 AI 초안 판정용 (OpenAI 호환)</small></div>
                <div class="row3">
                  <div><label>Base URL</label><input id="llm-url" value="${esc(c.baseUrl||'')}" placeholder="http://localhost:11434/v1 또는 GenOS 주소"></div>
                  <div><label>모델</label><input id="llm-model" value="${esc(c.model||'')}" placeholder="gemma4:latest"></div>
                  <div><label>API 키 ${c.hasKey?'<span class="pill ok">저장됨</span>':'<span class=st>(무인증이면 비움)</span>'}</label>
                    <input id="llm-key" type="password" placeholder="${c.hasKey?'변경 시에만 입력':'선택'}"></div></div>
                <button class="act" onclick="saveLlm()">저장</button>
                <button class="act ghost" onclick="testLlm()">연결 테스트</button>
                <div class="hint">GenOS 서빙 LLM 또는 로컬 올라마(localhost:11434/v1) 등 OpenAI 호환 엔드포인트. 키는 이 PC 로컬에만 저장(git 제외). 비전 모델이면 스크린샷도 근거로 사용.</div>
                <div id="llm-result"></div></div></div>`}
            async function saveLlm(){const key=$('#llm-key').value;
              const body={baseUrl:$('#llm-url').value.trim(),model:$('#llm-model').value.trim()};if(key)body.apiKey=key;
              const r=await api('/api/llm/save','POST',body);toast(r.configured?'AI 설정 저장됨':'저장됨(Base URL 비어있음)')}
            async function testLlm(){const box=$('#llm-result');box.innerHTML='<div class="hbox"><span class=st>설정 저장 후 연결 테스트 중…</span></div>';
              const key=$('#llm-key').value;const body={baseUrl:$('#llm-url').value.trim(),model:$('#llm-model').value.trim()};if(key)body.apiKey=key;
              await api('/api/llm/save','POST',body);
              let r;try{r=await api('/api/llm/test')}catch(e){r={error:String(e)}}
              if(r.configured===false){box.innerHTML='<div class="hbox no">Base URL 을 입력하세요</div>';return}
              if(r.error){box.innerHTML='<div class="hbox no">✗ 연결 실패: '+esc(r.error)+'</div>';return}
              const vok=r.vision==='yes';
              const vline=r.vision?('<div class=st style="margin-top:4px">'+(vok?'<b style="color:#137333">✓ 비전 지원</b> — 스크린샷을 직접 보고 판정 가능':'<b style="color:#b45309">△ 비전 미지원</b> — 텍스트 증거만으로 판정(스크린샷 못 봄). 비전 모델 권장(예: gemma3, qwen2.5vl)')+(r.visionDetail?' <span class=st>· 색판별 응답: "'+esc(r.visionDetail)+'"</span>':'')+'</div>'):'';
              box.innerHTML='<div class="hbox ok">✓ 연결 성공 · 모델 '+esc(r.model)+' · 응답 "'+esc(r.reply)+'"'+vline+'</div>'}
            async function loadConfig(){const {systems}=await api('/api/systems');
              const test=systems.filter(isTest),real=systems.filter(s=>!isTest(s));
              let html='<div class="tabintro">설정된 <b>검사 대상</b>의 baseUrl·계정·검사 경로를 편집하는 화면입니다. '
                +'각 시스템 카드를 펼쳐 채운 뒤 <b>[설정 저장]</b> → <b>[설정 확인(헬스체크)]</b>로 검증하세요. '
                +'새 시스템 추가는 <b>＋ 시스템 관리</b> 탭, AI 판정 설정은 <b>✦ AI 설정</b> 탭에서 합니다.</div>'
                +'<div class="autolegend"><span class="autoval-chip">자동</span> 표시된 값은 <b>유형 자동 판별·로그인 필드 자동 감지·검사 경로 자동 수집</b>으로 채워진 것입니다 — 내용을 확인한 뒤 <b>[설정 저장]</b>하세요. 직접 수정하면 표시가 사라집니다.</div>';
              if(test.length)html+='<div class=grouphd>테스트 대상</div>'+test.map(sysCard).join('');
              if(real.length)html+='<div class=grouphd>폐쇄망 대상 시스템</div>'+real.map(sysCard).join('');
              if(!systems.length)html+='<div class="cardsteps"><b>＋ 시스템 관리</b> 탭에서 검사 대상 시스템을 먼저 추가하세요.</div>';
              $('#config').innerHTML=html;
              systems.forEach(s=>{const el=document.getElementById('sys-'+s.name);
                el.querySelector('.hd').onclick=()=>el.classList.toggle('open');
                el.querySelectorAll('.autoval').forEach(attachAutoClear);
                const setAuthCls=t=>{const b=el.querySelector('.body');
                  b.classList.toggle('auth-form',t==='form'||t==='sso');
                  b.classList.toggle('auth-storage',t==='storage');};
                setAuthCls((s.authType==='form'||s.authType==='sso')?s.authType:'storage');
                el.querySelector('[name=authType]').onchange=e=>setAuthCls(e.target.value);
                el.querySelector('.capsess').onclick=()=>captureSession(s.name,el);
                el.querySelector('.chksess').onclick=()=>checkSession(s.name,el);
                el.querySelector('.save').onclick=()=>saveSys(s.name,el);
                el.querySelector('.savecred').onclick=()=>saveCred(s.name,el);
                el.querySelector('.health').onclick=()=>healthCheck(s.name);
                el.querySelector('.detect').onclick=()=>detectSel(s.name,el);
                el.querySelector('.dtype').onclick=()=>detectType(s.name,el);
                el.querySelector('.discover').onclick=()=>discoverUrls(s.name,el);
                initPaths(el);
                const np=el.querySelector('.newpath');
                const addNp=()=>{let v=np.value.trim();if(!v){toast('경로 입력');return}
                  if(!v.startsWith('/')&&!/^https?:/i.test(v))v='/'+v;addPath(el,v,'',true);np.value='';np.focus()};
                el.querySelector('.addpath').onclick=addNp;
                np.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();addNp()}};
                el.querySelector('.gorun').onclick=()=>goRun(s.name);
                el.querySelector('.del').onclick=()=>delSystem(s.name,s.displayName);
              })}
            // ── 시스템 관리 탭 (추가 + 등록 목록) ──
            async function loadSysCfg(){const {systems}=await api('/api/systems');
              let html='<div class="tabintro">검사 대상 <b>시스템을 추가</b>합니다. 추가한 뒤 <b>① 설정</b> 탭에서 baseUrl·계정·검사 경로를 채우세요.</div>'+addForm;
              html+='<div class=grouphd>등록된 시스템 ('+systems.length+')</div>';
              html+='<table><tr><th>시스템명</th><th>표시 이름</th><th>유형</th><th>baseUrl</th><th></th></tr>'
                +systems.map(s=>'<tr><td><code>'+esc(s.name)+'</code></td><td>'+esc(s.displayName||'')+'</td><td>'+esc(s.type||'')
                  +'</td><td class=st style="max-width:220px;overflow:hidden;text-overflow:ellipsis">'+esc(s.baseUrl||'(미설정)')
                  +'</td><td><button class="act ghost" onclick="delSystem(\\''+esc(s.name)+'\\',\\''+esc(s.displayName||s.name)+'\\')">삭제</button></td></tr>').join('')
                +'</table>';
              $('#syscfg').innerHTML=html;}
            // ── AI 설정 탭 ──
            async function loadAiCfg(){const llm=await api('/api/llm');
              $('#aicfg').innerHTML='<div class="tabintro">수동확인 항목의 <b>AI 초안 판정</b>에 쓸 LLM을 설정합니다(OpenAI 호환). '
                +'비전 모델이면 화면 스크린샷을 직접 보고 판정합니다. <b>연결 테스트</b>로 비전 지원 여부까지 확인하세요.</div>'+llmCard(llm);}
            async function addSystem(){const name=$('#ns-name').value.trim();
              if(!name){toast('시스템명 입력');return}
              const r=await api('/api/systems/add','POST',{name,displayName:$('#ns-disp').value.trim()||name,type:$('#ns-type').value});
              if(r.error){toast(r.error);return}toast('추가됨: '+name);loadSysCfg();loadConfig()}
            async function delSystem(name,disp){if(!confirm('시스템 삭제: '+(disp||name)+'\\n설정과 저장된 계정이 삭제됩니다. 계속?'))return;
              await api('/api/systems/delete/'+name,'POST',{});toast('삭제됨: '+name);loadSysCfg();loadConfig()}
            // 자동 판별/자동 수집으로 채워진 입력값을 청록색으로 표시(사용자가 편집하면 해제)
            function attachAutoClear(inp){if(!inp)return;
              const clr=()=>{inp.classList.remove('autoval');inp.removeEventListener('input',clr);inp.removeEventListener('change',clr)};
              inp.addEventListener('input',clr);inp.addEventListener('change',clr)}
            function markAuto(inp){if(!inp)return;inp.classList.add('autoval');attachAutoClear(inp)}
            async function detectSel(name,el){const g=n=>el.querySelector('[name='+n+']');
              const baseUrl=g('baseUrl').value.trim(),loginPath=g('loginPath').value.trim()||'/login';
              const box=el.querySelector('.detectbox');box.innerHTML=busy('로그인 페이지 여는 중…','아이디·비밀번호·제출 버튼 필드 감지');
              const r=await api('/api/detect-selectors','POST',{baseUrl,loginPath});
              if(r.error){box.innerHTML='<div class="hbox no">'+esc(r.error)+'</div>';return}
              // 리다이렉트 감지 — 입력한 로그인 경로가 실제 경로로 튕기면 경고 + loginPath 자동 교정
              let warn='';
              if(r.redirected&&r.finalPath){
                warn='<div class="hbox no">⚠ 입력한 로그인 경로 <code>'+esc(loginPath)+'</code> 는 실제 <code>'+esc(r.finalPath)
                  +'</code> 로 리다이렉트됩니다 — 아래 셀렉터는 <b>리다이렉트된 실제 로그인 페이지</b>에서 감지된 값입니다. '
                  +'로그인 경로를 <b>'+esc(r.finalPath)+'</b> 로 바로잡았습니다.</div>';
                g('loginPath').value=r.finalPath;markAuto(g('loginPath'));
              }
              if(r.idSelector){g('idSelector').value=r.idSelector;markAuto(g('idSelector'))}
              if(r.pwSelector){g('pwSelector').value=r.pwSelector;markAuto(g('pwSelector'))}
              if(r.submitSelector){g('submitSelector').value=r.submitSelector;markAuto(g('submitSelector'))}
              const found=[r.idSelector&&'아이디',r.pwSelector&&'비번',r.submitSelector&&'제출'].filter(Boolean);
              box.innerHTML=warn+'<div class="hbox '+(found.length?'ok':'no')+'">'+(found.length?'✓ 필드 감지: '+found.join('·')+' — 실제 로그인 성공은 <b>설정 확인(헬스체크)</b>로 검증하세요':'✗ 로그인 필드를 못 찾음 — 수동 입력')+'</div>'}
            async function detectType(name,el){const note=el.querySelector('.dtnote');
              const baseUrl=el.querySelector('[name=baseUrl]').value.trim();
              if(!baseUrl){toast('baseUrl 입력');return}
              note.innerHTML=busy('유형 확인 중…','로그인 필요 시 로그인 후 실제 검사 경로를 엽니다');
              // name 을 보내면 저장된 로그인·검사경로로 판정(로그인 뒤 SPA 대응)
              let r;try{r=await api('/api/detect-type','POST',{name,baseUrl})}catch(e){r={error:String(e)}}
              if(r.error){note.innerHTML='<div class="hbox no">✗ '+esc(r.error)+'</div>';return}
              if(r.type==='unknown'){ // 로그인 벽에 막힘 — 유형을 덮어쓰지 않음
                note.innerHTML='<div class="hbox no">⚠ '+esc(r.reason)+'</div>';return}
              el.querySelector('[name=type]').value=r.type;markAuto(el.querySelector('[name=type]'));
              note.innerHTML='<div class="hbox ok">✓ <b>'+esc(r.type.toUpperCase())+'</b> 로 설정 — '+esc(r.reason)+'<div class=hint>확인 후 [설정 저장]</div></div>'}
            async function healthCheck(name){const box=document.getElementById('health-'+name);
              box.innerHTML=busy('설정 확인 중…','브라우저 기동·로그인 점검 — 최대 30초');
              try{const r=await api('/api/health/'+name,'POST',{});
                const steps=(r.steps||[]).map(s=>`<div class="sstep ${s.ok?'ok':'no'}"><span class="dot">${s.ok?'✓':'✗'}</span>`
                  +`<div><div class="snm">${esc(s.name)}</div>${s.detail?`<div class="sdt">${esc(s.detail)}</div>`:''}</div></div>`).join('');
                box.innerHTML=`<div class="rcard ${r.ok?'ok':'no'}"><div class="rhd">`
                  +`<span class="badge">${r.ok?'정상':'실패'}</span><span>${r.ok?'설정 확인 완료':'설정 확인 실패'}</span></div>`
                  +`<div class="rbody">${steps}</div></div>`;
              }catch(e){box.innerHTML='<div class="rcard no"><div class="rhd"><span class="badge">오류</span><span>확인 중 오류</span></div><div class="rnote">'+esc(e)+'</div></div>'}}
            """;
    private static final String HTML_B = """
            function sysCard(s){const cfg=s.configured;const A=s.auto||[];const ac=n=>A.includes(n)?' autoval':'';
              return `<div class="sys" id="sys-${s.name}"><div class="hd">
                <div class="nm">${esc(s.displayName)}<small>${s.name}</small></div>
                <div>${s.hasCredentials?'<span class=pill ok>계정 저장됨</span>':(s.authType==='form'?'<span class=pill wait>계정 필요</span>':'')}
                  ${cfg?'<span class=pill ok>설정됨</span>':'<span class=pill no>미설정</span>'}</div></div>
                <div class="body">
                <div class="cardsteps"><b>진행 순서</b> ① 유형·baseUrl → ② 로그인 필드·계정 → ③ 검사 경로 → ④ 설정 저장 → ⑤ 설정 확인(헬스체크)</div>
                <div class="row"><div><label>유형</label><select name="type" class="${ac('type').trim()}">
                  <option value="spa" ${s.type==='spa'?'selected':''}>SPA (React/Vue)</option>
                  <option value="mpa" ${s.type==='mpa'?'selected':''}>MPA (서버렌더링)</option></select></div>
                  <div><label>로그인 방식</label><select name="authType">
                  <option value="storage" ${s.authType!=='form'&&s.authType!=='sso'?'selected':''}>세션 재사용 (수동 로그인)</option>
                  <option value="form" ${s.authType==='form'?'selected':''}>폼 로그인</option>
                  <option value="sso" ${s.authType==='sso'?'selected':''}>SSO (Keycloak/OIDC)</option>
                  <option value="none">없음</option></select></div></div>
                <label>baseUrl</label><input name="baseUrl" value="${esc(s.baseUrl)}" placeholder="http://10.0.x.x">
                <button class="act ghost dtype">🔎 유형 자동 판별 (MPA/SPA)</button>
                <div class="hint">baseUrl 입력 후 누르면 서버 응답으로 MPA/SPA 를 추정해 위 '유형'을 채웁니다.</div>
                <div class="dtnote" id="dtnote-${s.name}"></div>
                <div class="storage-only">
                  <div class="cardsteps"><b>세션 재사용</b> — OASIS SSO·포탈처럼 자동 폼 입력이 어려운 다단계 로그인은,
                    한 번 <b>직접 로그인</b>한 세션(쿠키·토큰)을 저장해 검사에 재사용합니다.
                    baseUrl 을 채운 뒤 <b>① 세션 로그인(수동)</b> → 열리는 브라우저에서 로그인 완료 → 자동 저장,
                    이후 <b>② 세션 유효성 확인</b>으로 살아있는지 점검하세요. 세션은 이 PC 로컬에만 저장(git·리포트 제외)됩니다.</div>
                  <div class="actbar"><button class="act ghost capsess">① 세션 로그인(수동)</button>
                    <button class="act ghost chksess">② 세션 유효성 확인</button></div>
                  <div class="sessbox" id="sess-${s.name}">${s.hasSession?'<div class="hbox ok">저장된 세션이 있습니다 — [세션 유효성 확인]으로 점검하세요.</div>':'<div class=st>저장된 세션이 없습니다. [세션 로그인]으로 저장하세요.</div>'}</div>
                </div>
                <div class="auth-only">
                  <div class="row3"><div><label>로그인 경로</label><input name="loginPath" value="${esc(s.loginPath||'/login')}"></div>
                    <div><label>아이디 셀렉터</label><input name="idSelector" class="${ac('idSelector').trim()}" value="${esc(s.idSelector||'')}" placeholder="input[name=username]"></div>
                    <div><label>비번 셀렉터</label><input name="pwSelector" class="${ac('pwSelector').trim()}" value="${esc(s.pwSelector||'')}" placeholder="input[name=password]"></div></div>
                  <label>제출 버튼 셀렉터</label><input name="submitSelector" class="${ac('submitSelector').trim()}" value="${esc(s.submitSelector||'')}" placeholder="button[type=submit]">
                  <button class="act ghost detect">🔍 로그인 필드 자동 감지</button>
                  <div class="hint">baseUrl·로그인 경로를 채운 뒤 누르면 로그인 페이지를 열어 셀렉터를 채웁니다.
                    <b>SSO(Keycloak/OIDC)</b>는 로그인 경로를 보호 페이지(예 <code>/</code>)로 두면 IdP로 리다이렉트되며, 셀렉터·계정은 <b>IdP 로그인 폼 기준</b>입니다(비우면 Keycloak 기본 <code>#username</code>·<code>#password</code>·<code>#kc-login</code> 사용).</div>
                  <div class="detectbox"></div>
                  <div class="row"><div><label>계정 ID ${s.hasCredentials?'<span class="pill ok">저장됨</span>':''}</label>
                    <input name="cid" value="${esc(s.credId||'')}" placeholder="아이디"></div>
                    <div><label>계정 PW ${s.hasCredentials?'<span class="pill ok">저장됨</span>':''}</label>
                    <div class="pwwrap"><input name="cpw" type="password" value="" placeholder="${s.hasCredentials?'저장됨 — 변경할 때만 새로 입력':'비밀번호'}">
                      <button type="button" class="eye" onclick="togglePw(this)" aria-label="비밀번호 표시/숨김" title="표시/숨김">${EYE_OPEN}</button></div></div></div>
                  <button class="act ghost savecred">계정만 저장</button>
                  <div class="hint">계정은 이 PC 로컬 파일에만 저장됩니다 (git·리포트 제외).</div>
                </div>
                <div class="pathhd"><label style="margin:0">검사 경로 <span class="pathcount"></span></label>
                  <span class="hint" style="margin:0">SPA는 우측에 대기 셀렉터 지정 가능</span></div>
                <div class="pathlist"></div>
                <textarea name="urls" style="display:none">${esc((s.urls||[]).join('\\n'))}</textarea>
                <div class="pathadd"><input class="newpath" placeholder="/경로 입력 후 Enter 또는 [추가]">
                  <button class="act ghost addpath" type="button">＋ 추가</button></div>
                <button class="act ghost discover">🧭 검사 경로 자동 수집</button>
                <div class="hint">로그인(필요 시) 후 내부 링크를 훑어 후보 경로를 제시합니다. 원하는 항목만 골라 위 목록에 추가하세요.</div>
                <div class="discovered" id="disc-${s.name}"></div>
                <div class="actbar"><button class="act save">설정 저장</button>
                <button class="act ghost health">설정 확인(헬스체크)</button>
                <button class="act gorun" style="background:var(--ok)">② 실행으로 이동 ▶</button></div>
                <div class="health" id="health-${s.name}"></div>
                <div class="danger"><button class="act ghost del">시스템 삭제</button>
                  <span class="hint" style="margin-left:8px">설정과 저장된 계정이 삭제됩니다.</span></div></div></div>`}
            function parseUrls(t){return t.split('\\n').map(l=>l.trim()).filter(Boolean).map(l=>{
              const[p,rs]=l.split('|').map(x=>x.trim());return{path:p,readySelector:rs||''}})}
            // ── 검사 경로 행 목록: 숨은 textarea[name=urls]가 저장·수집의 원본, 행 UI는 그 편집기 ──
            function pathRowHtml(path,sel){return '<div class="pathrow">'
              +'<input class="pp" spellcheck="false" value="'+esc(path)+'">'
              +'<input class="ps" spellcheck="false" placeholder="대기 셀렉터 (선택)" value="'+esc(sel||'')+'">'
              +'<button class="rm" type="button" title="삭제">✕</button></div>'}
            function bindPathRow(el,row){row.querySelector('.pp').oninput=()=>syncPaths(el);
              row.querySelector('.ps').oninput=()=>syncPaths(el);
              row.querySelector('.rm').onclick=()=>{row.remove();syncPaths(el)}}
            function updatePathCount(el,n){const c=el.querySelector('.pathcount');if(c)c.textContent='('+n+')'}
            function initPaths(el){const list=el.querySelector('.pathlist');if(!list)return;
              const items=parseUrls(el.querySelector('[name=urls]').value);
              list.innerHTML=items.length?items.map(u=>pathRowHtml(u.path,u.readySelector)).join('')
                :'<div class="pathempty">등록된 검사 경로가 없습니다 — 아래에서 추가하거나 [검사 경로 자동 수집]을 이용하세요.</div>';
              list.querySelectorAll('.pathrow').forEach(r=>bindPathRow(el,r));
              updatePathCount(el,items.length)}
            function syncPaths(el){const rows=[...el.querySelectorAll('.pathlist .pathrow')];
              const lines=rows.map(r=>{const p=r.querySelector('.pp').value.trim();if(!p)return '';
                const s=r.querySelector('.ps').value.trim();return s?p+' | '+s:p}).filter(Boolean);
              el.querySelector('[name=urls]').value=lines.join('\\n');updatePathCount(el,lines.length)}
            function addPath(el,path,sel,flash){const list=el.querySelector('.pathlist');
              const empty=list.querySelector('.pathempty');if(empty)list.innerHTML='';
              const tmp=document.createElement('div');tmp.innerHTML=pathRowHtml(path,sel||'');
              const row=tmp.firstChild;list.appendChild(row);bindPathRow(el,row);
              if(flash){row.classList.add('justadded');setTimeout(()=>row.classList.remove('justadded'),2000)}
              syncPaths(el);return row}
            async function saveSys(name,el){const g=n=>el.querySelector('[name='+n+']').value;
              const auto=[...el.querySelectorAll('.autoval')].map(x=>x.getAttribute('name')).filter(Boolean);
              await api('/api/save/'+name,'POST',{type:g('type'),authType:g('authType'),baseUrl:g('baseUrl'),
                loginPath:g('loginPath'),idSelector:g('idSelector'),pwSelector:g('pwSelector'),
                submitSelector:g('submitSelector'),urls:parseUrls(g('urls')),auto});
              const cid=g('cid'),cpw=g('cpw');if(cid&&cpw){await api('/api/credentials/'+name,'POST',{id:cid,pw:cpw});markCredSaved(el)}
              // 카드를 다시 그리지 않고 그대로 유지 — 설정됨 배지만 즉시 갱신
              const cfgOk=g('baseUrl')&&!g('baseUrl').includes('TODO');
              const badge=el.querySelector('.hd .pill.no');if(cfgOk&&badge){badge.className='pill ok';badge.textContent='설정됨'}
              toast('저장됨: '+name)}
            function markCredSaved(el){const cp=el.querySelector('.hd .pill.wait');
              if(cp){cp.className='pill ok';cp.textContent='계정 저장됨'}}
            async function saveCred(name,el){const cid=el.querySelector('[name=cid]').value,
              cpw=el.querySelector('[name=cpw]').value;if(!cid||!cpw){toast('ID/PW 입력');return}
              await api('/api/credentials/'+name,'POST',{id:cid,pw:cpw});markCredSaved(el);toast('계정 저장됨')}
            // 세션 로그인(수동) — 서버가 헤드풀 브라우저를 띄우고, 사용자가 로그인 완료하면 세션 저장(최대 3분)
            async function captureSession(name,el){const box=el.querySelector('.sessbox');
              const base=el.querySelector('[name=baseUrl]').value.trim();
              if(!base||base.includes('TODO')){toast('baseUrl 먼저 입력');return}
              const lpEl=el.querySelector('[name=loginPath]');const lp=lpEl?lpEl.value.trim():'';
              box.innerHTML=busy('브라우저를 띄웠습니다…','열린 창에서 로그인을 완료하세요. 폼이 사라지면 자동 저장(최대 3분)');
              let r;try{r=await api('/api/capture-session/'+name,'POST',{baseUrl:base,loginPath:lp})}catch(e){r={ok:false,detail:String(e)}}
              box.innerHTML=`<div class="hbox ${r.ok?'ok':'no'}">${r.ok?'✓ ':'✗ '}${esc(r.detail||'')}</div>`}
            // 세션 유효성 확인 — 저장 세션으로 보호 페이지 접근 시 로그인으로 안 튕기면 유효
            async function checkSession(name,el){const box=el.querySelector('.sessbox');
              const base=el.querySelector('[name=baseUrl]').value.trim();
              box.innerHTML=busy('세션 확인 중…','저장 세션으로 보호 페이지 접근 점검');
              let r;try{r=await api('/api/session-check/'+name,'POST',{baseUrl:base})}catch(e){r={valid:false,detail:String(e)}}
              box.innerHTML=`<div class="hbox ${r.valid?'ok':'no'}">${r.valid?'✓ 유효':'✗ 무효/만료'} — ${esc(r.detail||'')}${r.valid?'':'<div class=hint>[세션 로그인(수동)]으로 다시 저장하세요.</div>'}</div>`}
            // 검사 경로 자동 수집 — 백그라운드 잡 시작 → /api/status 폴링으로 진행 표시 → 완료 시 후보 제시
            async function discoverUrls(name,el){const box=el.querySelector('.discovered');
              box.innerHTML=busy('경로 수집 시작…','로그인/접속 후 내부 링크를 크롤합니다');
              try{await api('/api/discover-paths/'+name,'POST',{})}catch(e){box.innerHTML='<div class="hbox no">✗ 시작 실패 — '+esc(String(e))+'</div>';return}
              const key='discover:'+name;
              let msg='',paths=null,err=null;
              for(let t=0;t<150;t++){
                await new Promise(r=>setTimeout(r,1500));
                let s;try{s=await api('/api/status')}catch(e){continue}
                const j=(s.status||{})[key]; if(!j)continue;
                if(j.state==='RUNNING'){msg=j.message||'';box.innerHTML=busy('경로 수집 중…',msg);}
                else if(j.state==='DONE'){paths=(j.message||'').split('\\n').map(x=>x.trim()).filter(Boolean);break;}
                else if(j.state==='ERROR'){err=j.message||'실패';break;}
              }
              if(err){box.innerHTML='<div class="hbox no">✗ 수집 실패 — '+esc(err)+'<div class=hint>로그인/세션 설정을 먼저 확인하세요(SSO는 세션 재사용).</div></div>';return}
              if(paths===null){box.innerHTML='<div class="hbox no">✗ 시간 초과 — 다시 시도하세요.</div>';return}
              const cur=new Set(parseUrls(el.querySelector('[name=urls]').value).map(u=>u.path));
              const cand=paths.filter(p=>!cur.has(p));
              if(!cand.length){box.innerHTML='<div class="hbox ok">수집 '+paths.length+'건 — 새로 추가할 경로가 없습니다(이미 목록에 있음).</div>';return}
              box.innerHTML=`<div class="hbox ok"><b>후보 경로 ${cand.length}건</b> — 추가할 항목을 선택하세요.
                <div class="candtools"><button class="linkbtn" onclick="candAll('${name}',true)">전체 선택</button>
                  <button class="linkbtn" onclick="candAll('${name}',false)">전체 해제</button></div>
                <div class="candlist">${cand.map(p=>`<label class=cand><input type=checkbox checked value="${esc(p)}"> ${esc(p)}</label>`).join('')}</div>
                <button class="act" onclick="addDiscovered('${name}')">선택 경로 추가</button>
                <button class="act ghost" onclick="document.getElementById('disc-${name}').innerHTML=''">닫기</button></div>`}
            function togglePw(btn){const inp=btn.parentNode.querySelector('input');
              const show=inp.type==='password';inp.type=show?'text':'password';btn.innerHTML=show?EYE_OFF:EYE_OPEN}
            function candAll(name,v){document.querySelectorAll('#disc-'+name+' .cand input').forEach(c=>c.checked=v)}
            function addDiscovered(name){const el=document.getElementById('sys-'+name);
              const chosen=[...el.querySelectorAll('.discovered input:checked')].map(c=>c.value);
              if(!chosen.length){toast('선택된 경로 없음');return}
              const cur=new Set(parseUrls(el.querySelector('[name=urls]').value).map(u=>u.path));
              let added=0;chosen.forEach(p=>{if(!cur.has(p)){addPath(el,p,'',true);cur.add(p);added++}});
              el.querySelector('.discovered').innerHTML='';
              toast(added+'개 추가됨 — [설정 저장]을 눌러 반영하세요')}

            // 설정 카드 → 실행 탭으로 바로 이동(해당 시스템 행으로 스크롤·강조)
            function goRun(name){document.querySelector('nav button[data-tab=run]').click();
              let n=0;const iv=setInterval(()=>{const b=document.getElementById('runbtn-'+name);
                if(b||n++>25){clearInterval(iv);if(b){const tr=b.closest('tr');
                  tr.scrollIntoView({behavior:'smooth',block:'center'});
                  tr.classList.remove('flash');void tr.offsetWidth;tr.classList.add('flash')}}},100)}
            // ── 실행 탭 ──
            let poll;
            let runConfigured=[];
            async function loadRun(){const {systems}=await api('/api/systems');
              runConfigured=systems.filter(s=>s.configured).map(s=>s.name);
              $('#run').innerHTML=`<div class="tabintro">설정이 끝난 시스템을 검사합니다. 개별 <b>[검사]</b> 또는 <b>[전체 검사]</b>를 누르고,
                  상태가 <b>완료</b>가 되면 <b>③ 결과·검수</b> 탭에서 리포트와 판정을 확인하세요. (검사에는 수 분이 걸릴 수 있습니다)</div>
                <div class=filterbar><button class="act run" id="runall-btn" onclick="runAll()" ${runConfigured.length?'':'disabled'}>설정된 시스템 전체 검사 (${runConfigured.length})</button></div>`+
                '<table><thead><tr><th>시스템</th><th>설정</th><th>상태</th><th></th></tr></thead><tbody>'+
                systems.map(s=>`<tr><td>${esc(s.displayName)}<br><small class=st>${s.name}</small></td>
                  <td>${s.configured?'<span class=pill ok>준비</span>':'<span class=pill no>미설정</span>'}
                    ${s.authType==='form'&&!s.hasCredentials?'<span class=pill wait>계정 필요</span>':''}</td>
                  <td class="statcell" id="stat-${s.name}">-</td>
                  <td><button class="act run" id="runbtn-${s.name}" ${s.configured?'':'disabled'} onclick="runSys('${s.name}')">${s.configured?'검사':'미설정'}</button></td></tr>`).join('')+
                '</tbody></table>';refreshStatus();
              clearInterval(poll);poll=setInterval(refreshStatus,2000)}
            function setRunBtn(name,running){const b=document.getElementById('runbtn-'+name);
              if(!b)return;b.disabled=running;b.textContent=running?'검사 중…':'검사'}
            async function runSys(name){setRunBtn(name,true);
              await api('/api/run/'+name,'POST',{});toast('검사 시작: '+name);refreshStatus()}
            async function runAll(){const rab=document.getElementById('runall-btn');if(rab)rab.disabled=true;
              runConfigured.forEach(n=>setRunBtn(n,true));
              for(const n of runConfigured){await api('/api/run/'+n,'POST',{})}
              toast('전체 검사 시작 ('+runConfigured.length+')');refreshStatus()}
            async function refreshStatus(){const {status:raw}=await api('/api/status');
              // 경로 수집(discover:*) 잡은 실행 탭 상태에서 제외
              const status={};Object.entries(raw).forEach(([k,v])=>{if(!k.startsWith('discover:'))status[k]=v;});
              const anyRunning=Object.values(status).some(st=>st.state==='RUNNING');
              const rab=document.getElementById('runall-btn');
              if(rab)rab.disabled=anyRunning||runConfigured.length===0;
              Object.entries(status).forEach(([site,st])=>{
                setRunBtn(site,st.state==='RUNNING');            // 실행 중인 시스템 검사 버튼 잠금
                const el=document.getElementById('stat-'+site);if(!el)return;
                if(st.state==='RUNNING')el.innerHTML='<span class=pill wait>검사 중</span> <span class=st>'+esc(st.message)+'</span>';
                else if(st.state==='DONE'){const rel=st.message.replace(/^\\.?\\//,'');const ts=rel.split('/')[2];
                  el.innerHTML='<span class=pill ok>완료</span> <a href="/'+rel+'" target="_blank">리포트 ↗</a> '+
                    '<button class="linkbtn" onclick="openAssessment(\\''+site+'\\',\\''+ts+'\\')">검수</button> <span id="verdict-'+site+'" class=st></span>';
                  loadVerdict(site,ts)}
                else if(st.state==='ERROR')el.innerHTML='<span class=pill no>실패</span> <span class=st>'+esc(st.message)+'</span>'})}
            async function loadVerdict(site,ts){try{
              const d=await api('/api/assessment?site='+encodeURIComponent(site)+'&ts='+encodeURIComponent(ts));
              const el=document.getElementById('verdict-'+site);if(!el)return;
              const sc=d.compliance.status==='적합'?'ok':(d.compliance.status==='부적합'?'no':'wait');
              el.innerHTML='· <span class="pill '+sc+'">'+esc(d.compliance.status)+'</span> 미확정 '+d.compliance.pendingTotal;
            }catch(e){}}

            // ── 점검 항목 관리 탭 ──
            const AUTOLBL={auto:'자동',na:'해당없음(기본)',screen:'자동 스크리닝',manual:'수동확인'};
            // 공통 분류: KWCAG 4원칙(코드 1/2/3/4) + 웹 호환성(c-*). 그룹·코드 순으로 정렬.
            const PRIN=[['1','인식의 용이성 (Perceivable)'],['2','운용의 용이성 (Operable)'],
              ['3','이해의 용이성 (Understandable)'],['4','견고성 (Robustness)'],['c','웹 호환성']];
            const gkey=code=>String(code)[0]==='c'?'c':(String(code)[0]>='1'&&String(code)[0]<='4'?String(code)[0]:'4');
            const csort=(a,b)=>{const na=String(a).split('.').map(Number),nb=String(b).split('.').map(Number);
              for(let i=0;i<Math.max(na.length,nb.length);i++){const x=na[i]||0,y=nb[i]||0;if(x!==y)return x-y}return 0};
            async function loadItems(){const {items,excludedCount}=await api('/api/items');
              let html=`<div class="tabintro">검사할 <b>KWCAG 점검 항목</b>을 관리합니다. 체크를 <b>끄면</b> 그 항목은
                검사·집계·리포트에서 <b>제외</b>됩니다(전체 시스템 공통, 다음 검사부터 적용). 현재 제외 <b id="exCount">${excludedCount}</b>건.
                <br><span class=st>KWCAG 4원칙 + 웹 호환성 순으로 분류합니다.</span></div>
                <div class=filterbar><button class="act" onclick="saveItems()">제외 설정 저장</button>
                  <button class="act ghost" onclick="allItems(true)">전체 선택</button>
                  <button class="act ghost" onclick="allItems(false)">전체 해제</button></div>`;
              PRIN.forEach(([g,label])=>{let list=items.filter(it=>gkey(it.code)===g);
                if(g!=='c')list=list.slice().sort((a,b)=>csort(a.code,b.code));
                if(!list.length)return;
                html+=`<div class=grouphd>${label} (${list.length})</div><table><thead><tr><th style="width:1px">검사</th><th>코드</th><th>항목</th><th>판정 방식</th></tr></thead><tbody>`+
                  list.map(it=>`<tr><td style="width:1px"><input type=checkbox class=itemchk data-code="${esc(it.code)}" ${it.excluded?'':'checked'}></td>
                    <td><code>${esc(it.code)}</code></td><td>${esc(it.title)}</td>
                    <td class=st>${AUTOLBL[it.automation]||it.automation}</td></tr>`).join('')+'</tbody></table>';
              });
              $('#items').innerHTML=html}
            function allItems(v){document.querySelectorAll('.itemchk').forEach(c=>c.checked=v)}
            async function saveItems(){const excluded=[...document.querySelectorAll('.itemchk:not(:checked)')].map(c=>c.dataset.code);
              const r=await api('/api/items/exclude','POST',{excluded});
              const el=document.getElementById('exCount');if(el)el.textContent=r.excludedCount;
              toast(excluded.length?('제외 '+excluded.length+'건 저장 — 다음 검사부터 적용'):'제외 없음으로 저장')}

            // ── 결과 탭 (플랫폼별 그룹) ──
            const WD=['일','월','화','수','목','금','토'];
            function fmtTs(s){const m=/^(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})$/.exec(s||'');
              if(!m)return esc(s);const d=new Date(+m[1],+m[2]-1,+m[3],+m[4],+m[5]);
              return `${m[1]}-${m[2]}-${m[3]} (${WD[d.getDay()]}) ${m[4]}:${m[5]}`}
            function statusPill(r){if(!r||!r.status)return '<span class=st>-</span>';
              const sc=r.status==='적합'?'ok':(r.status==='부적합'?'no':'wait');
              return `<span class="pill ${sc}">${esc(r.status)}</span>${r.pendingTotal>0?' <span class=st>미확정 '+r.pendingTotal+'</span>':''}`}
            function itemNums(r){if(!r.status)return '<span class=st>-</span>';
              return `<span class=st style="white-space:nowrap"><b style="color:var(--navy)">전체 ${r.total??0}</b> · 적합 <b>${r.적합??0}</b> · 부적합 <b>${r.부적합??0}</b> · 수동확인 <b>${r.pendingTotal??0}</b> · 해당없음 <b>${r.해당없음??0}</b></span>`}
            function runRow(r,newest,gi){return `<tr><td><input type=checkbox class=cmpchk data-g="${gi}" data-ts="${esc(r.timestamp)}" onchange="onSel(${gi})"></td>
              <td>${fmtTs(r.timestamp)}${newest?'<span class=newtag>최신</span>':''}</td>
              <td>${statusPill(r)}</td>
              <td>${itemNums(r)}</td>
              <td><a href="${esc(r.path)}" target="_blank">리포트 ↗</a></td>
              <td><button class="act ${r.pendingTotal>0?'':'ghost'}" style="margin-top:0" onclick="openAssessment('${esc(r.site)}','${esc(r.timestamp)}')">검수${r.pendingTotal>0?' ('+r.pendingTotal+')':''}</button></td></tr>`}
            // 체크박스 선택 → 비교(정확히 2건)·선택 삭제(1건 이상) 버튼 상태 갱신
            function onSel(gi){const chk=[...document.querySelectorAll('.cmpchk[data-g="'+gi+'"]:checked')];
              const cb=document.getElementById('cmpbtn-'+gi);
              if(cb){cb.disabled=chk.length!==2;cb.textContent=chk.length===2?'선택한 2건 비교 ▶':('비교 ('+chk.length+'/2)')}
              const db=document.getElementById('delbtn-'+gi);
              if(db){db.disabled=chk.length===0;db.textContent='선택 삭제 ('+chk.length+')'}}
            async function delSelected(gi){const chk=[...document.querySelectorAll('.cmpchk[data-g="'+gi+'"]:checked')];
              if(!chk.length){toast('선택된 회차 없음');return}
              const site=document.getElementById('delbtn-'+gi).dataset.site;
              const list=chk.map(c=>c.dataset.ts);
              if(!confirm('선택한 '+list.length+'개 회차를 삭제합니다.\\n\\n시스템: '+site+'\\n회차: '+list.join(', ')+'\\n\\n리포트·스크린샷·검수 확정이 영구 삭제되며 되돌릴 수 없습니다. 삭제할까요?'))return;
              let ok=0;for(const ts of list){const r=await api('/api/reports/delete/'+encodeURIComponent(site)+'/'+encodeURIComponent(ts),'POST',{});if(r&&r.ok)ok++}
              toast(ok+'개 회차 삭제됨');loadResult()}
            async function loadResult(){
              const [{reports},{systems}]=await Promise.all([api('/api/reports'),api('/api/systems')]);
              if(!reports.length){$('#result').innerHTML='<p class=st>아직 생성된 리포트가 없습니다. ② 실행 탭에서 검사를 돌려보세요.</p>';return}
              const nameOf={};(systems||[]).forEach(s=>nameOf[s.name]=s.displayName||s.name);
              // reports 는 검사일시 내림차순 — site 별로 등장 순서 유지해 묶으면 그룹=최신검사순, 그룹내=최신순
              const groups=[],idx={};
              reports.forEach(r=>{if(!(r.site in idx)){idx[r.site]=groups.length;groups.push({site:r.site,runs:[]})}
                groups[idx[r.site]].runs.push(r)});
              const pend=reports.filter(r=>r.pendingTotal>0);
              const notice=pend.length?`<div class="notice">⚠ <b>수동확인(검수) 필요</b> — ${pend.length}건의 검사에 평가자 확정이 필요한 항목이 있습니다.
                각 행의 <b>검수</b> 버튼을 눌러 <b>적합/부적합/해당없음</b>으로 확정하면 최종 판정이 확정됩니다.</div>`:'';
              const body=groups.map((g,gi)=>{const cmp=g.runs.length>=2;
                return `<div class="platform"><div class="phd">
                  <b>${esc(nameOf[g.site]||g.site)}</b> <small>${esc(g.site)}</small>
                  ${statusPill(g.runs[0])} <span class=st>· 검사 ${g.runs.length}회</span>
                  <span style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap">
                    ${cmp?`<button class="act cmpbtn" id="cmpbtn-${gi}" data-site="${esc(g.site)}" disabled onclick="openCompare(${gi})">비교 (0/2)</button>`:''}
                    <button class="act delbtn" id="delbtn-${gi}" data-site="${esc(g.site)}" disabled style="background:var(--bad)" onclick="delSelected(${gi})">선택 삭제 (0)</button>
                  </span></div>
                <table><thead><tr><th>선택</th><th>검사일시</th><th>종합</th><th>항목 요약</th><th>리포트</th><th>검수</th></tr></thead><tbody>
                ${g.runs.map((r,i)=>runRow(r,i===0,gi)).join('')}</tbody></table></div>`}).join('');
              $('#result').innerHTML=notice+body}

            // ── 리포트 비교(같은 시스템 두 회차) ──
            function vpill(v){const sc=v==='적합'?'ok':v==='부적합'?'no':v==='수동확인필요'?'wait':'na';
              return `<span class="pill ${sc}">${esc(v)}</span>`}
            const vRank=v=>v==='적합'||v==='해당없음'?2:(v==='수동확인필요'?1:0); // 부적합=0
            async function openCompare(gi){
              const chk=[...document.querySelectorAll('.cmpchk[data-g="'+gi+'"]:checked')];
              if(chk.length!==2){toast('비교할 2건을 선택하세요');return}
              const site=document.getElementById('cmpbtn-'+gi).dataset.site;
              let [a,b]=chk.map(c=>c.dataset.ts).sort();  // 오래된 것=기준, 최신=대상
              ['config','run','result'].forEach(t=>$('#'+t).style.display='none');
              $('#compareview').style.display='';setNav('result');window.scrollTo(0,0);
              $('#compareview .body').innerHTML='<p class=st>비교 데이터를 불러오는 중…</p>';
              const q=(t)=>'/api/assessment?site='+encodeURIComponent(site)+'&ts='+encodeURIComponent(t);
              try{const [A,B]=await Promise.all([api(q(a)),api(q(b))]);renderCompare(site,a,b,A,B)}
              catch(e){$('#compareview .body').innerHTML='<div class="hbox no">비교 데이터를 불러오지 못했습니다: '+esc(e)+'</div>'}}
            function closeCompare(){$('#compareview').style.display='none';setNav('result');loadResult();$('#result').style.display=''}
            function renderCompare(site,tsA,tsB,A,B){
              const name=A.displayName||B.displayName||site;
              const fmtRate=c=>c?(c.pending>0?'<span class="st">산정 전</span>':'<b>'+c.rate+'%</b>'):'-';
              let html=`<div class="hd2"><button class="act ghost" style="margin-top:0" onclick="closeCompare()">← 결과 목록</button>
                <div><b>${esc(name)}</b> <small class=st>검사 회차 비교</small></div></div>
                <div class="cmphd"><div class="cmpcol"><div class=st>기준(이전)</div><b>${fmtTs(tsA)}</b><div>${vpill(A.compliance.status)} <span class=st>미확정 ${A.compliance.pendingTotal}</span></div></div>
                  <div class="arrow">→</div>
                  <div class="cmpcol"><div class=st>대상(최신)</div><b>${fmtTs(tsB)}</b><div>${vpill(B.compliance.status)} <span class=st>미확정 ${B.compliance.pendingTotal}</span></div></div></div>`;
              // 카테고리 준수율 변화
              const catB={};(B.compliance.categories||[]).forEach(c=>catB[c.label]=c);
              html+='<h3 class=grp>카테고리 준수율 변화</h3><table class="dt"><thead><tr><th>구분</th><th>기준</th><th>대상</th><th>변화</th></tr></thead><tbody>';
              (A.compliance.categories||[]).forEach(c=>{const d=catB[c.label];
                let delta='<span class=st>—</span>';
                if(c&&d&&c.pending===0&&d.pending===0){const diff=+(d.rate-c.rate).toFixed(1);
                  delta=diff>0?'<span style="color:var(--ok);font-weight:700">▲ +'+diff+'%p</span>'
                    :(diff<0?'<span style="color:var(--bad);font-weight:700">▼ '+diff+'%p</span>':'<span class=st>변화 없음</span>')}
                else if(c&&d&&(c.pending>0||d.pending>0))delta='<span class=st>산정 전</span>';
                html+=`<tr><td>${esc(c.label)}</td><td>${fmtRate(c)}</td><td>${fmtRate(d)}</td><td>${delta}</td></tr>`});
              html+='</tbody></table>';
              // 판정이 바뀐 항목
              const bByCode={};(B.items||[]).forEach(it=>bByCode[it.code]=it);
              const changed=(A.items||[]).map(it=>({a:it,b:bByCode[it.code]}))
                .filter(p=>p.b&&p.a.verdict!==p.b.verdict)
                .sort((x,y)=>(vRank(y.b.verdict)-vRank(y.a.verdict))-(vRank(x.b.verdict)-vRank(x.a.verdict)));
              const nImp=changed.filter(p=>vRank(p.b.verdict)>vRank(p.a.verdict)).length;
              const nReg=changed.filter(p=>vRank(p.b.verdict)<vRank(p.a.verdict)).length;
              html+=`<h3 class=grp>판정이 바뀐 항목 (${changed.length}건 · 개선 ${nImp} · 악화 ${nReg})</h3>`;
              if(changed.length)html+='<table class="dt"><thead><tr><th>코드</th><th>검사 항목</th><th>기준</th><th></th><th>대상</th></tr></thead><tbody>'+
                changed.map(p=>{const r=vRank(p.b.verdict)-vRank(p.a.verdict);const cls=r>0?'improve':(r<0?'regress':'');
                  return `<tr class="${cls}"><td><code>${esc(p.a.code)}</code></td><td>${esc(p.a.title)}</td>
                    <td>${vpill(p.a.verdict)}</td><td>→</td><td>${vpill(p.b.verdict)}</td></tr>`}).join('')+'</tbody></table>';
              // 위반(증거) 건수 변화 — 판정은 그대로지만 사이트 수정이 반영된 부분
              const evCount=it=>(it.evidence||[]).length;
              const evChg=(A.items||[]).map(it=>({a:it,b:bByCode[it.code]}))
                .filter(p=>p.b&&p.a.verdict===p.b.verdict&&evCount(p.a)!==evCount(p.b))
                .sort((x,y)=>(evCount(y.a)-evCount(y.b))-(evCount(x.a)-evCount(x.b)));
              if(evChg.length){
                html+=`<h3 class=grp>위반 건수 변화 · 판정 동일 (${evChg.length}건)</h3>
                  <p class=st>사이트 수정이 검사에 반영됐지만, 항목 판정(적합/부적합)을 바꿀 정도는 아닌 변화입니다.</p>
                  <table class="dt"><thead><tr><th>코드</th><th>검사 항목</th><th>판정</th><th>기준</th><th></th><th>대상</th></tr></thead><tbody>`+
                  evChg.map(p=>{const ca=evCount(p.a),cb=evCount(p.b);const cls=cb<ca?'improve':(cb>ca?'regress':'');
                    return `<tr class="${cls}"><td><code>${esc(p.a.code)}</code></td><td>${esc(p.a.title)}</td>
                      <td>${vpill(p.a.verdict)}</td><td>${ca}건</td><td>${cb<ca?'▼':'▲'}</td><td>${cb}건</td></tr>`}).join('')+'</tbody></table>';
              }
              if(!changed.length&&!evChg.length)html+='<p class=st style="margin-top:8px">두 검사 간 판정·위반 건수 변화가 모두 없습니다. (검사 대상 내용이 동일)</p>';
              $('#compareview .body').innerHTML=html}

            // ── 검수 화면(37항목) ──
            let curSite=null,curTs=null;
            function setNav(tab){document.querySelectorAll('nav button').forEach(x=>
              x.classList.toggle('on',x.dataset.tab===tab))}
            function openAssessment(site,ts){curSite=site;curTs=ts;
              ['config','run','result'].forEach(t=>$('#'+t).style.display='none');
              $('#assessview').style.display='';setNav('result');window.scrollTo(0,0);loadAssessment()}
            function closeAssessment(){$('#assessview').style.display='none';setNav('result');loadResult();$('#result').style.display=''}
            async function loadAssessment(){
              const data=await api('/api/assessment?site='+encodeURIComponent(curSite)+'&ts='+encodeURIComponent(curTs));
              renderAssessment(data)}
            async function confirmItem(code,verdict,memoId){
              const el=document.getElementById(memoId);const memo=el?el.value:'';
              const data=await api('/api/assessment?site='+encodeURIComponent(curSite)+'&ts='+encodeURIComponent(curTs),'POST',{code,verdict,memo});
              renderAssessment(data);toast(code+' → '+verdict)}
            function toggleOverride(id){const el=document.getElementById('ov-'+id);
              if(el)el.style.display=el.style.display==='none'?'':'none'}
            function verdictClass(v){return v==='적합'?'ok':v==='부적합'?'no':v==='수동확인필요'?'wait':'na'}
            function itemCard(it){const vc=verdictClass(it.verdict);
              const id=it.code.replace(/\\W/g,'_');const memoId='memo-'+id;
              const g=it.guide||{};const needs=it.verdict==='수동확인필요';
              const ev=evTables(it.evidence);
              return `<div class="item ${needs?'needs':'done'}">
                <div class="it-hd"><span class=st>${it.no}.</span><b>${esc(it.title)}</b><small class=st>${esc(it.code)}</small>
                <span class="pill ${vc}">${esc(it.verdict)}</span>
                <button class="linkbtn" onclick="toggleOverride('${id}')">${needs?'판정하기':'수정'}</button>
                ${needs?`<button class="linkbtn aibtn" onclick="aiDraft('${esc(it.code)}','${id}')"><span class="sparkle">✦</span> AI 초안</button>`:''}</div>
                <div class=st>근거: ${it.source==='assessor'?'평가자 확정':'자동판정'}${it.memo?(' · 메모: '+esc(it.memo)):''}</div>
                ${ev}
                ${needs&&(curData&&curData.screenshots&&curData.screenshots.length)?`<details class=guide><summary>대상 화면 미리보기 (${curData.screenshots.length}장) — 클릭하면 크게</summary>${thumbsRow(curData.screenshots)}</details>`:''}
                <div id="ai-${id}" class="aidraft"></div>
                <details class=guide><summary>상세 설명</summary>
                  <div><b>무엇</b> ${esc(g.what)}</div><div><b>왜</b> ${esc(g.why)}</div>
                  <div><b>근거</b> ${esc(g.basis)}</div><div><b>해결</b> ${esc(g.fix)}</div>
                  ${g.limit?'<div><b>한계</b> '+esc(g.limit)+'</div>':''}</details>
                <div id="ov-${id}" class=override style="display:${it.verdict==='수동확인필요'?'':'none'}">
                  <input id="${memoId}" placeholder="메모" value="${esc(it.memo||'')}">
                  <div class=ovbtns>
                    <button class="act ghost" onclick="confirmItem('${esc(it.code)}','적합','${memoId}')">적합</button>
                    <button class="act ghost" onclick="confirmItem('${esc(it.code)}','부적합','${memoId}')">부적합</button>
                    <button class="act ghost" onclick="confirmItem('${esc(it.code)}','해당없음','${memoId}')">해당없음</button>
                  </div></div></div>`}
            let aiBusy=false;
            function shotUrl(fn){return '/reports/'+encodeURIComponent(curData.site)+'/'+encodeURIComponent(curData.timestamp)+'/'+fn}
            function pageName(fn){var b=fn.split('/').pop();return b.replace('chromium-','').replace('.png','').replace(/_/g,'/')}
            function thumbsRow(images){return (images&&images.length)?'<div class="aithumbs">'+images.map(fn=>'<div class="aithumb-box"><img class="aithumb" loading="lazy" src="'+shotUrl(fn)+'" alt="근거 화면 '+esc(pageName(fn))+'" onclick="showLb(this.src)"><span class="cap">/'+esc(pageName(fn))+'</span></div>').join('')+'</div>':''}
            function showLb(src){let o=document.getElementById('lbov');if(!o){o=document.createElement('div');o.id='lbov';o.onclick=function(){o.style.display='none'};document.body.appendChild(o)}o.innerHTML='<img src="'+src+'">';o.style.display='flex'}
            async function aiDraft(code,id){const box=document.getElementById('ai-'+id);
              if(aiBusy)return; aiBusy=true; document.querySelectorAll('.aibtn').forEach(b=>{b.disabled=true;b.style.opacity=.45});
              box.innerHTML='<div class="hbox"><span class=st><span class="sparkle">✦</span> AI 판정 중… (한 번에 하나씩 — 다른 항목은 대기)</span></div>';
              let r;try{r=await api('/api/ai-judge?site='+encodeURIComponent(curSite)+'&ts='+encodeURIComponent(curTs)+'&code='+encodeURIComponent(code))}catch(e){r={error:String(e)}}
              finally{aiBusy=false; document.querySelectorAll('.aibtn').forEach(b=>{b.disabled=false;b.style.opacity=1})}
              if(r.error){box.innerHTML='<div class="hbox no">AI 초안 실패: '+esc(r.error)+'</div>';return}
              const vc=verdictClass(r.verdict==='판단보류'?'수동확인필요':r.verdict);const conf=Math.round((r.confidence||0)*100);
              box.innerHTML=`<div class="hbox"><b><span class="sparkle">✦</span> AI 초안 <span class="pill ${vc}">${esc(r.verdict)}</span> <span class=st>신뢰도 ${conf}% · ${esc(r.model)}</span></b>
                <div class=st style="margin:5px 0">${esc(r.reason)}</div>
                ${(r.images&&r.images.length)?`<div class=st style="margin:6px 0 3px">✦ AI가 근거로 본 화면 ${r.vision?'(비전 입력됨)':'<b style="color:#b45309">(※ 비전 미지원 모델일 수 있음 — 텍스트 증거만 반영)</b>'} — 클릭하면 크게:</div>
                  ${thumbsRow(r.images)}`:''}
                ${r.verdict==='판단보류'?'<span class=st>판단보류 — 평가자 확인 필요</span>':
                  `<input type=hidden id="ai-memo-${id}" value="${esc('[AI 초안] '+r.reason)}">
                   <button class="act" style="margin-top:0" onclick="confirmItem('${esc(code)}','${esc(r.verdict)}','ai-memo-${id}')">이 판정으로 확정 →</button>`}
                <div class=hint>AI 초안은 참고용입니다. 최종 판정은 평가자가 확정합니다.</div></div>`}
            let assessFilter=false,curData=null;
            function toggleFilter(){assessFilter=!assessFilter;renderAssessment(curData)}
            function renderAssessment(data){curData=data;
              const comp=data.compliance;
              const sc=comp.status==='적합'?'ok':(comp.status==='부적합'?'no':'wait');
              const confirmed=data.items.filter(it=>it.source==='assessor').length;
              let html=`<div class="hd2"><button class="act ghost" style="margin-top:0" onclick="closeAssessment()">← 결과 목록</button>
                <div><b>${esc(data.displayName||data.site)}</b> <small class=st>${esc(data.timestamp)}</small></div>
                <span class="pill ${sc}">종합판정: ${esc(comp.status)}</span></div>`;
              if(comp.pendingTotal>0)html+=`<div class="notice">⚠ <b>미확정 ${comp.pendingTotal}건</b> — 자동 검사가 판단할 수 없는 항목입니다.
                각 항목의 <b>판정하기</b>를 눌러 <b>적합/부적합/해당없음</b>을 선택하세요. 아래 <b>판정 필요한 항목만 보기</b>로 걸러 볼 수 있습니다.</div>`;
              else html+=`<div class="ok-note">✓ <b>검수 완료</b> — 모든 항목이 확정되었습니다. 종합판정 <b>${esc(comp.status)}</b>. 리포트에 최종 준수율이 반영됩니다.</div>`;
              html+=`<div class="filterbar">
                <label><input type=checkbox ${assessFilter?'checked':''} onclick="toggleFilter()"> 판정 필요한 항목만 보기</label>
                <span class="prog">평가자 확정 <b>${confirmed}</b> · 미확정 <b>${comp.pendingTotal}</b> / 전체 ${data.items.length}</span></div>`;
              html+=comp.categories.map(c=>{
                const pct=Math.max(0,Math.min(100,c.rate));
                const col=c.pending>0?'var(--warn)':(c.pass?'var(--ok)':'var(--bad)');
                const tag=c.pending>0?'<span class="pill wait">대기 '+c.pending+'</span>':(c.pass?'<span class="pill ok">충족</span>':'<span class="pill no">미달</span>');
                return `<div class="catbar"><div class="cb-top"><b>${esc(c.label)}</b>
                  <span>${tag} <span class=st>적합 ${c.적합}/${c.적합+c.부적합} · 기준 ${c.threshold}%</span></span></div>
                  <div class="bar"><i style="width:${pct}%;background:${col}"></i></div></div>`}).join('');
              html+=comp.categories.map(cat=>{
                let items=data.items.filter(it=>gkey(it.code)===cat.category);
                if(cat.category!=='c')items=items.slice().sort((a,b)=>csort(a.code,b.code));
                if(assessFilter)items=items.filter(it=>it.verdict==='수동확인필요');
                if(!items.length)return '';
                return `<h3 class=grp>${esc(cat.label)}</h3>`+items.map(itemCard).join('')}).join('');
              if(assessFilter&&comp.pendingTotal===0)html+='<p class=st style="text-align:center;padding:20px">판정할 항목이 없습니다. 모두 확정되었습니다.</p>';
              $('#assessview .body').innerHTML=html;syncEvtx($('#assessview'))}
            // 증거 표에 …말줄임이 실제로 생긴 경우만 '펼쳐보기' 버튼 노출
            function syncEvtx(root){(root||document).querySelectorAll('.evtwrap').forEach(w=>{
              const cap=w.previousElementSibling, btn=cap&&cap.querySelector('.evtx'); if(!btn)return;
              let tr=false; w.querySelectorAll('.evel,.evloc').forEach(c=>{if(c.scrollWidth>c.clientWidth+1)tr=true;});
              btn.style.display=tr?'inline-block':'none';})}

            loadConfig();
            // 리포트의 '결과 목록' 링크(/#result)로 진입 시 결과·검수 탭으로 이동
            if(location.hash==='#result')document.querySelector('nav button[data-tab=result]').click();
            </script></body></html>""";

    // 두 text block 을 런타임에 이어붙인다 — 단일 String 상수가 64KB(클래스 상수풀 한도)를 넘지 않게.
    static final String HTML = HTML_A.concat(HTML_B);
}
