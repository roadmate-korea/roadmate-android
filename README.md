# RoadMate Korea

한국에서 렌터카를 빌린 외국인 운전자에게 운전자의 언어, 평소 운전하던 나라, 한국 운전 경험에 맞춰 한국 교통 규칙을 음성과 자막으로 안내하는 Pleos Connect(Android Automotive) 앱입니다. 2026 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

**Same Road, Different Guidance.** 같은 길을 달려도 운전자마다 필요한 안내가 다릅니다.

## 주요 기능

- **2페이지 온보딩**: 언어(English, Français, Español) → 평소 운전 국가(11개, 언어로 거르지 않음)와 한국 운전 경험(3개). 회원가입 없이 기기에만 저장하고, 다음 실행부터는 바로 주행 화면으로 들어갑니다.
- **개인화 규칙**: 좌측통행 국가에서 온 운전자에게는 우측통행 적응 안내를, 좌핸들 차량이 처음인 운전자에게는 좌핸들 적응 안내를 더합니다. 한국 운전 경험이 있으면 기초 설명을 줄입니다. 기타 국가는 통행 방향을 추정하지 않습니다.
- **주행 중 안내**
  - 어린이보호구역(모든 운전자): 300m 전 경고, 제한 속도는 NaviHelper가 준 값을 그대로 씀
  - 어린이보호구역 과속(모든 운전자): 어린이보호구역 안에서 차량 속도가 제한보다 2km/h 넘게 빠르면 지금 속도와 제한 속도를 말하고, 계속 빠르면 30초마다 다시 알림(2km/h는 앱의 판단 여유이며 법적 허용 속도가 아님)
  - 우회전: 우회전 신호등, 빨간불에서 정지선 앞 일시정지, 보행자 보호
  - 비보호 좌회전: 녹색 화살표, 비보호 좌회전 표지에서 양보
  - 좌핸들 적응: 주행 시작, 좌회전, 차선 변경 때
  - 버스전용차로: 단속 지점 300m 전, 표지판 시간에는 들어가지 않기
  - 구간 단속: 시작 지점의 제한 속도, 구간 평균 속도가 제한을 넘으면 경고(모든 운전자)
- **안내 카드**: 안내마다 짧은 요약 카드를 보여 주고, 빨간불 우회전, 어린이보호구역, 버스전용차로 같은 교통 상황에는 위에서 본 상황 그림을 함께 넣습니다(일반 첫 방문·경험자 인사와 도착 안내는 그림 없이 아이콘과 글로 표시). 음성은 전체 문장을 읽습니다.
- **음성 질문(Ask RoadMate)**: 마이크 버튼을 누르고 자기 언어로 물으면(예: "Can I turn right on red?", "¿Cuál es el límite aquí?") 규칙 안내 문장으로 음성과 카드로 답합니다. 우회전, 비보호 좌회전, 어린이보호구역, 버스전용차로, 구간 단속, 지금 도로의 제한 속도(내비 값), "방금 안내 다시"를 물을 수 있습니다. 인식은 Pleos STT가 기기 안에서 하고, 질문 문장은 저장하거나 보내지 않습니다.
- **현재 속도 표시**: 주행 화면에 제한 속도 표지와 지금 속도를 나란히 보여 주고, 제한보다 2km/h 넘게 빠르면 빨간색으로 바뀝니다.
- **내비와 함께 쓰기**: 내비를 앞에 띄워도 안내가 이어집니다(포그라운드 서비스). 내비와 나란히 띄우는 분할 화면에서는 좁은 화면용 배치로 바뀝니다. 음성은 미디어가 아니라 내비 안내 용도로 재생합니다(시스템이 음악을 멈추는 대신 소리를 줄이는 용도).
- **안전**: 주행 중(P가 아님) 화면은 읽기 전용이고 설정은 정차 중에만 바꿀 수 있습니다. 주행 중에 눌리는 것은 마이크 버튼 하나뿐입니다(Pleos 가이드: 주행 중 음성 입력 허용). 질문을 듣는 동안 안전 경고가 오면 듣기를 멈추고 경고를 먼저 말합니다. 음성은 한 번에 하나만 나가며 위험도가 높은 안내가 먼저입니다. 같은 안내는 반복을 제한합니다. 음성을 쓸 수 없으면 자막으로 대신 보여 줍니다.
- **시연 모드**: 주행 화면의 [Play demo: Nakamura] [Play demo: Carlos]를 누르면, 실제 SDK와 같은 형식의 주행 신호를 재생해 두 운전자가 같은 길에서 서로 다른 안내를 받는 장면을 보여 줍니다.

시나리오 상세: [docs/mvp-scenario.md](docs/mvp-scenario.md)

## 두 페르소나

| | Nakamura | Carlos |
|---|---|---|
| 언어 / 운전 국가 / 경험 | English / Japan / 좌핸들 처음 | Español / Otro país / 한국 운전 처음 |
| 시작 음성 | Welcome to Korea. In Korea, vehicles drive on the right side of the road. Please take extra care at intersections. | ¡Bienvenido a Corea! Como es tu primera vez conduciendo aquí, te ayudaremos a conocer las normas de tráfico locales. |
| 우측통행·좌핸들 적응 안내 | 받음 | 받지 않음 |
| 한국 교통법규, 어린이보호구역 | 받음 | 받음 |

