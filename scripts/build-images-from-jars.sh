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

# 简要说明：
# 使用已构建好的 JAR 包来构建四个服务的运行时镜像（无 Maven 构建阶段）
# 支持通过环境变量 TAG 指定镜像标签，默认 latest

TAG=${TAG:-latest}
ROOT_DIR=$(cd "$(dirname "$0")"/.. && pwd)
LOG_PREFIX="[BUILD]"

SERVICES=(
  "svc-fault-scheduler:8103"
  "svc-reqrsp-proxy:8105"
  "svc-task-executor:8102"
  "svc-task-resource:8101"
)

function find_jar() {
  local module="$1"
  local jar
  # 优先选择 boot 可执行 jar（通常不包含 original），其次任意 jar
  jar=$(ls -1t "$ROOT_DIR/$module/target/"*.jar 2>/dev/null | grep -v "original-" | head -n 1 || true)
  if [[ -z "$jar" ]]; then
    jar=$(ls -1t "$ROOT_DIR/$module/target/"*.jar 2>/dev/null | head -n 1 || true)
  fi
  echo "$jar"
}

function build_service() {
  local module="$1"; local port="$2"; local image="chaosblade/$module:$TAG"
  echo "$LOG_PREFIX Building $module -> $image"

  local jar
  jar=$(find_jar "$module")
  if [[ -z "$jar" ]]; then
    echo "$LOG_PREFIX ERROR: 未找到 $module 的 JAR，请先在宿主机编译该模块。路径应为 $ROOT_DIR/$module/target/*.jar" >&2
    return 1
  fi
  echo "$LOG_PREFIX Found JAR: $jar"

  # 直接用模块自带 Dockerfile 构建（其中 COPY 会从 $module/target/*.jar 复制）
  (cd "$ROOT_DIR" && docker build -f "$module/Dockerfile" -t "$image" .)
  echo "$LOG_PREFIX SUCCESS: $module -> $image"
}

# 主流程
for svc in "${SERVICES[@]}"; do
  IFS=":" read -r module port <<<"$svc"
  build_service "$module" "$port"

done

echo "$LOG_PREFIX All images built with tag: $TAG"

