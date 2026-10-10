# 토익 영단어 Android

Kotlin + Jetpack Compose 네이티브 Android 앱입니다. 로그인·유료 API·WebView·인터넷 권한 없이 동작합니다.
최소 Android 8.0(API 26), compileSdk/targetSdk 36. 버전 1.5(versionCode 6).

## 사용

1. 홈의 **오늘의 학습**은 `1회독 · DAY 1`부터 순서대로 진행합니다. DAY는 선택하지 않습니다.
2. **학습 시작/학습 이어하기**를 누르면 해당 DAY의 문제를 풀거나 미응답 문제부터 이어갑니다.
3. 영어 단어·표현에 맞는 뜻을 선택하면 즉시 채점합니다. 제출한 답은 바꿀 수 없습니다.
4. 마지막 답 제출 시 다음 DAY가 저장됩니다. 결과의 **다음 DAY 학습**으로 같은 날 계속할 수 있습니다.
5. DAY 30 완료 시 **N회독 완료**와 **N+1회독 시작**을 표시합니다. 종료해도 다음 회독 DAY 1부터 시작합니다.
6. **맞춤학습**에서는 기존처럼 DAY를 선택합니다. 오늘의 학습 진행에는 영향을 주지 않습니다.
7. **오답노트**에는 틀린 단어가 중복 없이 저장됩니다. 복습에서 맞히면 제거하고 틀리면 유지합니다.

오늘의 학습은 날짜를 참조하지 않습니다. 모든 문제에 답하면 오답 수와 관계없이 DAY를 완료합니다.
현재 답변, 전체 문제 순서와 보기 순서를 앱 내부 `daily-progress-v1.json`에 저장합니다.
새 회독에서는 답변을 비우고 문제·보기를 다시 섞습니다. 기존 `wrong-answers-v1.txt`는 유지합니다.

오답 기록은 앱 내부 파일에 보관되며 앱 종료·재실행과 업데이트 후 유지됩니다.
앱 삭제 또는 Android 설정의 앱 데이터 삭제 시에는 기록이 삭제됩니다.
화면 회전 시 진행 중인 학습 상태는 ViewModel에서 유지합니다. 앱 프로세스가 종료되면 오늘의 학습은 미응답 문제부터 이어갑니다. 맞춤학습·오답 복습의 진행은 다시 시작하고 오답 기록은 유지합니다.
오답 복습은 시작 시점의 목록으로 진행하므로 정답 제거가 다음 문제를 건너뛰지 않습니다.
저장 실패 시 메시지를 표시하고 답 선택을 재시도할 수 있습니다.

## 나의 학습 분석

홈의 오답노트 아래에서 오늘의 정답률·푼 문제 수와 최근 7일 그래프를 확인합니다.
오늘의 학습·맞춤학습·오답 복습의 실제 제출만 기록하며 같은 단어를 다시 풀면 매번 포함합니다.
문제 진입·발음·채점 화면 재표시는 기록하지 않습니다. 기능 추가 전 결과를 추측하여 만들지 않습니다.
정답률은 맞힌 답/전체 답 기준이며 기록 없는 날은 점·연결선이 없습니다.
푼 문제 그래프는 날짜별 정답(민트)·오답(회색) 누적 막대와 전체 문제 수(짙은 선)입니다.
점·막대 또는 날짜 버튼을 누르면 정확한 수치를 보여줍니다. 작은 화면에서는 그래프를 좌우로 밀 수 있습니다.
각 답변의 제출 당시 현지 날짜로 집계하며 날짜를 넘겨 이어 풀어도 각각의 날짜에 반영합니다.
기록은 answer-history-v1.json에 저장하고 기존 오답·회독 파일과 함께 중단된 저장을 복구합니다.
검증과 화면 캡처: [분석 보고서](docs/analytics.md).

## 영단어 발음

오늘의 학습·맞춤학습·오답 복습의 단어 카드에 **소리 ON/OFF**, **다시 듣기**가 있습니다.
최초에는 ON이며 소리 설정은 앱 종료 후에도 유지됩니다.
ON일 때 문제당 한 번 자동 발음합니다. 채점·화면 갱신·백그라운드 복귀는 반복 재생하지 않습니다.
OFF에서도 다시 듣기는 가능하며 채점 후에도 사용할 수 있습니다. OFF→ON은 다음 문제부터 적용합니다.
다음 문제·학습 종료·백그라운드 이동 시 음성을 멈춥니다.
Android TextToSpeech의 설치된 오프라인 영어 음성을 사용하며 유료 API·미디어 음량 변경은 없습니다.
영어 음성이 없으면 학습은 계속 가능하고 단어 카드에서 설치 안내를 제공합니다.
기기 설정의 텍스트 음성 변환에서 음성 엔진 및 영어 음성 데이터를 설치/선택하세요.
기기별 설정 위치는 접근성 또는 일반 관리 등에 있습니다. 자세한 검증 범위: [발음 보고서](docs/pronunciation.md).

## 데이터

