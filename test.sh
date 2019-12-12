#! /bin/bash

export CHISEL_TYPE=TEST

sbt 'test:runMain TestMain'