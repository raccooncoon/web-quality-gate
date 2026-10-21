@echo off
chcp 65001 >nul
REM ============================================================
REM web-quality-gate 웹 콘솔 실행기 (윈도우 로컬 PC, 폐쇄망)
REM 이 파일을 패키지 최상위에 두고 더블클릭하면
REM   - 번들 JRE + 번들 브라우저로 웹 콘솔을 띄우고
REM   - 기본 브라우저로 http://localhost:8080 을 연다
REM ============================================================

REM 내부 호출 전용(아래 start 가 최소화 창으로 실행): 서버 포트가 LISTEN 될 때까지
REM 1초 간격으로 최대 120초 기다렸다가 브라우저를 연다. 느린 PC 에서 서버보다 브라우저가
REM 먼저 열려 '연결할 수 없음'이 뜨던 문제 방지. netstat 은 상태 문구가 OS 언어별로 달라
REM 'LISTENING' 대신 상대 주소 포트 0(:0)으로 대기 소켓을 판별한다.
if "%~1"=="--open-when-ready" (
  set "UP="
  for /l %%i in (1,1,120) do if not defined UP (
    netstat -an | findstr /r /c:":%~2 .*:0 " >nul && set "UP=1" || ping -n 2 127.0.0.1 >nul
  )
  start "" http://localhost:%~2
  exit /b 0
)

setlocal
cd /d "%~dp0"

REM 반입한 오프라인 브라우저 번들 사용 (런타임 다운로드 금지)
set "PLAYWRIGHT_BROWSERS_PATH=%~dp0pw-browsers"
set "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1"
REM 번들 vnu.jar 절대경로 주입 (sites.yaml 수정 불필요)
set "WQG_VNU_JAR=%~dp0vnu.jar"

REM 포트 (기본 8080). 이미 쓰는 포트면 첫 번째 인자로 바꾼다: start.bat 9090
set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

REM 포트를 이미 다른 프로그램이 쓰면 서버가 못 뜨고, 대기 중인 브라우저가 그 프로그램을 연다 — 미리 중단.
netstat -an | findstr /r /c:":%PORT% .*:0 " >nul && (
  echo [중단] 포트 %PORT% 를 이미 다른 프로그램이 사용 중입니다.
  echo        콘솔이 이미 떠 있으면 브라우저에서 http://localhost:%PORT% 를 여세요.
  echo        아니면 다른 포트로 실행하세요: start.bat 9090
  pause
  exit /b 1
)

REM 번들 JRE 우선, 없으면 시스템 java
set "JAVA=%~dp0jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

REM 로그를 logs\ 폴더에 회차별로 남긴다(문제 발생 시 이 파일을 열어 확인/전달).
if not exist "%~dp0logs" mkdir "%~dp0logs"
set "TS=%RANDOM%"
for /f "tokens=2 delims==." %%i in ('wmic os get localdatetime /value 2^>nul') do set "DT=%%i"
if defined DT set "TS=%DT:~0,8%-%DT:~8,6%"
set "LOG=%~dp0logs\wqg-%TS%.log"

echo web-quality-gate 웹 콘솔을 시작합니다 ...
echo 로그 파일: %LOG%
echo 서버가 준비되면 기본 브라우저로 http://localhost:%PORT% 가 자동으로 열립니다.
echo 2분이 지나도 화면이 안 뜨면 위 로그 파일을 확인하세요.
echo (이 창에는 출력이 표시되지 않습니다 - 진행/오류는 위 로그 파일에서 확인하세요.)
REM 서버 기동을 기다렸다가 브라우저를 여는 대기자를 최소화 창으로 띄운다(이 파일의 --open-when-ready).
start "" /min cmd /c ""%~f0" --open-when-ready %PORT%"
"%JAVA%" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0web-quality-gate.jar" serve %PORT% > "%LOG%" 2>&1

endlocal
