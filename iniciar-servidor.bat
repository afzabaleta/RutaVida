@echo off
rem Starts RutaVida with the jar of "mvn package". Needs backend\db.properties (never in Git).
cd /d "%~dp0backend"
if not exist target\rutavida.jar (
  echo [ERROR] No existe backend\target\rutavida.jar.
  echo         Primero, en IntelliJ: Maven ^> Lifecycle ^> package
  echo         o en la consola: mvn -f backend\pom.xml package
  pause
  exit /b 1
)
if not exist db.properties (
  echo [ERROR] Falta backend\db.properties. Copie db.properties.example y escriba la contrasena.
  pause
  exit /b 1
)
java -jar target\rutavida.jar %*
pause