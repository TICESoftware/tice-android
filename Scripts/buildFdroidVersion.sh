#!/usr/bin/env zsh

PACKAGE=$1
STAGE=$2

if [ -z $PACKAGE ]; then
  echo "Package identifier must be passed as parameter."
  exit 1
fi

if [ -z $STAGE ]; then
  echo "Stage must be passed as parameter."
  exit 1
fi

if [ -z "$KEYSTORE_PATH" ] || [ -z "$KEYSTORE_PASSWORD" ] || [ -z "$UPLOAD_KEY_PASSWORD" ]; then
  echo "Signing credentials not set. Need KEYSTORE_PATH, KEYSTORE_PASSWORD and UPLOAD_KEY_PASSWORD."
  exit 1
fi

if [ -z "$ANDROID_SDK_ROOT" ]; then
  ANDROID_SDK_ROOT=~/Library/Android/sdk
  echo "ANDROID_SDK_ROOT not set. Using default value: $ANDROID_SDK_ROOT"
fi

cd `mktemp -d`

echo "Checking out fdroiddata repo here: `pwd`"
git clone https://gitlab.com/fdroid/fdroiddata
cd fdroiddata

echo "Creating config file."
echo "gradle: gradle" > config.yml

echo "Modifying metadata file."
ORIGINAL_PACKAGE=app.tice.TICE.production

mv metadata/$ORIGINAL_PACKAGE.yml metadata/$PACKAGE.yml
sed -I '' -e "s/productionFdroid/${STAGE}Fdroid/g" metadata/$PACKAGE.yml
sed -I '' -e "s/releaseFdroid/${STAGE}/g" metadata/$PACKAGE.yml

fdroid checkupdates --auto --allow-dirty -v $PACKAGE

echo "Building app."
fdroid build -l -t $PACKAGE

echo "Signing APK."

cd tmp
UNSIGNED_APK=`find . -name "*.apk"`
ALIGNED_APK=${PACKAGE}_aligned.apk
SIGNED_APK=${PACKAGE}_signed.apk

$ANDROID_SDK_ROOT/build-tools/31.0.0/zipalign -p 4 $UNSIGNED_APK $ALIGNED_APK
$ANDROID_SDK_ROOT/build-tools/31.0.0/apksigner sign --ks $KEYSTORE_PATH --ks-pass env:KEYSTORE_PASSWORD --key-pass env:UPLOAD_KEY_PASSWORD --out $SIGNED_APK $ALIGNED_APK

echo "Signed APK ready at:"
echo "`pwd`/$SIGNED_APK"

exit 0
