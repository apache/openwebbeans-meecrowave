#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This script is forked from Apache Tomcat
#

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
os400=false
hpux=false
case "`uname`" in
CYGWIN*) cygwin=true;;
Darwin*) darwin=true;;
OS400*) os400=true;;
HP-UX*) hpux=true;;
esac

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set MEECROWAVE_HOME if not already set
[ -z "$MEECROWAVE_HOME" ] && MEECROWAVE_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

# Copy MEECROWAVE_BASE from MEECROWAVE_HOME if not already set
[ -z "$MEECROWAVE_BASE" ] && MEECROWAVE_BASE="$MEECROWAVE_HOME"

# Ensure that any user defined CLASSPATH variables are not used on startup,
# but allow them to be specified in setenv.sh, in rare case when it is needed.
CLASSPATH=

if [ -r "$MEECROWAVE_BASE/bin/setenv.sh" ]; then
  . "$MEECROWAVE_BASE/bin/setenv.sh"
elif [ -r "$MEECROWAVE_HOME/bin/setenv.sh" ]; then
  . "$MEECROWAVE_HOME/bin/setenv.sh"
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$JRE_HOME" ] && JRE_HOME=`cygpath --unix "$JRE_HOME"`
  [ -n "$MEECROWAVE_HOME" ] && MEECROWAVE_HOME=`cygpath --unix "$MEECROWAVE_HOME"`
  [ -n "$MEECROWAVE_BASE" ] && MEECROWAVE_BASE=`cygpath --unix "$MEECROWAVE_BASE"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# Ensure that neither MEECROWAVE_HOME nor MEECROWAVE_BASE contains a colon
# as this is used as the separator in the classpath and Java provides no
# mechanism for escaping if the same character appears in the path.
case $MEECROWAVE_HOME in
  *:*) echo "Using MEECROWAVE_HOME:   $MEECROWAVE_HOME";
       echo "Unable to start as MEECROWAVE_HOME contains a colon (:) character";
       exit 1;
esac
case $MEECROWAVE_BASE in
  *:*) echo "Using MEECROWAVE_BASE:   $MEECROWAVE_BASE";
       echo "Unable to start as MEECROWAVE_BASE contains a colon (:) character";
       exit 1;
esac

# For OS400
if $os400; then
  # Set job priority to standard for interactive (interactive - 6) by using
  # the interactive priority - 6, the helper threads that respond to requests
  # will be running at the same priority as interactive jobs.
  COMMAND='chgjob job('$JOBNAME') runpty(6)'
  system $COMMAND

  # Enable multi threading
  export QIBM_MULTI_THREADED=Y
fi

# Get standard Java environment variables
# Make sure prerequisite environment variables are set
if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
  if $darwin; then
    # Bugzilla 54390
    if [ -x '/usr/libexec/java_home' ] ; then
      export JAVA_HOME=`/usr/libexec/java_home`
    # Bugzilla 37284 (reviewed).
    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  else
    JAVA_PATH=`which java 2>/dev/null`
    if [ "x$JAVA_PATH" != "x" ]; then
      JAVA_PATH=`dirname $JAVA_PATH 2>/dev/null`
      JRE_HOME=`dirname $JAVA_PATH 2>/dev/null`
    fi
    if [ "x$JRE_HOME" = "x" ]; then
      # XXX: Should we try other locations?
      if [ -x /usr/bin/java ]; then
        JRE_HOME=/usr
      fi
    fi
  fi
  if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
    echo "Neither the JAVA_HOME nor the JRE_HOME environment variable is defined"
    echo "At least one of these environment variable is needed to run this program"
    exit 1
  fi
fi
if [ -z "$JAVA_HOME" -a "$1" = "debug" ]; then
  echo "JAVA_HOME should point to a JDK in order to run in debug mode."
  exit 1
fi
if [ -z "$JRE_HOME" ]; then
  JRE_HOME="$JAVA_HOME"
fi

# If we're running under jdb, we need a full jdk.
if [ "$1" = "debug" ] ; then
  if [ "$os400" = "true" ]; then
    if [ ! -x "$JAVA_HOME"/bin/java -o ! -x "$JAVA_HOME"/bin/javac ]; then
      echo "The JAVA_HOME environment variable is not defined correctly"
      echo "This environment variable is needed to run this program"
      echo "NB: JAVA_HOME should point to a JDK not a JRE"
      exit 1
    fi
  else
    if [ ! -x "$JAVA_HOME"/bin/java -o ! -x "$JAVA_HOME"/bin/jdb -o ! -x "$JAVA_HOME"/bin/javac ]; then
      echo "The JAVA_HOME environment variable is not defined correctly"
      echo "This environment variable is needed to run this program"
      echo "NB: JAVA_HOME should point to a JDK not a JRE"
      exit 1
    fi
  fi
fi

# Set standard commands for invoking Java, if not already set.
if [ -z "$_RUNJAVA" ]; then
  _RUNJAVA="$JRE_HOME"/bin/java
fi
if [ "$os400" != "true" ]; then
  if [ -z "$_RUNJDB" ]; then
    _RUNJDB="$JAVA_HOME"/bin/jdb
  fi
fi

# Add on extra jar files to CLASSPATH
if [ ! -z "$CLASSPATH" ] ; then
  CLASSPATH="$CLASSPATH":
fi
if [ "$MEECROWAVE_HOME" != "$MEECROWAVE_BASE" ]; then
  for i in "$MEECROWAVE_BASE/lib/"*.jar ; do
    if [ -z "$CLASSPATH" ] ; then
      CLASSPATH="$i"
    else
      CLASSPATH="$i:$CLASSPATH"
    fi
  done
fi
for i in "$MEECROWAVE_HOME/lib/"*.jar ; do
  if [ -z "$CLASSPATH" ] ; then
    CLASSPATH="$i"
  else
    CLASSPATH="$i:$CLASSPATH"
  fi
done

if [ -z "$MEECROWAVE_OUT" ] ; then
  MEECROWAVE_OUT="$MEECROWAVE_BASE"/logs/meecrowave.out
fi

if [ -z "$MEECROWAVE_TMPDIR" ] ; then
  # Define the java.io.tmpdir to use for MEECROWAVE
  MEECROWAVE_TMPDIR="$MEECROWAVE_BASE"/temp
fi

# Bugzilla 37848: When no TTY is available, don't output to console
have_tty=0
if [ "`tty`" != "not a tty" ]; then
    have_tty=1
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  JRE_HOME=`cygpath --absolute --windows "$JRE_HOME"`
  MEECROWAVE_HOME=`cygpath --absolute --windows "$MEECROWAVE_HOME"`
  MEECROWAVE_BASE=`cygpath --absolute --windows "$MEECROWAVE_BASE"`
  MEECROWAVE_TMPDIR=`cygpath --absolute --windows "$MEECROWAVE_TMPDIR"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
