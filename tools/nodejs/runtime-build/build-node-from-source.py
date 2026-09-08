#!/usr/bin/env python3
"""Build the pinned Android Node source with Docker on Linux (including WSL2)."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def sha256(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--abi', choices=['all', 'arm64-v8a', 'armeabi-v7a', 'x86_64'], default='all')
    parser.add_argument('--work-root', type=Path, required=True, help='Build directory on a Linux filesystem')
    parser.add_argument('--source-dir', type=Path, required=True, help='Directory containing the pinned source tar.xz')
    parser.add_argument('--run-id', required=True, help='A new name, e.g. first or repeat; never reuse a different input')
    parser.add_argument('--image', help='Exact local Docker image ID; defaults to the lock')
    parser.add_argument('--jobs', type=int, default=6)
    parser.add_argument('--memory', default='12g')
    parser.add_argument('--resume', action='store_true', help='Resume the same recorded inputs after an interruption')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,39}', args.run_id) or args.jobs < 1:
        parser.error('run-id must contain lowercase letters/digits/hyphens; jobs must be positive')
    root = Path(__file__).resolve().parents[3]
    build_dir = root / 'tools/nodejs/runtime-build'
    lock = json.loads((build_dir / 'runtime-build.lock.json').read_text(encoding='utf-8'))
    version = lock['node']['targetVersion']
    source_dir = args.source_dir.resolve(strict=True)
    archive = source_dir / f'node-v{version}.tar.xz'
    if sha256(archive) != lock['node']['sourceSha256']:
        raise ValueError('Node source archive does not match the lock')
    requested_image = args.image or lock['toolchain']['containerImageId']
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', requested_image):
        parser.error('--image must be an immutable Docker image ID (sha256:...), not a mutable tag')
    image = subprocess.check_output(['docker', 'image', 'inspect', '--format', '{{.Id}}', requested_image], text=True).strip()
    if image != requested_image:
        raise ValueError('Docker returned a different image ID')
    abis = list(lock['abis']) if args.abi == 'all' else [args.abi]
    patches = {file.name: sha256(file) for file in sorted((build_dir / 'patches').glob('*.patch'))}
    if not patches or patches != lock['node']['patchFiles']:
        raise ValueError('Android patches do not match the lock')
    for abi in abis:
        workspace = args.work_root.resolve() / f'{version}-{abi}-{args.run_id}'
        inputs = {
            'nodeVersion': version, 'abi': abi, 'sourceSha256': lock['node']['sourceSha256'],
            'imageId': image, 'patchFiles': patches,
            'buildScriptSha256': sha256(build_dir / 'container/build-source.sh'),
        }
        input_file = workspace / 'build-inputs.json'
        if workspace.exists():
            if not args.resume or not input_file.is_file() or json.loads(input_file.read_text()) != inputs:
                raise ValueError(f'{workspace} already exists; choose a fresh run-id, or --resume with identical inputs')
        else:
            workspace.mkdir(parents=True)
            input_file.write_text(json.dumps(inputs, indent=2) + '\n', encoding='utf-8')
        command = [
            'docker', 'run', '--rm', '--name', f'autojs6-node-{version}-{abi}-{args.run_id}',
            f'--memory={args.memory}', f'--cpus={args.jobs}',
            '-e', f'JOBS={args.jobs}',
            '-v', f'{workspace}:/work', '-v', f'{source_dir}:/sources:ro', '-v', f'{root}:/repo:ro',
            image, 'bash', '/repo/tools/nodejs/runtime-build/container/build-source.sh', version, abi,
        ]
        print(f'Building Node {version} / {abi} in {workspace}; image {image}', flush=True)
        subprocess.run(command, check=True)
        subprocess.run([
            'docker', 'run', '--rm', '-v', f'{workspace}:/work', '-v', f'{root}:/repo:ro', image,
            'python3', '/repo/tools/nodejs/runtime-build/verify-source-runtime.py',
            '--library', '/work/output/libnode.so', '--abi', abi,
            '--llvm-bin', '/opt/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin',
            '--report', '/work/output/runtime-artifact.json',
        ], check=True)
        print(f'Built and checked: {workspace}/output/libnode.so; Android tests are still required.', flush=True)


if __name__ == '__main__':
    main()
