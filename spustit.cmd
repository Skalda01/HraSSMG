@echo off
setlocal
pushd "%~dp0"
set "HRASSMG_JAVA=java"
set "HRASSMG_JAVAC=javac"
if defined JAVA_HOME (
    set "HRASSMG_JAVA=%JAVA_HOME%\bin\java.exe"
    set "HRASSMG_JAVAC=%JAVA_HOME%\bin\javac.exe"
)
"%HRASSMG_JAVAC%" -version >nul 2>&1
if errorlevel 1 (
    echo Je potreba skolou povolene JDK 21 nebo novejsi s javac na PATH nebo JAVA_HOME.
    goto failed
)
if not exist "target\school-classes" mkdir "target\school-classes"
"%HRASSMG_JAVAC%" --release 21 -encoding UTF-8 -cp "lib/*" -sourcepath "src/main/java" -d "target/school-classes" src/main/java/org/example/Main.java
if errorlevel 1 goto failed
"%HRASSMG_JAVA%" -Dsun.java2d.d3d=false -Dsun.java2d.opengl=false -Dhrassmg.renderer=java2d -cp "target/school-classes;src/main/resources;lib/*" org.example.Main
if errorlevel 1 goto failed
popd
exit /b 0
:failed
echo Spusteni selhalo. Podrobnosti jsou vyse.
pause
popd
exit /b 1
