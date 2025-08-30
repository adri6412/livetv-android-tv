@echo off
echo ========================================
echo    LiveTV Logcat Receiver Service
echo ========================================
echo.
echo Avvio del servizio per ricevere log da Android...
echo.
echo Per interrompere: CTRL+C
echo.
echo ========================================
echo.

python logcat_receiver.py --host 0.0.0.0 --port 8080

pause
