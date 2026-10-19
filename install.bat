@echo off
rem ===========================================================================
rem  install.bat  -  installs DocuQuery for the current user
rem
rem    install.bat                     installs to %LOCALAPPDATA%\DocuQuery
rem    install.bat "D:\Tools\DocuQuery" installs to the given folder
rem
rem  Copies the program folder (with its own DFC in lib\ and config\)
rem  and creates Desktop + Start-menu shortcuts.
rem ===========================================================================
setlocal EnableExtensions
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=%LOCALAPPDATA%\DocuQuery"
echo.
echo  Installing DocuQuery to "%TARGET%"
echo.

robocopy "%~dp0." "%TARGET%" /e /njh /njs /nfl /ndl /xd build .git >nul
if errorlevel 8 goto :fail
rem remove the "downloaded from the internet" mark so Windows stops asking on every start
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -LiteralPath '%TARGET%' -Recurse -File | Unblock-File" >nul 2>&1

if exist "%TARGET%\lib\dfc.jar" if exist "%TARGET%\config\dfc.properties" goto :dfcok
echo  WARNING: this package has no DFC (lib\dfc.jar / config\dfc.properties).
echo  Use the package built with make-package.bat (docuquery-ready.zip),
echo  or use "DFC setup..." in the DocuQuery login window.
:dfcok

where java >nul 2>nul
if errorlevel 1 if not exist "%TARGET%\jre\bin\java.exe" if not defined JAVA_HOME echo  WARNING: Java 17+ was not found. Install Java 17+ (e.g. Eclipse Temurin) or use a package with Java bundled (make-package.bat).

echo  Creating shortcuts ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$w = New-Object -ComObject WScript.Shell;" ^
  "foreach ($d in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) {" ^
  "  $l = $w.CreateShortcut((Join-Path $d 'DocuQuery.lnk'));" ^
  "  $l.TargetPath = '%TARGET%\run.bat'; $l.WorkingDirectory = '%TARGET%';" ^
  "  $l.IconLocation = '%TARGET%\docuquery.ico'; $l.WindowStyle = 7; $l.Description = 'DocuQuery - DQL client for Documentum'; $l.Save() }"

echo.
echo  DocuQuery is installed. Start it from the Desktop shortcut "DocuQuery".
echo.
pause
exit /b 0

:fail
echo  ERROR: copying files to "%TARGET%" failed.
pause
exit /b 1
