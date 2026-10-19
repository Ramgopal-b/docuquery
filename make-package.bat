@echo off
rem ===========================================================================
rem  make-package.bat  -  run ONCE to build a ready-to-install DocuQuery package
rem
rem  It puts everything DocuQuery needs into this folder and zips it:
rem     lib\     dfc.jar + DFC libraries
rem     config\  dfc.properties  (copied, or created from host + port you type)
rem     jre\     Java runtime     (optional - then PCs need no Java installed)
rem  Result:  docuquery-ready.zip  next to this folder.
rem
rem  Every other PC then only needs:  unzip docuquery-ready.zip  ->  install.bat
rem  No dqMan is needed anywhere.
rem ===========================================================================
setlocal EnableExtensions
cd /d "%~dp0"
set "HERE=%~dp0"
set "HERE=%HERE:~0,-1%"
echo.
echo  DocuQuery - build ready-to-install package
echo  ---------------------------------------
echo.

rem ---------------------------------------------------------------- 1. DFC jars
if exist "lib\dfc.jar" (
  echo  [1/3] DFC: already in lib\ - keeping it.
  goto :dfcdone
)
set "SRC="
if defined DOCUMENTUM_SHARED if exist "%DOCUMENTUM_SHARED%\dfc.jar" set "SRC=%DOCUMENTUM_SHARED%"
if not defined SRC if exist "C:\Program Files\Documentum\Shared\dfc.jar" set "SRC=C:\Program Files\Documentum\Shared"
if not defined SRC if exist "C:\Documentum\Shared\dfc.jar" set "SRC=C:\Documentum\Shared"
if not defined SRC if exist "C:\Documentum\dfc\dfc.jar" set "SRC=C:\Documentum\dfc"
echo  [1/3] DFC libraries
echo        Give the folder that contains dfc.jar, for example
echo          - a DFC installation:            C:\Program Files\Documentum\Shared
echo          - a copy from the Content Server: %%DOCUMENTUM%%\dfc  or  %%DOCUMENTUM%%\Shared
echo          - a DA / D2 / Webtop web app:     ...\WEB-INF\lib
echo          - the unzipped DFC download from OpenText My Support
:askdfc
if defined SRC echo        Found: %SRC%
set /p "SRC=       DFC folder [Enter = found folder]: "
if defined SRC set "SRC=%SRC:"=%"
if not defined SRC goto :askdfc
if not exist "%SRC%\dfc.jar" (
  echo        dfc.jar is not in "%SRC%" - try again.
  set "SRC="
  goto :askdfc
)
if not exist "lib\lib" mkdir "lib\lib"
copy /y "%SRC%\*.jar" "lib\" >nul || goto :fail
if exist "%SRC%\lib\*.jar" copy /y "%SRC%\lib\*.jar" "lib\lib\" >nul
echo        copied to lib\
:dfcdone
echo.

rem ---------------------------------------------------------- 2. dfc.properties
if exist "config\dfc.properties" (
  echo  [2/3] dfc.properties: already in config\ - keeping it.
  goto :cfgdone
)
if not exist "config" mkdir "config"
echo  [2/3] dfc.properties
echo        Folder that contains an existing dfc.properties
echo        (Content Server: %%DOCUMENTUM%%\config, web app: ...\WEB-INF\classes),
set "CFG="
set /p "CFG=       or just press Enter to create a new one: "
if defined CFG set "CFG=%CFG:"=%"
if not defined CFG goto :newcfg
if not exist "%CFG%\dfc.properties" (
  echo        dfc.properties is not in "%CFG%" - a new one will be created.
  goto :newcfg
)
for %%F in (dfc.properties dbor.properties log4j.properties log4j2.properties log4j2.xml) do (
  if exist "%CFG%\%%F" copy /y "%CFG%\%%F" "config\" >nul
)
echo        copied to config\
rem server / other-PC folder paths would not exist on the users' PCs: comment them out
powershell -NoProfile -ExecutionPolicy Bypass -Command "$f='%HERE%\config\dfc.properties'; (Get-Content $f) -replace '^(\s*dfc\.(data|config|user|checkout|export|tokenstorage)\.dir\s*=)', '# disabled by DocuQuery: $1' | Set-Content $f"
echo        (dfc.data.dir / dfc.config.dir lines were disabled - DocuQuery uses its own folder)
goto :cfgdone
:newcfg
set "HOST="
:askhost
set /p "HOST=       Docbroker (Connection Broker) host name: "
if not defined HOST goto :askhost
set "PORT=1489"
set /p "PORT=       Docbroker port [1489]: "
> "config\dfc.properties" echo # dfc.properties created by DocuQuery make-package.bat
>> "config\dfc.properties" echo dfc.docbroker.host[0]=%HOST%
>> "config\dfc.properties" echo dfc.docbroker.port[0]=%PORT%
echo        Global registry (only if you have its ENCRYPTED password from a working
echo        dfc.properties; otherwise press Enter - an incomplete registry setting
echo        makes queries on custom types very slow):
set "REPO="
set /p "REPO=       Global registry repository (Enter = skip): "
if not defined REPO goto :regdone
set "REGPW="
set /p "REGPW=       Encrypted dfc.globalregistry.password: "
if not defined REGPW (
  echo        No password given - global registry NOT configured.
  goto :regdone
)
>> "config\dfc.properties" echo dfc.globalregistry.repository=%REPO%
>> "config\dfc.properties" echo dfc.globalregistry.username=dm_bof_registry
>> "config\dfc.properties" echo dfc.globalregistry.password=%REGPW%
:regdone
echo        created config\dfc.properties
:cfgdone
echo.

rem ------------------------------------------------------------------ 3. Java
if exist "jre\bin\java.exe" (
  echo  [3/3] Java: already in jre\ - keeping it.
  goto :javadone
)
set "JH="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JH=%JAVA_HOME%"
echo  [3/3] Java runtime to bundle (Java 17 or newer, e.g. Eclipse Temurin 17/21).
echo        Bundling Java means the other PCs do not need Java installed.
if defined JH echo        Found: %JH%
set /p "JH=       Java folder [Enter = found folder, - = skip]: "
if defined JH set "JH=%JH:"=%"
if not defined JH goto :javaskip
if "%JH%"=="-" goto :javaskip
if not exist "%JH%\bin\java.exe" (
  echo        java.exe not found in "%JH%\bin" - Java not bundled.
  goto :javaskip
)
robocopy "%JH%" "jre" /e /njh /njs /nfl /ndl >nul
if errorlevel 8 goto :fail
"jre\bin\java.exe" -version
goto :javadone
:javaskip
echo        Java not bundled - every PC must have Java 17+ installed.
:javadone
echo.

rem ------------------------------------------------------------------ 4. zip
set "OUT=%HERE%\..\docuquery-ready.zip"
if exist "%OUT%" del "%OUT%"
echo  Creating docuquery-ready.zip ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%HERE%' -DestinationPath '%OUT%' -Force"
if errorlevel 1 goto :fail
for %%Z in ("%OUT%") do echo.& echo  Package ready:  %%~fZ
echo.
echo  On every PC:  unzip docuquery-ready.zip  and double-click  install.bat
echo.
pause
exit /b 0

:fail
echo.
echo  ERROR - package not created.
pause
exit /b 1
