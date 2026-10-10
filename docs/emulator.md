# 현재 Codex 환경의 Android Emulator 실행 검증

## 결론

가속 없이 소프트웨어 방식의 headless Android Emulator를 부팅하고, 앱을 설치·실행하여 실제 홈 화면을 캡처했습니다.
`DISPLAY`와 `WAYLAND_DISPLAY`가 없으므로 표시 창을 사용하지 않았습니다. 창이 없다는 사실은 에뮬레이터 실행 불가를 의미하지 않습니다.

## 확인 및 설치한 구성

- Linux x86_64, 컨테이너 CPU 한도 4코어, 메모리 한도 32GiB.
- 처음에는 Emulator·시스템 이미지·AVD가 없었습니다.
- 설치: Android Emulator 37.2.12(build 16428233), Android 11/API 30 default x86_64 이미지 revision 11.
- AVD: `toeic_headless_api30`, `/workspace/toolchains/avds/toeic_headless_api30.avd`.
- 화면 480×800, density 160, RAM 1536MB, 가상 CPU 2개.
- `/dev/kvm`과 `/dev/dri` 없음. `emulator -accel-check`의 실제 결과:

```
accel:
3
KVM requires a CPU that supports vmx or svm
accel
```

호스트 정보의 KVM hypervisor 표시는 게스트 컨테이너의 KVM 사용 가능성을 뜻하지 않습니다.
CPU 확장과 장치가 현재 컨테이너에 제공되지 않아 VM 가속은 사용할 수 없습니다.

## 실제 실행한 명령

```bash
cd /workspace/toiec
source scripts/env.sh
android sdk install emulator system-images/android-30/default/x86_64
printf 'no\n' | avdmanager create avd -n toeic_headless_api30 \
  -k 'system-images;android-30;default;x86_64' \
  -p /workspace/toolchains/avds/toeic_headless_api30.avd

emulator -avd toeic_headless_api30 -no-window -no-audio -no-boot-anim \
  -no-snapshot -accel off -gpu swiftshader -cores 2 -memory 1536 -port 5554
```

AVD 설정은 위 화면 크기와 메모리로 조정했습니다. `scripts/env.sh`에 `ANDROID_AVD_HOME`을 명시했습니다.
AVD 생성 도구와 에뮬레이터의 기본 검색 경로가 달라 처음에는 다음 오류가 났지만 명시적 경로 설정으로 해결했습니다.

```
Unknown AVD name [toeic_headless_api30]
```

CPU는 QEMU TCG 소프트웨어 에뮬레이션, 그래픽은 Google SwiftShader를 사용했습니다.
일반적인 GPU 소프트웨어 렌더링만 설정한 것이 아니라 `-accel off`로 CPU 가속도 명시적으로 껐습니다.

## adb와 부팅 완료

기본 제한에서 다음 오류가 실제 발생했습니다.

```
Cannot mkdir '/home/agent/.android': Read-only file system
```

명령 실행 도구의 좁은 범위 권한 확장을 통해 `/home/agent/.android`를 생성하고 `adb start-server`를 실행했습니다.
HOME을 변경하거나 인증 키를 출력·복사하지 않았습니다. 새 환경에서 인증 디렉터리가 복원되지 않으면 같은 초기화가 필요할 수 있습니다.

부팅 완료 증거:

```
adb devices -l
emulator-5554 device product:sdk_phone_x86_64 model:Android_SDK_built_for_x86_64

adb -s emulator-5554 shell getprop sys.boot_completed
1
```

에뮬레이터 로그: `Boot completed in 344984 ms` — 첫 부팅 약 5분 45초.
기기 연결·프로세스 존재와 부팅 완료를 별도로 확인했습니다.

## 앱 설치·실행·화면

- 최종 APK `artifacts/toeic-vocabulary-1.1-debug.apk` 설치: `Success`.
- 프로세스 실행 및 resumed Activity: `com.toiec.vocabulary/.MainActivity`.
- 초기 `am start -W`는 `Status: timeout`이었고 System UI 응답 지연 창도 나타났습니다.
  이는 소프트웨어 에뮬레이션의 초기 지연이었으며, 기다린 뒤 홈 화면이 표시되었습니다.
- 이후 동일 실행 명령 재확인: `Status: ok`, `WaitTime: 331` ms.
- 화면 캡처에서 앱 이름·총 954항목·DAY 목록·학습 시작·오답노트 버튼을 직접 확인했습니다.
- 화면 파일: `artifacts/emulator/toeic-home.png`.
- System UI 지연 진단 화면: `artifacts/emulator/boot-diagnostic.png`.
- 초기 실행 로그와 부팅 로그: `docs/emulator-evidence/`.

기존 단위 테스트 외에 이 단계에서 확인한 것은 Android 부팅, APK 설치, 홈 화면 실행과 캡처입니다.
전체 학습·오답 저장·재시작 시나리오를 실제 Android에서 모두 검증했다는 의미는 아닙니다.

## 비차단 경고와 제약

- `/home/agent/.emulator_console_auth_token` 접근 실패로 emulator console 제어에 제한이 있습니다.
  일반 adb shell, APK 설치, 앱 실행, 화면 캡처는 성공했습니다.
- TCG의 AVX/F16C 미지원 경고가 있으나 Android 부팅과 앱 화면 표시를 막지는 않았습니다.
- Android CLI layout 호출은 제한 시간 내 결과를 내지 못했습니다. 화면은 `adb exec-out screencap -p`로 캡처하고 직접 확인했습니다.
- 가속이 없어 부팅과 초기 UI 반응이 느립니다. 빠른 상시 테스트 환경과는 성능 차이가 큽니다.
- 현재 에뮬레이터는 실행 상태로 두었습니다. 클라우드 스냅샷은 프로세스를 보존하지 않으므로 새 작업에서는 다시 시작해야 합니다.

재실행용 헬퍼(이미 생성된 AVD 사용):

```bash
cd /workspace/toiec
bash scripts/start-headless-emulator.sh
```

헬퍼는 실행 중인 `emulator-5554`를 재사용하고, 없으면 headless로 시작하여 부팅 완료를 기다린 뒤 APK를 설치·실행합니다.
최종 화면은 다시 캡처·검사해야 합니다. `am start`가 반환하는 것만으로 화면 정상 표시를 판정하지 않습니다.
