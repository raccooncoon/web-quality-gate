package com.kt.wqg.web;

/** 웹 콘솔 단일 페이지 UI (인라인 CSS/JS, 외부 리소스 없음). */
final class WebConsoleUI {

    private WebConsoleUI() {}

    static final String HTML = """
            <!DOCTYPE html>
            <html lang="ko"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>web-quality-gate 콘솔</title>
            <style>
            /* 공공기관 문서 톤 — 밝은 단일 테마(다크모드 미사용), 리포트와 동일 팔레트 */
            :root{--bg:#eceff4;--card:#fff;--fg:#17202e;--muted:#5a6573;--line:#d4dbe4;--line2:#b9c3d0;
              --navy:#0f2340;--navy2:#1b3b63;--gov:#1f5aa6;--accent:#1f5aa6;--govsoft:#e9f1fb;--gold:#b0894a;
              --ok:#1f7a44;--bad:#c02a2a;--warn:#a9781c;--okbg:#e7f4ec;--badbg:#fbeaea;--warnbg:#faf1df;color-scheme:light}
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
            .hbox{margin-top:10px;padding:10px 12px;border-radius:4px;font-size:13px;border:1px solid var(--line)}
            .hbox.ok{background:var(--okbg);border-color:var(--ok)}.hbox.no{background:var(--badbg);border-color:var(--bad)}
            .hbox b{display:block;margin-bottom:5px}.hstep{padding:2px 0;font-size:12.5px}
            .pill.na{background:var(--line);color:var(--muted);border-color:var(--line2)}
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
            .verstamp{position:fixed;right:8px;bottom:6px;font-size:9px;color:var(--muted);z-index:20;opacity:.75;cursor:pointer;
              background:rgba(255,255,255,.9);border:1px solid var(--line);border-radius:3px;padding:1px 5px;font-family:ui-monospace,monospace}
            .verstamp:hover{opacity:1}
            .pwwrap{position:relative}.pwwrap input{padding-right:40px}
            .eye{position:absolute;right:4px;top:50%;transform:translateY(-50%);background:none;border:none;
              cursor:pointer;font-size:16px;line-height:1;padding:4px;opacity:.65}.eye:hover{opacity:1}
            </style></head><body>
            <header><h1>🛡️ web-quality-gate <span class="tag">웹접근성·웹호환성 검사 콘솔</span></h1>
            <nav><button data-tab="config" class="on">① 설정</button>
              <button data-tab="run">② 실행</button><button data-tab="result">③ 결과 · 검수</button>
              <button data-tab="items">④ 점검 항목</button></nav></header>
            <main><div id="config"></div><div id="run" style="display:none"></div>
              <div id="items" style="display:none"></div>
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
            let toastT;function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('show');
              clearTimeout(toastT);toastT=setTimeout(()=>t.classList.remove('show'),1800)}
            function copyVer(el){const s=el.textContent.trim();
              navigator.clipboard.writeText(s).then(()=>toast('복사됨: '+s),()=>toast('복사 실패'))}
            document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>{
              document.querySelectorAll('nav button').forEach(x=>x.classList.remove('on'));b.classList.add('on');
              $('#assessview').style.display='none';$('#compareview').style.display='none';  // 상단 탭으로 빠져나오기
              ['config','run','items','result'].forEach(t=>$('#'+t).style.display=t===b.dataset.tab?'':'none');
              if(b.dataset.tab==='run')loadRun();if(b.dataset.tab==='result')loadResult();if(b.dataset.tab==='items')loadItems()});

            // ── 설정 탭 ──
            const isTest=s=>s.name==='defect-kwater'||(s.displayName||'').includes('테스트');
            const addForm=`<div class="sys" style="border-style:dashed"><div class="body" style="display:block;border:none">
              <div class="grouphd" style="margin-top:0">시스템 추가</div>
              <div class="row3"><div><label>시스템명(영문)</label><input id="ns-name" placeholder="cloud-cmp"></div>
                <div><label>표시 이름</label><input id="ns-disp" placeholder="클라우드포털-CMP"></div>
                <div><label>유형</label><select id="ns-type"><option value="spa">SPA</option><option value="mpa">MPA</option></select></div></div>
              <button class="act" onclick="addSystem()">＋ 추가</button>
              <div class="hint">추가 후 baseUrl·계정·경로를 채우세요. 이름은 영소문자·숫자·하이픈만.</div></div></div>`;
            async function loadConfig(){const {systems}=await api('/api/systems');
              const test=systems.filter(isTest),real=systems.filter(s=>!isTest(s));
              let html='<div class="tabintro">폐쇄망 시스템의 <b>baseUrl·계정·검사 경로</b>를 설정하는 화면입니다. '
                +'각 시스템 카드를 펼쳐 채운 뒤 <b>[설정 저장]</b> → <b>[설정 확인(헬스체크)]</b>로 검증하세요. '
                +'새 시스템은 아래 <b>시스템 추가</b>에서 만듭니다.</div>'+addForm;
              if(test.length)html+='<div class=grouphd>테스트 대상</div>'+test.map(sysCard).join('');
              if(real.length)html+='<div class=grouphd>폐쇄망 대상 시스템</div>'+real.map(sysCard).join('');
              $('#config').innerHTML=html;
              systems.forEach(s=>{const el=document.getElementById('sys-'+s.name);
                el.querySelector('.hd').onclick=()=>el.classList.toggle('open');
                if(s.authType==='form')el.querySelector('.body').classList.add('auth-form');
                el.querySelector('[name=authType]').onchange=e=>
                  el.querySelector('.body').classList.toggle('auth-form',e.target.value==='form');
                el.querySelector('.save').onclick=()=>saveSys(s.name,el);
                el.querySelector('.savecred').onclick=()=>saveCred(s.name,el);
                el.querySelector('.health').onclick=()=>healthCheck(s.name);
                el.querySelector('.detect').onclick=()=>detectSel(s.name,el);
                el.querySelector('.dtype').onclick=()=>detectType(s.name,el);
                el.querySelector('.discover').onclick=()=>discoverUrls(s.name,el);
                el.querySelector('.gorun').onclick=()=>goRun(s.name);
                el.querySelector('.del').onclick=()=>delSystem(s.name,s.displayName);
              })}
            async function addSystem(){const name=$('#ns-name').value.trim();
              if(!name){toast('시스템명 입력');return}
              const r=await api('/api/systems/add','POST',{name,displayName:$('#ns-disp').value.trim()||name,type:$('#ns-type').value});
              if(r.error){toast(r.error);return}toast('추가됨: '+name);loadConfig()}
            async function delSystem(name,disp){if(!confirm('시스템 삭제: '+(disp||name)+'\\n설정과 저장된 계정이 삭제됩니다. 계속?'))return;
              await api('/api/systems/delete/'+name,'POST',{});toast('삭제됨: '+name);loadConfig()}
            async function detectSel(name,el){const g=n=>el.querySelector('[name='+n+']');
              const baseUrl=g('baseUrl').value.trim(),loginPath=g('loginPath').value.trim()||'/login';
              const box=el.querySelector('.health');box.innerHTML='<div class=st>로그인 페이지 여는 중… 필드 감지</div>';
              const r=await api('/api/detect-selectors','POST',{baseUrl,loginPath});
              if(r.error){box.innerHTML='<div class="hbox no">'+esc(r.error)+'</div>';return}
              if(r.idSelector)g('idSelector').value=r.idSelector;
              if(r.pwSelector)g('pwSelector').value=r.pwSelector;
              if(r.submitSelector)g('submitSelector').value=r.submitSelector;
              const found=[r.idSelector&&'아이디',r.pwSelector&&'비번',r.submitSelector&&'제출'].filter(Boolean);
              box.innerHTML='<div class="hbox '+(found.length?'ok':'no')+'">'+(found.length?'✓ 감지: '+found.join('·')+' (확인 후 저장)':'✗ 로그인 필드를 못 찾음 — 수동 입력')+'</div>'}
            async function detectType(name,el){const note=el.querySelector('.dtnote');
              const baseUrl=el.querySelector('[name=baseUrl]').value.trim();
              if(!baseUrl){toast('baseUrl 입력');return}
              note.innerHTML='<div class=st>서버 응답으로 유형 확인 중…</div>';
              let r;try{r=await api('/api/detect-type','POST',{baseUrl})}catch(e){r={error:String(e)}}
              if(r.error){note.innerHTML='<div class="hbox no">✗ '+esc(r.error)+'</div>';return}
              el.querySelector('[name=type]').value=r.type;
              note.innerHTML='<div class="hbox ok">✓ <b>'+esc(r.type.toUpperCase())+'</b> 로 설정 — '+esc(r.reason)+'<div class=hint>확인 후 [설정 저장]</div></div>'}
            async function healthCheck(name){const box=document.getElementById('health-'+name);
              box.innerHTML='<div class=st>설정 확인 중… (브라우저 기동·로그인 점검, 최대 30초)</div>';
              try{const r=await api('/api/health/'+name,'POST',{});
                box.innerHTML='<div class="hbox '+(r.ok?'ok':'no')+'">'+
                  '<b>'+(r.ok?'✓ 설정 정상':'✗ 설정 확인 실패')+'</b>'+
                  r.steps.map(s=>`<div class=hstep>${s.ok?'✓':'✗'} ${esc(s.name)} <span class=st>${esc(s.detail)}</span></div>`).join('')+'</div>';
              }catch(e){box.innerHTML='<div class="hbox no">확인 중 오류: '+esc(e)+'</div>'}}
            function sysCard(s){const cfg=s.configured;
              return `<div class="sys" id="sys-${s.name}"><div class="hd">
                <div class="nm">${esc(s.displayName)}<small>${s.name}</small></div>
                <div>${s.hasCredentials?'<span class=pill ok>계정 저장됨</span>':(s.authType==='form'?'<span class=pill wait>계정 필요</span>':'')}
                  ${cfg?'<span class=pill ok>설정됨</span>':'<span class=pill no>미설정</span>'}</div></div>
                <div class="body">
                <div class="cardsteps"><b>진행 순서</b> ① 유형·baseUrl → ② 로그인 필드·계정 → ③ 검사 경로 → ④ 설정 저장 → ⑤ 설정 확인(헬스체크)</div>
                <div class="row"><div><label>유형</label><select name="type">
                  <option value="spa" ${s.type==='spa'?'selected':''}>SPA (React/Vue)</option>
                  <option value="mpa" ${s.type==='mpa'?'selected':''}>MPA (서버렌더링)</option></select></div>
                  <div><label>로그인 방식</label><select name="authType">
                  <option value="none" ${s.authType!=='form'?'selected':''}>없음</option>
                  <option value="form" ${s.authType==='form'?'selected':''}>폼 로그인</option></select></div></div>
                <label>baseUrl</label><input name="baseUrl" value="${esc(s.baseUrl)}" placeholder="http://10.0.x.x">
                <button class="act ghost dtype">🔎 유형 자동 판별 (MPA/SPA)</button>
                <div class="hint">baseUrl 입력 후 누르면 서버 응답으로 MPA/SPA 를 추정해 위 '유형'을 채웁니다.</div>
                <div class="dtnote" id="dtnote-${s.name}"></div>
                <div class="auth-only">
                  <div class="row3"><div><label>로그인 경로</label><input name="loginPath" value="${esc(s.loginPath||'/login')}"></div>
                    <div><label>아이디 셀렉터</label><input name="idSelector" value="${esc(s.idSelector||'')}" placeholder="input[name=username]"></div>
                    <div><label>비번 셀렉터</label><input name="pwSelector" value="${esc(s.pwSelector||'')}" placeholder="input[name=password]"></div></div>
                  <label>제출 버튼 셀렉터</label><input name="submitSelector" value="${esc(s.submitSelector||'')}" placeholder="button[type=submit]">
                  <button class="act ghost detect">🔍 로그인 필드 자동 감지</button>
                  <div class="hint">baseUrl·로그인 경로를 채운 뒤 누르면 로그인 페이지를 열어 셀렉터를 채웁니다.</div>
                  <div class="row"><div><label>계정 ID ${s.hasCredentials?'<span class="pill ok">저장됨</span>':''}</label>
                    <input name="cid" value="${esc(s.credId||'')}" placeholder="아이디"></div>
                    <div><label>계정 PW ${s.hasCredentials?'<span class="pill ok">저장됨</span>':''}</label>
                    <div class="pwwrap"><input name="cpw" type="password" value="${esc(s.credPw||'')}" placeholder="비밀번호">
                      <button type="button" class="eye" onclick="togglePw(this)" aria-label="비밀번호 표시/숨김" title="표시/숨김">👁</button></div></div></div>
                  <button class="act ghost savecred">계정만 저장</button>
                  <div class="hint">계정은 이 PC 로컬 파일에만 저장됩니다 (git·리포트 제외).</div>
                </div>
                <label>검사 경로 (한 줄에 하나, "경로 | readySelector" 형식 가능)</label>
                <textarea name="urls">${esc((s.urls||[]).join('\\n'))}</textarea>
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
            async function saveSys(name,el){const g=n=>el.querySelector('[name='+n+']').value;
              await api('/api/save/'+name,'POST',{type:g('type'),authType:g('authType'),baseUrl:g('baseUrl'),
                loginPath:g('loginPath'),idSelector:g('idSelector'),pwSelector:g('pwSelector'),
                submitSelector:g('submitSelector'),urls:parseUrls(g('urls'))});
              const cid=g('cid'),cpw=g('cpw');if(cid&&cpw)await api('/api/credentials/'+name,'POST',{id:cid,pw:cpw});
              toast('저장됨: '+name);loadConfig()}
            async function saveCred(name,el){const cid=el.querySelector('[name=cid]').value,
              cpw=el.querySelector('[name=cpw]').value;if(!cid||!cpw){toast('ID/PW 입력');return}
              await api('/api/credentials/'+name,'POST',{id:cid,pw:cpw});toast('계정 저장됨');loadConfig()}
            // 검사 경로 자동 수집 — 로그인 후 내부 링크를 훑어 후보 제시 → 선택 → 검사 경로에 추가
            async function discoverUrls(name,el){const box=el.querySelector('.discovered');
              box.innerHTML='<div class=st>로그인(필요 시) 후 내부 경로 수집 중… (최대 30초)</div>';
              let r;try{r=await api('/api/discover-paths/'+name,'POST',{})}catch(e){r={error:String(e)}}
              if(r.error){box.innerHTML='<div class="hbox no">✗ 수집 실패 — '+esc(r.error)+'<div class=hint>baseUrl·로그인 설정을 먼저 저장했는지 확인하세요.</div></div>';return}
              const cur=new Set(parseUrls(el.querySelector('[name=urls]').value).map(u=>u.path));
              const cand=(r.paths||[]).filter(p=>!cur.has(p));
              if(!cand.length){box.innerHTML='<div class="hbox ok">수집 '+(r.paths||[]).length+'건 — 새로 추가할 경로가 없습니다(이미 목록에 있음).</div>';return}
              box.innerHTML=`<div class="hbox ok"><b>후보 경로 ${cand.length}건</b> — 추가할 항목을 선택하세요.
                <div class="candtools"><button class="linkbtn" onclick="candAll('${name}',true)">전체 선택</button>
                  <button class="linkbtn" onclick="candAll('${name}',false)">전체 해제</button></div>
                <div class="candlist">${cand.map(p=>`<label class=cand><input type=checkbox checked value="${esc(p)}"> ${esc(p)}</label>`).join('')}</div>
                <button class="act" onclick="addDiscovered('${name}')">선택 경로 추가</button>
                <button class="act ghost" onclick="document.getElementById('disc-${name}').innerHTML=''">닫기</button></div>`}
            function togglePw(btn){const inp=btn.parentNode.querySelector('input');
              const show=inp.type==='password';inp.type=show?'text':'password';btn.textContent=show?'🙈':'👁'}
            function candAll(name,v){document.querySelectorAll('#disc-'+name+' .cand input').forEach(c=>c.checked=v)}
            function addDiscovered(name){const el=document.getElementById('sys-'+name);
              const chosen=[...el.querySelectorAll('.discovered input:checked')].map(c=>c.value);
              if(!chosen.length){toast('선택된 경로 없음');return}
              const ta=el.querySelector('[name=urls]');const merged=parseUrls(ta.value).map(u=>u.path);
              chosen.forEach(p=>{if(!merged.includes(p))merged.push(p)});
              ta.value=merged.join('\\n');el.querySelector('.discovered').innerHTML='';
              toast(chosen.length+'개 추가됨 — [설정 저장]을 눌러 반영하세요')}

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
            async function refreshStatus(){const {status}=await api('/api/status');
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
            async function loadItems(){const {items,excludedCount}=await api('/api/items');
              const cats=[['accessibility',true,'웹접근성 자동'],['accessibility',false,'웹접근성 수동'],
                ['compatibility',true,'웹호환성 자동'],['compatibility',false,'웹호환성 수동']];
              let html=`<div class="tabintro">검사할 <b>KWCAG 점검 항목</b>을 관리합니다. 체크를 <b>끄면</b> 그 항목은
                검사·집계·리포트에서 <b>제외</b>됩니다(전체 시스템 공통, 다음 검사부터 적용). 현재 제외 <b id="exCount">${excludedCount}</b>건.</div>
                <div class=filterbar><button class="act" onclick="saveItems()">제외 설정 저장</button>
                  <button class="act ghost" onclick="allItems(true)">전체 선택</button>
                  <button class="act ghost" onclick="allItems(false)">전체 해제</button></div>`;
              cats.forEach(([cat,auto,label])=>{const list=items.filter(it=>it.category===cat&&(it.siClassification==='auto')===auto);
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
              <td>${fmtTs(r.timestamp)}${newest?'<span class=newtag>최신</span>':''}<div class="runid">회차 ID <code>${esc(r.timestamp)}</code></div></td>
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
              const ev=(it.evidence||[]).map(e=>`<div class=ev>${esc(e.rule)} — ${esc(e.detail)}${e.url?' <span class=st>'+esc(e.url)+'</span>':''}</div>`).join('');
              return `<div class="item ${needs?'needs':'done'}">
                <div class="it-hd"><span class=st>${it.no}.</span><b>${esc(it.title)}</b><small class=st>${esc(it.code)}</small>
                <span class="pill ${vc}">${esc(it.verdict)}</span>
                <button class="linkbtn" onclick="toggleOverride('${id}')">${needs?'판정하기':'수정'}</button></div>
                <div class=st>근거: ${it.source==='assessor'?'평가자 확정':'자동판정'}${it.memo?(' · 메모: '+esc(it.memo)):''}</div>
                ${ev}
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
                let items=data.items.filter(it=>it.category===cat.category&&(it.siClassification==='auto')===cat.siAuto);
                if(assessFilter)items=items.filter(it=>it.verdict==='수동확인필요');
                if(!items.length)return '';
                return `<h3 class=grp>${esc(cat.label)}</h3>`+items.map(itemCard).join('')}).join('');
              if(assessFilter&&comp.pendingTotal===0)html+='<p class=st style="text-align:center;padding:20px">판정할 항목이 없습니다. 모두 확정되었습니다.</p>';
              $('#assessview .body').innerHTML=html}

            loadConfig();
            // 리포트의 '결과 목록' 링크(/#result)로 진입 시 결과·검수 탭으로 이동
            if(location.hash==='#result')document.querySelector('nav button[data-tab=result]').click();
            </script></body></html>""";
}
