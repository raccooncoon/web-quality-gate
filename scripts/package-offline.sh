#!/usr/bin/env bash
# =============================================================
# Phase 1: 외부망 리눅스에서 실행 — 폐쇄망(리눅스) 반입물 패키징
#
# 산출물: dist/wqg-bundle-<date>.tar.gz  (아래를 모두 포함한 자립형 번들)
#   ├── web-quality-gate.jar      shade 단일 jar
#   ├── jre/                      최소 JRE 17 (jlink)               ← 서버에 Java 없어도 됨
#   ├── pw-browsers/              chromium/firefox/webkit 바이너리
#   ├── deps/                     Playwright 실행용 OS 라이브러리(.deb/.rpm) + 한글 폰트  ← 핵심
#   ├── vnu.jar                   W3C Nu Html Checker
#   ├── config/sites.yaml         시스템 템플릿
#   ├── start.sh                  실행기(환경변수 자동 세팅)
#   └── install-offline.sh        폐쇄망 설치기(deps 설치 + 검증)
#
# ★ 중요: 이 스크립트는 반드시 **폐쇄망 서버와 같은 배포판·같은 버전**의
#   외부망 리눅스에서 실행하세요(예: 둘 다 Ubuntu 22.04, 또는 둘 다 Rocky 9).
#   OS 라이브러리(.deb/.rpm)는 배포판/버전이 맞아야 설치됩니다.
#
# 요구(외부망 빌드 머신): JDK 17, Maven, 인터넷, 그리고
#   - Debian/Ubuntu 계열: apt-get, apt-cache, dpkg
#   - RHEL/Rocky/CentOS 계열: dnf(권장) 또는 yum + yum-utils
# =============================================================
set -euo pipefail
cd "$(dirname "$0")/.."   # 프로젝트 루트

DIST=dist/bundle
rm -rf "$DIST" && mkdir -p "$DIST/deps" "$DIST/config"

echo "[1/6] 애플리케이션 빌드"
mvn -q clean package
cp target/web-quality-gate-*.jar "$DIST/web-quality-gate.jar"
cp config/sites.yaml "$DIST/config/sites.yaml"

echo "[2/6] 최소 JRE 17 생성 (jlink) — 서버에 Java 미설치여도 실행 가능"
JLINK="${JAVA_HOME:+$JAVA_HOME/bin/}jlink"
"$JLINK" --add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.net.http,jdk.httpserver,jdk.crypto.ec,jdk.unsupported \
  --no-header-files --no-man-pages --strip-debug --output "$DIST/jre"

echo "[3/6] Playwright 브라우저 번들 다운로드"
export PLAYWRIGHT_BROWSERS_PATH="$PWD/$DIST/pw-browsers"
mvn -q exec:java -e \
  -D exec.mainClass=com.microsoft.playwright.CLI \
  -D exec.args="install chromium firefox webkit"

echo "[4/6] vnu.jar 다운로드"
VNU_VERSION=24.10.17
curl -fsSL -o /tmp/vnu.zip \
  "https://github.com/validator/validator/releases/download/${VNU_VERSION}/vnu.jar_${VNU_VERSION}.zip"
unzip -j -o /tmp/vnu.zip "*/vnu.jar" -d "$DIST"

