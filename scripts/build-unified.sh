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

# 构建统一镜像脚本

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

# 默认配置
DEFAULT_TAG="latest"
DEFAULT_REGISTRY="ghcr.io"
DEFAULT_REPOSITORY="chaosblade-io/chaosblade-space-exploration-unified"
DEFAULT_PUSH=false

# 颜色输出
log() { echo -e "[\033[32mINFO\033[0m] $*"; }
warn() { echo -e "[\033[33mWARN\033[0m] $*"; }
err() { echo -e "[\033[31mERROR\033[0m] $*" >&2; }

# 显示帮助信息
show_help() {
    cat <<EOF
构建统一镜像脚本

使用方法:
  $0 [选项]

选项:
  -h, --help         显示此帮助信息
  -t, --tag TAG      指定镜像标签 (默认: $DEFAULT_TAG)
  -r, --registry REG 指定镜像仓库 (默认: $DEFAULT_REGISTRY)
  -n, --name NAME    指定镜像名称 (默认: $DEFAULT_REPOSITORY)
  -p, --push         构建后推送到仓库
  --no-cache         不使用Docker缓存
  --platform PLAT    指定目标平台 (例如: linux/amd64,linux/arm64)

环境变量:
  DOCKER_USERNAME     Docker仓库用户名
  DOCKER_PASSWORD     Docker仓库密码
  DOCKER_TOKEN        Docker仓库Token

示例:
  $0 --tag v1.0.0
  $0 --tag v1.0.0 --push
  $0 --registry docker.io --name myorg/chaosblade-unified --tag latest
  $0 --platform linux/amd64,linux/arm64 --push
EOF
}

# 解析命令行参数
TAG="$DEFAULT_TAG"
REGISTRY="$DEFAULT_REGISTRY"
REPOSITORY="$DEFAULT_REPOSITORY"
PUSH="$DEFAULT_PUSH"
NO_CACHE=""
PLATFORM=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -t|--tag)
            TAG="$2"
            shift 2
            ;;
        -r|--registry)
            REGISTRY="$2"
            shift 2
            ;;
        -n|--name)
            REPOSITORY="$2"
            shift 2
            ;;
        -p|--push)
            PUSH=true
            shift
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        --platform)
            PLATFORM="$2"
            shift 2
            ;;
        -*)
            err "未知选项: $1"
            show_help
            exit 1
            ;;
        *)
            err "未知参数: $1"
            show_help
            exit 1
            ;;
    esac
done

# 构建完整镜像名称
FULL_IMAGE_NAME="${REGISTRY}/${REPOSITORY}:${TAG}"

log "开始构建统一镜像..."
log "镜像名称: $FULL_IMAGE_NAME"
log "平台: ${PLATFORM:-默认}"



DOCKER_COMMAND="podman"

# 构建Docker命令
DOCKER_BUILD_CMD="$DOCKER_COMMAND build"

if [[ -n "$PLATFORM" ]]; then
    DOCKER_BUILD_CMD="$DOCKER_COMMAND buildx build --platform $PLATFORM"
fi

if [[ "$PUSH" == true ]]; then
    DOCKER_BUILD_CMD="$DOCKER_BUILD_CMD --push"
fi

if [[ -n "$NO_CACHE" ]]; then
    DOCKER_BUILD_CMD="$DOCKER_BUILD_CMD $NO_CACHE"
fi

DOCKER_BUILD_CMD="$DOCKER_BUILD_CMD -f svc-unified/Dockerfile -t $FULL_IMAGE_NAME ."

log "执行命令: $DOCKER_BUILD_CMD"

# 执行构建
if eval "$DOCKER_BUILD_CMD"; then
    log "镜像构建成功: $FULL_IMAGE_NAME"

    if [[ "$PUSH" == false ]]; then
        log "本地镜像信息:"
        docker images "$FULL_IMAGE_NAME"

        log ""
        log "测试运行示例:"
        log "  docker run --rm -e SERVICE_NAME=task-resource -p 8101:8101 $FULL_IMAGE_NAME"
        log "  docker run --rm -e SERVICE_NAME=reqrsp-proxy -p 8105:8105 $FULL_IMAGE_NAME"
    else
        log "镜像已推送到仓库: $FULL_IMAGE_NAME"
    fi
else
    err "镜像构建失败"
    exit 1
fi

log "构建完成!"
