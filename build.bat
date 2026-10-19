@echo off
setlocal
cd /d "%~dp0"
call "%~dp0setenv.bat" || goto :fail

where javac >nul 2>nul || goto :nojdk

if exist build rmdir /s /q build
mkdir build\classes
dir /s /b src\*.java > build\sources.txt
javac --release 17 -encoding UTF-8 -nowarn -cp "%DFC_CP%" -d build\classes @build\sources.txt || goto :fail
jar --create --file docuquery.jar --main-class com.sedin.docuquery.App -C build\classes . || goto :fail
echo.
echo Build OK: docuquery.jar  -  start the tool with run.bat
endlocal
exit /b 0

:fail
echo.
echo BUILD FAILED
pause
endlocal
exit /b 1

:nojdk
echo ERROR: javac not found. Install a JDK 17 or newer and add its bin folder to PATH.
pause
exit /b 1