echo "[5/6] Playwright 실행용 OS 라이브러리 + 한글 폰트 수집 (폐쇄망 최다 실패 지점)"
DEPS="$PWD/$DIST/deps"
if command -v apt-get >/dev/null 2>&1; then
  echo "   -> Debian/Ubuntu 계열 (.deb) 수집"
  # Playwright(chromium+firefox+webkit) 의존 패키지 + 한글/이모지 폰트.
  # 버전마다 이름이 다른 패키지(libvpx7/6, libx264-163/160 등)는 실패해도 건너뛰고,
  # 실제 브라우저 바이너리의 ldd 결과로도 보강한다.
  APT_PKGS="libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 libdbus-1-3 \
    libatspi2.0-0 libx11-6 libxcomposite1 libxdamage1 libxext6 libxfixes3 libxrandr2 libxcb1 \
    libxkbcommon0 libgbm1 libpango-1.0-0 libcairo2 libasound2 libwayland-client0 \
    libgtk-3-0 libgdk-pixbuf-2.0-0 libx11-xcb1 libxcursor1 libxi6 libxtst6 libdbus-glib-1-2 \
    libgstreamer1.0-0 libgstreamer-plugins-base1.0-0 libgstreamer-gl1.0-0 \
    libwoff1 libvpx7 libvpx6 libevent-2.1-7 libopus0 libwebp7 libwebp6 libharfbuzz-icu0 \
    libenchant-2-2 libsecret-1-0 libhyphen0 libmanette-0.2-0 libgles2 libglx0 libgl1 \
    libx264-163 libx264-160 libflite1 \
    fonts-liberation fonts-unifont fonts-noto-color-emoji fonts-noto-cjk"

  # 1) 실제 브라우저 바이너리가 링크하는 .so → 제공 패키지 역추적(버전 정확)
  LDD_PKGS=""
  find "$PLAYWRIGHT_BROWSERS_PATH" -type f -perm -u+x 2>/dev/null \
    | while IFS= read -r bin; do ldd "$bin" 2>/dev/null | awk '/=>/{print $3}'; done \
    | grep -E '^/' | sort -u > /tmp/wqg-so.txt || true
  if [ -s /tmp/wqg-so.txt ]; then
    while IFS= read -r so; do dpkg -S "$so" 2>/dev/null; done < /tmp/wqg-so.txt \
      | awk -F: '{print $1}' | sort -u > /tmp/wqg-ldpkgs.txt || true
    LDD_PKGS="$(tr '\n' ' ' < /tmp/wqg-ldpkgs.txt)"
  fi

  # 2) 큐레이트 + ldd 역추적 목록의 재귀 의존 폐쇄집합을 .deb 로 내려받기
  ALL_PKGS="$APT_PKGS $LDD_PKGS"
  if command -v apt-cache >/dev/null 2>&1; then
    CLOSURE="$(apt-cache depends --recurse --no-recommends --no-suggests --no-conflicts \
      --no-breaks --no-replaces --no-enhances $ALL_PKGS 2>/dev/null \
      | grep -E '^[a-zA-Z0-9]' | sort -u)"
  else
    CLOSURE="$ALL_PKGS"
  fi
  ( cd "$DEPS" && for p in $CLOSURE; do apt-get download "$p" 2>/dev/null || true; done )
  echo "   수집된 .deb: $(ls "$DEPS"/*.deb 2>/dev/null | wc -l) 개"

elif command -v dnf >/dev/null 2>&1 || command -v yum >/dev/null 2>&1; then
  echo "   -> RHEL/Rocky/CentOS 계열 (.rpm) 수집"
  RPM_PKGS="nss nspr atk at-spi2-atk cups-libs libdrm dbus-libs at-spi2-core libX11 \
    libXcomposite libXdamage libXext libXfixes libXrandr libxcb libxkbcommon mesa-libgbm \
    pango cairo alsa-lib libwayland-client gtk3 gdk-pixbuf2 libXcursor libXi libXtst \
    gstreamer1 gstreamer1-plugins-base libwebp libsecret enchant2 hyphen \
    mesa-libGLES mesa-libGL libX11-xcb \
    google-noto-sans-cjk-ttc-fonts liberation-fonts google-noto-emoji-color-fonts"
  if command -v dnf >/dev/null 2>&1; then
    dnf download --resolve --alldeps --destdir "$DEPS" $RPM_PKGS 2>/dev/null || \
      dnf install --downloadonly --downloaddir="$DEPS" -y $RPM_PKGS 2>/dev/null || true
  else
    yum install -y yum-utils 2>/dev/null || true
    yumdownloader --resolve --destdir "$DEPS" $RPM_PKGS 2>/dev/null || true
  fi
  echo "   수집된 .rpm: $(ls "$DEPS"/*.rpm 2>/dev/null | wc -l) 개"
else
  echo "   !! apt/dnf/yum 를 못 찾음 — OS 라이브러리 수집 생략." >&2
  echo "      폐쇄망 서버와 같은 배포판의 리눅스에서 이 스크립트를 실행하세요." >&2
fi

echo "[6/6] 실행기·설치기 동봉 + 압축"
cat > "$DIST/start.sh" <<'RUN'
#!/usr/bin/env bash
# 폐쇄망 서버 실행기. 예) ./start.sh serve 8091   또는   ./start.sh config/sites.yaml
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
export PLAYWRIGHT_BROWSERS_PATH="$HERE/pw-browsers"
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
export WQG_VNU_JAR="$HERE/vnu.jar"
JAVA="$HERE/jre/bin/java"; [ -x "$JAVA" ] || JAVA=java
exec "$JAVA" -jar "$HERE/web-quality-gate.jar" "$@"
RUN
chmod +x "$DIST/start.sh"
cp scripts/install-offline.sh "$DIST/install-offline.sh"
chmod +x "$DIST/install-offline.sh"

OUT="dist/wqg-bundle-$(date +%Y%m%d).tar.gz"
tar -czf "$OUT" -C dist bundle
echo ""
echo "완료: $OUT"
echo "  폐쇄망 반입 후: tar xzf $(basename "$OUT") && cd bundle && sudo ./install-offline.sh"
