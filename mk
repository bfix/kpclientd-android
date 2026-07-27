#!/bin/bash

TGT=${1:-/vault/prj/security/katzenpost/clients/kp_mobile/apk/deploy}
if [ ! -d ${TGT} ]; then
    echo "Unknown target folder '${TGT}'."
    exit 1
fi

ARCH=${2:-amd64}
BASE=/vault/prj/security/katzenpost/clients/_android/data-${ARCH}
if [ ! -d ${BASE} ]; then
    echo "Unknown target architecture '${ARCH}'."
    exit 1
fi
OUT=${BASE}/adb/modules/kpclientd/kpclientd

echo "---------------------------------------"
echo "TGT  = ${TGT}"
echo "ARCH = ${ARCH}"
echo "BASE = ${BASE}"
echo "OUT  = ${OUT}"
echo "---------------------------------------"

pushd /vault/prj/security/katzenpost/src/katzenpost/ >/dev/null 2>&1
rm -f ${OUT}
case ${ARCH} in
    arm64)
        export NDK_CC=/usr/lib/android-sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang
        CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC=${NDK_CC} \
            go build -o ${OUT} ./cmd/kpclientd
        popd >/dev/null 2>&1
        tar czf ${TGT}/kpclientd-arm64-android.tgz -C data-arm64 .
        ;;
    amd64)
        export NDK_CC=/usr/lib/android-sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android28-clang
        CGO_ENABLED=1 GOOS=android GOARCH=amd64 CC=${NDK_CC} CGO_LDFLAGS="-Wl,--allow-shlib-undefined" \
            go build -buildmode=pie -o ${OUT} ./cmd/kpclientd
        popd >/dev/null 2>&1
        tar czf ${TGT}/kpclientd-amd64-android.tgz -C data-amd64 .
        ;;
esac
