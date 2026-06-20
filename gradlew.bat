@if "%DEBUG%"=="" @echo off
@rem Gradle startup script for Windows
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
%JAVA_HOME%\bin\java.exe -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
