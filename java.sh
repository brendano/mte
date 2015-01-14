#!/bin/zsh
# Set up classpath for running eclipse's compilation of the code
h=$(dirname $0)
CP=
CP=$h/bin:$CP
CP=$CP:$(print -l $h/lib/*.jar | tr '\n' :)
CP=$CP:$HOME/myutil/bin
# CP=$CP:$(print -l $HOME/myutil/lib/*.jar | tr '\n' :)
set -eu
java -ea -cp "$CP" "$@"
