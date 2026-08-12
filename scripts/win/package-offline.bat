@echo off
chcp 65001 >nul
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
REM   ├─ diag.bat                 드라이버(node) 진단기 — 기동 오류 시 실행
REM   ├─ logs\                    실행 로그 누적(start/run-all/diag)
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

echo [4/6] vnu.jar 다운로드 (validator 릴리스의 단일 jar 직접 수신)
REM validator 프로젝트는 롤링 'latest' 릴리스에 버전 접미사 없는 vnu.jar 를 직접 게시한다.
REM (구 vnu.jar_<버전>.zip 방식은 폐지되어 404). 특정 버전 고정이 필요하면 latest 를 해당 태그로 교체.
curl -fsSL -o "%OUT%\vnu.jar" "https://github.com/validator/validator/releases/download/latest/vnu.jar" || goto :err
if not exist "%OUT%\vnu.jar" (echo vnu.jar 다운로드 실패 & goto :err)

echo [5/6] 최소 JRE 17 생성 (jlink)
REM 괄호는 ^( ^) 로 이스케이프 — 안 하면 if 블록이 조기 종료돼 항상 구문 오류가 난다
if "%JAVA_HOME%"=="" (echo JAVA_HOME^(JDK17^) 설정 필요 & goto :err)
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
copy /y scripts\win\diag.bat "%OUT%\diag.bat" >nul
if exist scripts\win\README.md copy /y scripts\win\README.md "%OUT%\README.md" >nul

echo.
echo 완료: %OUT%  (폴더째 압축해 폐쇄망 반입)
echo 반입 후 대상 PC 에서 start.bat 더블클릭.
goto :eof

:err
echo.
echo [실패] 위 오류를 확인하세요.
exit /b 1
