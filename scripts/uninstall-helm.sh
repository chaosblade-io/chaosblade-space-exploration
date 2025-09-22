#!/bin/bash

# ChaosBlade Space Exploration Helm 卸载脚本
# 使用方法: ./scripts/uninstall-helm.sh [命名空间] [是否删除命名空间]

set -e

# 默认参数
NAMESPACE=${1:-"chaosblade"}
DELETE_NAMESPACE=${2:-"false"}
RELEASE_NAME="chaosblade-space-exploration"

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

# 确认卸载
confirm_uninstall() {
    log_warning "即将卸载 ChaosBlade Space Exploration"
    log_warning "发布名称: $RELEASE_NAME"
    log_warning "命名空间: $NAMESPACE"
    log_warning "删除命名空间: $DELETE_NAMESPACE"
    echo ""
    
    read -p "确认继续卸载？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "取消卸载"
        exit 0
    fi
}

# 卸载 Helm 发布
uninstall_release() {
    log_info "卸载 Helm 发布: $RELEASE_NAME"
    
    if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE"
        log_success "Helm 发布卸载完成"
    else
        log_warning "发布 $RELEASE_NAME 不存在"
    fi
}

# 等待资源清理
wait_for_cleanup() {
    log_info "等待资源清理..."
    
    # 等待 Pod 删除
    if kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=chaosblade-space-exploration &> /dev/null; then
        kubectl wait --for=delete pod -l app.kubernetes.io/name=chaosblade-space-exploration -n "$NAMESPACE" --timeout=60s
    fi
    
    log_success "资源清理完成"
}

# 清理 PVC（如果存在）
cleanup_pvc() {
    log_info "检查并清理 PVC..."
    
    local pvcs=$(kubectl get pvc -n "$NAMESPACE" -l app.kubernetes.io/name=chaosblade-space-exploration -o name 2>/dev/null || true)
    
    if [ -n "$pvcs" ]; then
        log_warning "发现以下 PVC，需要手动删除："
        echo "$pvcs"
        echo ""
        read -p "是否删除这些 PVC？(y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            kubectl delete $pvcs -n "$NAMESPACE"
            log_success "PVC 删除完成"
        else
            log_warning "跳过 PVC 删除"
        fi
    else
        log_info "没有发现需要清理的 PVC"
    fi
}

# 删除命名空间
delete_namespace() {
    if [ "$DELETE_NAMESPACE" = "true" ]; then
        log_info "删除命名空间: $NAMESPACE"
        
        if kubectl get namespace "$NAMESPACE" &> /dev/null; then
            kubectl delete namespace "$NAMESPACE"
            log_success "命名空间删除完成"
        else
            log_warning "命名空间 $NAMESPACE 不存在"
        fi
    else
        log_info "保留命名空间: $NAMESPACE"
    fi
}

# 显示清理状态
show_cleanup_status() {
    log_info "清理状态:"
    echo ""
    
    # 检查命名空间是否存在
    if kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_info "命名空间 $NAMESPACE 仍然存在"
        
        # 显示剩余资源
        local pods=$(kubectl get pods -n "$NAMESPACE" 2>/dev/null | wc -l)
        local services=$(kubectl get svc -n "$NAMESPACE" 2>/dev/null | wc -l)
        local secrets=$(kubectl get secret -n "$NAMESPACE" 2>/dev/null | wc -l)
        local configmaps=$(kubectl get configmap -n "$NAMESPACE" 2>/dev/null | wc -l)
        
        log_info "剩余资源数量:"
        log_info "  Pods: $((pods-1))"
        log_info "  Services: $((services-1))"
        log_info "  Secrets: $((secrets-1))"
        log_info "  ConfigMaps: $((configmaps-1))"
    else
        log_success "命名空间 $NAMESPACE 已删除"
    fi
}

# 清理 RBAC 资源
cleanup_rbac() {
    log_info "清理 RBAC 资源..."
    
    # 删除 ClusterRoleBinding
    if kubectl get clusterrolebinding | grep -q "chaosblade-space-exploration"; then
        kubectl delete clusterrolebinding -l app.kubernetes.io/name=chaosblade-space-exploration
        log_success "ClusterRoleBinding 清理完成"
    fi
    
    # 删除 ClusterRole
    if kubectl get clusterrole | grep -q "chaosblade-space-exploration"; then
        kubectl delete clusterrole -l app.kubernetes.io/name=chaosblade-space-exploration
        log_success "ClusterRole 清理完成"
    fi
}

# 主函数
main() {
    log_info "开始卸载 ChaosBlade Space Exploration"
    log_info "命名空间: $NAMESPACE"
    log_info "发布名称: $RELEASE_NAME"
    echo ""
    
    check_prerequisites
    confirm_uninstall
    uninstall_release
    wait_for_cleanup
    cleanup_pvc
    cleanup_rbac
    delete_namespace
    show_cleanup_status
    
    echo ""
    log_success "卸载完成！"
    
    if [ "$DELETE_NAMESPACE" = "false" ]; then
        log_info "命名空间 $NAMESPACE 已保留，您可以手动删除："
        log_info "kubectl delete namespace $NAMESPACE"
    fi
}

# 显示帮助信息
show_help() {
    echo "ChaosBlade Space Exploration Helm 卸载脚本"
    echo ""
    echo "使用方法:"
    echo "  $0 [命名空间] [是否删除命名空间]"
    echo ""
    echo "参数:"
    echo "  命名空间              要卸载的命名空间 (默认: chaosblade)"
    echo "  是否删除命名空间      是否删除命名空间 (true/false, 默认: false)"
    echo ""
    echo "示例:"
    echo "  $0                                    # 卸载默认命名空间，保留命名空间"
    echo "  $0 chaosblade false                  # 卸载 chaosblade 命名空间，保留命名空间"
    echo "  $0 chaosblade true                   # 卸载 chaosblade 命名空间，删除命名空间"
    echo ""
    echo "选项:"
    echo "  -h, --help                          显示此帮助信息"
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            main "$@"
            ;;
    esac
fi
