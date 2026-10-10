# Android 개발 환경 확인

## 공식 출처

- [Android CLI 개요](https://developer.android.com/tools/agents/android-cli)
- [CLI 설치](https://developer.android.com/tools/agents/android-cli/download)
- [Android Skills](https://developer.android.com/tools/agents/android-skills)
- [Skills 공식 저장소](https://github.com/android/skills)

developer.android.com의 직접 HTTPS 요청은 클라우드 프록시에서 403으로 차단되었습니다.
대신 설치한 Google Android CLI의 공식 Android Knowledge Base를 다운로드하여
`android docs fetch`로 위 문서 내용을 확인했습니다. 읽은 내용은 같은 디렉터리에 저장했습니다.
제공된 블로그 URL의 본문 자체는 직접 확인하지 못했습니다.

## 설치 및 지원 플랫폼

CLI는 Linux x86_64, macOS Apple/Intel, Windows x86_64용 설치를 공식 안내합니다.
현재 설치 스크립트와 android-cli 스킬은 Linux ARM64 경로도 제공합니다.
Linux는 curl 설치 또는 서명 키를 설정한 apt, macOS는 curl 또는 Homebrew,
Windows는 cmd 설치 또는 winget을 지원합니다. Windows PowerShell 설치와
Windows의 `android emulator`는 공식 개요의 알려진 제한입니다.
Skills는 SKILL.md 표준을 지원하는 에이전트에서 사용하며 별도 실행 OS 요구사항이 없습니다.

Linux 공식 기본 명령:

```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
android init
```

Codex 프로젝트에 명시적으로 설치:

```bash
android skills add android-cli --agent=codex --project=/workspace/toiec
```

이 명령을 실제 실행했고 `.agents/skills/android-cli`에 설치했습니다.
클라우드 사용자 홈은 읽기 전용이라 `ANDROID_USER_HOME`을 `/workspace/toolchains/android-user`로 지정했습니다.
SDK Command-line Tools 23에 포함된 Android CLI 런처로 공식 CLI 1.0.16500706을 설치했습니다.
Google SDK 저장소 메타데이터의 체크섬과 실제 파일을 대조했습니다.
JDK는 Eclipse Temurin 17.0.16+8을 공식 GitHub 릴리스에서 받고 SHA-256을 검증했습니다.
Gradle 9.1.0은 Google 템플릿의 Wrapper와 distributionSha256Sum으로 검증합니다.
HTTPS 프록시 설정과 머신에 제공된 Java CA truststore를 사용하며 TLS 검증을 해제하지 않았습니다.

## 재사용

`source scripts/env.sh` 후 CLI와 Wrapper를 사용합니다.
게시된 환경 스냅샷은 `/workspace/toolchains`의 도구와 캐시를 보존해야 합니다.
`scripts/setup-cloud.sh`는 기존 검증 도구의 SDK/의존성을 갱신하고 디버그 APK를 빌드합니다.
새 머신에서 스냅샷이 없는 경우 JDK와 SDK Command-line Tools를 먼저 설치해야 합니다.
현재 작업은 이미 격리된 환경이므로 별도 Git worktree를 생성하지 않습니다.

## 이번 실행의 검증 결과

- 최초 `:app:assembleDebug`: 성공, 36개 작업 실행.
- `bash scripts/setup-cloud.sh`: 성공, 36개 작업 up-to-date로 재사용 확인.
- `:app:lintDebug`: 성공, 오류 0개·경고 15개(템플릿 의존성 업데이트 안내 및 미사용 리소스 등). 실제 기기 테스트를 수행했다는 의미는 아닙니다.
- `apksigner verify --verbose`: 성공, APK Signature Scheme v2 검증.
- APK 패키지: `com.toiec.vocabulary`; 런처: `.MainActivity`; 최소 API 26, 타깃 API 36.
- APK에 INTERNET 권한 없음. 단어 기능은 아직 미구현입니다.
- APK: `/workspace/toiec/android/app/build/outputs/apk/debug/app-debug.apk` (약 12MB).
- SHA-256: `d0be378d06611e178c355df606b69cde93fabd43b2410ee5b013f0faff108bb6`.
- 실제 화면 실행: 미확인. 등록된 AVD 없음, KVM 및 USB 접근 경로 없음.
- 첨부 엑셀: 대화·워크스페이스에서 파일이 확인되지 않아 Day별 항목 수 미확인. 원본 수정 없음.

환경 설정 초안에 install_script, start_skill과 공식 문서/SDK용 네트워크 도메인을 저장했습니다.
저장은 현재 실행 환경을 준비하거나 게시하는 동작과 별개입니다. 환경 설정에서 검토·저장 후 게시해야
준비된 도구·캐시의 스냅샷을 이후 작업에 사용할 수 있습니다. 새 작업에서의 복원은 아직 검증하지 않았습니다.
