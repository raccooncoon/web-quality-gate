package com.kt.wqg.check.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 존재-검사 기반 자동 스크리닝 프로브(안전 자동화).
 * 한 번의 page.evaluate 로 각 KWCAG 항목의 "대상 요소" 유무와 명백한 위반을 감지한다.
 *   - 대상 없음  → Finding 을 내지 않음 → 상위에서 '해당없음'
 *   - 명백한 위반 → kind="violation" → '부적합'
 *   - 대상 있으나 품질 판단 필요 → kind="review" → '수동확인필요'
 * 규칙 이름은 kwcag22-catalog.yaml 의 resolver.rules 와 일치시킨다.
 */
public final class HeuristicProbe implements PageProbe {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public String id() { return "heuristic"; }

    @Override
    public List<Finding> run(Page page, String url) {
        Object raw = page.evaluate(JS);
        JsonNode d;
        try {
            d = JSON.readTree(String.valueOf(raw));
        } catch (Exception e) {
            return List.of();
        }
        List<Finding> out = new ArrayList<>();

        // 1.2.1 자막: 미디어 있고 caption 트랙 없으면 부적합, 있으면 품질 검토
        int media = d.path("media").asInt(), noTrack = d.path("mediaNoTrack").asInt();
        if (media > 0) {
            if (noTrack > 0) out.add(v(url, "media-captions", "자막/대체 트랙 없는 미디어 " + noTrack + "개"));
            else out.add(r(url, "media-captions", "미디어 " + media + "개 — 자막 내용 적절성 확인 필요"));
        }
        // 3.2.1 새 창: target=_blank / window.open 있으면 검토(사전 안내 여부)
        int nw = d.path("newWindow").asInt();
        if (nw > 0) out.add(r(url, "new-window", "새 창/탭 여는 링크·동작 " + nw + "개 — 사전 안내 확인 필요"));
        // 2.2.1 응답시간: meta refresh(자동 새로고침/이동) 있으면 부적합
        if (d.path("metaRefresh").asBoolean()) out.add(v(url, "meta-refresh", "meta refresh 자동 새로고침/이동 사용"));
        // 2.2.2 정지 기능: 자동재생/움직임 있으면 검토(정지 수단 여부)
        int anim = d.path("autoAnim").asInt();
        if (anim > 0) out.add(r(url, "auto-animation", "자동 재생/움직임 요소 " + anim + "개 — 정지 수단 확인 필요"));
        // 2.3.1 깜빡임: blink/marquee 는 부적합
        int blink = d.path("blinkMarquee").asInt();
        if (blink > 0) out.add(v(url, "blink-flash", "blink/marquee 사용 " + blink + "개"));
        // 2.1.4 문자 단축키: accesskey 있으면 검토(끄기/재지정 제공 여부)
        int ak = d.path("accesskey").asInt();
        if (ak > 0) out.add(r(url, "accesskey", "accesskey 단축키 " + ak + "개 — 끄기/재지정 확인 필요"));
        // 2.5.4 동작 기반: devicemotion/orientation 사용 시 검토
        if (d.path("deviceMotion").asBoolean()) out.add(r(url, "devicemotion", "devicemotion/deviceorientation 사용 — 대체 수단 확인 필요"));
        // 2.1.1 키보드 사용: 인라인 onclick 만으로는 JS 키보드 핸들러를 못 보므로 자동 부적합 대신 검토(오탐 방지)
        int mouseOnly = d.path("mouseOnly").asInt(), interactive = d.path("interactive").asInt();
        if (mouseOnly > 0) out.add(r(url, "keyboard-operable", "마우스 전용 의심 클릭 요소 " + mouseOnly + "개(비포커스·인라인 키핸들러 없음) — 키보드 조작 확인 필요"));
        else if (interactive > 0) out.add(r(url, "keyboard-operable", "상호작용 요소 " + interactive + "개 — 전체 키보드 조작 확인 필요"));
        // 1.3.2 선형 구조: 양수 tabindex 는 읽기순서 훼손 우려(자동 부적합은 과하므로 검토)
        int posTab = d.path("posTabindex").asInt();
        if (posTab > 0) out.add(r(url, "linear-order", "양수 tabindex " + posTab + "개 — 읽기/초점 순서 훼손 우려, 확인 필요"));
        else out.add(r(url, "linear-order", "콘텐츠 선형 순서 적절성 확인 필요"));
        // 3.3.1 오류 정정 / 3.3.3 인증: 폼/비밀번호 있을 때만 검토
        if (d.path("form").asBoolean()) out.add(r(url, "form-validation", "입력 폼 존재 — 오류 안내/정정 수단 확인 필요"));
        if (d.path("password").asBoolean()) out.add(r(url, "auth", "인증(비밀번호) 폼 존재 — 접근 가능한 인증 확인 필요"));
        // 2.5.1/2.5.2 포인터 제스처: 드래그/터치 제스처 있을 때만 검토
        if (d.path("gesture").asBoolean()) out.add(r(url, "pointer-gesture", "드래그/포인터 제스처 사용 — 단일 포인터 대안·취소 확인 필요"));

        return out;
    }

