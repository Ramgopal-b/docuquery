@echo off
rem Removes the DocuQuery shortcuts. Delete the installation folder yourself afterwards.
rem Your history / favourites stay in %USERPROFILE%\.docuquery
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "foreach ($d in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) {" ^
  "  $p = Join-Path $d 'DocuQuery.lnk'; if (Test-Path $p) { Remove-Item $p } }"
echo Shortcuts removed. You can now delete the folder:
echo   %~dp0
pause
