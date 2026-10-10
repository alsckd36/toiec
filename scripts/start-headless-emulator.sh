#!/usr/bin/env bash
set -euo pipefail
cd /workspace/toiec
source scripts/env.sh
avd_name=toeic_headless_api30
runtime_dir=/workspace/toolchains/emulator-runtime
mkdir -p "$runtime_dir"
# adb needs its standard user-home .android directory. Do not change HOME.
# If startup fails on the cloud read-only mount, ask the command runner for the
# narrowly scoped escalation used during setup: mkdir -p /home/agent/.android;
# adb start-server. Do not print/copy adb authentication keys.
adb start-server
if ! emulator -list-avds | rg -qx "$avd_name"; then
    echo "Required AVD is missing; see docs/emulator.md for the tested creation steps." >&2
    exit 1
fi
if ! adb devices | rg -q "^emulator-5554[[:space:]]"; then
    nohup emulator -avd "$avd_name" -no-window -no-audio -no-boot-anim -no-snapshot \
        -accel off -gpu swiftshader -cores 2 -memory 1536 -port 5554 \
        > "$runtime_dir/emulator.log" 2>&1 &
    echo "$!" > "$runtime_dir/emulator.pid"
fi
python3 -u - <<'PY'
import subprocess,time
start=time.monotonic()
for i in range(90):
    try:
        result=subprocess.run(['adb','-s','emulator-5554','shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=12)
        if result.returncode==0 and result.stdout.strip()=='1':
            print('Android boot completed:',round(time.monotonic()-start),'seconds');break
    except subprocess.TimeoutExpired:
        pass
    if i % 3 == 0: print('Waiting for software-emulated Android boot:',round(time.monotonic()-start),'seconds')
    time.sleep(10)
else: raise SystemExit('Android boot did not complete within the polling budget; inspect emulator.log')
PY
adb -s emulator-5554 install -r artifacts/toeic-vocabulary-1.5-debug.apk
adb -s emulator-5554 shell am start -W -n com.toiec.vocabulary/.MainActivity
