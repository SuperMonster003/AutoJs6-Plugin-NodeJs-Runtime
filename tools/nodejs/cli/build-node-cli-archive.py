#!/usr/bin/env python3
"""Build or verify the npm + corepack asset archive used by the AutoJs6 terminal launcher.

npm and corepack are platform-independent JavaScript, so they are taken from the official
Node.js Linux x64 tarball of the exact version the plugin runtime is built from and stored
as a deterministic zip under app/src/main/assets (with a .bin suffix because *.zip is
ignored by Git). The AutoJs6 host verifies the SHA-256 declared in the plugin manifest and
extracts the archive root into its terminal prefix; the libnodexe.so launcher resolves
npm/bin/npm-cli.js and corepack/dist/*.js below that root.
"""
import argparse
import hashlib
import io
import json
import re
import tarfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CLI_DIR = ROOT / 'tools/nodejs/cli'
LOCK_FILE = CLI_DIR / 'node-cli.lock.json'
RUNTIME_LOCK_FILE = ROOT / 'tools/nodejs/runtime-build/runtime-build.lock.json'
ASSET_DIR = ROOT / 'app/src/main/assets'
SCHEMA = 'autojs6-node-cli-archive-v1'
ARCHIVE_ROOT = 'lib/node_modules'
PACKAGES = ('npm', 'corepack')
REQUIRED_ENTRIES = (
    'npm/package.json',
    'npm/bin/npm-cli.js',
    'npm/bin/npx-cli.js',
    'corepack/package.json',
    'corepack/dist/corepack.js',
    'corepack/dist/yarn.js',
    'corepack/dist/yarnpkg.js',
    'corepack/dist/pnpm.js',
    'corepack/dist/pnpx.js',
)
PRUNED_DIRECTORIES = ('npm/docs', 'npm/man')
DIST_BASE = 'https://nodejs.org/dist'
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)


