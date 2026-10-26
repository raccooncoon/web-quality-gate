@echo off
chcp 65001 >nul
REM ============================================================
REM web-quality-gate 드라이버 진단기 (폐쇄망 윈도우 PC)
REM  'Failed to create driver' 등 로그인/브라우저 기동 오류가 날 때 실행.
REM  - 환경/브라우저 번들 확인
REM  - Playwright 드라이버 생성 시도(전체 원인 스택 출력)
REM  - 추출된 node 실행 파일을 직접 --version 실행해 'node 가 실제로 도는지' 확정
REM  화면에는 단계별 요약([OK]/[FAIL]/[HINT])만 표시하고, 전체는 logs\diag-latest.log(최신 회차)에 저장.
REM  해결이 안 되면 그 파일 하나를 그대로 보내면 원인 파악 가능.
REM ============================================================
setlocal
cd /d "%~dp0"

set "PLAYWRIGHT_BROWSERS_PATH=%~dp0pw-browsers"
set "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1"
set "WQG_VNU_JAR=%~dp0vnu.jar"

set "JAVA=%~dp0jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

if not exist "%~dp0logs" mkdir "%~dp0logs"
set "TS=%RANDOM%"
for /f "tokens=2 delims==." %%i in ('wmic os get localdatetime /value 2^>nul') do set "DT=%%i"
if defined DT set "TS=%DT:~0,8%-%DT:~8,6%"
set "LOG=%~dp0logs\diag-%TS%.log"
set "LATEST=%~dp0logs\diag-latest.log"

echo 드라이버 진단을 실행합니다. 잠시 기다려 주세요 ...
echo.
"%JAVA%" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0web-quality-gate.jar" doctor > "%LOG%" 2>&1
set "RC=%ERRORLEVEL%"

REM 최신 회차를 고정 이름으로도 남긴다 — 보낼 때 이 파일 하나만 첨부하면 된다.
copy /y "%LOG%" "%LATEST%" >nul

REM 스택트레이스까지 다 쏟지 않고, doctor 가 끝에 남긴 단계별 요약([OK]/[FAIL]/[HINT])만 보여준다.
REM (findstr 은 한글 검색어가 불안정해 ASCII 표식으로 고른다)
echo ===================== 진단 요약 =====================
findstr /b /l /c:"[OK]" /c:"[FAIL]" /c:"[HINT]" "%LOG%" || echo [요약 없음] 진단이 도중에 중단됐습니다 - 로그 전체를 확인하세요.
echo ======================================================
if "%RC%"=="0" (echo 결과: 정상 - 드라이버·브라우저 모두 동작합니다.) else (echo 결과: 문제 있음 - [HINT] 조치 후 다시 실행하세요.)
echo.
echo 전체 로그: %LATEST%
echo 해결이 안 되면 이 파일 하나를 그대로 첨부해 보내주세요.
echo.
choice /c YN /n /m "전체 로그를 메모장으로 열까요? [Y/N] "
if not errorlevel 2 start "" notepad "%LATEST%"
endlocal & exit /b %RC%
