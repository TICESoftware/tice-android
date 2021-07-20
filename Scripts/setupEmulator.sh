#!/usr/bin/env zsh

RED='\033[0;31m'
GREEN='\033[0;32m'
RESET='\033[0m'

EMULATOR_NAME=InstrumentationTestEmulator
EMULATOR_PACKAGE="system-images;android-29;google_apis;x86_64"

if [ -z "$ANDROID_SDK_ROOT" ]; then
  ANDROID_SDK_ROOT=~/Library/Android/sdk
  echo "ANDROID_SDK_ROOT not set. Using default value: $ANDROID_SDK_ROOT"
fi

AVDMANAGER=$ANDROID_SDK_ROOT/tools/bin/avdmanager
EMULATOR=$ANDROID_SDK_ROOT/emulator/emulator
ADB=$ANDROID_SDK_ROOT/platform-tools/adb

if [[ `$AVDMANAGER list avd | rg -q InstrumentationDevice` -ne 0 ]]; then
  echo "Emulator with name '$EMULATOR_NAME' already existing. Deleting."
  $AVDMANAGER delete avd --name $EMULATOR_NAME
fi

echo "Creating emulator '$EMULATOR_NAME'"

echo "" | $AVDMANAGER create avd --name $EMULATOR_NAME --package $EMULATOR_PACKAGE
echo ""

echo "Waiting for emulator to finish booting..."
nohup $EMULATOR -avd $EMULATOR_NAME -no-snapshot -wipe-data > emulator.log 2>&1 &
EMULATOR_PID=$!
echo $EMULATOR_PID > emulator.pid

$ADB wait-for-device

while [ "`$ADB shell getprop sys.boot_completed | tr -d '\r' `" != "1" ]; do
  sleep 1
done

echo "Emulator has finished booting."
echo "${GREEN}Emulator $EMULATOR_NAME is ready. PID: $EMULATOR_PID${RESET}"