fi

if [ -z "$JSSE_OPTS" ] ; then
  JSSE_OPTS="-Djdk.tls.ephemeralDHKeySize=2048"
fi
JAVA_OPTS="$JAVA_OPTS $JSSE_OPTS"

# Register custom URL handlers
# Do this here so custom URL handles (specifically 'war:...') can be used in the security policy
JAVA_OPTS="$JAVA_OPTS -Djava.protocol.handler.pkgs=org.apache.tomcat.webresources"

# Set juli LogManager config file if it is present and an override has not been issued
if [ -z "$LOGGING_CONFIG" ]; then
  if [ -r "$MEECROWAVE_BASE"/conf/logging.properties ]; then
    LOGGING_CONFIG="-Djava.util.logging.config.file=$MEECROWAVE_BASE/conf/logging.properties"
  else
    # Bugzilla 45585
    LOGGING_CONFIG="-Dmeecrowave.script.nologgingconfig"
  fi
fi

if [ -z "$LOGGING_MANAGER" ]; then
  LOGGING_MANAGER="-Djava.util.logging.manager=${logManager}"
fi

# Set UMASK unless it has been overridden
if [ -z "$UMASK" ]; then
    UMASK="0027"
fi
umask $UMASK

# Uncomment the following line to make the umask available when using the
# org.apache.tomcat.security.SecurityListener
#JAVA_OPTS="$JAVA_OPTS -Dorg.apache.tomcat.security.SecurityListener.UMASK=`umask`"

if [ -z "$USE_NOHUP" ]; then
    if $hpux; then
        USE_NOHUP="true"
    else
        USE_NOHUP="false"
    fi
fi
unset _NOHUP
if [ "$USE_NOHUP" = "true" ]; then
    _NOHUP=nohup
fi

if [ -z "$MEECROWAVE_PID" ]; then
  MEECROWAVE_PID="$MEECROWAVE_BASE"/conf/meecrowave.pid
fi
# ----- Execute The Requested Command -----------------------------------------

# Bugzilla 37848: only output this if we have a TTY
if [ $have_tty -eq 1 ]; then
  echo "Using MEECROWAVE_BASE:   $MEECROWAVE_BASE"
  echo "Using MEECROWAVE_HOME:   $MEECROWAVE_HOME"
  echo "Using MEECROWAVE_TMPDIR: $MEECROWAVE_TMPDIR"
  echo "Using JRE_HOME:        $JRE_HOME"
  echo "Using CLASSPATH:       $CLASSPATH"
  if [ ! -z "$MEECROWAVE_PID" ]; then
    echo "Using MEECROWAVE_PID:    $MEECROWAVE_PID"
  fi
fi

if [ "$1" = "jpda" ] ; then
  if [ -z "$JPDA_TRANSPORT" ]; then
    JPDA_TRANSPORT="dt_socket"
  fi
  if [ -z "$JPDA_ADDRESS" ]; then
    JPDA_ADDRESS="localhost:8000"
  fi
  if [ -z "$JPDA_SUSPEND" ]; then
    JPDA_SUSPEND="n"
  fi
  if [ -z "$JPDA_OPTS" ]; then
    JPDA_OPTS="-agentlib:jdwp=transport=$JPDA_TRANSPORT,server=y,address=$JPDA_ADDRESS,suspend=$JPDA_SUSPEND"
  fi
  MEECROWAVE_OPTS="$JPDA_OPTS $MEECROWAVE_OPTS"
  shift
fi

MEECROWAVE_LOG4J2_PATH="$MEECROWAVE_BASE"/conf/log4j2.xml
if [ -f "$MEECROWAVE_LOG4J2_PATH" ]; then
  if $cygwin; then
    MEECROWAVE_LOG4J2_PATH=`cygpath --absolute --windows "$MEECROWAVE_LOG4J2_PATH"`
  fi
  MEECROWAVE_OPTS="$MEECROWAVE_OPTS "-Dlog4j.configurationFile=\"$MEECROWAVE_LOG4J2_PATH\"""
fi

MEECROWAVE_PROPERTIES_PATH="$MEECROWAVE_BASE"/conf/meecrowave.properties
if [ -f "$MEECROWAVE_PROPERTIES_PATH" ]; then
  if $cygwin; then
    MEECROWAVE_PROPERTIES_PATH=`cygpath --absolute --windows "$MEECROWAVE_PROPERTIES_PATH"`
  fi
  MEECROWAVE_ARGS="$MEECROWAVE_ARGS --meecrowave-properties="\"$MEECROWAVE_PROPERTIES_PATH\"""
fi

MEECROWAVE_SERVERXML_PATH="$MEECROWAVE_BASE"/conf/server.xml
if [ -f "$MEECROWAVE_SERVERXML_PATH" ]; then
  if $cygwin; then
    MEECROWAVE_SERVERXML_PATH=`cygpath --absolute --windows "$MEECROWAVE_SERVERXML_PATH"`
  fi
  MEECROWAVE_ARGS="$MEECROWAVE_ARGS --server-xml="\"$MEECROWAVE_SERVERXML_PATH\"""
