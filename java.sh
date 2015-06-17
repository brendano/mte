#!/bin/zsh
# Set up classpath for running eclipse's compilation of the code
h=$(dirname $0)
CP=$h/bin:$h/lib/*
set -eu
set -x
java -ea -cp "$CP" "$@"
