$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tool = Join-Path $root 'toolchain'
$jdk = Join-Path $tool 'jdk\jdk-17.0.2'
$bt = Join-Path $tool 'buildtools\android-14'
$androidJar = Join-Path $tool 'platform\android-34\android.jar'
$app = Join-Path $root 'app'
$build = Join-Path $root ('build-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$out = Join-Path $root 'out'

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$bt;$env:Path"

New-Item -ItemType Directory -Path $build -Force | Out-Null
New-Item -ItemType Directory -Path "$build\gen" | Out-Null
New-Item -ItemType Directory -Path "$build\classes" | Out-Null
New-Item -ItemType Directory -Path "$build\dex" | Out-Null
New-Item -ItemType Directory -Path $out -Force | Out-Null

Write-Host '[1/7] aapt2 compile'
& "$bt\aapt2.exe" compile --dir "$app\res" -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '[2/7] aapt2 link'
& "$bt\aapt2.exe" link -o "$build\base.apk" `
    -I $androidJar `
    --manifest "$app\AndroidManifest.xml" `
    -A "$app\assets" `
    --java "$build\gen" `
    --min-sdk-version 24 `
    --target-sdk-version 34 `
    --version-code 1 `
    --version-name 1.0 `
    "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '[3/7] javac'
$javaSources = @(Get-ChildItem "$app\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$rJava = @(Get-ChildItem "$build\gen" -Recurse -Filter R.java | ForEach-Object { $_.FullName })
$javacArgs = @('-J-Duser.language=en', '-J-Duser.country=US', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
    '-classpath', $androidJar, '-d', "$build\classes") + $javaSources + $rJava
& "$jdk\bin\javac.exe" @javacArgs
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '[4/7] d8'
& "$jdk\bin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& "$bt\d8.bat" --release --lib $androidJar --min-api 24 --output "$build\dex" "$build\classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[5/7] package'
Copy-Item "$build\base.apk" "$build\unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$build\unsigned.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$build\dex\classes.dex", 'classes.dex', [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

Write-Host '[6/7] zipalign'
& "$bt\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '[7/7] sign'
$ks = Join-Path $root 'debug.keystore'
if (-not (Test-Path $ks)) {
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -alias androiddebugkey `
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --v1-signing-enabled true --v2-signing-enabled true `
    --out "$out\jieci.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

$size = (Get-Item "$out\jieci.apk").Length
Write-Host ("BUILD OK -> {0} ({1:N0} bytes)" -f "$out\jieci.apk", $size)
