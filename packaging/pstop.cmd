@echo off
setlocal
"%~dp0runtime\bin\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -classpath "%~dp0lib\*" dev.pstop.MainKt %*
exit /b %ERRORLEVEL%
