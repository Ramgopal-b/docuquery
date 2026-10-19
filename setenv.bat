@echo off
rem ---------------------------------------------------------------------------
rem  DocuQuery uses ONLY the DFC files inside its own folder:
rem     lib\dfc.jar (+ lib\*.jar, lib\lib\*.jar)   and   config\dfc.properties
rem  Nothing is read from C:\Documentum, Program Files or other system folders.
rem ---------------------------------------------------------------------------
set "APP_HOME=%~dp0"
set "DFC_CP=%APP_HOME%lib\*;%APP_HOME%lib\lib\*"
set "DFC_CONFIG=%APP_HOME%config"

rem ---- Java: the folder's own jre\ first, then JAVA_HOME, then PATH
set "JAVA_EXE=java"
set "JAVAW_EXE=javaw"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW_EXE=%JAVA_HOME%\bin\javaw.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if exist "%APP_HOME%jre\bin\javaw.exe" set "JAVAW_EXE=%APP_HOME%jre\bin\javaw.exe"
if exist "%APP_HOME%jre\bin\java.exe" set "JAVA_EXE=%APP_HOME%jre\bin\java.exe"

if not exist "%APP_HOME%lib\dfc.jar" goto :nodfc
if not exist "%APP_HOME%config\dfc.properties" goto :noconfig
echo DFC jars  : %APP_HOME%lib
echo DFC config: %APP_HOME%config\dfc.properties
echo Java      : %JAVA_EXE%
exit /b 0

:nodfc
echo ERROR: lib\dfc.jar is missing in %APP_HOME%
echo Build the package with make-package.bat, or use "DFC setup..." in the login window.
exit /b 1

:noconfig
echo ERROR: config\dfc.properties is missing in %APP_HOME%
echo Build the package with make-package.bat, or use Edit ^> dfc.properties.
exit /b 1
