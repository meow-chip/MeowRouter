#! /bin/bash

sbt 'test:runMain Main --target-dir build --top-name Top --no-dce'