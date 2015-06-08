#!/bin/bash

set -eu

# get maven to build the code and download libs to target/lib/
mvn package

# remove what's there and replace
rm -rf lib/
mkdir lib/
cp target/lib/* lib/

git add -f lib/*.jar
