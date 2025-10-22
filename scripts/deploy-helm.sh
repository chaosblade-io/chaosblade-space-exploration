#!/bin/bash
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


# ChaosBlade Space Exploration Helm 部署脚本
# 使用方法: ./scripts/deploy-helm.sh [环境] [命名空间]

set -e

# 默认参数
ENVIRONMENT=${1:-"dev"}
NAMESPACE=${2:-"chaosblade"}
RELEASE_NAME="chaosblade-space-exploration"
CHART_PATH="helm/chaosblade-space-exploration"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查前置条件
check_prerequisites() {
    log_info "检查前置条件..."
    
    # 检查 kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl 未安装或不在 PATH 中"
        exit 1
    fi
    
    # 检查 helm
    if ! command -v helm &> /dev/null; then
        log_error "Helm 未安装或不在 PATH 中"
        exit 1
    fi
    
    # 检查 Kubernetes 连接
    if ! kubectl cluster-info &> /dev/null; then
        log_error "无法连接到 Kubernetes 集群"
        exit 1
    fi
    
    log_success "前置条件检查通过"
}

# 创建命名空间
create_namespace() {
    log_info "创建命名空间: $NAMESPACE"
    
    if kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_warning "命名空间 $NAMESPACE 已存在"
    else
        kubectl create namespace "$NAMESPACE"
        log_success "命名空间 $NAMESPACE 创建成功"
    fi
}

# 更新 Helm 依赖
update_dependencies() {
    log_info "更新 Helm 依赖..."
    
    cd "$CHART_PATH"
    helm dependency update
    cd - > /dev/null
    
    log_success "Helm 依赖更新完成"
}

# 部署应用
deploy_application() {
    log_info "部署应用到环境: $ENVIRONMENT"
    
    local values_file=""
    case $ENVIRONMENT in
        "dev")
            values_file=""
            ;;
        "staging")
            values_file="-f values-staging.yaml"
            ;;
        "production")
            values_file="-f values-production.yaml"
            ;;
        *)
            log_warning "未知环境: $ENVIRONMENT，使用默认配置"
            ;;
    esac
    
    # 检查是否已存在发布
    if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
        log_warning "发布 $RELEASE_NAME 已存在，执行升级..."
        helm upgrade "$RELEASE_NAME" "$CHART_PATH" $values_file -n "$NAMESPACE"
    else
        log_info "安装新发布: $RELEASE_NAME"
        helm install "$RELEASE_NAME" "$CHART_PATH" $values_file -n "$NAMESPACE"
    fi
    
    log_success "应用部署完成"
}

# 等待部署完成
wait_for_deployment() {
    log_info "等待部署完成..."
    
    # 等待所有 Pod 就绪
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=chaosblade-space-exploration -n "$NAMESPACE" --timeout=300s
    
    log_success "所有 Pod 已就绪"
}

# 显示部署状态
show_status() {
    log_info "部署状态:"
    echo ""
    
    # 显示 Helm 发布状态
    helm status "$RELEASE_NAME" -n "$NAMESPACE"
    echo ""
    
    # 显示 Pod 状态
    kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=chaosblade-space-exploration
    echo ""
    
    # 显示服务状态
    kubectl get svc -n "$NAMESPACE" -l app.kubernetes.io/name=chaosblade-space-exploration
    echo ""
    
    # 显示 Ingress 状态（如果存在）
    if kubectl get ingress -n "$NAMESPACE" &> /dev/null; then
        kubectl get ingress -n "$NAMESPACE"
        echo ""
    fi
}

# 显示访问信息
show_access_info() {
    log_info "访问信息:"
    echo ""
    
    # 获取服务信息
    local services=("task-resource" "task-executor" "fault-scheduler" "result-processor" "reqrsp-proxy" "topo")
    local ports=("8101" "8102" "8103" "8104" "8105" "8106")
    
    for i in "${!services[@]}"; do
        local service_name="${services[$i]}"
        local port="${ports[$i]}"
        
        # 检查服务是否存在
        if kubectl get svc "$RELEASE_NAME-$service_name" -n "$NAMESPACE" &> /dev/null; then
            local service_type=$(kubectl get svc "$RELEASE_NAME-$service_name" -n "$NAMESPACE" -o jsonpath='{.spec.type}')
            
            case $service_type in
                "ClusterIP")
                    log_info "$service_name: kubectl port-forward svc/$RELEASE_NAME-$service_name $port:$port -n $NAMESPACE"
                    ;;
                "NodePort")
                    local node_port=$(kubectl get svc "$RELEASE_NAME-$service_name" -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')
                    log_info "$service_name: <node-ip>:$node_port"
                    ;;
                "LoadBalancer")
                    local lb_ip=$(kubectl get svc "$RELEASE_NAME-$service_name" -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                    if [ -n "$lb_ip" ]; then
                        log_info "$service_name: $lb_ip:$port"
                    else
                        log_info "$service_name: <load-balancer-ip>:$port (IP 分配中...)"
                    fi
                    ;;
            esac
        fi
    done
    
    echo ""
    log_info "健康检查端点:"
    for i in "${!services[@]}"; do
        local service_name="${services[$i]}"
        local port="${ports[$i]}"
        log_info "$service_name: http://<service-ip>:$port/actuator/health"
    done
}

# 主函数
main() {
    log_info "开始部署 ChaosBlade Space Exploration"
    log_info "环境: $ENVIRONMENT"
    log_info "命名空间: $NAMESPACE"
    log_info "发布名称: $RELEASE_NAME"
    echo ""
    
    check_prerequisites
    create_namespace
    update_dependencies
    deploy_application
    wait_for_deployment
    show_status
    show_access_info
    
    echo ""
    log_success "部署完成！"
    log_info "使用 'helm status $RELEASE_NAME -n $NAMESPACE' 查看详细状态"
    log_info "使用 'kubectl logs -l app.kubernetes.io/name=chaosblade-space-exploration -n $NAMESPACE -f' 查看日志"
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
