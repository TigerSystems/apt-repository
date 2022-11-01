#!/usr/bin/bash

script /dev/null
screen -S "install-deb-screen" reprepro -C main includedeb "$1" "$2"
