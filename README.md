# 베프 (BEFF)

> 최종 갱신: 2026-11-15 · 상태: 공모전 1차 제출용

**주차하기 전에, 내릴 수 있는지 아는 차.**
Pleos Connect 차량용 앱이다. 휠체어 · 유모차 · 보행 보조기를 쓰는 사람과 함께 타는 운전자에게, 목적지의 편의시설과 주차장에서 시설까지 가는 길을 내리기 전에 알려 준다.

- 공모전: 현대자동차그룹 차량용 앱 개발 공모전 「Code your App into Pleos App Market」
- 팀: 베프
- 패키지: `com.beff.app` · 버전 0.1.0

## 1. 주요 기능

| 기능 | 하는 일 |
|---|---|
| 위치정보 · 개인정보 동의 | 첫 실행 때 스플래시 다음에 묻는다(주차 중). 세 줄 요약과 약관 QR([`docs/PRIVACY.md`](docs/PRIVACY.md)). 동의 전에는 차의 위치를 쓰지 않는다 |
| 프로필 | 함께 타는 사람의 이동 수단(휠체어 · 유모차 · 보행 도움)과 먼저 찾을 시설을 저장한다 |
| 홈 · 검색 | 프로필에서 고른 시설이 있는 곳부터 추천하고, 장소를 검색한다(음성 검색 포함) |
| 장소 상세 | 편의시설, 사진, 주차장에서 시설까지 걸어가는 길과 거리를 지도로 보여 준다 |
| 출발 브리핑 | 목적지까지의 거리 · 시간, 가는 길에 들를 만한 곳을 보여 주고 내비 안내를 시작한다 |
| 주행 중 | 화면을 남은 여정만 보이는 화면으로 바꾸고 음성으로 안내한다. 경유지는 음성으로 바꾼다 |
| 하차 브리핑 | 도착해서 기어를 P에 놓으면 열린다. 장애인 주차구역 · 출입구 정보와 주변 시설 지도를 보여 준다 |
| 대체 하차지 | 내리기 어려울 때 근처의 장애인 전용주차구역이 있는 주차장을 보여 준다 |
| 체크인 | 다녀온 뒤 정보가 맞았는지 묻는다(응답 저장 서버는 아직 없다) |

3분할 화면의 1/3 카드와 2/3 화면, 라이트 · 다크 테마를 지원한다.

**공개 SDK로 붙는 기능만 넣었다(2026-11-15).** 차량 신호는 기어(Vehicle `GearSelection`) · 문 위치(Vehicle `DoorPosition`) · 주행 제한(AAOS `CarUxRestrictions`)만 쓴다. 공개 SDK에 좌석 제어 · 승객 좌석 · 어라운드뷰(카메라) · 측면 센서 API가 없어서 예전 콘셉트 화면(앉은 자리 선택, 조수석 뒤로 밀기, 하차 공간 확인)은 뺐다.

## 2. 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect 에뮬레이터 `Pleos_Connect_v2.1.0` (AAOS) |
| 화면 규격 | 2560 × 1440 (가로). 앱은 3분할 중 1/3 카드(약 840px 폭) 또는 2/3 화면으로 뜬다 |
| Android | minSdk 28 · targetSdk 34 · compileSdk 34 |
| 빌드 도구 | JDK 17, Gradle 8.13(래퍼), Android Gradle Plugin 8.13.2, Kotlin 2.0.21, Jetpack Compose |
| Pleos SDK | NaviHelper 3.0.5 · Vehicle 2.3.2 · FusedLocation 1.1.3 · TextToSpeech 3.3.1 · SpeechToText 3.3.1 |

JDK 25로는 빌드가 실패한다. JDK 17을 쓴다.

## 3. 실행 방법

```bash
# 1) 빌드 (macOS 기준)
JAVA_HOME="$(/usr/libexec/java_home -v 17)" ./gradlew :beff:assembleDebug
#    산출물: beff/build/outputs/apk/debug/beff-debug.apk

# 2) 에뮬레이터 실행 후 설치
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0
adb install -r beff/build/outputs/apk/debug/beff-debug.apk

# 3) 권한 (이 에뮬레이터의 앱 사용자는 10이다)
adb shell pm grant --user 10 com.beff.app android.permission.RECORD_AUDIO
adb shell pm grant --user 10 com.beff.app pleos.car.permission.FUSED_LOCATION
```

### 필요한 설정 (선택)

인증키는 저장소에 없다. 루트의 `local.properties`(gitignore) 또는 환경변수에 넣는다.

| 이름 | 쓰는 곳 | 없을 때 |
|---|---|---|
| `KAKAO_REST_KEY` | 카카오 로컬(장소 검색) | 검색이 앱 안 무장애 장소 1,000곳에서만 된다 |
| `KAKAO_JS_KEY` | 카카오맵 지도 배경 | 지도 배경 없이 도식 지도만 그린다 |

Vehicle 권한(`CAR_DRIVE`, `CAR_DOORS`)과 내비 · 음성 · 위치 권한은 매니페스트에 선언돼 있다.

### APK

- 제출용 release APK: `apk/베프_BEFF_release.apk`
- 만드는 법: `./gradlew :beff:assembleRelease` 뒤 `apksigner`로 서명한다(시연용이라 Android 디버그 키로 서명했다).
- 카카오 키를 `local.properties`에 넣고 빌드하면 키가 APK 안에 들어간다. 공개 저장소에 올리는 APK는 이 점을 확인하고 올린다.

## 4. 플로우 점검 페이지 (개발용 백엔드)

