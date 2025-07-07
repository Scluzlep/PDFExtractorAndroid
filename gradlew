#!/usr/bin/env bash

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

# OS specific support.
cygwin=false
darwin=false
linux=false
case "`uname`" in
  CYGWIN*)
    cygwin=true
    ;;
  Darwin*)
    darwin=true
    ;;
  Linux)
    linux=true
    ;;
esac

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

# Add an extra slash at the end of APP_HOME, if it is not there yet.
if [ -n "${APP_HOME}" ] && [ "`echo ${APP_HOME} | awk 'END {print substr($0, length($0), 1)}'`" != "/" ]; then
    APP_HOME="${APP_HOME}/"
fi

# Read relative path to Gradle Wrapper properties file
WRAPPER_PROPS_PATH="${APP_HOME}gradle/wrapper/gradle-wrapper.properties"

# Read relative path to Gradle Wrapper an optional properties file that will be used for customisation
# This file is not part of the Gradle distribution
CUSTOM_WRAPPER_PROPS_PATH="${APP_HOME}gradle/wrapper/gradle-wrapper-custom.properties"

# Define the directory where the gradle distributions are stored
if [ -f "$CUSTOM_WRAPPER_PROPS_PATH" ]; then
    GRADLE_USER_HOME_STRING="`grep 'gradle.user.home' "$CUSTOM_WRAPPER_PROPS_PATH"`"
    if [ -n "$GRADLE_USER_HOME_STRING" ]; then
        GRADLE_USER_HOME_STRING="`echo $GRADLE_USER_HOME_STRING | cut -d'=' -f2`"
        GRADLE_USER_HOME="`eval echo $GRADLE_USER_HOME_STRING`"
    fi
fi
if [ -z "$GRADLE_USER_HOME" ]; then
    GRADLE_USER_HOME="$HOME/.gradle"
fi
DOT_GRADLE_DIR="$GRADLE_USER_HOME"

# Define the location of the gradle-wrapper.jar
if [ -n "$GRADLE_WRAPPER_JAR" ]; then
    # if GRADLE_WRAPPER_JAR is defined, we use it
    true
elif [ -n "$APP_HOME" ]; then
    # otherwise, we compute its location based on APP_HOME
    GRADLE_WRAPPER_JAR="${APP_HOME}gradle/wrapper/gradle-wrapper.jar"
else
    # Should not happen
    echo "Cannot locate gradle-wrapper.jar" 1>&2
    exit 1
fi

# Define the location of the gradle-wrapper-support.jar
# (for versions of Gradle older than 0.9)
GRADLE_WRAPPER_SUPPORT_JAR="${APP_HOME}gradle/wrapper/gradle-wrapper-support.jar"

# Set the GRADLE_OPTS environment variable, if not set, to the value of DEFAULT_JVM_OPTS
if [ -z "$GRADLE_OPTS" ] ; then
    GRADLE_OPTS="$DEFAULT_JVM_OPTS"
fi

# Set the JAVA_OPTS environment variable, if not set, to the value of GRADLE_OPTS
if [ -z "$JAVA_OPTS" ] ; then
    JAVA_OPTS="$GRADLE_OPTS"
fi

# Add -server to the JVM options, if available
if [ "$cygwin" = "false" ] ; then
    if "$JAVA_HOME/bin/java" -server -version > /dev/null 2>&1 ; then
        JAVA_OPTS="-server $JAVA_OPTS"
    fi
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    APP_HOME=`cygpath --path --windows "$APP_HOME"`
    DOT_GRADLE_DIR=`cygpath --path --windows "$DOT_GRADLE_DIR"`
    GRADLE_WRAPPER_JAR=`cygpath --path --windows "$GRADLE_WRAPPER_JAR"`
    GRADLE_WRAPPER_SUPPORT_JAR=`cygpath --path --windows "$GRADLE_WRAPPER_SUPPORT_JAR"`
    CLASSPATH="$GRADLE_WRAPPER_JAR"
    if [ -f "$GRADLE_WRAPPER_SUPPORT_JAR" ] ; then
       CLASSPATH="$CLASSPATH;$GRADLE_WRAPPER_SUPPORT_JAR"
    fi
else
    CLASSPATH="$GRADLE_WRAPPER_JAR"
    if [ -f "$GRADLE_WRAPPER_SUPPORT_JAR" ] ; then
       CLASSPATH="$CLASSPATH:$GRADLE_WRAPPER_SUPPORT_JAR"
    fi
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
    APP_HOME=`cygpath --unix "$APP_HOME"`
    DOT_GRADLE_DIR=`cygpath --unix "$DOT_GRADLE_DIR"`
fi

# Check for the java command
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses /jre/sh/java
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and 'java' is not on the PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum number of open files
if [ "$cygwin" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ "$?" -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            # Use the system limit
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ "$?" -ne 0 ] ; then
            echo "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        echo "Could not query maximum file descriptor limit"
    fi
fi

# Collect all arguments for the java command, following the shell quoting and substitution rules
eval set -- "$JAVACMD" $JAVA_OPTS -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain "$@"

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
    # We need to re-cygpath the command and arguments
    args=
    for arg do
        if
            # The first argument is the java command and does not need to be converted
            [ "$arg" = "$JAVACMD" ]
        then
            true
        elif
            # This is a classpath argument and we've already converted it
            [ "$arg" = "-classpath" ] ||
            [ "$arg" = "\"$CLASSPATH\"" ]
        then
            true
        elif
            # This is a system property argument and could be a path, so we convert it
            case "$arg" in
            -D*)
                # This is a system property, check if it's a path
                value=`echo "$arg" | cut -d'=' -f2`
                if [ -e "$value" ] ; then
                    # It's a path, so we convert it
                    value=`cygpath --path --windows "$value"`
                    arg="`echo "$arg" | cut -d'=' -f1`=$value"
                fi
                ;;
            esac
        fi
        args="$args \"$arg\""
    done
    eval set -- $args
fi

# Execute the command
exec "$@"