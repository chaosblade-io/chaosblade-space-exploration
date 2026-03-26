#!/usr/bin/env bash
# deploy.sh — Deploy test-services + proxy-agent to Kubernetes.
#
# Usage:
#   export KUBECONFIG=~/.kube/coroot-config
#   chmod +x deploy.sh
#   ./deploy.sh
#
# To delete:
#   kubectl delete namespace test-services

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "============================================"
echo "  Deploying test-services to Kubernetes"
echo "============================================"
echo ""

echo "[STEP 1] Creating namespace..."
kubectl apply -f "$SCRIPT_DIR/namespace.yaml"

echo "[STEP 2] Deploying db-svc..."
kubectl apply -f "$SCRIPT_DIR/db-svc.yaml"

echo "[STEP 3] Deploying inventory-svc..."
kubectl apply -f "$SCRIPT_DIR/inventory-svc.yaml"

echo "[STEP 4] Deploying order-svc..."
kubectl apply -f "$SCRIPT_DIR/order-svc.yaml"

echo "[STEP 5] Deploying proxy-agent..."
kubectl apply -f "$SCRIPT_DIR/proxy-agent.yaml"

echo "[STEP 6] Deploying gateway..."
kubectl apply -f "$SCRIPT_DIR/gateway.yaml"

echo ""
echo "[STEP 7] Waiting for all pods to be ready..."
kubectl -n test-services wait --for=condition=ready pod --all --timeout=120s

echo ""
echo "============================================"
echo "  Deployment complete!"
echo ""
echo "  Check pods:"
echo "    kubectl -n test-services get pods"
echo ""
echo "  Access gateway:"
echo "    curl http://<node-ip>:30080/health"
echo ""
echo "  Access proxy-agent control API:"
echo "    kubectl -n test-services port-forward svc/proxy-agent 9090:9090"
echo "    curl http://localhost:9090/control/health"
echo "============================================"
