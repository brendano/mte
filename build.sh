#!/bin/zsh
set -eux
rm -rf _build
mkdir _build
javac -cp $(print lib/**/*.jar | tr ' ' :) -d _build src/**/*.java
cd _build
for jar in ../lib/*.jar; jar xf $jar
  (
cat<<-EOF
Main-Class: ui.Main
EOF
) > Manifest.txt
jar cfm ../te.jar Manifest.txt *
cd ..
ls -l te.jar
