#!/bin/bash
set -eou pipefail

git submodule update --init --recursive
cd ConcurrentUtil
mvn install
