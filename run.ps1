param(
    [ValidateSet('build','server','client','database','test','ui-test','presentation-test')][string]$Mode = 'client',
    [int]$Port = 5555,
    [string]$HostName = 'localhost',
    [string]$Snapshot = '',
    [string]$Screen = 'home',
    [switch]$LocalStore,
    [switch]$MySqlTest,
    [switch]$AudioDevice
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$utf8 = New-Object System.Text.UTF8Encoding $false
$depDir = Join-Path $PSScriptRoot '.deps'
$classesDir = Join-Path $PSScriptRoot 'target\classes'
$testsDir = Join-Path $PSScriptRoot 'target\test-classes'
New-Item -ItemType Directory -Force -Path $depDir,$classesDir | Out-Null

function Clear-CompiledClasses([string]$Directory) {
    $compiledPath = [IO.Path]::GetFullPath($Directory)
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'target')) + [IO.Path]::DirectorySeparatorChar
    if (!$compiledPath.StartsWith($targetRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Compiled output must stay inside the project target directory.'
    }
    if (Test-Path -LiteralPath $compiledPath) {
        Get-ChildItem -LiteralPath $compiledPath -Recurse -File -Filter '*.class' |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    }
}

function Resolve-Jar([string]$Relative) {
    $cached = Join-Path (Join-Path $env:USERPROFILE '.m2\repository') $Relative
    if (Test-Path -LiteralPath $cached) { return $cached }
    $file = Join-Path $depDir ([IO.Path]::GetFileName($Relative))
    if (!(Test-Path -LiteralPath $file)) {
        $url = 'https://repo.maven.apache.org/maven2/' + $Relative.Replace('\','/')
        Write-Host ('Downloading ' + [IO.Path]::GetFileName($Relative))
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile ($file + '.part')
        Move-Item -LiteralPath ($file + '.part') -Destination $file -Force
    }
    return $file
}

$fxJars = @('base','graphics','controls') | ForEach-Object {
    Resolve-Jar ('org\openjfx\javafx-' + $_ + '\21.0.8\javafx-' + $_ + '-21.0.8-win.jar')
}
$mysqlJar = Resolve-Jar 'com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar'
$modulePath = $fxJars -join ';'
$sourceFiles = @(Get-ChildItem -LiteralPath 'src\main\java' -Recurse -Filter '*.java' | ForEach-Object FullName)
if ($sourceFiles.Count -eq 0) { throw 'Missing game source files.' }
Clear-CompiledClasses $classesDir
$compileArgs = @('-encoding','UTF-8','--release','17','--module-path',$modulePath,'--add-modules','javafx.controls','-cp',$mysqlJar,'-d',$classesDir) + $sourceFiles
& javac @compileArgs
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
if (Test-Path -LiteralPath 'src\main\resources') {
    Copy-Item -Path 'src\main\resources\*' -Destination $classesDir -Recurse -Force
}
Write-Host ('Build OK (' + $sourceFiles.Count + ' Java files).') -ForegroundColor Green
$javaArgs = @('--module-path',$modulePath,'--add-modules','javafx.controls','-cp',($classesDir + ';' + $mysqlJar))
switch ($Mode) {
    'build' { exit 0 }
    'database' {
        & java @javaArgs fruit.server.infrastructure.persistence.DatabaseSetup
    }
    'server' {
        $serverArgs = @('fruit.server.Server',('--port=' + $Port))
        if ($LocalStore) { $serverArgs += '--local' }
        & java @javaArgs @serverArgs
    }
    'client' {
        $clientArgs = @('fruit.client.Client',('--host=' + $HostName),('--port=' + $Port))
        if ($Snapshot) { $clientArgs += @(('--snapshot=' + $Snapshot), ('--screen=' + $Screen)) }
        & java @javaArgs @clientArgs
    }
    { $_ -in 'test','ui-test','presentation-test' } {
        New-Item -ItemType Directory -Force -Path $testsDir | Out-Null
        Clear-CompiledClasses $testsDir
        $testSources = @(Get-ChildItem -LiteralPath 'src\test\java' -Recurse -Filter '*.java' | ForEach-Object FullName)
        & javac -encoding UTF-8 --release 17 --module-path $modulePath --add-modules javafx.controls -cp ($classesDir + ';' + $mysqlJar) -d $testsDir @testSources
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        $testClasspath = $classesDir + ';' + $testsDir + ';' + $mysqlJar
        if ($Mode -eq 'presentation-test') {
            $presentationArgs = @('fruit.client.features.settings.PresentationTest')
            if ($AudioDevice) { $presentationArgs += '--device' }
            & java --module-path $modulePath --add-modules javafx.controls -cp $testClasspath @presentationArgs
        } elseif ($Mode -eq 'ui-test') {
            & java --module-path $modulePath --add-modules javafx.controls -cp $testClasspath fruit.server.application.ClientTest
        } else {
            $testArgs = @('fruit.server.application.GameTest')
            if ($MySqlTest) { $testArgs += '--mysql' }
            & java -cp $testClasspath @testArgs
        }
    }
}
if ($LASTEXITCODE -ne 0) { throw ('Process failed with exit code ' + $LASTEXITCODE) }