## 구조

교통 상황 판단 로직, 언어별 문구, 음성 출력을 분리했습니다.

```
Vehicle·NaviHelper SDK ─┐
                        ├─ RawSignal ─ SignalMapper ─ DriveEvent ─ GuidanceEngine ─ 안내 ID
DemoRoute(시연) ────────┘                                                         │
                                                  문구 리소스(en, fr, es, ko) ─ GuidanceSpeaker ─ Pleos TTS
                                                                                  └─ 화면 자막
```

- `rules/`: 판단 로직(순수 Kotlin, Android import 없음, 단위 테스트)
- `i18n/`, `res/values*`: 언어별 문구
- `voice/`: Pleos TTS, 음성 큐
- `vehicle/`, `navi/`, `drive/`: SDK 어댑터와 신호 변환, 주행 세션, 포그라운드 서비스
- `demo/`: 시연 경로와 페르소나
- `ui/`: 온보딩, 주행 화면, 상황 그림(`ui/scene/`)

## 사용한 Pleos Connect SDK

| SDK | 버전 | 용도 | 권한 |
|---|---|---|---|
| Vehicle | 2.3.2 | 기어, 방향지시등 | `CAR_DRIVE`, `CAR_LIGHTS` |
| NaviHelper | 3.0.5 | 제한 속도, 어린이보호구역, 단속 카메라, 회전 안내, 도착 | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` |
| TextToSpeech | 3.3.1 | 음성 안내(ON_DEVICE) | `TTS_SERVICE` |
| SpeechToText | 3.3.1 | 음성 질문(ON_DEVICE, en-US, fr-CA, es-US) | `STT_SERVICE`, `RECORD_AUDIO`(정차 중 한 번 요청, 거부하면 마이크 버튼을 숨김) |

TTS 로케일은 Pleos 온디바이스 엔진이 지원하는 en-US, fr-CA, es-US, ko-KR을 씁니다.

Pleos SDK 밖에서 쓰는 것:

| 기능 | 용도 | 권한 |
|---|---|---|
| Android Automotive `CarPropertyManager` | 차량 속도(`PERF_VEHICLE_SPEED`). Vehicle SDK에 속도 API가 없음 | `android.car.permission.CAR_SPEED`(정차 중 한 번 요청, 허용 창에는 "위치 정보"로 표시) |
| 포그라운드 서비스 | 내비를 앞에 띄워도 안내 유지 | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` |

차량 속도는 기기 안에서만 쓰고 외부로 보내지 않습니다(위치는 앱이 직접 읽지 않습니다). 속도 권한을 거부하면 현재 속도 표시와 어린이보호구역 과속 경고가 빠지고, 내비 신호를 쓰는 나머지 안내(구간 단속 평균 속도 경고 포함)는 그대로 나갑니다.

## 빌드

요구 사항: Android Studio, Android SDK Platform 35. Gradle은 JDK 21에서 돌도록 정해 두었습니다(`gradle/gradle-daemon-jvm.properties`). 실행하는 JDK가 25여도(최신 Android Studio 내장 JDK) Gradle이 설치된 JDK 21을 찾고, 없으면 내려받아 씁니다.

```bash
./gradlew testDebugUnitTest   # 단위 테스트
./gradlew assembleDebug       # 개발용(한국어와 확인용 화면 포함)
./gradlew assembleRelease     # 제출용
```

## 실행

Pleos Connect Emulator 2.1.0(Android Studio SDK Manager)에서 실행합니다. 일반 안드로이드 폰에서는 Pleos 차량 서비스가 없어 동작하지 않습니다.

```bash
adb install -r dist/roadmate-korea-1.1.0.apk
```

1. 온보딩에서 언어, 국가, 경험을 고르고 START를 누르면 시작 음성이 나옵니다.
2. 주행 화면에서 [Play demo: Nakamura] 또는 [Play demo: Carlos]를 누르면 시연 경로가 재생됩니다.
3. 실제 신호로 확인하려면 Pleos Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`)에서 기어, 방향지시등, 속도를 바꿉니다.
4. 내비와 함께 보려면 앱 영역 오른쪽 끝 손잡이를 끌어 내비와 RoadMate를 나란히 띄웁니다.

제출용 APK는 `dist/` 폴더에 있습니다.

## 확인한 것과 남은 것

- 에뮬레이터에서 확인: 온보딩과 저장, 4개 언어 TTS 재생, 실제 기어·방향지시등 신호로 시작·회전 안내와 주행 중 잠금, 두 페르소나 시연 전체
- 에뮬레이터에서 확인: Virtual Console로 넣은 속도가 화면에 표시됨, 내비를 앞에 띄운 상태에서 안내 재생, 분할 화면 배치(영어, 프랑스어, 스페인어)
- 아직 실제 NaviHelper 경로 안내로 받은 어린이보호구역·카메라·TBT 이벤트는 확인하지 못했습니다(시연 모드와 단위 테스트로 검증).
- 실차 확인은 하지 않았습니다.
