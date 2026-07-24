#!/usr/bin/env bash
# =============================================================
# Phase 2: 폐쇄망 서버에서 실행 — 반입물 설치 + 스모크 테스트
# 사용법: ./install-offline.sh wqg-bundle-YYYYMMDD.tar.gz [스모크테스트 URL]
# =============================================================
set -euo pipefail

BUNDLE="${1:?반입 번들 경로를 지정하세요}"
SMOKE_URL="${2:-}"
INSTALL_DIR=/opt/wqg

echo "[1/3] 번들 해제 → $INSTALL_DIR"
sudo mkdir -p "$INSTALL_DIR"
sudo tar -xzf "$BUNDLE" -C "$INSTALL_DIR" --strip-components=1

echo "[2/3] 브라우저 OS 의존 라이브러리 확인 (폐쇄망 최다 실패 지점)"
export PLAYWRIGHT_BROWSERS_PATH="$INSTALL_DIR/pw-browsers"
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
CHROME_BIN=$(find "$PLAYWRIGHT_BROWSERS_PATH" -name chrome -type f | head -1)
if ldd "$CHROME_BIN" | grep "not found"; then
  echo "!! 위 라이브러리 rpm/deb 추가 반입 필요" >&2
  exit 1
fi
echo "   OS 라이브러리 이상 없음"

echo "[3/3] 스모크 테스트"
if [ -n "$SMOKE_URL" ]; then
  java -jar "$INSTALL_DIR/web-quality-gate.jar" --smoke "$SMOKE_URL"
else
  echo "   URL 미지정 — 건너뜀 (예: ./install-offline.sh bundle.tar.gz http://10.0.0.1)"
fi
echo "설치 완료"
