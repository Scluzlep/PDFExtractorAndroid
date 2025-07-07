@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>NUL
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
echo.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
echo.
goto fail

:init
@rem Get command-line arguments, handling Windowz variants

if not "%OS%" == "Windows_NT" goto main
:checkVer
set CMD_LINE_ARGS=
set MINGW_HOME=
set MINGW_PREFIX=
if defined MINGW_CHOST (
  set MINGW_PREFIX=%MINGW_CHOST%-
  set MINGW_HOME=%MINGW_PREFIX%
)
set _SKIP=0
:winnt
if "%~1"=="" goto main
if "%~1"=="-version" set _SKIP=1
if "%~1"=="--version" set _SKIP=1
if %_SKIP% == 1 (
  set CMD_LINE_ARGS=%*
  goto main
)
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto winnt

:main
@rem Read relative path to Gradle Wrapper properties file
set WRAPPER_PROPS_PATH="%APP_HOME%gradle\wrapper\gradle-wrapper.properties"

@rem Read relative path to Gradle Wrapper an optional properties file that will be used for customisation
@rem This file is not part of the Gradle distribution
set CUSTOM_WRAPPER_PROPS_PATH="%APP_HOME%gradle\wrapper\gradle-wrapper-custom.properties"

@rem Define the directory where the gradle distributions are stored
if exist %CUSTOM_WRAPPER_PROPS_PATH% (
    for /f "tokens=1,2 delims==" %%a in ('findstr /b "gradle.user.home" %CUSTOM_WRAPPER_PROPS_PATH%') do (
        set "GRADLE_USER_HOME=%%b"
    )
)
if not defined GRADLE_USER_HOME (
    set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
)
set "DOT_GRADLE_DIR=%GRADLE_USER_HOME%"

@rem Define the location of the gradle-wrapper.jar
if defined GRADLE_WRAPPER_JAR (
    @rem if GRADLE_WRAPPER_JAR is defined, we use it
    goto wrapperJarDefined
)
if not defined APP_HOME (
    @rem should not happen
    echo Cannot locate gradle-wrapper.jar
    goto fail
)
set GRADLE_WRAPPER_JAR="%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
:wrapperJarDefined

@rem Define the location of the gradle-wrapper-support.jar
@rem (for versions of Gradle older than 0.9)
set GRADLE_WRAPPER_SUPPORT_JAR="%APP_HOME%gradle\wrapper\gradle-wrapper-support.jar"

@rem Set the GRADLE_OPTS environment variable, if not set, to the value of DEFAULT_JVM_OPTS
if not defined GRADLE_OPTS (
    set "GRADLE_OPTS=%DEFAULT_JVM_OPTS%"
)

@rem Set the JAVA_OPTS environment variable, if not set, to the value of GRADLE_OPTS
if not defined JAVA_OPTS (
    set "JAVA_OPTS=%GRADLE_OPTS%"
)

set CLASSPATH=%GRADLE_WRAPPER_JAR%
if exist %GRADLE_WRAPPER_SUPPORT_JAR% (
   set CLASSPATH=%CLASSPATH%;%GRADLE_WRAPPER_SUPPORT_JAR%
)

@rem Execute Gradle
"%JAVA_EXE%" %JAVA_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code.
if not defined GRADLE_EXIT_CONSOLE (
  exit /b 1
)
exit 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega