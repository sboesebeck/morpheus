#!/bin/bash
# Optimized run script with smart recompilation detection

csv=$(dirname $0)/cp.csv
rerun=0
recompile=0

# Parse flags
while [[ "$1" == --* ]]; do
  case "$1" in
  --rerun)
    rerun=1
    shift
    ;;
  --recompile)
    recompile=1
    shift
    ;;
  *)
    break
    ;;
  esac
done

# Check if recompilation is needed (only check once)
needs_rebuild=0

if [ "$rerun" -eq 0 ]; then
  # If cp.csv doesn't exist, we need to build
  if [ ! -f "$csv" ]; then
    needs_rebuild=1
  # If --recompile flag is given, force rebuild
  elif [ "$recompile" -eq 1 ]; then
    echo "DEBUG: Forced recompilation (--recompile flag)" >&2
    needs_rebuild=1
  # Check if pom.xml is newer than cp.csv
  elif [ "pom.xml" -nt "$csv" ]; then
    echo "DEBUG: pom.xml changed, recompilation needed" >&2
    needs_rebuild=1
  # Check if target/classes exists (might have been cleaned)
  elif [ ! -d "target/classes" ]; then
    echo "DEBUG: target/classes missing, recompilation needed" >&2
    needs_rebuild=1
  # Check if any source files are newer than cp.csv
  elif [ -d "src/main/java" ]; then
    newer_files=$(find src/main/java -name "*.java" -newer "$csv" 2>/dev/null | head -1)
    if [ -n "$newer_files" ]; then
      echo "DEBUG: Source files changed, recompilation needed" >&2
      needs_rebuild=1
    else
      echo "DEBUG: No changes detected, using cached build" >&2
    fi
  else
    echo "DEBUG: No changes detected, using cached build" >&2
  fi
fi

# Regenerate dependencies if needed
if [ "$needs_rebuild" -eq 1 ]; then
  echo "DEBUG: Refreshing dependencies..." >&2
  mvn -U dependency:list | grep ":.*:.*:compile" | sed "s/\[INFO\]    \([^:]*\):\([^:]*\):jar:\([^:]*\):compile/\1;\2;\3/" | sed -e 's/--.*$//' | sort -u >$csv
elif [ ! -f "$csv" ]; then
  # If cp.csv doesn't exist and we're in rerun mode, create it
  mvn -U dependency:list | grep ":.*:.*:compile" | sed "s/\[INFO\]    \([^:]*\):\([^:]*\):jar:\([^:]*\):compile/\1;\2;\3/" | sed -e 's/--.*$//' | sort -u >$csv
fi

# Build classpath
cp="./target/classes"
for l in $(<$csv); do
  # echo "Line: $l"
  path=$(echo $l | cut -f1 -d';' | sed -e 's!\.!/!g')
  art=$(echo $l | cut -f2 -d';')
  ver=$(echo $l | cut -f3 -d';')

  # echo "G: $path a: $art ver:$ver"
  if [ ! -e ~/.m2/repository/$path/$art/$ver/$art-$ver.jar ]; then
    #   echo "dependency $art-$ver OK"
    # else
    echo "G: $path a: $art ver:$ver"
    echo "MISSING"
  fi
  cp="$cp:$HOME/.m2/repository/$path/$art/$ver/$art-$ver.jar"
done

# Compile if needed
if [ "$needs_rebuild" -eq 1 ]; then
  echo "DEBUG: Running Maven compile..." >&2
  mvn compile 2>&1 | grep -v "^WARNING: " >/dev/null || {
    echo "Maven compile failed"
    exit 1
  }
  # Touch cp.csv to update its timestamp
  touch "$csv"
fi

# Export terminal size for Java to detect
# Method 1: Try stty size (most reliable when stdin is a terminal)
TERM_SIZE=$(stty size 2>/dev/null)
if [ -n "$TERM_SIZE" ]; then
  export LINES=$(echo $TERM_SIZE | cut -d' ' -f1)
  export COLUMNS=$(echo $TERM_SIZE | cut -d' ' -f2)
else
  # Method 2: Try tput (needs TERM to be set)
  COLS=$(tput cols 2>/dev/null)
  ROWS=$(tput lines 2>/dev/null)
  if [ -n "$COLS" ] && [ -n "$ROWS" ]; then
    export COLUMNS=$COLS
    export LINES=$ROWS
  else
    # Fallback: Use default or existing values
    export COLUMNS=${COLUMNS:-80}
    export LINES=${LINES:-24}
  fi
fi

# Debug output (can be disabled by commenting out)
echo "DEBUG: Terminal size set to ${COLUMNS}x${LINES}" >&2

java --sun-misc-unsafe-memory-access=allow -cp $cp de.caluga.morpheus.Morpheus "$@"
