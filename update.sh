#!/usr/bin/bash

rm tiger-os/db -rf
rm tiger-os/dists -rf
rm tiger-os/pool -rf

./gradlew run --args='lorax'

sudo rm tmp -rf