    private static Finding v(String url, String rule, String help) { return new Finding(url, rule, help, null, "violation"); }
    private static Finding r(String url, String rule, String help) { return new Finding(url, rule, help, null, "review"); }

    // 페이지에서 각 항목의 대상 존재/위반 신호를 한 번에 수집. 성능 위해 요소 스캔은 상한을 둔다.
    private static final String JS = """
        () => {
          const q = s => document.querySelectorAll(s);
          const html = document.documentElement.innerHTML;
          // 미디어 자막
          const medias = [...q('video, audio')];
          const noTrack = medias.filter(m => !m.querySelector('track[kind="captions"], track[kind="subtitles"]')).length;
          // 새 창
          let newWindow = q('a[target="_blank"], area[target="_blank"]').length;
          newWindow += [...q('[onclick]')].filter(e => /window\\.open|target\\s*=\\s*['"]?_blank/i.test(e.getAttribute('onclick')||'')).length;
          // meta refresh
          const metaRefresh = [...q('meta[http-equiv]')].some(m =>
            (m.getAttribute('http-equiv')||'').toLowerCase()==='refresh' && /\\d/.test(m.getAttribute('content')||''));
          // blink / marquee
          const blinkMarquee = q('blink, marquee').length;
          // accesskey
          const accesskey = q('[accesskey]').length;
          // devicemotion (인라인 스크립트/속성 스캔)
          const deviceMotion = /devicemotion|deviceorientation/i.test(html);
          // 양수 tabindex
          const posTabindex = [...q('[tabindex]')].filter(e => (parseInt(e.getAttribute('tabindex'),10)||0) > 0).length;
          // 폼 / 비밀번호
          const form = q('form, input, select, textarea').length > 0;
          const password = q('input[type="password"]').length > 0;
          // 제스처
          const gesture = q('[draggable="true"]').length > 0 || /touchstart|pointerdown|gesturestart|ontouchmove/i.test(html);
          // 마우스 전용 클릭 핸들러(비포커스 요소 + 키핸들러 없음) & 상호작용 요소 수 (상한 4000)
          const NATIVE = new Set(['A','BUTTON','INPUT','SELECT','TEXTAREA','SUMMARY','AREA']);
          const all = [...q('*')].slice(0, 4000);
          let mouseOnly = 0, interactive = 0, autoAnim = 0;
          for (const e of all) {
            const tag = e.tagName;
            const focusable = NATIVE.has(tag) || e.hasAttribute('tabindex') || e.getAttribute('role');
            if (NATIVE.has(tag) || e.hasAttribute('onclick') || e.getAttribute('role')==='button' || e.getAttribute('role')==='link') interactive++;
            if (e.hasAttribute('onclick') && !focusable && !e.hasAttribute('onkeydown') && !e.hasAttribute('onkeypress')) mouseOnly++;
            // 자동 재생/무한 애니메이션
            if ((tag==='VIDEO'||tag==='AUDIO') && e.hasAttribute('autoplay')) { autoAnim++; continue; }
            try { const cs = getComputedStyle(e);
              if (cs.animationName && cs.animationName!=='none' && cs.animationIterationCount==='infinite') autoAnim++;
            } catch(_) {}
          }
          return JSON.stringify({ media: medias.length, mediaNoTrack: noTrack, newWindow, metaRefresh,
            autoAnim, blinkMarquee, accesskey, deviceMotion, mouseOnly, interactive, posTabindex,
            form, password, gesture });
        }""";
}
