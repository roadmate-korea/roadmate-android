# RoadMate Korea

한국에서 렌터카를 빌린 외국인 운전자에게 한국 교통 규칙을 운전자의 언어로 안내하는 Pleos Connect 앱입니다. 2026 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

## 주요 기능 (1차 MVP)

- 2페이지 온보딩: 사용 언어(English, Français, Español), 평소 운전 국가, 한국 운전 경험
- 개인화 안내: 좌측통행 국가 출신이나 좌핸들 차량이 처음인 운전자에게 적응 안내를 더하고, 한국 운전 경험에 따라 설명 수준을 조절
- 주행 중 음성 안내: 어린이보호구역, 우회전, 비보호 좌회전
- 시연 모드: 같은 경로에서 두 운전자(Nakamura, Carlos)가 서로 다른 안내를 받는 장면 재생

시나리오 상세: [docs/mvp-scenario.md](docs/mvp-scenario.md)

## 사용한 Pleos Connect SDK

| SDK | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 기어, 방향지시등 |
| NaviHelper | 3.0.5 | 제한 속도, 어린이보호구역, 단속 카메라, 회전 안내 |
| TextToSpeech | 3.3.1 | 음성 안내 |
| SpeechToText | 3.3.1 | 정차 중 통역(2단계) |
| FusedLocation | 1.1.3 | 위치 |

## 빌드

요구 사항: Android Studio, JDK 17 이상 21 이하(Android Studio 내장 JDK 권장), Android SDK Platform 35

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 실행

Pleos Connect Emulator(Android Studio SDK Manager에서 설치)에서 실행합니다. 일반 안드로이드 폰에서는 Pleos 차량 서비스가 없어 동작하지 않습니다.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

제출용 APK는 `dist/` 폴더에 있습니다.
