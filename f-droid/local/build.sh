#!/bin/bash

mkdir $1
cd $1

fdroid init

mkdir metadata
cp ../org.hoi_polloi.kpdaemon.yml metadata/

mkdir build
cd build/
#git clone https://github.com/bfix/kpclientd-android org.hoi_polloi.kpdaemon
git clone http://127.0.0.1:13000/brf/kpclientd-android org.hoi_polloi.kpdaemon
cd org.hoi_polloi.kpdaemon
git switch dev
cd ../..

cat > config.yml <<EOF
---
sdk_path: /usr/lib/android-sdk
ndk_paths:
    r28: /usr/lib/android-sdk/ndk/28.0.13004108

repo_keyalias: saturn.hoi-polloi.org
keystore: keystore.p12
keystorepass: gnXL/MMpkVbp/g8O32UGRQetcpcMmaUpkV2WcxuzzrI=
keypass: gnXL/MMpkVbp/g8O32UGRQetcpcMmaUpkV2WcxuzzrI=
keydname: CN=saturn.hoi-polloi.org, OU=F-Droid
use_in_repository_metadata: false
EOF

mkdir srclibs
cat > srclibs/katzenpost.yml <<EOF
Repo: https://github.com/katzenpost/katzenpost
RepoType: git
EOF

fdroid build --verbose org.hoi_polloi.kpdaemon

ls -la unsigned
