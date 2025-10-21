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

# 
# 
# 使用说明：
#   ./scripts/run-services.sh start|stop|restart|status|logs
#   可通过环境变量控制：
#     TAG                镜像标签，默认 latest
#     NETWORK_NAME       Docker 网络名，默认 spaceexploration-net
#     CONFIG_ROOT        配置目录根路径，默认 ./configs
#     ENV_FILE           （可选）--env-file 路径，若提供则传给所有容器
#   配置文件：
#     若存在 ./configs/<service>/application.yml 将以只读方式挂载到 /app/config/application.yml
#     Dockerfile 已设置 SPRING_CONFIG_ADDITIONAL_LOCATION=/app/config/ ，Spring 会自动加载该配置
# 

TAG=${TAG:-latest}
NETWORK_NAME=${NETWORK_NAME:-spaceexploration-net}
ROOT_DIR=$(cd "$(dirname "$0")"/.. && pwd)
CONFIG_ROOT=${CONFIG_ROOT:-"$ROOT_DIR/configs"}
ENV_FILE=${ENV_FILE:-}
LOG_PREFIX="[RUN]"

# 定义服务列表：模块名:容器名:端口
SERVICES=(
  "svc-task-resource:svc-task-resource:8101"
  "svc-task-executor:svc-task-executor:8102"
  "svc-fault-scheduler:svc-fault-scheduler:8103"
  "svc-reqrsp-proxy:svc-reqrsp-proxy:8105"
)

ensure_network() {
  if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
    echo "$LOG_PREFIX Create network $NETWORK_NAME"
    docker network create "$NETWORK_NAME" >/dev/null
  fi
}

container_exists() {
  local name="$1"
  docker ps -a --format '{{.Names}}' | grep -E "^${name}$" >/dev/null 2>&1
}

stop_one() {
  local name="$1"
  if container_exists "$name"; then
    echo "$LOG_PREFIX Stopping $name"
    docker rm -f "$name" >/dev/null || true
  fi
}

start_one() {
  local module="$1"; local name="$2"; local port="$3"; local image="chaosblade/${module}:${TAG}"

  # 配置文件挂载
  local cfg_dir="$CONFIG_ROOT/${module}"
  local cfg_flag=()
  if [[ -f "$cfg_dir/application.yml" ]]; then
    echo "$LOG_PREFIX Using config $cfg_dir/application.yml for $name"
    cfg_flag=( -v "$cfg_dir/application.yml:/app/config/application.yml:ro" )
  fi

  # 可选 env-file
  local env_flag=()
  if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
    echo "$LOG_PREFIX Using --env-file ${ENV_FILE} for $name"
    env_flag=( --env-file "${ENV_FILE}" )
  fi

  # 若已存在同名容器，先删除
  stop_one "$name"

  echo "$LOG_PREFIX Starting $name on :$port from $image"
  docker run -d \
    --name "$name" \
    --restart unless-stopped \
    --network "$NETWORK_NAME" \
    -p "$port:$port" \
    -e TZ=Asia/Shanghai \
    "${cfg_flag[@]}" \
    "${env_flag[@]}" \
    "$image" >/dev/null
}

health_check() {
  local name="$1"; local port="$2"; local retries=60
  echo "$LOG_PREFIX Health checking $name (port $port)"
  for ((i=1; i<=retries; i++)); do
    if curl -fsS "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
      echo "$LOG_PREFIX $name is healthy (actuator)"
      return 0
    fi
    if curl -fsS "http://127.0.0.1:${port}/hello" >/dev/null 2>&1; then
      echo "$LOG_PREFIX $name is healthy (hello)"
      return 0
    fi
    sleep 2
  done
  echo "$LOG_PREFIX WARNING: $name health check timed out"
  return 1
}

start_all() {
  ensure_network
  for s in "${SERVICES[@]}"; do
    IFS=":" read -r module name port <<<"$s"
    start_one "$module" "$name" "$port"
  done
  # 健康检查
  for s in "${SERVICES[@]}"; do
    IFS=":" read -r module name port <<<"$s"
    health_check "$name" "$port" || true
  done
  echo "$LOG_PREFIX All services started"
}

stop_all() {
  for s in "${SERVICES[@]}"; do
    IFS=":" read -r module name port <<<"$s"
    stop_one "$name"
  done
  echo "$LOG_PREFIX All services stopped"
}

status_all() {
  docker ps --filter "name=svc-" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

logs_all() {
  for s in "${SERVICES[@]}"; do
    IFS=":" read -r module name port <<<"$s"
    echo "----- Logs: $name -----"
    docker logs --since=10s -f "$name" &
  done
  wait
}

case "${1:-}" in
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all || true
    start_all
    ;;
  status)
    status_all
    ;;
  logs)
    logs_all
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|logs}"
    exit 1
    ;;
 esac

