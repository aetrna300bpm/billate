@echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
  set JAVA_EXEC=%JAVA_HOME%\bin\java
) else (
  set JAVA_EXEC=java
)

"%JAVA_EXEC%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
