# Builds both jars and installs each into its test instance (Fabric: 26.2, NeoForge: 26.2(1)).
$ErrorActionPreference = "Stop"
$instances = "$env:APPDATA\ElyPrismLauncher\instances"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat -I tools\mirror.init.gradle.kts build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

foreach ($pair in @(@("fabric", "26.2"), @("neoforge", "26.2(1)"))) {
    $loader, $instance = $pair
    $mods = "$instances\$instance\minecraft\mods"
    if (-not (Test-Path -LiteralPath $mods)) { continue }
    Get-ChildItem -LiteralPath $mods -Filter "ntm-air-defense-*.jar" | Remove-Item
    Get-ChildItem "$loader\build\libs\ntm-air-defense-$loader-*.jar" | Sort-Object LastWriteTime | Select-Object -Last 1 | Copy-Item -Destination $mods
    Write-Host "Installed into ${instance}:" (Get-ChildItem -LiteralPath $mods -Filter "ntm-air-defense-*.jar").Name
}
