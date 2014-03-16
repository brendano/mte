#!/bin/zsh
h=$(dirname $0)
CP=
CP=$h/bin:$CP
# CP=$CP:$(print -l $h/lib/*.jar | tr '\n' :)
CP=$CP:$HOME/myutil/myutil.jar
CP=$CP:$(print -l $HOME/myutil/lib/*.jar | tr '\n' :)
set -eux
java -ea -Dfile.encoding=UTF-8 -XX:ParallelGCThreads=2 -Xmx4g -cp "$CP" "$@"
