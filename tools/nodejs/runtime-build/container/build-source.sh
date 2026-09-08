#!/usr/bin/env bash
set -euo pipefail

version="${1:-24.20.0}"
abi="${2:-arm64-v8a}"
case "$version" in
  24.20.0) source_sha=2732fc3f588dd335cd6779c06864f7cd424bb1b5ff9a1743059a66c54f9ca4a1 ;;
  *) echo "Unpinned Node source version: $version" >&2; exit 2 ;;
esac
case "$abi" in
  arm64-v8a) cpu=arm64; triple=aarch64-linux-android24 ;;
  armeabi-v7a) cpu=arm; triple=armv7a-linux-androideabi24 ;;
  x86_64) cpu=x64; triple=x86_64-linux-android24 ;;
  *) echo "Unsupported Android ABI: $abi" >&2; exit 2 ;;
esac

archive="/sources/node-v$version.tar.xz"
echo "$source_sha  $archive" | sha256sum -c -
mkdir -p /work/node /work/output
if [ ! -f /work/node/.autojs6-patched ]; then
  tar -xf "$archive" --strip-components=1 -C /work/node
  cd /work/node
  for patch_file in /repo/tools/nodejs/runtime-build/patches/*.patch; do patch --batch --forward -p1 < "$patch_file"; done
  touch .autojs6-patched
fi
cd /work/node
toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$toolchain/bin:$PATH"
export CC="$toolchain/bin/$triple-clang" CXX="$toolchain/bin/$triple-clang++"
export AR="$toolchain/bin/llvm-ar" RANLIB="$toolchain/bin/llvm-ranlib"
export CC_host=clang-18 CXX_host=clang++-18 AR_host=ar RANLIB_host=ranlib
export GYP_DEFINES="target_arch=$cpu v8_target_arch=$cpu android_target_arch=$cpu host_os=linux OS=android android_ndk_path=$ANDROID_NDK_HOME android_ndk_sysroot=$toolchain/sysroot"
export CFLAGS='-fPIC -ffile-prefix-map=/work/node=. -fdebug-prefix-map=/work/node=.'
export CXXFLAGS="$CFLAGS"
export LDFLAGS='-Wl,-z,max-page-size=16384 -Wl,--build-id=sha1'
if [ ! -f out/Release/build.ninja ]; then
  ./configure --dest-cpu="$cpu" --dest-os=android --cross-compiling --shared --ninja \
    --without-node-snapshot --without-npm --without-corepack --openssl-no-asm --with-intl=none
fi
ninja -C out/Release -j "${JOBS:-6}" libnode
library=$(find out/Release -name 'libnode.so*' -type f -print -quit)
test -n "$library"
cp "$library" /work/output/libnode.so
llvm-strip --strip-unneeded /work/output/libnode.so
llvm-readelf -lW /work/output/libnode.so > /work/output/program-headers.txt
llvm-readelf -n /work/output/libnode.so > /work/output/build-id.txt
llvm-nm -D --defined-only /work/output/libnode.so > /work/output/exports.txt
sha256sum /work/output/libnode.so > /work/output/SHA256SUMS
echo "Built Node $version for $abi"
