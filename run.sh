#!/bin/bash
#
csv=$(mktemp)
mvn -U -o dependency:list | grep ":.*:.*:compile" | sed "s/\[INFO\]    \([^:]*\):\([^:]*\):jar:\([^:]*\):compile/\1;\2;\3/" | sed -e 's/--.*$//' | sort -u >$csv
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

mvn compile >/dev/null || exit 1

java -cp $cp de.caluga.morpheus.Morpheus "$@"
