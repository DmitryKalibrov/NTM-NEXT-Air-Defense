# Builds the addon and installs it into the test instance.
$ErrorActionPreference = "Stop"
$mods = "$env:APPDATA\ElyPrismLauncher\instances\26.2\minecraft\mods"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat -I tools\mirror.init.gradle.kts build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Remove-Item "$mods\ntm-air-defense-*.jar" -ErrorAction SilentlyContinue
Copy-Item build\libs\ntm-air-defense-*.jar $mods\
Write-Host "Installed:" (Get-ChildItem "$mods\ntm-air-defense-*.jar").Name
