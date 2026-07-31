@echo off
REM ============================================================
REM 반입 번들 완전성 검증 (폐쇄망 윈도우 PC에서 start.bat 전에 1회 실행 권장)
REM 필수 구성요소가 다 들어왔는지, 번들 JRE 로 자바가 뜨는지 확인한다.
REM ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"
set "MISS=0"

echo === web-quality-gate 번들 검증 ===
echo.

call :chk "web-quality-gate.jar"   "애플리케이션 jar"
call :chk "vnu.jar"                "마크업 검사기(vnu)"
call :chk "jre\bin\java.exe"       "번들 JRE(자바)"
call :chk "config\sites.yaml"      "검사 대상 설정"
call :chkdir "pw-browsers"         "Playwright 브라우저 폴더"

REM 3개 엔진 폴더가 실제로 들어왔는지(이름에 버전 접미사가 붙음)
call :chkglob "pw-browsers\chromium*"        "  - chromium 엔진"
call :chkglob "pw-browsers\firefox*"         "  - firefox 엔진"
call :chkglob "pw-browsers\webkit*"          "  - webkit 엔진"

echo.
echo --- 번들 JRE 자바 버전 ---
if exist "jre\bin\java.exe" ( "jre\bin\java.exe" -version ) else ( echo    [없음] jre\bin\java.exe )

echo.
if "%MISS%"=="0" (
  echo [정상] 필수 구성요소가 모두 있습니다.  start.bat 을 실행하세요.
) else (
  echo [주의] 누락 %MISS% 건 — 외부망 PC에서 package-offline.bat 을 다시 실행해 재생성하세요.
)
echo.
pause
exit /b %MISS%

:chk
if exist "%~1" (echo   [OK ] %~2 ^(%~1^)) else (echo   [빠짐] %~2 ^(%~1^) & set /a MISS+=1)
exit /b 0
:chkdir
if exist "%~1\" (echo   [OK ] %~2 ^(%~1\^)) else (echo   [빠짐] %~2 ^(%~1\^) & set /a MISS+=1)
exit /b 0
:chkglob
set "FOUND="
for /d %%d in ("%~1") do set "FOUND=1"
if defined FOUND (echo   [OK ] %~2) else (echo   [빠짐] %~2 & set /a MISS+=1)
exit /b 0
