@echo off
chcp 65001 >nul
REM ============================================================
REM 전체 시스템 일괄 검사 (주간 예약 실행용, 윈도우 폐쇄망)
REM 설정된 모든 시스템을 검사하고 reports\ 에 리포트를 생성한다.
REM 계정은 콘솔에서 저장한 credentials.properties(있으면) 또는 환경변수 사용.
REM Windows 작업 스케줄러에 이 파일을 주 1회로 등록하면 주간 자동 점검이 된다.
REM 결과 확인: logs\runall-latest.log (최신 회차) — 맨 끝 '검사 요약' 에 시스템별 [OK]/[FAIL].
REM 종료코드: 0=전체 성공, 1=실패 시스템 있음/대상 없음 (스케줄러 '마지막 실행 결과'로 확인).
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
set "LOG=%~dp0logs\runall-%TS%.log"

set "LATEST=%~dp0logs\runall-latest.log"

echo [%date% %time%] 전체 시스템 검사 시작 (로그: %LOG%)
REM 시스템명 인자 없이 실행하면 config\sites.yaml 의 모든 시스템을 순회한다.
"%JAVA%" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0web-quality-gate.jar" "%~dp0config\sites.yaml" > "%LOG%" 2>&1
set "RC=%ERRORLEVEL%"

REM 최신 회차를 고정 이름으로도 남긴다 — 타임스탬프 파일을 찾지 않고 이 파일만 열면 된다.
copy /y "%LOG%" "%LATEST%" >nul

echo [%date% %time%] 검사 종료 (종료코드 %RC%)
echo.
REM 앱이 로그 끝에 남긴 요약(=====, [OK], [FAIL] 로 시작하는 줄)만 골라 보여준다.
REM 요약 줄이 하나도 없으면 앱이 도중에 죽은 것 — 로그 전체를 봐야 한다.
findstr /b /l /c:"=====" /c:"[OK]" /c:"[FAIL]" "%LOG%" || echo [요약 없음] 검사가 도중에 중단됐습니다 - 아래 로그 파일 전체를 확인하세요.
echo.
echo 로그 전체: %LATEST%
echo 리포트   : %~dp0reports  (콘솔 결과 탭에서도 열람 가능)

REM 종료코드를 그대로 돌려준다 — 실패 시 작업 스케줄러 '마지막 실행 결과'가 0x1 로 남는다.
endlocal & exit /b %RC%
