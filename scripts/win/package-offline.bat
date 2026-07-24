@echo off
REM ============================================================
REM Phase 1 (윈도우 외부망 PC에서 실행): 폐쇄망 반입 패키지 생성
REM 산출물: dist\wqg-win\  (이 폴더째 압축해 반입)
REM   ├─ start.bat                더블클릭 실행기
REM   ├─ web-quality-gate.jar     shade 단일 jar
REM   ├─ vnu.jar                  W3C Nu Html Checker
REM   ├─ jre\                     윈도우용 JRE 17 (jlink 로 생성)
REM   ├─ pw-browsers\             윈도우용 chromium/firefox/webkit
REM   ├─ config\sites.yaml        11개 시스템 템플릿
REM   └─ reports\                 결과 저장 위치(비어 있음)
REM 요구: 이 PC 에 JDK 17 + Maven + 인터넷. (JAVA_HOME=JDK17)
REM ============================================================
setlocal
cd /d "%~dp0\..\.."
set "OUT=dist\wqg-win"

echo [1/6] 이전 산출물 정리
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\config" "%OUT%\reports" 2>nul

echo [2/6] 애플리케이션 빌드
call mvn -q clean package || goto :err
for %%f in (target\web-quality-gate-*.jar) do copy /y "%%f" "%OUT%\web-quality-gate.jar" >nul

echo [3/6] Playwright 브라우저(윈도우) 다운로드
set "PLAYWRIGHT_BROWSERS_PATH=%CD%\%OUT%\pw-browsers"
call mvn -q exec:java -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium firefox webkit" || goto :err

echo [4/6] vnu.jar 다운로드
curl -fsSL -o "%OUT%\vnu.jar" "https://github.com/validator/validator/releases/download/latest/vnu.jar" || goto :err

echo [5/6] 최소 JRE 17 생성 (jlink)
if "%JAVA_HOME%"=="" (echo JAVA_HOME(JDK17) 설정 필요 & goto :err)
"%JAVA_HOME%\bin\jlink" --add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.net.http,jdk.httpserver,jdk.crypto.ec,jdk.unsupported --no-header-files --no-man-pages --strip-debug --output "%OUT%\jre" || goto :err

echo [6/6] 설정·실행기 복사
copy /y config\sites.yaml "%OUT%\config\sites.yaml" >nul
copy /y scripts\win\start.bat "%OUT%\start.bat" >nul
copy /y scripts\win\run-all.bat "%OUT%\run-all.bat" >nul

echo.
echo 완료: %OUT%  (폴더째 압축해 폐쇄망 반입)
echo 반입 후 대상 PC 에서 start.bat 더블클릭.
goto :eof

:err
echo.
echo [실패] 위 오류를 확인하세요.
exit /b 1
