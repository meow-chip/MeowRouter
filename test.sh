#! /bin/bash

export CHISEL_TYPE=TEST
sbt 'test:runMain TopTestMain --target-dir build --top-name Top --no-dce'
sbt 'test:runMain forward.Main --target-dir build --top-name Top --no-dce'