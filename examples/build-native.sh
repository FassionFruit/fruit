#!/bin/sh

cargo build --release

mkdir -p "$FASSIONFRUIT_OUTPUT_DIR"

cp target/release/libmylib.so \
   "$FASSIONFRUIT_OUTPUT_DIR/"