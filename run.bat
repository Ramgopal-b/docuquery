@echo off
rem Starts DocuQuery.  "run.bat console" keeps a console window open with the DFC log.
setlocal
cd /d "%~dp0"
call "%~dp0setenv.bat" >nul || goto :setup
if not exist docuquery.jar goto :nobuild
"%JAVA_EXE%" -version >nul 2>&1 || goto :nojava
set "JAVA_OPTS=-Xmx1g "-Ddfc.properties.file=%DFC_CONFIG%\dfc.properties" --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/sun.security.provider=ALL-UNNAMED"

if /i "%~1"=="console" goto :console
start "DocuQuery" "%JAVAW_EXE%" %JAVA_OPTS% -cp "docuquery.jar;%DFC_CONFIG%;%DFC_CP%" com.sedin.docuquery.App
endlocal
exit /b 0

:console
"%JAVA_EXE%" %JAVA_OPTS% -cp "docuquery.jar;%DFC_CONFIG%;%DFC_CP%" com.sedin.docuquery.App
pause
endlocal
exit /b 0

:setup
rem DFC missing: start anyway so "DFC setup..." in the login window can fix it
call "%~dp0setenv.bat"
echo.
echo Starting DocuQuery without DFC - use "DFC setup..." in the login window.
if not defined JAVAW_EXE set "JAVAW_EXE=javaw"
start "DocuQuery" "%JAVAW_EXE%" -cp "docuquery.jar" com.sedin.docuquery.App
timeout /t 5 >nul
exit /b 1

:nojava
echo Java was not found. Install Java 17 or newer (the same version DFC uses),
echo set JAVA_HOME, or use a package with Java bundled (make-package.bat).
pause
exit /b 1

:nobuild
echo docuquery.jar not found - run build.bat first.
pause
exit /b 1