`tools/beff-admin/`은 의존성 없는 Node 서버다. 화면 흐름도에서 상자를 누르면 에뮬레이터의 앱이 그 화면으로 이동한다. 심사 · 시연 때 특정 화면을 바로 열어 볼 수 있다.

```bash
node tools/beff-admin/server.mjs      # http://127.0.0.1:5174
```

| 하는 일 | 방법 |
|---|---|
| 화면 이동 | `adb shell am start -n com.beff.app/.MainActivity -f 0x20000 --es debug <대상>`으로 앱의 점검 진입점을 부른다 |
| 지금 화면 표시 | 앱(디버그 빌드)이 남기는 `BeffDebug: state {...}` 로그를 `adb logcat`으로 읽는다 |
| 주행 · 정차 | Pleos 가상 콘솔(에뮬레이터 `:8088`)에 기어 · 속도 · 주행 보조 상태를 보낸다. 콘솔 페이지가 브라우저에 열려 있어야 응답한다 |
| 위치 이동 | `adb emu geo fix`로 에뮬레이터 위치를 목적지 · 경유지로 옮긴다 |
| 도착 흉내 | 에뮬레이터 내비가 도착 신호를 보내지 않아, 점검용 도착 신호를 앱 안에서 만든다 |

API: `GET /api/status`(기기 · 앱 상태), `POST /api/jump` `{ "target": "unload:tour-2436233" }` 또는 `{ "query": "화담숲" }`.

- 흐름도(`flow.json`)는 Figma 「PleOS Connect › 1112 검증 › 8. 전체 화면 플로우」에서 뽑았다.
- 실제 차량에는 아무것도 보내지 않는다. 127.0.0.1에서만 열린다.
- 대부분의 점검 대상은 프로필을 예시 프로필 「동생」(휠체어)으로 되돌린다. 다른 프로필 화면은 `query`만 보내고 화면을 직접 누른다.

## 5. 데이터

앱에 담아 두는 데이터는 `tools/public_data/`의 스크립트로 받아 가공한다(수집일 2026-11-08, 화담숲 지도는 2026-11-14).

| 데이터 | 출처 | 가공 | 앱 파일 |
|---|---|---|---|
| 무장애 장소 1,000곳 (서울 · 경기 · 인천) | 한국관광공사 무장애 여행정보(KorWithService2) | `tour_region.py` → `build_places.py`. 원문에 「있음 · 가능 · 설치」가 분명할 때만 시설 칩을 켠다 | `assets/places.json` |
| 장애인 전용주차구역이 있는 주차장 | 전국주차장정보표준데이터 | `build_parking.py`. 보유 여부가 「Y」인 곳만 남긴다(빈칸은 「모른다」로 보고 뺀다) | `assets/parking.json` |
| 걸어가는 길 · 화장실 | OpenStreetMap(Overpass API, ODbL) | `build_osm_walkmap.py <장소id> [반경m]`. 앱이 주차장에서 시설까지의 최단 경로를 계산한다 | `assets/osm/<장소id>.json` |
| 장소 사진 | 한국관광공사 대표 사진, 위키미디어 공용 | `add_images.mjs`, `add_commons_images.mjs` | `places.json`의 `image` |
| 장소 검색 · 주변 시설 · 지도 | 카카오 로컬 API, 카카오맵 | 실행 중 호출 | — |

- 공공데이터 인증키는 환경변수 `DATA_GO_KR_KEY`에서만 읽는다.
- 미리 받아 둔 걷는 길 지도는 서울숲(`tour-128611`)과 화담숲(`tour-2436233`) 두 곳이다. 다른 곳은 실행 중에 받아 온다.
- 화담숲 지도의 출발점은 OpenStreetMap의 「주차장7」이며 현장 확인 전이다.

## 6. SDK 활용

| SDK | 쓰는 기능 |
|---|---|
| Vehicle | 기어 상태를 읽어 주행 중 화면을 줄이고, 주차(P)하면 하차 흐름을 연다. 문 열림을 읽는다. 읽기만 하고 차량 제어는 하지 않는다 |
| NaviHelper | 경로 요청, 경유지 추가 · 삭제, 남은 거리 · 시간, 도착 이벤트 |
| TextToSpeech | 안내 시작, 쉬어갈 곳, 경유지 · 목적지 접근, 도착 안내 문장 |
| SpeechToText | 음성 검색, 주행 중 경유지 변경 |
| FusedLocation | 목적지 150m 도착 판정, 쉬어갈 곳 접근 안내(위치정보 동의했을 때만 켠다) |

**SDK에 추가되기를 바라는 기능:** ① 좌석 위치 제어 ② 측면 카메라 · 주차 센서 값 ③ 내비 검색 결과와 경로 좌표 수신.

## 7. 폴더

```
beff/ (저장소 루트)
├── beff/                  # 앱 모듈 (Kotlin · Jetpack Compose)
│   └── src/main/assets/   # places.json · parking.json · osm/ · 그림 · Lottie
├── apk/                   # 제출용 release APK (베프_BEFF_release.apk)
├── tools/
│   ├── beff-admin/        # 플로우 점검 페이지 (Node 서버)
│   ├── public_data/       # 공공 데이터 수집 · 가공 스크립트
│   └── beff-sfx/          # 효과음 생성
└── docs/PRIVACY.md        # 개인정보 처리방침 · 위치정보 이용 안내 (시연판 초안)
```

## 8. 디자인 원본

- 화면: Figma `PleOS Connect` 파일(제출하지 않음)
