#!/bin/sh
# clone 후 한 번만 실행한다. git이 .githooks/ 를 훅 디렉터리로 쓰게 만든다.
set -eu

git config core.hooksPath .githooks
chmod +x .githooks/*
echo "pre-commit 훅이 설치되었습니다 (core.hooksPath=.githooks)"
