@echo off
REM ============================================================
REM 전체 시스템 일괄 검사 (주간 예약 실행용, 윈도우 폐쇄망)
REM 설정된 모든 시스템을 검사하고 reports\ 에 리포트를 생성한다.
REM 계정은 콘솔에서 저장한 credentials.properties(있으면) 또는 환경변수 사용.
REM Windows 작업 스케줄러에 이 파일을 주 1회로 등록하면 주간 자동 점검이 된다.
REM ============================================================
setlocal
cd /d "%~dp0"

set "PLAYWRIGHT_BROWSERS_PATH=%~dp0pw-browsers"
set "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1"
set "WQG_VNU_JAR=%~dp0vnu.jar"

set "JAVA=%~dp0jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

echo [%date% %time%] 전체 시스템 검사 시작
REM 시스템명 인자 없이 실행하면 config\sites.yaml 의 모든 시스템을 순회한다.
"%JAVA%" -jar "%~dp0web-quality-gate.jar" "%~dp0config\sites.yaml"
echo [%date% %time%] 검사 완료 — reports 폴더에서 결과 확인 (콘솔 결과 탭에서도 열람 가능)

endlocal
