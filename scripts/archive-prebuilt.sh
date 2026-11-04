#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${VERSION:?Pass an immutable release candidate version}"
[[ "$VERSION" =~ ^[0-9][0-9A-Za-z._-]*$ ]] || { echo 'Invalid release version' >&2; exit 1; }
# 保留旧产物和失败证据，由统一入口提供空任务目录。
[[ ! -e build/maven && ! -e build/prebuilt/http-maven.tar.gz ]] || { echo 'Use a fresh task directory: staging or archive already exists' >&2; exit 1; }
bash gradlew --no-daemon --max-workers=1 -Dorg.gradle.parallel=false publishAllPublicationsToStagingRepository
python3 scripts/jitpack-metadata.py build/maven/com/github/gycrosskit/http
python3 scripts/check-maven.py build/maven com.github.gycrosskit.http "$VERSION" http-core ios_arm64,ios_simulator_arm64,ios_x64,ohos_arm64 http-core,http-core-android,http-core-iosarm64,http-core-iossimulatorarm64,http-core-iosx64,http-core-ohosarm64 --max-jvm-major 55
mkdir -p build/prebuilt
COPYFILE_DISABLE=1 tar --no-xattrs -czf build/prebuilt/http-maven.tar.gz -C build/maven com/github/gycrosskit/http
(
    cd build/prebuilt
    shasum -a 256 http-maven.tar.gz > http-maven.tar.gz.sha256
    cat http-maven.tar.gz.sha256
)
