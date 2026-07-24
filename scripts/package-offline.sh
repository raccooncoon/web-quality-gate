#!/usr/bin/env bash
# =============================================================
# Phase 1: 외부망에서 실행 — 폐쇄망 반입물 패키징
# 산출물: dist/wqg-bundle-<date>.tar.gz
#   ├── web-quality-gate.jar      (shade 빌드 결과)
#   ├── pw-browsers/              (chromium/firefox/webkit 바이너리)
#   ├── vnu.jar                   (W3C Nu Html Checker)
#   └── m2-repo/                  (선택: Nexus 없을 경우 로컬 Maven repo)
# =============================================================
set -euo pipefail

DIST=dist/bundle
rm -rf "$DIST" && mkdir -p "$DIST"

echo "[1/4] 애플리케이션 빌드"
mvn -q clean package
cp target/web-quality-gate-*.jar "$DIST/web-quality-gate.jar"

echo "[2/4] Playwright 브라우저 번들 다운로드"
export PLAYWRIGHT_BROWSERS_PATH="$PWD/$DIST/pw-browsers"
mvn -q exec:java -e \
  -D exec.mainClass=com.microsoft.playwright.CLI \
  -D exec.args="install chromium firefox webkit"

echo "[3/4] vnu.jar 다운로드"
VNU_VERSION=24.10.17
curl -fsSL -o /tmp/vnu.zip \
  "https://github.com/validator/validator/releases/download/${VNU_VERSION}/vnu.jar_${VNU_VERSION}.zip"
unzip -j -o /tmp/vnu.zip "*/vnu.jar" -d "$DIST"

echo "[4/4] 압축"
tar -czf "dist/wqg-bundle-$(date +%Y%m%d).tar.gz" -C dist bundle
echo "완료: dist/wqg-bundle-$(date +%Y%m%d).tar.gz"
