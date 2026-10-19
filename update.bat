@echo off
rem ===========================================================================
rem  update.bat  -  get the latest DocuQuery from git and install it
rem
rem  Run this inside your git clone of DocuQuery (first time: see INSTALL.txt).
rem  Your DFC files (lib\, config\) in the install folder are kept.
rem ===========================================================================
setlocal
cd /d "%~dp0"
where git >nul 2>nul || goto :nogit
echo.
echo  Getting the latest DocuQuery ...
git pull --ff-only || goto :fail
echo.
call "%~dp0install.bat" %*
exit /b 0

:nogit
echo  git is not installed. Install "Git for Windows" (https://git-scm.com) and try again.
pause
exit /b 1
:fail
echo  git pull failed - check your network / repository access.
pause
exit /b 1
