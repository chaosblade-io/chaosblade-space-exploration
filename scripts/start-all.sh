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

set -Eeuo pipefail

# One-click local startup for ChaosBlade Space Exploration (apps only)
# - Builds the project with Maven
# - Launches all services in background, logs -> logs/<svc>.log
# - DOES NOT start or configure MySQL/Redis; please prepare external infra and set env vars
#
# Usage:
#   chmod +x scripts/start-all.sh
#   # Minimal
#   DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=spaceexploration \
#   DB_USERNAME=youruser DB_PASSWORD=yourpass \
#   SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=6379 \
#   ./scripts/start-all.sh
#
# Optional:
#   SERVICES="svc-task-resource svc-reqrsp-proxy ..." ./scripts/start-all.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/logs"
PID_DIR="$ROOT_DIR/logs/pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

DB_USER="${DB_USERNAME:-root}"
DB_PASS="${DB_PASSWORD:-root}"
DB_NAME="${DB_NAME:-spaceexploration}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"







build_all() {
  echo "[STEP] Building all modules (skip tests) ..."
  mvn -T1C -DskipTests package
}

start_service() {
  local dir="$1" svc_name="$1" port="${2:-}"
  echo "[STEP] Starting $svc_name ..."
  pushd "$dir" >/dev/null
  local jar
  jar=$(ls -1 target/*-SNAPSHOT.jar 2>/dev/null | grep -v original | head -n1 || true)
  if [[ -z "$jar" ]]; then
    jar=$(ls -1 target/*.jar 2>/dev/null | grep -v original | head -n1 || true)
  fi
  if [[ -z "$jar" ]]; then
    echo "[ERROR] No jar found in $dir/target. Did the build succeed?" >&2
    popd >/dev/null
    return 1
  fi
  # Common env overrides
  SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
  DB_USERNAME="$DB_USER" \
  DB_PASSWORD="$DB_PASS" \
  SPRING_DATA_REDIS_HOST="$REDIS_HOST" \
  SPRING_DATA_REDIS_PORT="$REDIS_PORT" \
  nohup java -jar "$jar" >"$LOG_DIR/${svc_name}.log" 2>&1 &
  echo $! >"$PID_DIR/${svc_name}.pid"
  popd >/dev/null
}

main() {
  echo "[INFO] Repo: $ROOT_DIR"
  echo "[INFO] Logs: $LOG_DIR"
  echo "[INFO] DB: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} | Redis: ${REDIS_HOST}:${REDIS_PORT}"

  echo "[INFO] This script does not manage MySQL/Redis; ensure they are available and env vars are set."
  echo "[INFO] Using DB=${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} and Redis=${REDIS_HOST}:${REDIS_PORT}"
  build_all

  local default_services=(
    svc-task-resource
    svc-reqrsp-proxy
    svc-fault-scheduler
    svc-task-executor
    svc-result-processor
    svc-topo
  )
  local services_str="${SERVICES:-}"
  local services
  if [[ -n "$services_str" ]]; then
    # shellcheck disable=SC2206
    services=($services_str)
  else
    services=("${default_services[@]}")
  fi

  for s in "${services[@]}"; do
    if [[ -d "$s" ]]; then
      start_service "$s" || true
    else
      echo "[WARN] Skip $s (directory not found)"
    fi
  done

  echo "\n[OK] All requested services started in background. Logs under: $LOG_DIR"
  echo "Hints:"
  echo "  - tail -f logs/*.log"
  echo "  - To filter services: SERVICES=\"svc-reqrsp-proxy svc-topo\" ./scripts/start-all.sh"
}

main "$@"