첨부된 ‘토익 영단어.xlsx’의 실제 A1 DAY 제목을 확인하여 F열 번호·G열 영어·H열 뜻을 읽었습니다.
시트명 `Day 25 (2)`는 실제 제목 **DAY 26 은행**으로 분류했습니다.
Test 30개 시트를 제외하고, 단어·뜻이 없는 행을 제외했습니다. 원본은 수정하지 않았습니다.

30개 DAY, **954항목**. DAY 11·13·14·20·28·30은 31개, 나머지는 32개입니다.
전체 DAY별 표와 원본 체크섬: [추출 보고서](docs/vocabulary-report.md).
앱 자산: `android/app/src/main/assets/vocabulary.json`.
재추출은 개발용 Python/openpyxl로만 수행합니다. 앱 실행에는 Python·Excel·네트워크가 필요 없습니다.

```bash
cd /workspace/toiec
python3 scripts/extract-vocabulary.py /path/to/토익\ 영단어.xlsx
```

보기는 원본 뜻 전체를 사용합니다. 순서를 무작위로 섞고 동일 뜻, 같은 영어 표현의 다른 뜻,
겹치는 뜻 및 등록된 유사 의미군을 보수적으로 제외합니다.
원본의 오탈자나 번역을 임의로 고치지 않았습니다.

## 개발 및 빌드

Android Studio에서는 `android/`를 프로젝트로 엽니다.
JDK 17, Gradle 9.1.0, AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01을 사용합니다.

```bash
cd /workspace/toiec
source scripts/env.sh
python3 scripts/configure-gradle-proxy.py
cd android
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
`local.properties`와 프록시 설정은 로컬용입니다. 다른 개발 머신에서는 JDK 17과 SDK API 36/Build Tools 36을 설치하고 SDK 경로를 설정합니다.
Gradle Wrapper는 공식 체크섬으로 배포 파일을 검증합니다.
도구 설치와 공식 CLI/Skills 정보: [환경 기록](docs/environment.md).

## Android 휴대폰에 설치

Android 8.0 이상 휴대폰에서 제공된 APK를 다운로드한 뒤 파일 앱 또는 브라우저의 다운로드 목록에서 엽니다.
Android가 요청하면 APK를 여는 브라우저/파일 앱의 **이 출처 허용**을 활성화하고 설치합니다.
설치 후 앱을 열어 DAY 선택·학습·오답 복습을 확인하고 해당 설치 허용 설정을 다시 끌 수 있습니다.
기존 기본 앱 위에 설치하면 업데이트됩니다. 서명이 다른 기존 설치본이 있으면 동일 서명의 APK가 필요하며,
앱을 삭제하면 기존 오답 기록도 삭제됩니다.

개발 머신에서 USB 디버깅을 사용하는 경우:

```bash
adb devices -l
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.toiec.vocabulary/.MainActivity
```

현재 클라우드 머신에는 KVM·USB 기기 접근 경로가 없지만, 추가 검증에서 API 30 AVD를 생성하고
소프트웨어 headless 방식으로 부팅·APK 설치·홈 화면 실행과 캡처에 성공했습니다.
[에뮬레이터 실행 기록](docs/emulator.md)을 참고하세요. 디자인 적용 후 실제 학습·오답 저장·복습 정답 제거 흐름도 Android 계측 테스트로 검증했습니다.
코드·JVM 테스트·빌드 검사 결과는 [검증 보고서](docs/validation.md)에 구분해 기록합니다.
버전 1.3 오늘의 학습 및 1.4 발음 기능은 사용자 요청에 따라 에뮬레이터를 실행하지 않았습니다.
진행 검증은 [오늘의 학습 보고서](docs/daily-study.md), 발음 검증은 [발음 보고서](docs/pronunciation.md)를 확인하세요.
1.5 분석 기능은 최신 요청에 따라 에뮬레이터 실행·화면·기록 복원 검증을 완료했습니다. 결과는 [분석 보고서](docs/analytics.md)에 별도로 기록합니다.

## 코드 구조

- `speech/`: Android TextToSpeech, 자동 발음 제어, 소리 설정 저장
- `data/Vocabulary.kt`: 번들 JSON 데이터 로드
- `data/Notebook.kt`: 중복 없는 오답 ID의 동기화·원자적 파일 저장
- `study/DailyStudy.kt`: 오늘의 학습 문제·보기·답변의 원자적 저장, 이어하기, DAY·회독 전환
- `study/StudySession.kt`: 보기 생성, 채점, 제출 잠금, 진행, 오답 추가·제거
- `ui/main/StudyViewModel.kt`: 생명주기와 파일 I/O 처리
- `ui/main/MainScreen.kt`: DAY 선택·문제·오답노트·결과 화면
- `app/src/test/.../StudyTest.kt`: 실제 데이터 및 채점·오답·저장 테스트

따뜻한 흰색·짙은 글자·민트 포인트의 Material 3 디자인을 적용했습니다.
설치한 디자인 스킬과 화면별 검증: [디자인 보고서](docs/design.md).
