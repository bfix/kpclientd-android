#!/bin/bash

export NDK_CC=/usr/lib/android-sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang

pushd /vault/prj/security/katzenpost/src/katzenpost/ >/dev/null 2>&1

CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC=$NDK_CC \
go build -v -o /vault/prj/security/katzenpost/clients/_android/data/adb/modules/katzenpost/kpclientd ./cmd/kpclientd

popd >/dev/null 2>&1

tar czf deploy.tgz -C data .
