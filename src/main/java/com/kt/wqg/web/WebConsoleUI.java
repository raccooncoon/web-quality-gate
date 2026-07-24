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
            :root{--bg:#f4f6f8;--card:#fff;--fg:#1a1d21;--muted:#6b7280;--line:#e2e6ea;--accent:#2563eb;
              --ok:#16a34a;--bad:#dc2626;--warn:#d97706;--okbg:#eafbf0;--badbg:#fdecec}
            @media(prefers-color-scheme:dark){:root{--bg:#0e1116;--card:#171b22;--fg:#e6e8eb;--muted:#9099a5;
              --line:#262c36;--okbg:#0f2a1a;--badbg:#2a1414}}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
              font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;line-height:1.5}
            header{background:var(--card);border-bottom:1px solid var(--line);padding:14px 22px;position:sticky;top:0;z-index:5}
            header h1{font-size:16px;margin:0;display:flex;align-items:center;gap:10px}
            header .tag{font-size:11px;color:var(--muted);font-weight:400}
            nav{display:flex;gap:4px;margin-top:10px}
            nav button{background:none;border:none;color:var(--muted);font-size:14px;font-weight:600;
              padding:6px 14px;border-radius:8px;cursor:pointer}
            nav button.on{background:var(--accent);color:#fff}
            main{max-width:920px;margin:0 auto;padding:22px}
            .sys{background:var(--card);border:1px solid var(--line);border-radius:12px;margin-bottom:14px;overflow:hidden}
            .sys>.hd{padding:12px 16px;display:flex;justify-content:space-between;align-items:center;cursor:pointer;gap:12px}
            .sys .nm{font-weight:700}.sys .nm small{color:var(--muted);font-weight:400;font-family:ui-monospace,monospace;margin-left:6px;font-size:12px}
            .sys .body{padding:6px 16px 16px;border-top:1px solid var(--line);display:none}
            .sys.open .body{display:block}
            .pill{font-size:11px;font-weight:700;padding:2px 9px;border-radius:999px}
            .pill.ok{background:var(--okbg);color:var(--ok)}.pill.no{background:var(--badbg);color:var(--bad)}
            .pill.wait{background:var(--badbg);color:var(--warn)}
            label{display:block;font-size:12px;color:var(--muted);margin:10px 0 3px;font-weight:600}
            input,select,textarea{width:100%;padding:8px 10px;border:1px solid var(--line);border-radius:8px;
              background:var(--bg);color:var(--fg);font-size:13px;font-family:inherit}
            textarea{min-height:90px;font-family:ui-monospace,monospace;font-size:12px;resize:vertical}
            .row{display:grid;grid-template-columns:1fr 1fr;gap:10px}
            .row3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px}
            .hint{font-size:11px;color:var(--muted);margin-top:3px}
            button.act{background:var(--accent);color:#fff;border:none;border-radius:8px;padding:9px 16px;
              font-size:13px;font-weight:600;cursor:pointer;margin-top:14px}
            button.ghost{background:var(--card);color:var(--fg);border:1px solid var(--line)}
            button.run{background:var(--ok)}
            .toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:var(--fg);color:var(--bg);
              padding:9px 18px;border-radius:999px;font-size:13px;opacity:0;transition:.2s;pointer-events:none}
            .toast.show{opacity:1}
            .st{font-size:12px;color:var(--muted)}
            table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);border-radius:12px;overflow:hidden}
            th,td{text-align:left;padding:10px 14px;border-bottom:1px solid var(--line);font-size:13px}
            th{font-size:11px;color:var(--muted);text-transform:uppercase}tr:last-child td{border-bottom:none}
            a{color:var(--accent)}.auth-only{display:none}.auth-form .auth-only{display:block}
            .hbox{margin-top:10px;padding:10px 12px;border-radius:8px;font-size:13px;border:1px solid var(--line)}
            .hbox.ok{background:var(--okbg);border-color:var(--ok)}.hbox.no{background:var(--badbg);border-color:var(--bad)}
            .hbox b{display:block;margin-bottom:5px}.hstep{padding:2px 0;font-size:12.5px}
            .pill.na{background:var(--line);color:var(--muted)}
            .catbar{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:10px 14px;margin-bottom:8px}
            .cb-top{display:flex;justify-content:space-between;gap:10px;font-size:13px;margin-bottom:6px;flex-wrap:wrap}
            .bar{background:var(--line);border-radius:999px;height:8px;overflow:hidden}
            .bar>i{display:block;height:100%}
            .grp{font-size:12px;color:var(--muted);margin:18px 0 8px;text-transform:uppercase;letter-spacing:.03em}
            .item{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px;margin-bottom:8px}
            .it-hd{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:4px}
            .it-hd b{flex:1}
            .linkbtn{background:none;border:none;color:var(--accent);font-size:12px;cursor:pointer;padding:0}
            .ev{font-size:12px;color:var(--muted);margin:2px 0}
            .guide{margin-top:6px;font-size:12.5px;color:var(--muted)}
            .guide summary{cursor:pointer;color:var(--accent);font-weight:600}
            .guide div{margin:4px 0}
            .override{margin-top:10px;border-top:1px dashed var(--line);padding-top:10px}
            .ovbtns{display:flex;gap:8px;margin-top:8px}
            .ovbtns button{flex:1}
            .hd2{display:flex;align-items:center;gap:14px;margin-bottom:14px;flex-wrap:wrap}
            #assessview .body{max-width:920px;margin:0 auto}
            .item.needs{border-color:var(--warn);border-left:4px solid var(--warn)}
            .item.done{opacity:.72}
            .filterbar{display:flex;align-items:center;gap:14px;margin:16px 0 4px;flex-wrap:wrap}
            .filterbar label{display:flex;align-items:center;gap:6px;margin:0;color:var(--fg);font-weight:600;cursor:pointer}
            .filterbar input{width:auto}
            .prog{font-size:12px;color:var(--muted)}
            .prog b{color:var(--fg)}
            .grouphd{font-size:12px;color:var(--muted);font-weight:700;margin:20px 0 8px;padding-bottom:4px;border-bottom:1px solid var(--line);text-transform:uppercase;letter-spacing:.03em}
            td .pill{margin-right:6px}
            </style></head><body>
            <header><h1>🛡️ web-quality-gate <span class="tag">웹접근성·호환성 검사 콘솔</span></h1>
            <nav><button data-tab="config" class="on">① 설정</button>
              <button data-tab="run">② 실행</button><button data-tab="result">③ 결과</button></nav></header>
            <main><div id="config"></div><div id="run" style="display:none"></div>
              <div id="result" style="display:none"></div>
              <div id="assessview" style="display:none"><div class="body"></div></div></main>
            <div class="toast" id="toast"></div>
            <script>
            const $=s=>document.querySelector(s);
            const api=(u,m,b)=>fetch(u,{method:m||'GET',headers:{'Content-Type':'application/json'},
              body:b?JSON.stringify(b):undefined}).then(r=>r.json());
            const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
            let toastT;function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('show');
              clearTimeout(toastT);toastT=setTimeout(()=>t.classList.remove('show'),1800)}
            document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>{
              document.querySelectorAll('nav button').forEach(x=>x.classList.remove('on'));b.classList.add('on');
              ['config','run','result'].forEach(t=>$('#'+t).style.display=t===b.dataset.tab?'':'none');
              if(b.dataset.tab==='run')loadRun();if(b.dataset.tab==='result')loadResult()});

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
              let html=addForm;
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
                <div class="row"><div><label>유형</label><select name="type">
                  <option value="spa" ${s.type==='spa'?'selected':''}>SPA (React/Vue)</option>
                  <option value="mpa" ${s.type==='mpa'?'selected':''}>MPA (서버렌더링)</option></select></div>
                  <div><label>로그인 방식</label><select name="authType">
                  <option value="none" ${s.authType!=='form'?'selected':''}>없음</option>
                  <option value="form" ${s.authType==='form'?'selected':''}>폼 로그인</option></select></div></div>
                <label>baseUrl</label><input name="baseUrl" value="${esc(s.baseUrl)}" placeholder="http://10.0.x.x">
                <div class="auth-only">
                  <div class="row3"><div><label>로그인 경로</label><input name="loginPath" value="${esc(s.loginPath||'/login')}"></div>
                    <div><label>아이디 셀렉터</label><input name="idSelector" value="${esc(s.idSelector||'')}" placeholder="input[name=username]"></div>
                    <div><label>비번 셀렉터</label><input name="pwSelector" value="${esc(s.pwSelector||'')}" placeholder="input[name=password]"></div></div>
                  <label>제출 버튼 셀렉터</label><input name="submitSelector" value="${esc(s.submitSelector||'')}" placeholder="button[type=submit]">
                  <button class="act ghost detect">🔍 로그인 필드 자동 감지</button>
                  <div class="hint">baseUrl·로그인 경로를 채운 뒤 누르면 로그인 페이지를 열어 셀렉터를 채웁니다.</div>
                  <div class="row"><div><label>계정 ID ${s.hasCredentials?'<span class="pill ok">저장됨</span>':''}</label>
                    <input name="cid" placeholder="${s.hasCredentials?'●●●●●●●● (변경 시에만 입력)':'아이디'}"></div>
                    <div><label>계정 PW ${s.hasCredentials?'<span class="pill ok">저장됨</span>':''}</label>
                    <input name="cpw" type="password" placeholder="${s.hasCredentials?'●●●●●●●● (변경 시에만 입력)':'비밀번호'}"></div></div>
                  <button class="act ghost savecred">계정만 저장</button>
                  <div class="hint">계정은 이 PC 로컬 파일에만 저장됩니다 (git·리포트 제외).</div>
                </div>
                <label>검사 경로 (한 줄에 하나, "경로 | readySelector" 형식 가능)</label>
                <textarea name="urls">${esc((s.urls||[]).join('\\n'))}</textarea>
                <div class="hint">로그인 후 경로 자동수집: 터미널에서 DiscoverMain 실행 (README 참고).</div>
                <button class="act save">설정 저장</button>
                <button class="act ghost health">설정 확인(헬스체크)</button>
                <button class="act ghost del" style="color:var(--bad)">시스템 삭제</button>
                <div class="health" id="health-${s.name}"></div></div></div>`}
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

            // ── 실행 탭 ──
            let poll;
            let runConfigured=[];
            async function loadRun(){const {systems}=await api('/api/systems');
              runConfigured=systems.filter(s=>s.configured).map(s=>s.name);
              $('#run').innerHTML=`<div class=filterbar><button class="act run" onclick="runAll()" ${runConfigured.length?'':'disabled'}>설정된 시스템 전체 검사 (${runConfigured.length})</button></div>`+
                '<table><thead><tr><th>시스템</th><th>설정</th><th>상태</th><th></th></tr></thead><tbody>'+
                systems.map(s=>`<tr><td>${esc(s.displayName)}<br><small class=st>${s.name}</small></td>
                  <td>${s.configured?'<span class=pill ok>준비</span>':'<span class=pill no>미설정</span>'}
                    ${s.authType==='form'&&!s.hasCredentials?'<span class=pill wait>계정 필요</span>':''}</td>
                  <td class=st id="stat-${s.name}">-</td>
                  <td><button class="act run" ${s.configured?'':'disabled'} onclick="runSys('${s.name}')">검사</button></td></tr>`).join('')+
                '</tbody></table>';refreshStatus();
              clearInterval(poll);poll=setInterval(refreshStatus,2000)}
            async function runSys(name){await api('/api/run/'+name,'POST',{});toast('검사 시작: '+name);refreshStatus()}
            async function runAll(){for(const n of runConfigured){await api('/api/run/'+n,'POST',{})}
              toast('전체 검사 시작 ('+runConfigured.length+')');refreshStatus()}
            async function refreshStatus(){const {status}=await api('/api/status');
              Object.entries(status).forEach(([site,st])=>{const el=document.getElementById('stat-'+site);if(!el)return;
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

            // ── 결과 탭 ──
            function statusPill(r){if(!r.status)return '';
              const sc=r.status==='적합'?'ok':(r.status==='부적합'?'no':'wait');
              return `<span class="pill ${sc}">${esc(r.status)}</span> <span class=st>미확정 ${r.pendingTotal}</span>`}
            async function loadResult(){const {reports}=await api('/api/reports');
              $('#result').innerHTML=reports.length?'<table><thead><tr><th>시스템</th><th>검사일시</th><th>판정</th><th></th></tr></thead><tbody>'+
                reports.map(r=>`<tr><td>${esc(r.site)}</td><td>${esc(r.timestamp)}</td>
                  <td>${statusPill(r)}</td>
                  <td><a href="${esc(r.path)}" target="_blank">리포트 ↗</a>
                  <button class="act ghost" onclick="openAssessment('${esc(r.site)}','${esc(r.timestamp)}')">검수</button></td></tr>`).join('')+'</tbody></table>'
                :'<p class=st>아직 생성된 리포트가 없습니다. ② 실행 탭에서 검사를 돌려보세요.</p>'}

            // ── 검수 화면(37항목) ──
            let curSite=null,curTs=null;
            function openAssessment(site,ts){curSite=site;curTs=ts;
              $('#result').style.display='none';$('#assessview').style.display='';loadAssessment()}
            function closeAssessment(){$('#assessview').style.display='none';$('#result').style.display=''}
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
              let html=`<div class="hd2"><button class="act ghost" onclick="closeAssessment()">← 결과로</button>
                <div><b>${esc(data.displayName||data.site)}</b> <small class=st>${esc(data.timestamp)}</small></div>
                <span class="pill ${sc}">종합판정: ${esc(comp.status)}</span></div>`;
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
            </script></body></html>""";
}
