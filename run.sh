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

java -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=5555 -cp $cp de.caluga.morpheus.Morpheus "$@"
