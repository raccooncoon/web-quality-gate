@echo off
REM ============================================================
REM web-quality-gate 웹 콘솔 실행기 (윈도우 로컬 PC, 폐쇄망)
REM 이 파일을 패키지 최상위에 두고 더블클릭하면
REM   - 번들 JRE + 번들 브라우저로 웹 콘솔을 띄우고
REM   - 기본 브라우저로 http://localhost:8080 을 연다
REM ============================================================
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

REM 번들 JRE 우선, 없으면 시스템 java
set "JAVA=%~dp0jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

echo web-quality-gate 웹 콘솔을 시작합니다 ...
echo 브라우저가 자동으로 열리지 않으면 http://localhost:%PORT% 를 여세요.
REM 서버가 뜰 시간(약 3초)을 준 뒤 기본 브라우저로 콘솔을 연다(별도 창에서 대기).
start "" cmd /c "timeout /t 3 >nul & start "" http://localhost:%PORT%"
"%JAVA%" -jar "%~dp0web-quality-gate.jar" serve %PORT%

endlocal
