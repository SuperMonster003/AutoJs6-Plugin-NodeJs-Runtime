param(
    [string] $LockFile = "tools/nodejs/runtime-build/runtime-build.lock.json",
    [string] $OutputDir = "build/node-runtime",
    [string] $ArchiveFile = "build/runtime-build-cache/nodejs-mobile-24.05-android.zip",
    [switch] $Execute
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$lockPath = Resolve-Path (Join-Path $repoRoot $LockFile)
$lock = Get-Content -Encoding UTF8 $lockPath | ConvertFrom-Json

Write-Host "AutoJs6 Node runtime build plan"
Write-Host "  target Node: $($lock.node.targetVersion)"
Write-Host "  current Node: $($lock.node.currentVersion)"
Write-Host "  NDK: $($lock.toolchain.ndkVersion)"
Write-Host "  output: $OutputDir"
Write-Host "  ABIs: $([string]::Join(', ', $lock.abis.PSObject.Properties.Name))"

if (-not $Execute) {
    Write-Host "Plan-only mode. Re-run with -Execute to materialize the pinned Node 24.5 upstream binary archive."
    exit 0
}

$archive = Join-Path $repoRoot $ArchiveFile
$archiveParent = Split-Path -Parent $archive
New-Item -ItemType Directory -Force $archiveParent | Out-Null
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -Uri $lock.androidFork.androidArtifact.url -OutFile $archive
}

$actualArchiveSize = (Get-Item -LiteralPath $archive).Length
if ($actualArchiveSize -ne [long] $lock.androidFork.androidArtifact.size) {
    throw "Runtime archive size mismatch: expected $($lock.androidFork.androidArtifact.size), actual $actualArchiveSize"
}
$actualArchiveSha = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualArchiveSha -ne $lock.androidFork.androidArtifact.sha256) {
    throw "Runtime archive SHA-256 mismatch: expected $($lock.androidFork.androidArtifact.sha256), actual $actualArchiveSha"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$outputRoot = Join-Path $repoRoot $OutputDir
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
try {
    foreach ($abiProperty in $lock.androidFork.androidArtifact.entries.PSObject.Properties) {
        $abi = $abiProperty.Name
        $entryLock = $abiProperty.Value
        $matches = @($zip.Entries | Where-Object { $_.FullName -ceq $entryLock.path })
        if ($matches.Count -ne 1) {
            throw "Pinned archive must contain exactly one entry for $abi at $($entryLock.path); found $($matches.Count)"
        }
        $entry = $matches[0]
        if ($entry.Length -ne [long] $entryLock.size) {
            throw "Archive entry size mismatch for ${abi}: expected $($entryLock.size), actual $($entry.Length)"
        }
        $abiOutput = Join-Path $outputRoot $abi
        New-Item -ItemType Directory -Force $abiOutput | Out-Null
        $outputFile = Join-Path $abiOutput "libnode.so"
        $input = $entry.Open()
        try {
            $output = [System.IO.File]::Open($outputFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
            try { $input.CopyTo($output) } finally { $output.Dispose() }
        } finally { $input.Dispose() }
        $actualSha = (Get-FileHash -LiteralPath $outputFile -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualSha -ne $entryLock.sha256) {
            throw "Materialized libnode.so SHA-256 mismatch for ${abi}: expected $($entryLock.sha256), actual $actualSha"
        }
    }
} finally {
    $zip.Dispose()
}

& node "$repoRoot/tools/nodejs/runtime-build/verify-runtime-build-plan.js" `
    --repo-root $repoRoot `
    --lock-file $lockPath `
    --materialized-root $outputRoot
if ($LASTEXITCODE -ne 0) {
    throw "Materialized runtime verification failed with exit code $LASTEXITCODE"
}
Write-Host "Pinned Node 24.5 runtime materialized at $outputRoot"
