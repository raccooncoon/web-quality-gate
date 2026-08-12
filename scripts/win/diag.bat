@echo off
chcp 65001 >nul
REM ============================================================
REM web-quality-gate 드라이버 진단기 (폐쇄망 윈도우 PC)
REM  'Failed to create driver' 등 로그인/브라우저 기동 오류가 날 때 실행.
REM  - 환경/브라우저 번들 확인
REM  - Playwright 드라이버 생성 시도(전체 원인 스택 출력)
REM  - 추출된 node 실행 파일을 직접 --version 실행해 'node 가 실제로 도는지' 확정
REM  결과는 화면에 표시되고 logs\ 폴더에도 저장된다. 그 파일을 그대로 보내면 원인 파악 가능.
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

echo 드라이버 진단을 실행합니다. 잠시 기다려 주세요 ...
echo 결과 로그: %LOG%
echo.
"%JAVA%" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0web-quality-gate.jar" doctor > "%LOG%" 2>&1

type "%LOG%"
echo.
echo ============================================================
echo 위 내용이 로그 파일에도 저장되었습니다:
echo   %LOG%
echo 이 파일을 그대로 첨부해 보내주세요.
echo ============================================================
pause
endlocal
