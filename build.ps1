# Bestiary 로컬 빠른 빌드 (Gradle 없이 javac 로) — JDK 21 필요
# libs\ (또는 ..\libs\) 에 paper-api · adventure-* jar 필요
$root = $PSScriptRoot
$libDir = if (Test-Path (Join-Path $root 'libs')) { Join-Path $root 'libs' } else { Join-Path $root '..\libs' }
$libs = (Get-ChildItem $libDir -File -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) -join ';'
if (-not $libs) { throw "$libDir 에 빌드용 jar 가 없습니다" }
$javac = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javac.exe'
if (-not (Test-Path $javac)) { $javac = 'javac' }
$jar = if (Test-Path $javac) { $javac -replace 'javac\.exe$', 'jar.exe' } else { 'jar' }
$out = Join-Path $root 'out'
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $out -Force | Out-Null
$srcs = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -File -Filter *.java | ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 -classpath $libs -d $out $srcs
if ($LASTEXITCODE -ne 0) { throw 'javac 실패' }
Copy-Item (Join-Path $root 'src\main\resources\*') $out -Force
$target = Join-Path $root 'out\Bestiary-2.0.1.jar'
& $jar --create --file $target -C $out .
if ($LASTEXITCODE -ne 0) { throw 'jar 실패' }
"OK -> $target"
