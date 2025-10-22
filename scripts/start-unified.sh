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

# 统一微服务启动脚本
# 支持通过参数启动不同的微服务

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

# 默认配置
DEFAULT_SERVICE="task-resource"
DEFAULT_PORT=8080
DEFAULT_TAG="latest"
DEFAULT_IMAGE="chaosblade/svc-unified"

# 颜色输出
log() { echo -e "[\033[32mINFO\033[0m] $*"; }
warn() { echo -e "[\033[33mWARN\033[0m] $*"; }
err() { echo -e "[\033[31mERROR\033[0m] $*" >&2; }

# 显示帮助信息
show_help() {
    cat <<EOF
统一微服务启动脚本

使用方法:
  $0 [选项] <服务名>

服务名:
  task-resource     任务资源管理服务 (端口: 8101)
  task-executor     任务执行服务 (端口: 8102)
  fault-scheduler   故障调度服务 (端口: 8103)
  result-processor  结果处理服务 (端口: 8104)
  reqrsp-proxy      请求响应代理服务 (端口: 8105)
  topo              拓扑服务 (端口: 8106)

选项:
  -h, --help         显示此帮助信息
  -p, --port PORT    指定服务端口 (默认: 根据服务自动设置)
  -t, --tag TAG      指定镜像标签 (默认: $DEFAULT_TAG)
  -i, --image IMAGE  指定镜像名称 (默认: $DEFAULT_IMAGE)
  -d, --detach       后台运行
  --build            构建镜像
  --logs             查看日志
  --stop             停止服务
  --status           查看状态

环境变量:
  DB_HOST           数据库主机 (默认: localhost)
  DB_PORT           数据库端口 (默认: 3306)
  DB_NAME           数据库名称 (默认: spaceexploration)
  DB_USERNAME       数据库用户名 (默认: root)
  DB_PASSWORD       数据库密码 (默认: root)
  REDIS_HOST        Redis主机 (默认: localhost)
  REDIS_PORT        Redis端口 (默认: 6379)
  KUBERNETES_API_URL Kubernetes API地址
  KUBERNETES_TOKEN   Kubernetes Token

示例:
  $0 task-resource
  $0 --port 8101 task-resource
  $0 --build --detach reqrsp-proxy
  $0 --logs task-executor
  $0 --stop fault-scheduler
EOF
}

# 解析命令行参数
SERVICE=""
PORT=""
TAG="$DEFAULT_TAG"
IMAGE="$DEFAULT_IMAGE"
DETACH=false
BUILD=false
LOGS=false
STOP=false
STATUS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        -t|--tag)
            TAG="$2"
            shift 2
            ;;
        -i|--image)
            IMAGE="$2"
            shift 2
            ;;
        -d|--detach)
            DETACH=true
            shift
            ;;
        --build)
            BUILD=true
            shift
            ;;
        --logs)
            LOGS=true
            shift
            ;;
        --stop)
            STOP=true
            shift
            ;;
        --status)
            STATUS=true
            shift
            ;;
        -*)
            err "未知选项: $1"
            show_help
            exit 1
            ;;
        *)
            if [[ -z "$SERVICE" ]]; then
                SERVICE="$1"
            else
                err "只能指定一个服务名"
                exit 1
            fi
            shift
            ;;
    esac
done

# 如果没有指定服务名，显示帮助
if [[ -z "$SERVICE" && "$LOGS" == false && "$STOP" == false && "$STATUS" == false ]]; then
    show_help
    exit 1
fi

# 服务端口映射
declare -A SERVICE_PORTS=(
    ["task-resource"]="8101"
    ["task-executor"]="8102"
    ["fault-scheduler"]="8103"
    ["result-processor"]="8104"
    ["reqrsp-proxy"]="8105"
    ["topo"]="8106"
)

# 验证服务名
if [[ -n "$SERVICE" && ! ${SERVICE_PORTS[$SERVICE]+_} ]]; then
    err "无效的服务名: $SERVICE"
    err "支持的服务: ${!SERVICE_PORTS[*]}"
    exit 1
fi

# 设置默认端口
if [[ -z "$PORT" && -n "$SERVICE" ]]; then
    PORT="${SERVICE_PORTS[$SERVICE]}"
fi

# 容器名
CONTAINER_NAME="chaosblade-${SERVICE}-${PORT}"

# 构建镜像
build_image() {
    log "构建统一镜像..."
    docker build -f svc-unified/Dockerfile -t "$IMAGE:$TAG" .
    log "镜像构建完成: $IMAGE:$TAG"
}

