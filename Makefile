SHELL := /usr/bin/env bash -e

all: install test

clean:
	lein clean

install:
	lein deps

.PHONY: test
test:
	lein test
