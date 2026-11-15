# 주차까지 (가칭)

내비 목적지의 주차 지원 여부와 주변 주차장의 이용 조건(차량 크기·주차 방식·잔여 대수·요금)을 비교해 고르고, 차량 도착지를 그 주차장 입구로 바꿔 주는 차량용 앱. 현대자동차그룹 차량용 앱 개발 공모전 출품작이며 Pleos Connect(AAOS)에서 돈다.

기획서: 「주차까지 차량용 앱 화면 시안 기획서」(2026-11-01). 규칙의 정본은 [docs/specs/parking-guidance.md](docs/specs/parking-guidance.md).

## Pleos 에뮬레이터와 이 앱의 관계

Pleos Connect 에뮬레이터는 Android Studio 안에서 도는 차량(AAOS) 가상 기기다. 앱을 대신하는 것이 아니라 앱을 설치해서 돌리는 **대상 기기**이고, Virtual Console은 기어·도어·배터리 같은 차량 상태를 가상으로 바꿔 앱의 반응을 보는 도구다. 그래서 앱(이 저장소)은 필요하고, 에뮬레이터는 검증 환경이다. 기획서 27절도 Kotlin·Compose 앱과 mock/pleos 빌드 변형을 요구한다.

## 실행 방법

1. Android Studio에서 이 폴더를 연다. Gradle 9.6, AGP 9.4.1.
2. 루트의 `local.properties`에 아래 값을 넣는다. 이 파일은 저장소에 올리지 않는다.

   ```properties
   sdk.dir=<Android SDK 경로>
   # Pleos Playground My Project의 Client ID·Secret. 비우면 음성은 온디바이스 엔진만 쓴다. (pleos 플레이버)
   pleos.clientId=
   pleos.clientSecret=
   # 공공데이터포털 「전국주차장정보표준데이터」 활용신청 후 받은 일반 인증키. 비우면 pleos 플레이버의 주차장 조회가 실패로 끝난다
   # 같은 키로 실시간 잔여 출처(부산시설공단·대전·한국교통안전공단 주차정보)도 부른다. 출처마다 활용신청이 따로 필요하다
   parking.serviceKey=
   ```

3. 빌드 변형 둘(기획서 27절):
   - `mockDebug` — 가상 데이터·가상 차량 상태. 폰 에뮬레이터에서 화면 전부를 확인한다. 화면 위 보라색 띠(MOCK 환경)로 주행 상태·내비 목적지·만차·음성 입력을 바꾼다. `./gradlew :app:installMockDebug`
   - `pleosDebug` — 실제 Pleos SDK(NaviHelper·STT·TTS·CarUxRestrictions). Pleos Connect 에뮬레이터용. `./gradlew :app:installPleosDebug`

단위 테스트: `./gradlew :usecase:test`

## 확인한 것 / 확인 전인 것

| 항목 | 상태 |
|---|---|
| `:usecase:test` | README 작성 시점 결과는 완료 보고에 적는다 |
| `:app:assembleMockDebug` | 같음 |
| Pleos Connect 에뮬레이터 | 이 맥(M1 Max)에서 부팅 15초 뒤 멈추는 문제가 미해결이라 pleos 플레이버는 미확인 |
| 서버 v1 API | 없음. 규칙은 클라이언트(usecase)가 계산하고 데이터는 가상이다 |
| 공공 주차장 API | pleos 플레이버에 전국주차장정보표준데이터 어댑터를 붙였다(전국 데이터를 받아 기기에 30일 저장, 반경 검색은 기기에서). 2026-11-09 Pleos 에뮬레이터에서 확인: 18,994건 19쪽을 약 10초에 받아 11.6MB로 저장, 내비 목적지 "송정역 5호선" 반경 1km에 신방화역(782m)이 나온다. 서울은 표준데이터 등록 건수가 적어 반경 500m 결과가 없을 수 있다. 규칙은 [docs/specs/public-parking-data.md](docs/specs/public-parking-data.md). 2026-11-14 실시간 잔여 출처(부산시설공단·대전·한국교통안전공단)를 붙였다. 표준데이터 시설과 짝이 맞는 곳에만 남은 자리가 나온다(교통안전공단 290곳, 대전 4곳, 부산 0곳). 규칙은 [docs/specs/realtime-availability.md](docs/specs/realtime-availability.md). TourAPI는 미연결 |
| 화면 S04·S09·S10·S12·S14 | 미구현 |
| 혼잡도 제보 | 2026-11-14 추가. 안내받은 주차장에 도착해 정차하면 혼잡·보통·원활을 고르고(S13), S02 표의 혼잡도 열에 최근 2시간 평균과 입력 수가 보인다. 입력은 기기 파일에 저장해 pleos 빌드에서는 이 기기의 입력만 보이고, mock 빌드는 가상 입력을 더한다. 단위 테스트 23개 통과, mock 빌드로 에뮬레이터에서 흐름 확인. 도착했을 때 내비 목적지가 실제로 없어지는지는 Pleos 에뮬레이터에서 확인 전. 규칙은 [docs/specs/congestion-report.md](docs/specs/congestion-report.md) |
| S00 목적지 직접 검색 | 2026-11-09 추가. 버튼 → "어디로 갈까요?" → 들은 말로 내비 장소 검색 화면을 연다(정차 중만). 단위 테스트 6개 통과, Pleos 에뮬레이터 실제 동작과 시안 대조는 확인 전. 규칙은 [docs/specs/destination-search.md](docs/specs/destination-search.md) |
| 목적지 알림 | 2026-11-12 추가. 앱이 화면에 없을 때 내비 목적지가 새 방문으로 바뀌면 알림을 띄우고, 누르면 앱이 S02로 열린다. 단위 테스트 4개 통과, Pleos 에뮬레이터에서 알림이 보이는 방식은 확인 전. 규칙은 [docs/specs/destination-alert.md](docs/specs/destination-alert.md) |

