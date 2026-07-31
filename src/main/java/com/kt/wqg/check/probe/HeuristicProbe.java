package com.kt.wqg.check.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 존재-검사 기반 자동 스크리닝 프로브(안전 자동화).
 * 한 번의 page.evaluate 로 각 KWCAG 항목의 "대상 요소" 유무·위반과 실제 요소 샘플을 수집한다.
 *   - 대상 없음  → Finding 없음 → 상위에서 '해당없음'
 *   - 명백한 위반 → kind="violation" → '부적합'
 *   - 대상 있으나 품질 판단 필요 → kind="review" → '수동확인필요'
 * 증거 help 에 실제 대상 샘플(링크 텍스트·href, 미디어 파일, 폼 필드 등)을 함께 담아
 * 평가자·AI 가 근거를 구체적으로 확인할 수 있게 한다.
 */
public final class HeuristicProbe implements PageProbe {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public String id() { return "heuristic"; }

    @Override
    public List<Finding> run(Page page, String url) {
        JsonNode d;
        try {
            d = JSON.readTree(String.valueOf(page.evaluate(JS)));
        } catch (Exception e) {
            return List.of();
        }
        List<Finding> out = new ArrayList<>();

        int media = d.path("media").asInt(), noTrack = d.path("mediaNoTrack").asInt();
        if (media > 0) {
            if (noTrack > 0) out.add(v(url, "media-captions", "자막/대체 트랙 없는 미디어 " + noTrack + "개" + ex(d, "media")));
            else out.add(r(url, "media-captions", "미디어 " + media + "개 — 자막 내용 적절성 확인 필요" + ex(d, "media")));
        }
        int nw = d.path("newWindow").asInt();
        if (nw > 0) out.add(r(url, "new-window", "새 창/탭 여는 링크·동작 " + nw + "개 — 사전 안내 확인 필요" + ex(d, "newWindow")));
        if (d.path("metaRefresh").asBoolean())
            out.add(v(url, "meta-refresh", "meta refresh 자동 새로고침/이동 사용" + ex(d, "metaRefresh")));
        int anim = d.path("autoAnim").asInt();
        if (anim > 0) out.add(r(url, "auto-animation", "자동 재생/움직임 요소 " + anim + "개 — 정지 수단 확인 필요" + ex(d, "autoAnim")));
        int blink = d.path("blinkMarquee").asInt();
        if (blink > 0) out.add(v(url, "blink-flash", "blink/marquee 사용 " + blink + "개" + ex(d, "blink")));
        int ak = d.path("accesskey").asInt();
        if (ak > 0) out.add(r(url, "accesskey", "accesskey 단축키 " + ak + "개 — 끄기/재지정 확인 필요" + ex(d, "accesskey")));
        if (d.path("deviceMotion").asBoolean())
            out.add(r(url, "devicemotion", "devicemotion/deviceorientation 사용 — 대체 수단 확인 필요"));
        int mouseOnly = d.path("mouseOnly").asInt(), interactive = d.path("interactive").asInt();
        if (mouseOnly > 0) out.add(r(url, "keyboard-operable", "마우스 전용 의심 클릭 요소 " + mouseOnly + "개(비포커스·인라인 키핸들러 없음) — 키보드 조작 확인 필요" + ex(d, "mouseOnly")));
        else if (interactive > 0) out.add(r(url, "keyboard-operable", "상호작용 요소 " + interactive + "개 — 전체 키보드 조작 확인 필요"));
        int posTab = d.path("posTabindex").asInt();
        // 양수 tabindex 는 읽기/초점 순서를 훼손하는 명백한 안티패턴(axe 도 위반 처리) → 자동 부적합.
        if (posTab > 0) out.add(v(url, "linear-order", "양수 tabindex " + posTab + "개 — 읽기/초점 순서 훼손(명백한 위반)" + ex(d, "posTabindex")));
        else out.add(r(url, "linear-order", "콘텐츠 선형 순서 적절성 확인 필요"));
        if (d.path("form").asBoolean())
            out.add(r(url, "form-validation", "입력 폼 존재 — 오류 안내/정정 수단 확인 필요" + ex(d, "form")));
        if (d.path("password").asBoolean())
            out.add(r(url, "auth", "인증(비밀번호) 폼 존재 — 접근 가능한 인증 확인 필요" + ex(d, "auth")));
        if (d.path("gesture").asBoolean())
            out.add(r(url, "pointer-gesture", "드래그/포인터 제스처 사용 — 단일 포인터 대안·취소 확인 필요" + ex(d, "gesture")));

        return out;
    }

