#!/usr/bin/env bash
# build-and-push.sh — Build Docker images for all test-services + proxy-agent
# and push to Harbor registry.
#
# Usage:
#   chmod +x build-and-push.sh
#   ./build-and-push.sh
#
# Environment:
#   REGISTRY    — Harbor registry address (default: 1.94.151.57:85/test)
#   TAG         — Image tag (default: latest)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REGISTRY="${REGISTRY:-1.94.151.57:85/test}"
TAG="${TAG:-latest}"

echo "============================================"
echo "  Build & Push — Test Services + Proxy Agent"
echo "  Registry: $REGISTRY"
echo "  Tag: $TAG"
echo "============================================"
echo ""

# Step 1: Login to Harbor
echo "[STEP 1] Logging in to Harbor..."
docker login 1.94.151.57:85 -u admin -p Harbor12345
echo ""

# Step 2: Build test-services
echo "[STEP 2] Building test-service images..."

declare -A SERVICE_IMAGES=(
    ["gateway"]="Dockerfile.gateway"
    ["order-svc"]="Dockerfile.order"
    ["inventory-svc"]="Dockerfile.inventory"
    ["db-svc"]="Dockerfile.db"
)

for svc in "${!SERVICE_IMAGES[@]}"; do
    dockerfile="${SERVICE_IMAGES[$svc]}"
    image="${REGISTRY}/${svc}:${TAG}"
    echo "  Building ${svc}..."
    docker build -t "$image" -f "$SCRIPT_DIR/$dockerfile" "$SCRIPT_DIR"
    echo "  → Built: $image"
done
echo ""

# Step 3: Build proxy-agent
echo "[STEP 3] Building proxy-agent image..."
PROXY_IMAGE="${REGISTRY}/proxy-agent:${TAG}"
docker build -t "$PROXY_IMAGE" -f "$PROJECT_ROOT/proxy-agent/Dockerfile" "$PROJECT_ROOT/proxy-agent"
echo "  → Built: $PROXY_IMAGE"
echo ""

# Step 4: Push all images
echo "[STEP 4] Pushing images to registry..."

for svc in "${!SERVICE_IMAGES[@]}"; do
    image="${REGISTRY}/${svc}:${TAG}"
    echo "  Pushing ${svc}..."
    docker push "$image"
done

echo "  Pushing proxy-agent..."
docker push "$PROXY_IMAGE"
echo ""

# Step 5: Summary
echo "============================================"
echo "  All images pushed successfully!"
echo ""
echo "  Images:"
for svc in "${!SERVICE_IMAGES[@]}"; do
    echo "    ${REGISTRY}/${svc}:${TAG}"
done
echo "    ${REGISTRY}/proxy-agent:${TAG}"
echo "============================================"
