@echo off
setlocal
cd /d "%~dp0"

where jpackage >nul 2>nul
if errorlevel 1 (
  echo Se requiere JDK 17 o superior con jpackage en el PATH.
  exit /b 1
)

set "APP_VERSION=1.1.0"
if not "%~1"=="" set "APP_VERSION=%~1"
set "PACKAGE_WORK=%CD%\target\windows-package-%RANDOM%-%RANDOM%"
set "PACKAGE_INPUT=%PACKAGE_WORK%\input"
set "PORTABLE=%CD%\dist\EstudioDocumental-%APP_VERSION%-windows-x64.zip"
if exist "%PORTABLE%" (
  echo El paquete ya existe. Se conserva sin reemplazar: %PORTABLE%
  exit /b 1
)
mkdir "%PACKAGE_INPUT%"
call mvnw.cmd -DskipTests package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=%PACKAGE_INPUT%"
if errorlevel 1 exit /b 1

copy /Y target\estudio-documental-1.1.0.jar.original "%PACKAGE_INPUT%\studio.jar" >nul
if errorlevel 1 exit /b 1

if not exist dist mkdir dist
jpackage --type app-image --name EstudioDocumental --app-version "%APP_VERSION%" --input "%PACKAGE_INPUT%" --dest "%PACKAGE_WORK%\portable" --main-jar studio.jar --main-class com.ejemplo.crudpersona.MCLauncher --icon img\logo.ico
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -Command "Compress-Archive -LiteralPath (Join-Path $env:PACKAGE_WORK 'portable\EstudioDocumental') -DestinationPath $env:PORTABLE -ErrorAction Stop"
if errorlevel 1 exit /b 1
jpackage --type exe --name EstudioDocumental --app-version "%APP_VERSION%" --app-image "%PACKAGE_WORK%\portable\EstudioDocumental" --dest dist --win-menu --win-shortcut --win-per-user-install
if errorlevel 1 exit /b 1

echo Instalador y paquete portable creados en dist. Los paquetes anteriores se conservan.
