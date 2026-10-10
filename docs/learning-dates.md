# 학습일 표시 수정 · 버전 1.6

두 그래프는 실제 답변 제출 기록의 localDate만 그룹화하고 최신 학습 날짜 최대 7개를 오래된 순서로 표시합니다. 앱 접속이나 통계 새로 고침은 날짜나 풀이 기록을 만들지 않습니다. 학습하지 않은 날짜를 보충하지 않고 모든 날짜를 같은 간격으로 배치합니다.

오늘의 집계는 그래프 목록과 별도입니다. 과거 기록만 있으면 그래프에는 과거 날짜가 보이지만 오늘 정답률은 —, 오늘 문제 수는 0개입니다. 한 학습 날짜는 점·막대만 표시하며, 두 날짜 이상이면 표시한 날짜 사이를 연결합니다. 기록이 없으면 그래프와 날짜 버튼을 만들지 않고 기존 기록 없음 안내와 학습 시작 버튼을 유지합니다. 날짜·점·막대 터치로 상세 수치를 확인할 수 있습니다.

저장 형식과 제출 처리에는 변경이 없습니다. answer-history-v1.json, daily-progress-v1.json, wrong-answers-v1.txt, sound-settings-v1.txt를 삭제·변환하지 않습니다. 반복 풀이와 동일 제출의 저장 재시도 처리도 유지합니다.

## 검증

- JVM 55개 테스트 통과: 기존 학습·회독·발음·오답 저장 검증과 빈 기록, 과거 한 날짜, 간격이 큰 날짜, 1~6개 날짜, 최신 7개 선택·정렬, 오늘 별도 집계, 반복 조회 시 기록 수 불변.
- 디버그 APK 및 Android 테스트 APK 빌드 성공. Lint 오류 0개, 기존 경고 19개. APK 서명 검증 통과.
- 실행 환경: API 30 Android 11 소프트웨어 headless 에뮬레이터. 실제 휴대폰과 실제 발음 출력은 이번 단계에서 확인하지 않습니다.
- Android 화면 테스트 5개 통과(75.068초): 기록 0개·과거 1일·간격이 큰 3일, 날짜 상세 선택, 320dp/480dp에서 글꼴 200%의 날짜·상세 텍스트 잘림 없음.
- 실제 MainActivity/ViewModel의 답 제출 흐름 테스트 1개 통과(92.764초): 접속만으로 기록 0건 유지, 3정답/1오답=4개/75%, 채점 재표시·재제출 호출 시 중복 없음, Activity 재생성 및 오늘의 학습 이어하기, 맞춤학습 새 제출 추가, 오답 복습 정답 시 제거 및 과거 기록 유지. 화면 테스트와 합쳐 Android 검증 6개 통과.
- 실행 검증 후 에뮬레이터 앱 파일은 사전 백업과 파일별 바이트 일치까지 확인하여 복원했습니다.
- LearningDatesTest 화면은 명시적인 UI 테스트 데이터로 실제 Compose 컴포넌트를 실행합니다. 테스트 데이터는 앱 풀이 파일에 저장하지 않습니다. 과거 날짜를 위한 기기 시계 조작도 하지 않습니다.
- 스크린샷은 이번 요청의 명시적인 캡처 요구에 따라 생성합니다. 일반 작업에서 캡처하지 않는 기존 선호는 유지합니다.

## APK

artifacts/toeic-vocabulary-1.6-debug.apk (versionCode 7, Android 8.0 이상).
SHA256: 696fb0279edfcb8e732199e0f8f1e22d8a7aa4d6113539fdff938fdf1fa4be54
기존 앱을 삭제하거나 데이터를 지우지 않고 업데이트 설치하세요.

이전 1.5 집계·검증 기록은 analytics.md에 버전별로 남깁니다. 최신 날짜 표시 기준은 이 문서가 우선합니다.

## 화면 캡처

- 실제 앱에서 답 제출: artifacts/learning-days/actual-accuracy.png, actual-count.png
- 기록 없음: learning-days-00-empty-accuracy.png, learning-days-00-empty-count.png
- 과거 학습일 하나·오늘 풀이 없음: learning-days-01-single-accuracy.png, learning-days-01-single-count.png
- 간격이 큰 학습일 세 개: learning-days-03-multiple-accuracy.png, learning-days-03-multiple-count.png

실제 앱 캡처 이외에는 production Compose 화면에 명시적인 테스트 데이터를 전달한 캡처입니다. 과거 날짜를 실제 사용 기록처럼 생성하지 않습니다.
