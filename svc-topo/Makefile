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

# svc-topo Makefile

# 变量定义
APP_NAME = svc-topo
VERSION = 1.0.0
DOCKER_IMAGE = ghcr.io/stleox/chaosblade/$(APP_NAME):$(VERSION)

# 默认目标
.PHONY: help build package docker-build docker-push clean docker-buildx-amd64

help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Available targets:"
	@echo "  build              构建项目"
	@echo "  package            打包项目为 JAR 文件"
	@echo "  docker-build       构建 Docker 镜像"
	@echo "  docker-buildx-amd64 使用 buildx 构建 amd64 架构 Docker 镜像"
	@echo "  docker-push        推送 Docker 镜像到仓库"
	@echo "  clean              清理构建产物"
	@echo "  help               显示帮助信息"

# 构建项目
build:
	@echo "Building $(APP_NAME)..."
	mvn clean compile

# 打包项目为 JAR 文件
package:
	@echo "Packaging $(APP_NAME)..."
	mvn clean package -DskipTests

# 构建 Docker 镜像
docker-build: package
	@echo "Building Docker image $(DOCKER_IMAGE)..."
	docker build -t $(DOCKER_IMAGE) .

# 使用 buildx 构建 amd64 架构 Docker 镜像
docker-buildx-amd64: package
	@echo "Building Docker image $(DOCKER_IMAGE) for amd64 platform..."
	@if ! docker buildx ls | grep -q container-builder; then \
		docker buildx create --name container-builder --use; \
	fi
	docker buildx build --platform linux/amd64 -t $(DOCKER_IMAGE) .

# 推送 Docker 镜像到仓库
docker-push:
	@echo "Pushing Docker image $(DOCKER_IMAGE)..."
	docker push $(DOCKER_IMAGE)

# 清理构建产物
clean:
	@echo "Cleaning build artifacts..."
	mvn clean
	rm -f *.jar