fi
MEECROWAVE_ARGS="$MEECROWAVE_ARGS --tmp-dir="\"$MEECROWAVE_TMPDIR\"""

if [ "$1" = "run" ]; then

  shift
  eval exec "\"$_RUNJAVA\"" "\"$LOGGING_CONFIG\"" $LOGGING_MANAGER $JAVA_OPTS $MEECROWAVE_OPTS \
    -classpath "\"$CLASSPATH\"" \
    -Dmeecrowave.base="\"$MEECROWAVE_BASE\"" \
    -Dmeecrowave.home="\"$MEECROWAVE_HOME\"" \
    -Djava.io.tmpdir="\"$MEECROWAVE_TMPDIR\"" \
    ${main} "$MEECROWAVE_ARGS" "$@"

elif [ "$1" = "start" ] ; then

  if [ ! -z "$MEECROWAVE_PID" ]; then
    if [ -f "$MEECROWAVE_PID" ]; then
      if [ -s "$MEECROWAVE_PID" ]; then
        echo "Existing PID file found during start."
        if [ -r "$MEECROWAVE_PID" ]; then
          PID=`cat "$MEECROWAVE_PID"`
          ps -p $PID >/dev/null 2>&1
          if [ $? -eq 0 ] ; then
            echo "Meecrowave appears to still be running with PID $PID. Start aborted."
            echo "If the following process is not a Meecrowave process, remove the PID file and try again:"
            ps -f -p $PID
            exit 1
          else
            echo "Removing/clearing stale PID file."
            rm -f "$MEECROWAVE_PID" >/dev/null 2>&1
            if [ $? != 0 ]; then
              if [ -w "$MEECROWAVE_PID" ]; then
                cat /dev/null > "$MEECROWAVE_PID"
              else
                echo "Unable to remove or clear stale PID file. Start aborted."
                exit 1
              fi
            fi
          fi
        else
          echo "Unable to read PID file. Start aborted."
          exit 1
        fi
      else
        rm -f "$MEECROWAVE_PID" >/dev/null 2>&1
        if [ $? != 0 ]; then
          if [ ! -w "$MEECROWAVE_PID" ]; then
            echo "Unable to remove or write to empty PID file. Start aborted."
            exit 1
          fi
        fi
      fi
    fi
  fi

  shift
  touch "$MEECROWAVE_OUT"
  eval $_NOHUP "\"$_RUNJAVA\"" "\"$LOGGING_CONFIG\"" $LOGGING_MANAGER $JAVA_OPTS $MEECROWAVE_OPTS \
    -classpath "\"$CLASSPATH\"" \
    -Dmeecrowave.base="\"$MEECROWAVE_BASE\"" \
    -Dmeecrowave.home="\"$MEECROWAVE_HOME\"" \
    -Djava.io.tmpdir="\"$MEECROWAVE_TMPDIR\"" \
    ${main} "$MEECROWAVE_ARGS" "$@" \
    >> "$MEECROWAVE_OUT" 2>&1 "&"

  if [ ! -z "$MEECROWAVE_PID" ]; then
    echo $! > "$MEECROWAVE_PID"
  fi

  echo "Meecrowave started."

