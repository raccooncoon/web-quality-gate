@echo off
REM ============================================================
REM Phase 1 (윈도우 외부망 PC에서 실행): 폐쇄망 반입 패키지 생성
REM 산출물: dist\wqg-win\  (이 폴더째 압축해 반입)
REM   ├─ start.bat                더블클릭 실행기
REM   ├─ web-quality-gate.jar     shade 단일 jar
REM   ├─ vnu.jar                  W3C Nu Html Checker
REM   ├─ jre\                     윈도우용 JRE 17 (jlink 로 생성)
REM   ├─ pw-browsers\             윈도우용 chromium/firefox/webkit
REM   ├─ config\sites.yaml        검사 대상 설정(없으면 sites.example.yaml 사용)
REM   ├─ verify.bat               반입 후 완전성 검증기
REM   └─ reports\                 결과 저장 위치(비어 있음)
REM 요구: 이 PC 에 JDK 17 + Maven + 인터넷. (JAVA_HOME=JDK17)
REM ★ 반드시 '윈도우'에서 실행 — JRE·브라우저는 OS 네이티브라 mac/리눅스에서 못 만듦.
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

echo [4/6] vnu.jar 다운로드 (버전별 zip 을 받아 vnu.jar 추출)
set "VNU_VER=24.10.17"
curl -fsSL -o "%TEMP%\vnu.zip" "https://github.com/validator/validator/releases/download/%VNU_VER%/vnu.jar_%VNU_VER%.zip" || goto :err
if exist "%TEMP%\vnuex" rmdir /s /q "%TEMP%\vnuex"
mkdir "%TEMP%\vnuex"
tar -xf "%TEMP%\vnu.zip" -C "%TEMP%\vnuex" || goto :err
for /r "%TEMP%\vnuex" %%v in (vnu.jar) do if exist "%%v" copy /y "%%v" "%OUT%\vnu.jar" >nul
if not exist "%OUT%\vnu.jar" (echo vnu.jar 추출 실패 & goto :err)

echo [5/6] 최소 JRE 17 생성 (jlink)
if "%JAVA_HOME%"=="" (echo JAVA_HOME(JDK17) 설정 필요 & goto :err)
REM java.se = 모든 Java SE 모듈(java.xml 등) — 번들 JRE 로 vnu.jar(HTML/XML 검증)도 실행하므로
REM 앱뿐 아니라 vnu 의 모듈 요구까지 안전하게 커버한다. jdk.charsets 는 EUC-KR/MS949 페이지용.
REM ※ 리눅스판 scripts/package-offline.sh 와 모듈 목록·vnu 버전(VNU_VER)을 동기 유지할 것.
"%JAVA_HOME%\bin\jlink" --add-modules java.se,jdk.httpserver,jdk.crypto.ec,jdk.charsets,jdk.unsupported --no-header-files --no-man-pages --strip-debug --output "%OUT%\jre" || goto :err

echo [6/6] 설정·실행기 복사
REM 실제 설정(config\sites.yaml)이 있으면 그것을, 없으면 예시 템플릿을 반입
if exist config\sites.yaml (
  copy /y config\sites.yaml "%OUT%\config\sites.yaml" >nul
) else (
  copy /y config\sites.example.yaml "%OUT%\config\sites.yaml" >nul
)
copy /y config\sites.example.yaml "%OUT%\config\sites.example.yaml" >nul
copy /y scripts\win\start.bat "%OUT%\start.bat" >nul
copy /y scripts\win\run-all.bat "%OUT%\run-all.bat" >nul
copy /y scripts\win\verify.bat "%OUT%\verify.bat" >nul
if exist scripts\win\README.md copy /y scripts\win\README.md "%OUT%\README.md" >nul

echo.
echo 완료: %OUT%  (폴더째 압축해 폐쇄망 반입)
echo 반입 후 대상 PC 에서 start.bat 더블클릭.
goto :eof

:err
echo.
echo [실패] 위 오류를 확인하세요.
exit /b 1
