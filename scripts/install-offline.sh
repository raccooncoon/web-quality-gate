#!/usr/bin/env bash
# =============================================================
# Phase 2: 폐쇄망 리눅스 서버에서 실행 — 반입 번들 설치 + 검증
#
# 번들 안에서 실행:  sudo ./install-offline.sh [스모크테스트_URL]
#   - deps/ 의 OS 라이브러리·한글 폰트를 오프라인 설치(dpkg/rpm, 인터넷 불필요)
#   - 폰트 캐시 갱신, Playwright 브라우저 ldd 검증
#   - (URL 지정 시) 실제 접속 스모크 테스트
#
# 이후 실행:  ./start.sh serve 8091   또는   ./start.sh config/sites.yaml
# =============================================================
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SMOKE_URL="${1:-}"

echo "[1/4] OS 라이브러리·폰트 오프라인 설치 (deps/)"
if ls "$HERE"/deps/*.deb >/dev/null 2>&1; then
  echo "   .deb 설치 (dpkg, 오프라인)"
  sudo dpkg -i "$HERE"/deps/*.deb 2>/dev/null || sudo dpkg --configure -a || true
elif ls "$HERE"/deps/*.rpm >/dev/null 2>&1; then
  echo "   .rpm 설치 (오프라인)"
  if command -v dnf >/dev/null 2>&1; then
    sudo dnf localinstall -y --disablerepo='*' "$HERE"/deps/*.rpm 2>/dev/null \
      || sudo rpm -Uvh --replacepkgs --nodeps "$HERE"/deps/*.rpm || true
  else
    sudo rpm -Uvh --replacepkgs "$HERE"/deps/*.rpm 2>/dev/null \
      || sudo rpm -Uvh --replacepkgs --nodeps "$HERE"/deps/*.rpm || true
  fi
else
  echo "   !! deps/ 에 .deb/.rpm 이 없습니다. 서버와 같은 배포판에서 재패키징하세요." >&2
fi

echo "[2/4] 폰트 캐시 갱신 (한글 렌더링)"
command -v fc-cache >/dev/null 2>&1 && sudo fc-cache -f >/dev/null 2>&1 && echo "   갱신 완료" \
  || echo "   (fc-cache 없음 — 무시)"

echo "[3/4] Playwright 브라우저 OS 의존성 검증 (ldd)"
export PLAYWRIGHT_BROWSERS_PATH="$HERE/pw-browsers"
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
CHROME_BIN="$(find "$PLAYWRIGHT_BROWSERS_PATH" -type f -name chrome 2>/dev/null | head -1)"
if [ -z "$CHROME_BIN" ]; then
  echo "   !! chromium 바이너리를 찾지 못함 — pw-browsers/ 반입 확인" >&2; exit 1
fi
MISSING="$(ldd "$CHROME_BIN" 2>/dev/null | grep 'not found' || true)"
if [ -n "$MISSING" ]; then
  echo "   !! 아직 없는 라이브러리:" >&2
  echo "$MISSING" >&2
  echo "   -> 서버 배포판/버전이 빌드 머신과 다르면 발생. 같은 배포판에서 재패키징 필요." >&2
  exit 1
fi
echo "   OS 라이브러리 이상 없음 ✓"

echo "[4/4] 스모크 테스트"
export WQG_VNU_JAR="$HERE/vnu.jar"
JAVA="$HERE/jre/bin/java"; [ -x "$JAVA" ] || JAVA=java
if [ -n "$SMOKE_URL" ]; then
  "$JAVA" -jar "$HERE/web-quality-gate.jar" --smoke "$SMOKE_URL" \
    || echo "   (--smoke 미지원 빌드면 무시) 대신 ./start.sh serve 8091 로 콘솔을 띄워 확인하세요."
else
  echo "   URL 미지정 — 건너뜀. 콘솔: ./start.sh serve 8091  |  CLI: ./start.sh config/sites.yaml"
fi
echo "설치·검증 완료 ✓"
