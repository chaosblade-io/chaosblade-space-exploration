#!/usr/bin/env bash
# generate.sh — Regenerate protobuf Go code from .proto files.
# Requires: protoc, protoc-gen-go, protoc-gen-go-grpc
#
# Install the Go plugins:
#   go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
#   go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Generating protobuf Go code..."

protoc \
    --proto_path=proto \
    --go_out=gen \
    --go_opt=module=test-services/gen \
    --go-grpc_out=gen \
    --go-grpc_opt=module=test-services/gen \
    proto/db.proto proto/inventory.proto

echo "Done. Generated files:"
find gen -name '*.go' -type f | sort
