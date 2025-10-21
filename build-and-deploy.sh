#!/usr/bin/env bash
# Copyright 2025 The ChaosBlade Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT_DIR"

log() { echo -e "[\033[32mINFO\033[0m] $*"; }
err() { echo -e "[\033[31mERROR\033[0m] $*" >&2; }

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    err "Missing required command: $1"; exit 1; fi
}

require_cmd docker
if docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
else
  require_cmd docker-compose
  DOCKER_COMPOSE="docker-compose"
fi

log "Building Docker images (this may take a few minutes on first run)..."
docker build -f svc-task-resource/Dockerfile -t chaosblade/svc-task-resource:latest .
docker build -f svc-reqrsp-proxy/Dockerfile -t chaosblade/svc-reqrsp-proxy:latest .
docker build -f svc-fault-scheduler/Dockerfile -t chaosblade/svc-fault-scheduler:latest .
docker build -f svc-task-executor/Dockerfile -t chaosblade/svc-task-executor:latest .

log "Bringing up infrastructure and services via docker-compose..."
$DOCKER_COMPOSE up -d --build

log "Services started. Mapped ports: 8101(resource), 8102(executor), 8103(scheduler), 8105(proxy)"
log "MySQL/Redis exposed at localhost:3306 / :6379"

log "Tail latest logs (press Ctrl+C to quit)"
$DOCKER_COMPOSE logs -f --tail=100 svc-task-resource svc-task-executor svc-fault-scheduler svc-reqrsp-proxy || true

log "Done. Health checks:"
cat <<EOF
  curl http://localhost:8101/hello  # svc-task-resource
  curl http://localhost:8102/hello  # svc-task-executor
  curl http://localhost:8103/hello  # svc-fault-scheduler
  curl http://localhost:8105/hello  # svc-reqrsp-proxy
EOF