    /** samples[key] 배열을 " · 예: a, b, c" 로. */
    private static String ex(JsonNode d, String key) {
        JsonNode arr = d.path("samples").path(key);
        if (!arr.isArray() || arr.isEmpty()) return "";
        List<String> s = new ArrayList<>();
        arr.forEach(n -> { String t = n.asText().trim(); if (!t.isEmpty()) s.add(t); });
        return s.isEmpty() ? "" : " · 예) " + String.join(" / ", s);
    }

    private static Finding v(String url, String rule, String help) { return new Finding(url, rule, help, null, "violation"); }
    private static Finding r(String url, String rule, String help) { return new Finding(url, rule, help, null, "review"); }

    // 각 항목의 대상 존재/위반 신호 + 실제 요소 샘플을 한 번에 수집. 요소 스캔은 상한을 둔다.
    private static final String JS = """
        () => {
          const q = s => document.querySelectorAll(s);
          const html = document.documentElement.innerHTML;
          const txt = e => ((e.innerText||e.value||e.getAttribute&&(e.getAttribute('aria-label')||e.getAttribute('title')||e.getAttribute('alt'))||'')+'').replace(/\\s+/g,' ').trim().slice(0,28);
          // 요소의 고유 CSS 경로(위치를 정확히 짚기 위함) — id 있으면 즉시 종료, 없으면 class 1~2개 + nth-of-type
          const sel = e => {
            if (!e || e.nodeType !== 1) return '';
            const seg = n => { let s = n.tagName.toLowerCase();
              if (n.id) return s + '#' + n.id;
              if (typeof n.className === 'string' && n.className.trim())
                s += '.' + n.className.trim().split(/\\s+/).slice(0,2).join('.');
              const p = n.parentElement;
              if (p) { const same = [...p.children].filter(c => c.tagName === n.tagName);
                if (same.length > 1) s += ':nth-of-type(' + (same.indexOf(n)+1) + ')'; }
              return s; };
            const parts = []; let cur = e, depth = 0;
            while (cur && cur.nodeType === 1 && cur.tagName !== 'BODY' && depth < 4) {
              parts.unshift(seg(cur)); if (cur.id) break; cur = cur.parentElement; depth++; }
            return parts.join('>'); };
          const S = (list, fn, n=3) => list.slice(0,n).map(fn).filter(Boolean);
          const samples = {};

          const medias = [...q('video, audio')];
          const noTrack = medias.filter(m => !m.querySelector('track[kind="captions"], track[kind="subtitles"]')).length;
          samples.media = S(medias, m => sel(m)+' ('+(((m.currentSrc||m.getAttribute('src')||(m.querySelector('source')&&m.querySelector('source').getAttribute('src'))||'src?')+'').split('/').pop())+')');

          const nwLinks = [...q('a[target="_blank"], area[target="_blank"]')];
          let newWindow = nwLinks.length;
          const nwClick = [...q('[onclick]')].filter(e => /window\\.open|target\\s*=\\s*['"]?_blank/i.test(e.getAttribute('onclick')||''));
          newWindow += nwClick.length;
          samples.newWindow = S([...nwLinks, ...nwClick], a => sel(a)+" '"+(txt(a)||'무텍스트')+"'→"+(a.getAttribute('href')||'onclick'));

          const metaRefresh = [...q('meta[http-equiv]')].some(m =>
            (m.getAttribute('http-equiv')||'').toLowerCase()==='refresh' && /\\d/.test(m.getAttribute('content')||''));
          const mr = [...q('meta[http-equiv]')].find(m => (m.getAttribute('http-equiv')||'').toLowerCase()==='refresh');
          samples.metaRefresh = mr ? ['content='+(mr.getAttribute('content')||'')] : [];

          const blinkEls = [...q('blink, marquee')];
          const blinkMarquee = blinkEls.length;
          samples.blink = S(blinkEls, e => sel(e)+" '"+txt(e)+"'");

          const akEls = [...q('[accesskey]')];
          const accesskey = akEls.length;
          samples.accesskey = S(akEls, e => sel(e)+"[accesskey="+e.getAttribute('accesskey')+"] '"+txt(e)+"'");

          const deviceMotion = /devicemotion|deviceorientation/i.test(html);
          const posEls = [...q('[tabindex]')].filter(e => (parseInt(e.getAttribute('tabindex'),10)||0) > 0);
          const posTabindex = posEls.length;
          samples.posTabindex = S(posEls, e => sel(e)+"[tabindex="+e.getAttribute('tabindex')+"]");

          const forms = [...q('form')];
          const form = forms.length>0 || q('input:not([type=hidden]), select, textarea').length>0;
          samples.form = forms.length
            ? S(forms, f => "form"+(f.getAttribute('action')?'→'+f.getAttribute('action'):'')+" 필드"+f.querySelectorAll('input:not([type=hidden]),select,textarea').length+"개")
            : S([...q('input:not([type=hidden]), select, textarea')], e => e.tagName.toLowerCase()+(e.type?'['+e.type+']':''), 5);
          const pw = [...q('input[type="password"]')];
          const password = pw.length>0;
          samples.auth = S([...q('form')].filter(f=>f.querySelector('input[type=password]')), f => "로그인 폼 필드"+f.querySelectorAll('input:not([type=hidden])').length+"개");

          const gestureTok = (html.match(/touchstart|pointerdown|gesturestart|ontouchmove/i)||[])[0];
          const gesture = q('[draggable="true"]').length > 0 || !!gestureTok;
          samples.gesture = gestureTok ? [gestureTok+' 사용'] : (q('[draggable="true"]').length?['draggable 요소']:[]);

          const NATIVE = new Set(['A','BUTTON','INPUT','SELECT','TEXTAREA','SUMMARY','AREA']);
          const all = [...q('*')].slice(0, 4000);
          let mouseOnly = 0, interactive = 0, autoAnim = 0;
          const moS = [], animS = [];
          for (const e of all) {
            const tag = e.tagName;
            const focusable = NATIVE.has(tag) || e.hasAttribute('tabindex') || e.getAttribute('role');
            if (NATIVE.has(tag) || e.hasAttribute('onclick') || e.getAttribute('role')==='button' || e.getAttribute('role')==='link') interactive++;
            if (e.hasAttribute('onclick') && !focusable && !e.hasAttribute('onkeydown') && !e.hasAttribute('onkeypress')) {
              mouseOnly++; if (moS.length<3) moS.push(sel(e)+" '"+txt(e)+"'");
            }
            if ((tag==='VIDEO'||tag==='AUDIO') && e.hasAttribute('autoplay')) { autoAnim++; if(animS.length<3)animS.push(sel(e)+' [autoplay]'); continue; }
            try { const cs = getComputedStyle(e);
              if (cs.animationName && cs.animationName!=='none' && cs.animationIterationCount==='infinite') { autoAnim++; if(animS.length<3)animS.push(sel(e)+' · 무한 애니메이션'); }
            } catch(_) {}
          }
          samples.mouseOnly = moS; samples.autoAnim = animS;

          return JSON.stringify({ media: medias.length, mediaNoTrack: noTrack, newWindow, metaRefresh,
            autoAnim, blinkMarquee, accesskey, deviceMotion, mouseOnly, interactive, posTabindex,
            form, password, gesture, samples });
        }""";
}
