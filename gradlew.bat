@rem Set local scope for the variables with windows-style line endings
@echo off

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

if not "%JAVA_HOME%" == "" goto x_use_javahome
set JAVACMD=java
goto x_execute

:x_use_javahome
set JAVACMD=%JAVA_HOME%\bin\java

:x_execute
"%JAVACMD%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
