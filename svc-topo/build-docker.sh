#!/bin/bash

# Docker 镜像构建脚本，支持跨平台构建

set -e  # 遇到错误时退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 变量定义
APP_NAME="svc-topo"
VERSION="1.0.0"
DOCKER_IMAGE="ghcr.io/stleox/chaosblade/${APP_NAME}:${VERSION}"

# 默认平台
DEFAULT_PLATFORMS="linux/amd64"
BUILDER_NAME="multi-platform-builder"

# 显示帮助信息
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo "Build Docker image for specified platforms."
    echo ""
    echo "Options:"
    echo "  -p, --platform PLATFORMS    Specify target platforms (default: linux/amd64)"
    echo "                              Multiple platforms can be separated by commas"
    echo "                              e.g., linux/amd64,linux/arm64"
    echo "  -t, --tag TAG              Specify Docker image tag (default: ${DOCKER_IMAGE})"
    echo "  -h, --help                 Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                  # Build for amd64 only"
    echo "  $0 -p linux/amd64,linux/arm64      # Build for both amd64 and arm64"
    echo "  $0 -t myrepo/myapp:v1.0            # Build with custom tag"
}

# 解析命令行参数
PLATFORMS=""
CUSTOM_TAG=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--platform)
            PLATFORMS="$2"
            shift 2
            ;;
        -t|--tag)
            CUSTOM_TAG="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# 如果没有指定平台，使用默认值
if [ -z "$PLATFORMS" ]; then
    PLATFORMS="$DEFAULT_PLATFORMS"
fi

# 如果指定了自定义标签，使用它
if [ -n "$CUSTOM_TAG" ]; then
    DOCKER_IMAGE="$CUSTOM_TAG"
fi

# 检查是否安装了 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

# 检查是否安装了 Docker Buildx
if ! docker buildx version &> /dev/null; then
    echo -e "${YELLOW}Docker Buildx is not installed. Installing...${NC}"
    # 在大多数现代 Docker 版本中，Buildx 已经内置
    echo -e "${YELLOW}Please ensure you have Docker Desktop 2.4+ or Docker Engine 19.03+${NC}"
    exit 1
fi

# 打包 JAR 文件
echo -e "${GREEN}Packaging JAR file...${NC}"
mvn clean package -DskipTests

# 创建或使用现有的构建器实例
echo -e "${GREEN}Setting up Docker Buildx builder...${NC}"
if ! docker buildx inspect $BUILDER_NAME &> /dev/null; then
    echo -e "${YELLOW}Creating new builder instance: $BUILDER_NAME${NC}"
    docker buildx create --name $BUILDER_NAME --use
else
    echo -e "${GREEN}Using existing builder instance: $BUILDER_NAME${NC}"
    docker buildx use $BUILDER_NAME
fi

# 启动构建器
docker buildx inspect --bootstrap

# 构建并加载到本地 Docker（仅当单个平台时）
if [[ "$PLATFORMS" != *,* ]]; then
    if [ "$PLATFORMS" = "linux/amd64" ] || [ "$PLATFORMS" = "linux/arm64" ]; then
        echo -e "${GREEN}Building Docker image for $PLATFORMS and loading to local Docker...${NC}"
        docker buildx build --platform $PLATFORMS -t $DOCKER_IMAGE --load .
    else
        echo -e "${GREEN}Building Docker image for $PLATFORMS...${NC}"
        docker buildx build --platform $PLATFORMS -t $DOCKER_IMAGE --push .
    fi
else
    # 多平台构建，推送到仓库
    echo -e "${GREEN}Building Docker image for multiple platforms: $PLATFORMS${NC}"
    echo -e "${YELLOW}Note: Multi-platform images will be pushed to registry${NC}"
    docker buildx build --platform $PLATFORMS -t $DOCKER_IMAGE --push .
fi

echo -e "${GREEN}Docker image build completed successfully!${NC}"
echo -e "${GREEN}Image: $DOCKER_IMAGE${NC}"
echo -e "${GREEN}Platforms: $PLATFORMS${NC}"