def sha256_of(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def is_pruned(relative):
    """relative is the path below the archive root, e.g. npm/docs/index.html."""
    package, _, rest = relative.partition('/')
    if any(relative == pruned or relative.startswith(pruned + '/') for pruned in PRUNED_DIRECTORIES):
        return True
    # Top-level markdown (README, CHANGELOG, ...) is documentation; keep every LICENSE file.
    return '/' not in rest and rest.lower().endswith('.md') and not rest.upper().startswith('LICENSE')


def download(url, destination):
    if destination.is_file():
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    print(f'downloading {url}')
    with urllib.request.urlopen(url, timeout=120) as response, destination.open('wb') as stream:
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            stream.write(chunk)


def official_sha256(shasums, file_name):
    for line in shasums.read_text(encoding='utf-8').splitlines():
        digest, _, name = line.partition('  ')
        if name.strip() == file_name:
            return digest.strip()
    raise ValueError(f'{file_name} is not listed in {shasums}')


def collect_members(tarball, node_version):
    prefix = f'node-v{node_version}-linux-x64/{ARCHIVE_ROOT}/'
    files = {}
    with tarfile.open(tarball, 'r:xz') as tar:
        for member in tar:
            if not member.name.startswith(prefix):
                continue
            relative = member.name[len(prefix):]
            if relative.partition('/')[0] not in PACKAGES or is_pruned(relative):
                continue
            if member.issym() or member.islnk():
                # Symbolic links cannot be recreated portably by the host extractor; npm and
                # corepack do not rely on any inside their own package directories.
                continue
            if not member.isfile():
                continue
            with tar.extractfile(member) as stream:
                files[f'{ARCHIVE_ROOT}/{relative}'] = (member.mode & 0o777, stream.read())
    if not files:
        raise ValueError(f'{tarball} does not contain {prefix}')
    return files


def write_archive(files, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name in sorted(files):
            mode, data = files[name]
            info = zipfile.ZipInfo(name, date_time=ZIP_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = (0o100000 | mode) << 16
            archive.writestr(info, data, compresslevel=9)
    destination.write_bytes(buffer.getvalue())


def package_version(read, package):
    manifest = json.loads(read(f'{ARCHIVE_ROOT}/{package}/package.json').decode('utf-8'))
    version = manifest['version']
    if not re.fullmatch(r'\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?', version):
        raise ValueError(f'unexpected {package} version {version!r}')
    return version


def describe_archive(path):
    """Validate the archive layout and return the facts recorded in the lock."""
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        for name in names:
            parts = name.split('/')
            if not name.startswith(ARCHIVE_ROOT + '/') or name.endswith('/'):
                raise ValueError(f'entry outside the archive root: {name}')
            if '..' in parts or '' in parts or name.startswith('/') or '\\' in name:
                raise ValueError(f'unsafe entry name: {name}')
            if parts[len(ARCHIVE_ROOT.split('/'))] not in PACKAGES:
                raise ValueError(f'entry outside the npm / corepack packages: {name}')
        for required in REQUIRED_ENTRIES:
            if f'{ARCHIVE_ROOT}/{required}' not in names:
                raise ValueError(f'required entry missing: {required}')
        versions = {package: package_version(archive.read, package) for package in PACKAGES}
        return {
            'sha256': sha256_of(path),
            'entryCount': len(infos),
            'uncompressedBytes': sum(info.file_size for info in infos),
            'compressedBytes': path.stat().st_size,
        }, versions


def build_lock(node_version, source, archive_path, facts, versions):
    return {
        'schema': SCHEMA,
        'nodeVersion': node_version,
        'source': source,
        'archive': {
            'assetPath': archive_path.relative_to(ASSET_DIR).as_posix(),
            'sourcePath': archive_path.relative_to(ROOT).as_posix(),
            'root': ARCHIVE_ROOT,
            **facts,
        },
        'packages': versions,
        'prunedDirectories': list(PRUNED_DIRECTORIES),
        'requiredEntries': list(REQUIRED_ENTRIES),
    }


def check(lock_path):
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    if lock.get('schema') != SCHEMA:
        raise ValueError(f'{lock_path}: unsupported schema {lock.get("schema")!r}')
    runtime_version = json.loads(RUNTIME_LOCK_FILE.read_text(encoding='utf-8'))['node']['targetVersion']
    if lock['nodeVersion'] != runtime_version:
        raise ValueError(f'lock Node version {lock["nodeVersion"]} differs from the runtime target {runtime_version}')
    archive_path = ROOT / lock['archive']['sourcePath']
    if lock['archive']['assetPath'] != archive_path.relative_to(ASSET_DIR).as_posix():
        raise ValueError('archive assetPath and sourcePath disagree')
    if lock['archive']['root'] != ARCHIVE_ROOT:
        raise ValueError(f'unexpected archive root {lock["archive"]["root"]!r}')
    facts, versions = describe_archive(archive_path)
    recorded = {key: lock['archive'][key] for key in facts}
    if recorded != facts:
        raise ValueError(f'archive facts drifted: lock={recorded} actual={facts}')
    if lock['packages'] != versions:
        raise ValueError(f'package versions drifted: lock={lock["packages"]} actual={versions}')
    print(f'{archive_path.relative_to(ROOT).as_posix()}: sha256 {facts["sha256"]}, '
          f'{facts["entryCount"]} entries, {facts["uncompressedBytes"]} bytes, '
          f'npm {versions["npm"]}, corepack {versions["corepack"]}: OK')


def build(args):
    node_version = args.node_version or json.loads(RUNTIME_LOCK_FILE.read_text(encoding='utf-8'))['node']['targetVersion']
    file_name = f'node-v{node_version}-linux-x64.tar.xz'
    cache = args.cache_dir.resolve()
    shasums = cache / f'SHASUMS256-{node_version}.txt'
    tarball = cache / file_name
    download(f'{DIST_BASE}/v{node_version}/SHASUMS256.txt', shasums)
    download(f'{DIST_BASE}/v{node_version}/{file_name}', tarball)
    expected = official_sha256(shasums, file_name)
    actual = sha256_of(tarball)
    if actual != expected:
        raise ValueError(f'{tarball}: sha256 {actual} does not match SHASUMS256 {expected}')
    files = collect_members(tarball, node_version)
    archive_path = ASSET_DIR / 'nodejs/cli' / f'node-cli-{node_version}.bin'
    write_archive(files, archive_path)
    facts, versions = describe_archive(archive_path)
    source = {
        'fileName': file_name,
        'url': f'{DIST_BASE}/v{node_version}/{file_name}',
        'sha256': actual,
        'shasumsUrl': f'{DIST_BASE}/v{node_version}/SHASUMS256.txt',
    }
    lock = build_lock(node_version, source, archive_path, facts, versions)
    LOCK_FILE.write_text(json.dumps(lock, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'wrote {archive_path.relative_to(ROOT).as_posix()} and {LOCK_FILE.relative_to(ROOT).as_posix()}')
    check(LOCK_FILE)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='Only verify the committed archive against the lock (offline)')
    parser.add_argument('--node-version', help='Node.js version to take npm / corepack from; defaults to the runtime build lock')
    parser.add_argument('--cache-dir', type=Path, default=ROOT / 'build/node-cli-cache', help='Where downloaded tarballs are kept')
    args = parser.parse_args()
    if args.check:
        check(LOCK_FILE)
    else:
        build(args)


if __name__ == '__main__':
    main()
