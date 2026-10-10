# 오늘의 학습 · 버전 1.3

## 구현

홈에 오늘의 학습·맞춤학습·오답노트를 표시하고 기존 따뜻한 흰색·민트 Material 3 스타일을 유지합니다.
맞춤학습은 기존 DAY 선택과 학습 방식 그대로이며 결과 이름을 구분합니다.
오늘의 학습은 1회독 DAY 1부터 시작하고 사용자가 DAY를 선택하지 않습니다.
현재 회독·DAY와 시작/이어하기 버튼을 표시합니다.

시작 전에 해당 DAY 전체 문제를 섞고 모든 4지선다 보기를 생성하여 순서 그대로 저장합니다.
답을 고르면 기존 오답 규칙으로 저장하고 선택한 보기 번호를 오늘의 학습 파일에 기록한 후 즉시 채점 결과를 보여줍니다.
중도 종료 후에는 저장된 답을 건너뛰고 미응답 문제부터 이어갑니다. 정답 수는 이전 답변을 포함합니다.
마지막 답을 제출하는 순간 다음 DAY로 진행 상태를 원자적으로 저장합니다. 결과 보기·다음 DAY 버튼을 누르기 전 종료해도 완료가 유지됩니다.
오답이 있어도 완료합니다. 같은 날 다음 DAY를 계속할 수 있고 날짜에 따라 건너뛰지 않습니다.
DAY 30의 마지막 답에서 다음 회독 DAY 1을 저장합니다. 결과는 완료한 회독을 표시하고 다음 회독 시작 버튼을 제공합니다.
새 회독에서는 이전 답변을 비우고 문제 및 보기 순서를 새로 생성합니다. 무작위이므로 개별 문제의 보기가 우연히 같을 수 있습니다.

## 데이터 보존·중복 방지

기존 오답 파일 `wrong-answers-v1.txt`와 Notebook.kt를 변경하지 않았습니다.
별도 `daily-progress-v1.json`(schema version 1)에 회독·DAY·세션 토큰·전체 문제 ID/보기·답변을 저장합니다.
임시 파일 쓰기, fsync, 원자적 교체를 사용하며, 저장 실패 시 답을 확정하지 않습니다.
오답 저장 뒤 진행 쓰기가 실패해도 재시도의 오답 추가는 중복 없이 처리됩니다.
이전 스냅샷과 현재 저장 상태가 일치할 때만 답을 수락하여 오래된 세션의 중복 전환을 막습니다.
UI busy/제출 잠금과 엔진의 이미 제출된 답 반환으로 연속 입력을 막습니다. 완료 뒤 Next는 더 진행하지 않습니다.
파일이 손상되었을 때 진행을 임의로 초기화하지 않고 오류를 표시합니다. 기존 오답 파일은 그대로 유지합니다.
맞춤학습·오답 복습은 오늘의 학습 파일에 쓰지 않습니다.
동일 applicationId 및 기존 디버그 서명으로 업데이트 설치하면 오답을 보존합니다. 앱 삭제/데이터 삭제는 피하세요.
작업 전 소스 백업: `/workspace/backups/toiec-before-daily.tar.gz`. Git은 기존처럼 unborn이며 사용자 파일은 untracked 상태로 유지했습니다.

## 검증 범위

사용자 요청에 따라 이 단계에서는 에뮬레이터 시작, APK 설치, 실제 앱 실행, 스크린샷 캡처를 수행하지 않았습니다.
이전 버전 1.2의 실행 캡처/계측 결과는 이번 버전의 실행 증거가 아닙니다.
Android 계측 테스트 소스는 변경된 맞춤학습 진입에 맞춰 수정했고 테스트 APK까지 컴파일했지만 실행하지 않았습니다.

JVM에서 실제 954개 원본 자산, FileNotebook, DailyStore의 파일 저장과 엔진을 사용해 검증합니다.
DailyStudyTest는 첫 시작, 모든 문제/보기 저장, 중도 종료 후 정확한 순서 복원, 정답 수 복원,
오답이 있는 DAY 완료, 결과 버튼 전 재생성 후 다음 DAY, 같은 날 연속 진행,
맞춤학습 및 복습의 진행 분리, DAY 30 회독 전환, 답/Next 중복,
전체 30개 DAY를 두 회독 완료하여 회독이 정확히 3이 되는지,
기존 오답 보존, 새 회독 답변 초기화/문제·보기 재섞기,
오래된 세션의 중복 전환 차단, 저장 실패/오답 저장 실패/손상 파일의 비파괴 처리를 검사합니다.

재현 명령:
```bash
cd /workspace/toiec
source scripts/env.sh
cd android
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

## 사용자가 로컬에서 확인할 항목

1. 기존 앱에 업데이트 설치 후 오답 기록 유지, 홈에 1회독 DAY 1 및 세 메뉴 표시.
2. 오늘의 학습에서 몇 문제 답하기 → 앱 강제 종료 → 재실행 → 미응답 문제 이어하기.
3. 마지막 답 제출 → 결과 버튼을 누르기 전에 종료 → 재실행 시 다음 DAY 표시.
4. 결과의 다음 DAY 학습으로 같은 날 즉시 다음 DAY 시작.
5. 맞춤학습에서 다른 DAY 완료 후 오늘의 학습 진행이 변하지 않음.
6. DAY 30 완료 시 회독 완료 및 다음 회독 시작, 재실행 후 다음 회독 DAY 1 표시.
7. 버튼 연속 입력 시 답/회독 증가가 중복되지 않고, 새 회독에도 오답노트 유지.
8. 큰 글꼴에서 새 홈/결과 화면의 스크롤과 버튼 접근 확인.

APK는 artifacts/toeic-vocabulary-1.3-debug.apk입니다. Android 8.0 이상에서 다운로드·열기 후 요청 시 출처 허용을 켜고 업데이트 설치합니다.

## 최종 결과

- 기존 StudyTest 15개 + DailyStudyTest 11개 = 26개 통과, 실패·오류·건너뜀 0.
- assembleDebug / assembleDebugAndroidTest / lintDebug: BUILD SUCCESSFUL (36초). 계측 테스트는 컴파일만 했습니다.
- Lint는 기존과 같은 0 errors, 19 warnings이며 홈 디렉터리의 읽기 전용 analytics.settings 경고는 앱 빌드에 영향을 주지 않습니다.
- APK 서명 검증 통과. 이전 1.2와 동일 인증서 SHA256: 2e2456522228bf951b39d57e94bfa14a1d54a0c5e9eb1aec5695e1040e56db7b.
- APK applicationId com.toiec.vocabulary, versionName 1.3 / versionCode 4, minSdk 26 / targetSdk 36.
- APK SHA256: abfcbeea6746236cf4a97b52f31dd9994a11eba3b6f513d5b9d5d22d5e166e76.
- 백업 비교: Notebook.kt, StudySession.kt, vocabulary.json 변경 없음. APK에 포함된 vocabulary.json도 동일.
- 실제 실행 검증: 미수행. 최신 JVM 결과·빌드 로그는 daily-evidence/에 보관했습니다.
