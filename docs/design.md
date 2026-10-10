# Android 디자인 적용 · 2026-10-10

## 보존 및 범위

작업 전 Git은 커밋이 없는 unborn 상태이며 기존 소스가 모두 untracked였습니다. 기존 파일을 삭제하거나 Git reset/commit하지 않았습니다.
소스 백업: `/workspace/backups/toiec-before-design-20261010.tar.gz`.
기존 APK 백업: `/workspace/backups/toeic-vocabulary-1.1-before-design.apk`.
원본 Excel, vocabulary.json, Vocabulary.kt, Notebook.kt, StudySession.kt, StudyViewModel.kt는 백업과 바이트 단위로 같습니다.

## 스킬 설치

참고 페이지 https://open-design.ai/skills/mobile-android-design/ 는 현재 환경에서 HTTP 403을 반환하여 본문을 읽지 못했습니다.
원본 https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/mobile-android-design/SKILL.md 와 상세 참조를 직접 확인했습니다.
사용자가 제시한 `npx skills add https://github.com/wshobson/agents --skill mobile-android-design` 명령은 유효하며 프로젝트/Codex 범위를 명확히 지정해 실행했습니다.

```bash
cd /workspace/toiec
NPM_CONFIG_CACHE=/workspace/toolchains/npm-cache npx --yes skills add https://github.com/wshobson/agents --skill mobile-android-design --agent codex --yes
NPM_CONFIG_CACHE=/workspace/toolchains/npm-cache npx --yes skills list --agent codex
```

기본 npm 캐시가 읽기 전용이므로 쓰기 가능한 캐시만 지정했습니다. HOME은 바꾸지 않았습니다.
실제 설치 위치 `.agents/skills/mobile-android-design/SKILL.md`, 참조 4개, 루트 skills-lock.json을 확인했습니다.
`skills list --agent codex`에 프로젝트 스킬 mobile-android-design / Agents: Codex가 표시됩니다.
현재 세션에 설치 스킬을 자동 재검색했다고 주장하지 않으며, 설치 파일을 직접 읽고 적용했습니다. 새 Codex 작업에서 표준 .agents/skills 경로로 검색 가능합니다.
별도 디자인 프로그램이나 API 키는 필요 없습니다. 설치 명령에는 현재 설치된 Node.js/npx가 필요합니다.
SKILL.md 원본 일치 SHA256: 0a209a1c45585ce0056548cc2f0fd352b7fde5980a3b5dd397a9daf5dff19fe7.
설치 및 인식 로그는 design-evidence/에 보관했습니다.

## 적용

네이티브 Kotlin·Compose와 Material 3을 유지했습니다. 사용자가 지정한 팔레트에 따라 고정 밝은 테마를 사용합니다.
배경 #FAF9F5, 본문 #202B29, 민트 #276B58, 옅은 민트 #E5F2EC, 오답 #963E37 및 #FFEDE8.
본문 대비 13.85:1, 보조 글자 5.57:1, 주요 버튼 5.99:1, 오답 피드백 6.09:1.
공통 글꼴 크기·행간·여백·둥근 카드·버튼을 홈/문제/채점/결과/오답노트에 적용했습니다.
영단어 40sp, 뜻 18sp, 보기 최소 64dp, 주요 버튼 최소 56dp, 보조 터치 최소 48dp.
보기 4개는 세로 배치하고 뜻의 줄 수를 제한하지 않습니다. 스크롤과 safeDrawingPadding으로 시스템 바를 피합니다.
정답/오답은 색상 외에 정답·내 선택·오답 문구, 선택/정답 전문으로 표시합니다. 제출한 보기는 비활성화합니다.
자동 채점, 오답 중복 방지, 파일 저장, 복습 정답 제거와 보기 생성 로직은 유지했습니다.
기능·메뉴·입력 방식은 추가하지 않았습니다.

## 검증

기존 JVM 테스트 15개: 모두 통과, skip 0. debug 앱/계측 APK 빌드와 lintDebug 통과.
Test Android Apps 플러그인은 현재 callable 도구에 없어 사용할 수 없었습니다. Android 공식 adb와 Compose 계측 테스트를 사용했습니다.
API 30 Android 11 소프트웨어 headless 에뮬레이터에서 실제 앱을 설치하고 실행했습니다. 물리 휴대폰 검증은 수행하지 않았습니다.
화면 캡처는 artifacts/design/에 있습니다. 큰 글꼴 긴 뜻 화면은 실제 운영 StudyContent에 최장 원본 뜻을 넣은 계측 레이아웃 fixture입니다.
첫 레이아웃 테스트는 병합된 semantics의 보기 번호를 뜻으로 검사해 실패했습니다. 캡처로 뜻 줄바꿈을 확인하고 실제 뜻의 unmerged TextLayoutResult를 검사하도록 수정했습니다. 검사를 제거하지 않았습니다.

APK: artifacts/toeic-vocabulary-1.2-debug.apk, versionName 1.2 / versionCode 3, Android 8.0 이상.
APK 서명 검증 통과. SHA256: 122eaef870330c601b6c21d49fcb8cacbc0752595384851895bab3b3c30fb5d1.
설치: 휴대폰에서 APK 다운로드 → 파일 열기 → 요청 시 해당 파일 앱/브라우저의 ‘이 출처 허용’ → 설치. 기존 동일 서명 앱에는 업데이트로 설치해 오답을 보존하세요.

최종 계측 테스트: OK (2 tests), 47.054초. 실제 앱 흐름 및 200% 글꼴 레이아웃 검사 모두 통과했습니다.
화면별 PNG: 01-home, 02-question, 03-wrong-answer, 04-notebook, 05-correct-answer, 06-result, 07-large-font-long-meaning, 08-large-font-last-option.
200%에서는 모든 보기가 한 화면에 들어가지 않아 스크롤하며, 마지막 보기와 하단 버튼까지 접근 가능하고 뜻의 TextLayoutResult에 잘림이 없습니다.

Android 시스템 font_scale=2.0에서도 실제 홈과 무작위 학습 문제를 실행해 09-system-font-200-home.png 및 10-system-font-200-question.png로 확인했습니다. 앱 콘텐츠와 하단 버튼은 시스템 바 안쪽에 있고 보기 뜻은 줄바꿈됩니다. API 30 시스템 상태 바 시계 자체는 200%에서 상단이 일부 잘리므로 OS 영역의 제약으로 구분합니다. 앱 화면에서는 같은 잘림이 없습니다.
글꼴 설정은 원래 unset(null)이어서 settings delete system font_scale로 복구했습니다. 테스트 전 존재하지 않던 오답 파일도 삭제하여 원래 상태로 복구했습니다.
200% 변경 직후 am start -W는 소프트웨어 에뮬레이터 지연으로 timeout을 보고했지만 UI hierarchy 및 실제 캡처로 정상 화면 표시를 확인했습니다.
클라우드 시작 안내 start_skill은 버전 1.2 및 스킬/계측 테스트 절차로 저장했습니다. 설정 화면에서 검토·저장 후 Publish해야 새 환경에 반영됩니다. 새 작업에서 복원되는지는 아직 별도로 검증하지 않았습니다.
