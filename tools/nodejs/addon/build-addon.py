#!/usr/bin/env python3
"""Build the plugin-owned Node-API sample for Android, using local NDK/Node assets."""
import argparse
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[3]
ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", required=True, type=Path)
    parser.add_argument("--cmake", default="cmake")
    parser.add_argument("--ninja", default="ninja")
    parser.add_argument("--abi", action="append", choices=ABIS)
    parser.add_argument("--build-dir", type=Path, default=ROOT / "app/build/native-addon")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "sample/nodejs/native-addon/prebuilds")
    args = parser.parse_args()
    toolchain = args.ndk.resolve() / "build/cmake/android.toolchain.cmake"
    if not toolchain.is_file():
        parser.error(f"Android NDK toolchain not found: {toolchain}")
    for abi in args.abi or ABIS:
        library = ROOT / "app/src/main/jniLibs" / abi / "libnode.so"
        with library.open("rb") as source:
            if source.read(4) != b"\x7fELF":
                parser.error(f"Materialize the Git LFS library before building: {library}")
        build = args.build_dir.resolve() / abi
        subprocess.run([
            args.cmake, "-S", str(Path(__file__).parent), "-B", str(build), "-G", "Ninja",
            f"-DCMAKE_MAKE_PROGRAM={args.ninja}", f"-DCMAKE_TOOLCHAIN_FILE={toolchain}",
            f"-DANDROID_ABI={abi}", "-DANDROID_PLATFORM=android-24", "-DCMAKE_BUILD_TYPE=Release",
        ], check=True)
        subprocess.run([args.cmake, "--build", str(build), "--target", "hello"], check=True)
        target = args.output_dir.resolve() / abi / "hello.node"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(build / "hello.node", target)
        print(f"Built {target} ({target.stat().st_size} bytes)", flush=True)


if __name__ == "__main__":
    main()
