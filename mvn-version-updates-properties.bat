@echo off

setlocal

set DIR=target
set FILE=%DIR%\versions-property-updates.log

echo ============ logging output to %FILE%
mkdir %DIR%
call mvn versions:update-properties > %FILE%

endlocal
