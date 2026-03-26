#!/usr/bin/env bash
# =============================================================================
# deploy.sh - Build, push, and deploy ChaosBlade Space Exploration to K8s
#
# Usage:
#   ./deploy.sh                    # full pipeline: build + push + deploy
#   ./deploy.sh --build-only       # build Docker images only
#   ./deploy.sh --push-only        # push images to Harbor only
#   ./deploy.sh --deploy-only      # helm install/upgrade only
#   ./deploy.sh --dry-run          # helm template (render without installing)
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration (override via environment variables)
# ---------------------------------------------------------------------------
REGISTRY="${REGISTRY:-1.94.151.57:85/test}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
NAMESPACE="${NAMESPACE:-chaosblade}"
RELEASE_NAME="${RELEASE_NAME:-space-exploration}"
CHART_DIR="$(cd "$(dirname "$0")/chaosblade-space-exploration" && pwd)"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Services to build (directory name -> image name)
declare -A SERVICES=(
  [svc-task-resource]=svc-task-resource
  [svc-task-executor]=svc-task-executor
  [svc-reqrsp-proxy]=svc-reqrsp-proxy
  [svc-result-processor]=svc-result-processor
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; }

check_prereqs() {
    local missing=0
    for cmd in docker mvn helm kubectl; do
        if ! command -v "$cmd" &>/dev/null; then
            err "Required command not found: $cmd"
            missing=1
        fi
    done
    if [ "$missing" -eq 1 ]; then
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Step 1: Build Java JARs with Maven
# ---------------------------------------------------------------------------
build_jars() {
    info "Building Java JARs with Maven..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -pl "$(IFS=,; echo "${!SERVICES[*]}")" -am
    ok "Maven build complete."
}

# ---------------------------------------------------------------------------
# Step 2: Build Docker images
# ---------------------------------------------------------------------------
build_images() {
    info "Building Docker images..."

    # Create a temporary generic Dockerfile if services don't have one
    local tmp_dockerfile
    tmp_dockerfile=$(mktemp)
    cat > "$tmp_dockerfile" <<'DOCKERFILE'
FROM eclipse-temurin:17-jre-alpine

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY target/*.jar app.jar

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

USER appuser

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
DOCKERFILE

    for svc_dir in "${!SERVICES[@]}"; do
        local image_name="${SERVICES[$svc_dir]}"
        local full_tag="${REGISTRY}/${image_name}:${IMAGE_TAG}"
        local svc_path="${PROJECT_ROOT}/${svc_dir}"

        info "  Building ${full_tag} ..."

        # Use service's own Dockerfile if it exists, otherwise use the generic one
        local dockerfile="${svc_path}/Dockerfile"
        if [ ! -f "$dockerfile" ]; then
            dockerfile="$tmp_dockerfile"
        fi

        docker build \
            -f "$dockerfile" \
            -t "$full_tag" \
            "$svc_path"

        ok "  Built ${full_tag}"
    done

    rm -f "$tmp_dockerfile"
    ok "All Docker images built."
}

# ---------------------------------------------------------------------------
# Step 3: Push Docker images to Harbor
# ---------------------------------------------------------------------------
push_images() {
    info "Pushing Docker images to ${REGISTRY}..."

    for svc_dir in "${!SERVICES[@]}"; do
        local image_name="${SERVICES[$svc_dir]}"
        local full_tag="${REGISTRY}/${image_name}:${IMAGE_TAG}"

        info "  Pushing ${full_tag} ..."
        docker push "$full_tag"
        ok "  Pushed ${full_tag}"
    done

    ok "All images pushed."
}

# ---------------------------------------------------------------------------
# Step 4: Helm install/upgrade
# ---------------------------------------------------------------------------
helm_deploy() {
    info "Deploying with Helm..."
    info "  Release:   ${RELEASE_NAME}"
    info "  Namespace: ${NAMESPACE}"
    info "  Chart:     ${CHART_DIR}"

    helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
        --namespace "${NAMESPACE}" \
        --create-namespace \
        --set registry="${REGISTRY}" \
        --set imageTag="${IMAGE_TAG}" \
        --set namespace="${NAMESPACE}" \
        --wait \
        --timeout 10m

    ok "Helm deploy complete."
    echo ""
    info "Checking deployment status..."
    kubectl get pods -n "${NAMESPACE}" -o wide
}

# ---------------------------------------------------------------------------
# Step 5: Helm dry-run (template rendering)
# ---------------------------------------------------------------------------
helm_dry_run() {
    info "Rendering Helm templates (dry-run)..."
    helm template "${RELEASE_NAME}" "${CHART_DIR}" \
        --namespace "${NAMESPACE}" \
        --set registry="${REGISTRY}" \
        --set imageTag="${IMAGE_TAG}" \
        --set namespace="${NAMESPACE}"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    local action="${1:-all}"

    case "$action" in
        --build-only)
            check_prereqs
            build_jars
            build_images
            ;;
        --push-only)
            check_prereqs
            push_images
            ;;
        --deploy-only)
            check_prereqs
            helm_deploy
            ;;
        --dry-run)
            check_prereqs
            helm_dry_run
            ;;
        all|"")
            check_prereqs
            build_jars
            build_images
            push_images
            helm_deploy
            ;;
        *)
            err "Unknown option: $action"
            echo "Usage: $0 [--build-only|--push-only|--deploy-only|--dry-run]"
            exit 1
            ;;
    esac
}

main "$@"
