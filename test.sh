#! /bin/bash

sbt 'test:runMain forward.Main --target-dir build --top-name Top --no-dce'