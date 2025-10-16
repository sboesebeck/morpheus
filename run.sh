#!/bin/bash
# test a change
csv=$(dirname $0)/cp.csv
rerun=0
if [ "q$1" == "q--rerun" ]; then
  rerun=1
  shift
else
  rm -f $csv
fi
if [ "$rerun" -eq 0 ]; then
  mvn -U dependency:list | grep ":.*:.*:compile" | sed "s/\[INFO\]    \([^:]*\):\([^:]*\):jar:\([^:]*\):compile/\1;\2;\3/" | sed -e 's/--.*$//' | sort -u >$csv
fi
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
if [ "$rerun" != 1 ]; then
  mvn compile >/dev/null || {
    echo "Maven compile failed"
    exit 1
  }
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

java -cp $cp de.caluga.morpheus.Morpheus "$@"
