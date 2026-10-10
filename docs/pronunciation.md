# 영단어 발음 · 버전 1.4

## 구현

네이티브 Android TextToSpeech를 사용합니다. 유료 API, 새로운 앱 인터넷 권한, WebView는 없습니다.
설치된 영어 음성 중 networkConnectionRequired=false 및 notInstalled 표시가 없는 음성을 선택합니다. 미국 영어를 우선하고 설치된 다른 영어 음성도 사용할 수 있습니다.
영어 음성 없음·엔진 초기화 실패·재생 오류는 UNAVAILABLE 상태로 표시하며 학습·채점에는 영향을 주지 않습니다.
Android 11 이상 TTS 엔진 검색을 위해 manifest queries에 android.intent.action.TTS_SERVICE를 추가했습니다.
AudioAttributes는 USAGE_MEDIA/CONTENT_TYPE_SPEECH이며 미디어 음량을 변경하지 않습니다.

모든 학습 모드는 동일한 발음 경로를 사용합니다. 세션 UUID·문제 인덱스·단어 ID를 키로 삼아 문제당 자동 재생을 한 번만 소비합니다.
채점·화면 갱신·ViewModel을 유지하는 화면 회전은 같은 키를 사용하므로 중복 자동 재생하지 않습니다.
TTS 준비 중에는 현재 문제만 대기하고, 문제 전환·화면 종료·백그라운드·소리 OFF 시 과거의 대기를 취소합니다.
OFF에서 ON으로 바꿀 때 현재 문제의 자동 재생을 새로 예약하지 않습니다.
OFF에서도 다시 듣기는 동작하며 채점 후에도 가능합니다. 수동 재생은 이전 음성을 stop + QUEUE_FLUSH로 교체합니다.
다음 문제 전 stop을 호출하고 홈·맞춤 선택·오답노트·결과 화면 이동 시 현재 단어 키를 지워 재생을 중단합니다.
Activity ON_PAUSE/ON_STOP 및 Compose 화면 해제 시 stop하며 onCleared에서 shutdown합니다.
백그라운드 복귀 시 같은 문제는 자동으로 다시 재생하지 않습니다. 영어 데이터 설치 후 복귀하면 엔진을 재초기화하고 수동 다시 듣기 또는 다음 문제를 사용할 수 있습니다.

## UI·설정·기존 데이터

기존 단어 카드에 48dp 이상 소리 ON/OFF와 다시 듣기 버튼을 배치했습니다. 넓이가 부족하면 FlowRow로 줄바꿈합니다.
스피커/음소거 아이콘과 재생 아이콘을 구분하고 버튼 contentDescription 및 소리 stateDescription을 제공합니다.
소리 설정은 별도 sound-settings-v1.txt에 저장합니다. 기본 ON이며 파일 저장은 임시 파일·fsync·원자적 교체를 사용합니다.
저장 실패 시 토글을 이전 값으로 되돌리고 안내합니다. 기존 오답·회독 파일을 변경하거나 삭제하지 않습니다.
영어 음성이 없으면 같은 카드에서 설치 안내와 버튼을 표시합니다. 설치 화면을 지원하지 않는 기기는 TTS 설정, 이어서 일반 설정으로 이동하며 모두 불가능해도 안내만 표시합니다.
다시 듣기는 준비/사용 불가 상태에만 비활성화되며 OFF·채점 상태에는 비활성화하지 않습니다.
백업: /workspace/backups/toiec-before-speech.tar.gz. 기존 Git untracked 작업을 보존했습니다.

## 검증 구분

SpeechTest는 가짜 SpeechPort로 재생/중단 요청을 기록합니다. 실제 스피커 출력 또는 Android TTS 엔진을 실행한 검증은 아닙니다.
자동 재생 중복 방지, 세 학습 모드/새 세션, 다음 문제 이전 중단, OFF 수동 다시 듣기, 채점 후 다시 듣기,
OFF→ON 지연 적용, 비동기 초기화 중 문제 전환, 대기 중 OFF→ON,
백그라운드 및 화면 이동 중단, 늦은 초기화 콜백, 사용 불가/실패 시 반복 재생 방지, shutdown을 검사합니다.
소리 기본값 및 재실행 설정은 실제 SoundSettings 파일 구현을 JVM에서 재생성하여 검사합니다.
첫 테스트에서 음성 사용 불가 상태에 나타난 문제가 엔진 복구 시 자동 재생될 가능성을 발견하고, 사용 불가 상태에서는 자동 대기를 만들지 않도록 수정했습니다.
SpeechButtonsTest는 OFF·채점 상태 수동 버튼과 접근성 상태를 확인하는 Compose 계측 테스트이며 테스트 APK에 포함했습니다. 이번 단계에서는 실행하지 않았습니다.
이전 학습/회독/오답 테스트도 함께 실행합니다.

사용자 요청에 따라 에뮬레이터를 시작하거나 APK를 설치·실행하지 않았습니다. 실제 기기 음성 출력·TTS 엔진 호환성·설치 설정 화면은 미검증입니다.

## 휴대폰에서 확인

1. 기존 앱을 삭제하지 말고 같은 서명의 APK로 업데이트 설치합니다.
2. 휴대폰 미디어 음량과 영어 TTS 데이터 설치 상태를 확인합니다.
3. 오늘의 학습·맞춤학습·오답 복습에서 첫 문제 발음 한 번, 채점 시 반복 없음, 다음 문제 이전 음성 중단을 확인합니다.
4. OFF 상태 수동 다시 듣기 및 채점 후 다시 듣기, OFF→ON은 다음 문제부터 자동 발음하는지 확인합니다.
5. 앱 재실행 시 OFF 유지, 홈/결과 이동 및 앱 백그라운드 시 재생 중단을 확인합니다.
6. 영어 음성이 없는 기기에서도 학습이 계속되고 설치 안내가 열리는지 확인합니다.
7. 설치 후 복귀해 수동 다시 듣기와 다음 문제 발음을 확인합니다.

Android API 참고: https://developer.android.com/reference/android/speech/tts/TextToSpeech 및 https://developer.android.com/reference/android/speech/tts/Voice.

## 최종 결과

- JVM 테스트 42개 통과: 기존 StudyTest 15개, DailyStudyTest 11개, SpeechTest 16개. 실패·오류·건너뜀 0.
- 앱/계측 APK 빌드, lintDebug 성공(39초). Lint 0 errors, 기존 19 warnings.
- APK: artifacts/toeic-vocabulary-1.4-debug.apk, versionName 1.4 / versionCode 5, Android 8.0 이상.
- APK 서명 검증 통과. 이전 APK와 동일 인증서 SHA256: 2e2456522228bf951b39d57e94bfa14a1d54a0c5e9eb1aec5695e1040e56db7b.
- APK SHA256: 0f3c4bc081786e371f0caf7f06796e323c0dbfe068e176d5300bb5cd6ee94841.
- 백업 비교에서 Notebook.kt, StudySession.kt, DailyStudy.kt 및 vocabulary.json이 바이트 단위로 동일하며 APK의 오프라인 자산도 같습니다.
- 실제 기기/에뮬레이터 음성 재생, 화면 실행, 계측 테스트 실행은 미수행입니다. SpeechPort 요청 검증을 실제 음성 출력으로 보고하지 않습니다.
- 로그 및 테스트 XML: docs/speech-evidence/.