elif [ "$1" = "stop" ] ; then

  shift

  SLEEP=15
  if [ ! -z "$1" ]; then
    echo $1 | grep "[^0-9]" >/dev/null 2>&1
    if [ $? -gt 0 ]; then
      SLEEP=$1
      shift
    fi
  fi

  FORCE=0
  if [ "$1" = "-force" ]; then
    shift
    FORCE=1
  fi

  if [ ! -z "$MEECROWAVE_PID" ]; then
    if [ -f "$MEECROWAVE_PID" ]; then
      if [ -s "$MEECROWAVE_PID" ]; then
        kill -15 `cat "$MEECROWAVE_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          echo "PID file found but no matching process was found. Stop aborted."
          exit 1
        fi
      else
        echo "PID file is empty and has been ignored."
      fi
    else
      echo "\$MEECROWAVE_PID was set but the specified file does not exist. Is Meecrowave running? Stop aborted."
      exit 1
    fi
  fi

  if [ ! -z "$MEECROWAVE_PID" ]; then
    if [ -f "$MEECROWAVE_PID" ]; then
      while [ $SLEEP -ge 0 ]; do
        kill -15 `cat "$MEECROWAVE_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          rm -f "$MEECROWAVE_PID" >/dev/null 2>&1
          if [ $? != 0 ]; then
            if [ -w "$MEECROWAVE_PID" ]; then
              cat /dev/null > "$MEECROWAVE_PID"
              # If Meecrowave has stopped don't try and force a stop with an empty PID file
              FORCE=0
            else
              echo "The PID file could not be removed or cleared."
            fi
          fi
          echo "Meecrowave stopped."
          break
        fi
        if [ $SLEEP -gt 0 ]; then
          sleep 1
        fi
        if [ $SLEEP -eq 0 ]; then
          echo "Meecrowave did not stop in time."
          if [ $FORCE -eq 0 ]; then
            echo "PID file was not removed."
          fi
          echo "To aid diagnostics a thread dump has been written to standard out."
          kill -3 `cat "$MEECROWAVE_PID"`
        fi
        SLEEP=`expr $SLEEP - 1 `
      done
    fi
  fi

  KILL_SLEEP_INTERVAL=15
  if [ $FORCE -eq 1 ]; then
    if [ -z "$MEECROWAVE_PID" ]; then
      echo "Kill failed: \$MEECROWAVE_PID not set"
    else
      if [ -f "$MEECROWAVE_PID" ]; then
        PID=`cat "$MEECROWAVE_PID"`
        echo "Killing Meecrowave with the PID: $PID"
        kill -9 $PID
        while [ $KILL_SLEEP_INTERVAL -ge 0 ]; do
            kill -0 `cat "$MEECROWAVE_PID"` >/dev/null 2>&1
            if [ $? -gt 0 ]; then
                rm -f "$MEECROWAVE_PID" >/dev/null 2>&1
                if [ $? != 0 ]; then
                    if [ -w "$MEECROWAVE_PID" ]; then
                        cat /dev/null > "$MEECROWAVE_PID"
                    else
                        echo "The PID file could not be removed."
                    fi
                fi
                echo "The Meecrowave process has been killed."
                break
            fi
            if [ $KILL_SLEEP_INTERVAL -gt 0 ]; then
                sleep 1
            fi
            KILL_SLEEP_INTERVAL=`expr $KILL_SLEEP_INTERVAL - 1 `
        done
        if [ $KILL_SLEEP_INTERVAL -lt 0 ]; then
            echo "Meecrowave has not been killed completely yet. The process might be waiting on some system call or might be UNINTERRUPTIBLE."
        fi
      fi
    fi
  fi

else

  echo "Usage: MEECROWAVE.sh ( commands ... )"
  echo "commands:"
  echo "  jpda start        Start MEECROWAVE under JPDA debugger"
  echo "  run               Start MEECROWAVE in the current window"
  echo "  start             Start MEECROWAVE in a separate window"
  echo "  stop              Stop MEECROWAVE, waiting up t/o 15 seconds for the process to end"
  echo "  stop n            Stop MEECROWAVE, waiting up to n seconds for the process to end"
  echo "  stop -force       Stop MEECROWAVE, wait up to 15 seconds and then use kill -KILL if still running"
  echo "  stop n -force     Stop MEECROWAVE, wait up to n seconds and then use kill -KILL if still running"
  echo "Note: Waiting for the process to end and use of the -force option require that \$MEECROWAVE_PID is defined"
  exit 1

fi