## 구조

클린 아키텍처로 계층을 나누고, 의존은 안쪽(domain)으로만 향한다. 외부 SDK 하나는 data 모듈 하나에서만 import한다. Pleos SDK 모듈은 pleos 플레이버에만 들어가고 mock APK에는 SDK가 없다.

```mermaid
flowchart LR
    app[app<br/>조립: Hilt 모듈, Activity, mock 패널] --> feature[feature:parking<br/>화면 S00~S08·ViewModel]
    app --> usecase[usecase<br/>판정·제외·정렬·요금·음성 해석]
    app --> parking[data:parking<br/>가상 데이터]
    app --> vehicle[data:vehicle<br/>DataStore]
    app --> notification[data:notification<br/>Android 알림]
    app -. pleos .-> navi[data:navi<br/>NaviHelper]
    app -. pleos .-> voice[data:voice<br/>STT·TTS]
    app -. pleos .-> driving[data:driving<br/>CarUxRestrictions]
    feature --> domain[domain<br/>엔티티, 유스케이스·저장소 인터페이스]
    usecase --> domain
    parking --> domain
    vehicle --> domain
    notification --> domain
    navi --> domain
    voice --> domain
    driving --> domain
```

| 모듈 | 하는 일 | 외부 의존 |
|---|---|---|
| `domain` | 엔티티, 저장소·유스케이스 인터페이스, 오류 어휘. 순수 Kotlin | 코루틴 |
| `usecase` | 차량 제한 판정, 잔여 상태, 요금, 제외·정렬, 검색·재조회, 반경 확대, 페이지, 입력 검증, 음성 해석, 목적지 변경 분류 | 없음 |
| `feature:parking` | 화면 S00·S01·S02·S03/S11·S07·S08과 ViewModel(단일 상태 저장소). S06 최종 확인은 2026-11-12에 없앴다(S02 줄을 누르면 바로 안내). domain 인터페이스만 본다 | Compose, Hilt |
| `data:parking` | 기획서 24절 가상 주차장 데이터(mock)와 전국주차장정보표준데이터 어댑터(pleos) | 없음(JDK HTTP·Android org.json) |
| `data:vehicle` | 차량 프로필 저장(version 증가) | Jetpack DataStore |
| `data:notification` | 목적지 알림 띄우기·지우기 | 없음(Android 알림) |
| `data:navi` | 목적지 조회·변경 이벤트, 경로 변경 요청과 결과 확인 | Pleos NaviHelper |
| `data:voice` | 음성 안내, 음성 답 한 번 듣기 | Pleos STT·TTS |
| `data:driving` | 주행·정차·불명 (주행 규제 상태) | AAOS `android.car` |
| `app` | 의존 그래프(`di/AppModules.kt` + 플레이버별 `di/EnvModule.kt`), 매니페스트, mock 패널 | Hilt |
