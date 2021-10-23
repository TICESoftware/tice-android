#!/usr/bin/env zsh

RED='\033[0;31m'
GREEN='\033[0;32m'
RESET='\033[0m'

EMULATOR_NAME=InstrumentationTestEmulator

if [ -f emulator.pid ]; then
  kill -TERM $(cat emulator.pid) || true
  rm emulator.pid
  echo "Emulator stopped."
else
  echo "Emulator not running"
fi

echo "Deleting emulator."

if [ -z "$ANDROID_SDK_ROOT" ]; then
  ANDROID_SDK_ROOT=~/Library/Android/sdk
  echo "ANDROID_SDK_ROOT not set. Using default value: $ANDROID_SDK_ROOT"
fi

AVDMANAGER=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager

$AVDMANAGER delete avd --name $EMULATOR_NAME

echo "${GREEN}Emulator $EMULATOR_NAME has been deleted.${RESET}"
