# 폐쇄망 윈도우 PC 배포 가이드

대상: **윈도우만 설치된 PC**(Java·브라우저 등 아무것도 없는 상태). 번들 하나로 완전 자립 실행.

> ⚠️ **핵심**: JRE와 브라우저는 OS 네이티브라 **반드시 윈도우 PC에서 패키징**해야 합니다.
> mac·리눅스에서 만든 번들은 윈도우에서 실행되지 않습니다.

---

## 전체 흐름 (2단계)

```
[외부망 윈도우 PC]                         [폐쇄망 윈도우 PC (윈도우만)]
JDK17 + Maven + 인터넷                      아무것도 없음
   │                                          │
   │ package-offline.bat 실행                  │
   ▼                                          │
dist\wqg-win\  ──── ZIP 압축 ──── USB 반입 ──▶ 압축 해제
                                              │ verify.bat  (완전성 확인)
                                              │ start.bat   (더블클릭 → 콘솔 실행)
                                              ▼
                                        http://localhost:8080 자동 열림
```

---

## 1단계 — 외부망 윈도우 PC에서 패키지 생성

**요구사항**: JDK 17 설치(`JAVA_HOME` 을 JDK17로), Maven, 인터넷, Windows 10/11(curl·tar 내장).

```bat
REM 저장소 루트에서
scripts\win\package-offline.bat
```

산출물: `dist\wqg-win\` — 이 폴더를 **통째로 ZIP** 해서 반입합니다.

| 구성요소 | 설명 | 크기(대략) |
|---|---|---|
| `web-quality-gate.jar` | 앱(의존성 포함 단일 jar) | ~200 MB |
| `jre\` | 윈도우용 최소 JRE 17 (jlink) — **Java 미설치 PC용** | ~50 MB |
| `pw-browsers\` | chromium·firefox·webkit (윈도우 빌드) | ~500 MB |
| `vnu.jar` | W3C Nu Html Checker (마크업 검사) | ~20 MB |
| `config\sites.yaml` | 검사 대상 설정(없으면 예시 템플릿) | — |
| `config\sites.example.yaml` | 설정 예시 | — |
| `start.bat` | 콘솔 실행기(더블클릭) | — |
| `run-all.bat` | 전체 일괄 검사(작업 스케줄러용) | — |
| `verify.bat` | 반입 후 완전성 검증기 | — |
| `reports\` | 결과 저장 위치(비어 있음) | — |

---

## 2단계 — 폐쇄망 윈도우 PC에서 실행

1. 반입한 ZIP을 원하는 폴더에 **압축 해제**(예: `C:\wqg`).
2. **`verify.bat` 더블클릭** — 필수 구성요소가 다 왔는지, 번들 자바가 뜨는지 확인. `[정상]` 이면 OK.
3. **`start.bat` 더블클릭** — 웹 콘솔이 뜨고 약 3초 뒤 기본 브라우저로 `http://localhost:8080` 이 열립니다.
   - 포트 변경: `start.bat 9090`
4. 콘솔 **① 설정** 탭에서 대상 시스템의 baseUrl·검사 경로·계정을 채우고 검사하세요.
   - 로그인 세션/전용 브라우저 프로필·계정은 **이 PC 로컬에만** 저장됩니다.
5. 주간 자동 검사: **작업 스케줄러**에 `run-all.bat` 을 주 1회로 등록.

---

## 완전성 체크리스트 (빠지면 안 되는 것)

- [ ] `web-quality-gate.jar` — 앱
- [ ] `jre\bin\java.exe` — **번들 자바**(PC에 Java 없어도 실행)
- [ ] `pw-browsers\chromium*` / `firefox*` / `webkit*` — 3개 엔진 폴더
- [ ] `vnu.jar` — 마크업 검사기 (+ `start.bat` 이 `WQG_VNU_JAR` 로 주입)
- [ ] `config\sites.yaml` — 검사 대상
- [ ] `start.bat` / `run-all.bat` / `verify.bat`

`verify.bat` 이 위를 자동 점검합니다. 하나라도 `[빠짐]` 이면 외부망에서 `package-offline.bat` 을 재실행하세요.

---

## 알려진 주의사항 (폐쇄망 실패 지점)

- **한글/이모지 폰트**: 윈도우 기본 폰트(맑은 고딕·Segoe UI Emoji)로 충분 — 별도 반입 불필요.
- **WebKit 실행 실패 시**: 아주 드물게 **Microsoft Visual C++ 재배포 패키지(x64)** 가 필요할 수 있습니다.
  이 경우 외부망에서 `vc_redist.x64.exe` 를 함께 반입해 설치하세요.
- **SmartScreen/백신**: 서명 없는 `.bat`·`.exe` 를 막을 수 있습니다 — 실행 허용(추가 정보 → 실행).
- **방화벽 팝업**: 자바가 포트를 열 때 방화벽이 물어보면 허용(콘솔은 `127.0.0.1` 로컬 전용).
- **런타임 다운로드 금지**: `start.bat`·`run-all.bat` 이 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` 로
  번들 브라우저만 쓰도록 강제합니다(폐쇄망에서 인터넷 접근 시도 없음).
- **계정 커밋 금지**: 계정은 콘솔에서 저장(로컬 `credentials.properties`)하거나 환경변수
  `WQG_<시스템명대문자>_ID` / `_PW` 로 주입 — 설정 파일에 넣지 마세요.