# 停止服务
stop_service() {
    if [[ -n "$SERVICE" ]]; then
        log "停止服务: $SERVICE"
        if docker ps -q -f name="$CONTAINER_NAME" | grep -q .; then
            docker stop "$CONTAINER_NAME" >/dev/null
            docker rm "$CONTAINER_NAME" >/dev/null
            log "服务已停止: $SERVICE"
        else
            warn "服务未运行: $SERVICE"
        fi
    else
        log "停止所有统一服务..."
        docker ps -q -f name="chaosblade-.*" | xargs -r docker stop
        docker ps -aq -f name="chaosblade-.*" | xargs -r docker rm
        log "所有服务已停止"
    fi
}

# 查看状态
show_status() {
    if [[ -n "$SERVICE" ]]; then
        log "服务状态: $SERVICE"
        if docker ps -f name="$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -q "$CONTAINER_NAME"; then
            docker ps -f name="$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        else
            warn "服务未运行: $SERVICE"
        fi
    else
        log "所有统一服务状态:"
        docker ps -f name="chaosblade-.*" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    fi
}

# 查看日志
show_logs() {
    if [[ -n "$SERVICE" ]]; then
        log "查看服务日志: $SERVICE"
        if docker ps -q -f name="$CONTAINER_NAME" | grep -q .; then
            docker logs -f "$CONTAINER_NAME"
        else
            err "服务未运行: $SERVICE"
            exit 1
        fi
    else
        err "请指定服务名"
        exit 1
    fi
}

# 启动服务
start_service() {
    # 检查镜像是否存在
    if ! docker image inspect "$IMAGE:$TAG" >/dev/null 2>&1; then
        err "镜像不存在: $IMAGE:$TAG"
        err "请先运行: $0 --build"
        exit 1
    fi

    # 停止已运行的同名服务
    if docker ps -q -f name="$CONTAINER_NAME" | grep -q .; then
        log "停止已运行的服务: $SERVICE"
        docker stop "$CONTAINER_NAME" >/dev/null
        docker rm "$CONTAINER_NAME" >/dev/null
    fi

    # 构建Docker运行命令
    local docker_cmd="docker run"
    
    if [[ "$DETACH" == true ]]; then
        docker_cmd="$docker_cmd -d"
    else
        docker_cmd="$docker_cmd -it"
    fi

    docker_cmd="$docker_cmd --name $CONTAINER_NAME"
    docker_cmd="$docker_cmd -p $PORT:$PORT"
    docker_cmd="$docker_cmd -e SERVICE_NAME=$SERVICE"
    docker_cmd="$docker_cmd -e SERVER_PORT=$PORT"
    
    # 添加环境变量
    [[ -n "${DB_HOST:-}" ]] && docker_cmd="$docker_cmd -e DB_HOST=$DB_HOST"
    [[ -n "${DB_PORT:-}" ]] && docker_cmd="$docker_cmd -e DB_PORT=$DB_PORT"
    [[ -n "${DB_NAME:-}" ]] && docker_cmd="$docker_cmd -e DB_NAME=$DB_NAME"
    [[ -n "${DB_USERNAME:-}" ]] && docker_cmd="$docker_cmd -e DB_USERNAME=$DB_USERNAME"
    [[ -n "${DB_PASSWORD:-}" ]] && docker_cmd="$docker_cmd -e DB_PASSWORD=$DB_PASSWORD"
    [[ -n "${REDIS_HOST:-}" ]] && docker_cmd="$docker_cmd -e REDIS_HOST=$REDIS_HOST"
    [[ -n "${REDIS_PORT:-}" ]] && docker_cmd="$docker_cmd -e REDIS_PORT=$REDIS_PORT"
    [[ -n "${KUBERNETES_API_URL:-}" ]] && docker_cmd="$docker_cmd -e KUBERNETES_API_URL=$KUBERNETES_API_URL"
    [[ -n "${KUBERNETES_TOKEN:-}" ]] && docker_cmd="$docker_cmd -e KUBERNETES_TOKEN=$KUBERNETES_TOKEN"

    docker_cmd="$docker_cmd $IMAGE:$TAG"

    log "启动服务: $SERVICE (端口: $PORT)"
    log "命令: $docker_cmd"
    
    eval "$docker_cmd"
    
    if [[ "$DETACH" == true ]]; then
        log "服务已在后台启动: $SERVICE"
        log "查看日志: $0 --logs $SERVICE"
        log "健康检查: curl http://localhost:$PORT/actuator/health"
    fi
}

# 主逻辑
main() {
    if [[ "$BUILD" == true ]]; then
        build_image
    fi

    if [[ "$STOP" == true ]]; then
        stop_service
        exit 0
    fi

    if [[ "$STATUS" == true ]]; then
        show_status
        exit 0
    fi

    if [[ "$LOGS" == true ]]; then
        show_logs
        exit 0
    fi

    if [[ -n "$SERVICE" ]]; then
        start_service
    fi
}

main "$@"
