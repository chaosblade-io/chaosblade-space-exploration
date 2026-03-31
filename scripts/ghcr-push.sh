#!/usr/bin/env bash
# =============================================================================
# ghcr-push.sh - Build and push all chaosblade images to GHCR
#
# Usage:
#   ./scripts/ghcr-push.sh                # build + push all 8 images
#   ./scripts/ghcr-push.sh --build-only   # build only
#   ./scripts/ghcr-push.sh --push-only    # push only (images must exist)
#
# Environment variables:
#   REGISTRY    - Target registry (default: ghcr.io/chaosblade-io)
#   IMAGE_TAG   - Image tag (default: latest)
#   PLATFORM    - Target platform (default: empty; set to linux/amd64 on ARM Mac)
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
REGISTRY="${REGISTRY:-ghcr.io/chaosblade-io}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
PLATFORM="${PLATFORM:-}"

# Resolve paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SE_ROOT/.." && pwd)"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; }

# ---------------------------------------------------------------------------
# Image definitions: name | dockerfile | context
# ---------------------------------------------------------------------------
# Group A: Java 21 services (context = SE_ROOT, Dockerfile at service root)
# Group B: svc-topo (context = svc-topo/, Dockerfile inside)
# Group C: proxy-agent (Go multi-stage, context = proxy-agent/)
# Group D: chaosblade-box (Java 8, context = chaosblade-box/)
# Group E: chaosblade-box-fe (Nginx, context = chaosblade-box-fe/)

declare -a IMAGE_NAMES=(
  svc-task-resource
  svc-task-executor
  svc-reqrsp-proxy
  svc-result-processor
  svc-k8s-graph
  svc-topo
  proxy-agent
  chaosblade-box
  chaosblade-box-fe
)

# Returns: dockerfile_path context_path
get_build_args() {
  local name="$1"
  case "$name" in
    svc-task-resource|svc-task-executor|svc-reqrsp-proxy|svc-fault-scheduler)
      echo "$SE_ROOT/$name/Dockerfile" "$SE_ROOT"
      ;;
    svc-result-processor|svc-k8s-graph)
      echo "$SE_ROOT/$name/Dockerfile" "$SE_ROOT"
      ;;
    svc-topo)
      echo "$SE_ROOT/svc-topo/Dockerfile" "$SE_ROOT/svc-topo"
      ;;
    proxy-agent)
      echo "$SE_ROOT/proxy-agent/Dockerfile" "$SE_ROOT/proxy-agent"
      ;;
    chaosblade-box)
      echo "$PROJECT_ROOT/chaosblade-box/Dockerfile" "$PROJECT_ROOT/chaosblade-box"
      ;;
    chaosblade-box-fe)
      echo "$PROJECT_ROOT/chaosblade-box-fe/Dockerfile.k8s" "$PROJECT_ROOT/chaosblade-box-fe"
      ;;
    *)
      err "Unknown image: $name"
      return 1
      ;;
  esac
}

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
build_images() {
  info "Building ${#IMAGE_NAMES[@]} images..."
  local failed=0

  for name in "${IMAGE_NAMES[@]}"; do
    local full_tag="${REGISTRY}/${name}:${IMAGE_TAG}"
    read -r dockerfile context <<< "$(get_build_args "$name")"

    if [ ! -f "$dockerfile" ]; then
      err "Dockerfile not found: $dockerfile"
      failed=1
      continue
    fi

    info "  Building ${full_tag} ..."
    info "    Dockerfile: $dockerfile"
    info "    Context:    $context"

    if [ -n "$PLATFORM" ]; then
      docker buildx build \
        --platform "$PLATFORM" \
        -f "$dockerfile" \
        -t "$full_tag" \
        --load \
        "$context"
    else
      docker build \
        -f "$dockerfile" \
        -t "$full_tag" \
        "$context"
    fi

    ok "  Built ${full_tag}"
  done

  if [ "$failed" -eq 1 ]; then
    err "Some images failed to build."
    exit 1
  fi

  ok "All ${#IMAGE_NAMES[@]} images built."
}

# ---------------------------------------------------------------------------
# Push
# ---------------------------------------------------------------------------
push_images() {
  info "Pushing ${#IMAGE_NAMES[@]} images to ${REGISTRY}..."

  for name in "${IMAGE_NAMES[@]}"; do
    local full_tag="${REGISTRY}/${name}:${IMAGE_TAG}"

    info "  Pushing ${full_tag} ..."
    docker push "$full_tag"
    ok "  Pushed ${full_tag}"
  done

  ok "All ${#IMAGE_NAMES[@]} images pushed."
  echo ""
  info "=== Summary ==="
  for name in "${IMAGE_NAMES[@]}"; do
    echo "  ${REGISTRY}/${name}:${IMAGE_TAG}"
  done
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  local action="${1:-all}"

  # Pre-flight check
  if ! command -v docker &>/dev/null; then
    err "docker not found. Please install Docker first."
    exit 1
  fi

  case "$action" in
    --build-only)
      build_images
      ;;
    --push-only)
      push_images
      ;;
    all|"")
      build_images
      push_images
      ;;
    *)
      err "Unknown option: $action"
      echo "Usage: $0 [--build-only|--push-only]"
      exit 1
      ;;
  esac
}

main "$@"
