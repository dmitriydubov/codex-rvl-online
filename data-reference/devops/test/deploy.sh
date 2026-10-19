#!/bin/bash

set -e

TAG=${1:?Specify a tag, for example: ./devops/test/deploy.sh v2}
NAMESPACE=rvl-test
APP=data-reference
SECRET=$APP-secret

JDK_IMAGE=${JDK_IMAGE:-nexus.sogaz.ru/library/eclipse-temurin:21.0.12.1_1-jdk}
JRE_IMAGE=${JRE_IMAGE:-nexus.sogaz.ru/library/eclipse-temurin:21.0.12.1_1-jre}
GRADLE_DISTRIBUTION_URL=${GRADLE_DISTRIBUTION_URL:-https://nexus.sogaz.ru/repository/raw-proxy-services.gradle.com/gradle-8.14.3-bin.zip}

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
MODULE_DIR="$(readlink -f "$SCRIPT_DIR/../..")"
cd "$MODULE_DIR"

if [ ! -f Dockerfile ] || [ ! -f gradlew ] || [ ! -d src ]; then
    echo "No module sources in $MODULE_DIR: copy the whole data-reference module, this script lives in devops/test/."
    exit 1
fi

shopt -s nullglob
CA_FILES=(certs/*.crt certs/*.cer certs/*.pem)
shopt -u nullglob
if [ ${#CA_FILES[@]} -eq 0 ]; then
    echo "No CA certificate in $MODULE_DIR/certs: put the corporate CA there (.crt, .cer or .pem)."
    echo "Gradle inside the image needs it to trust Nexus."
    exit 1
fi

if ! kubectl -n "$NAMESPACE" get secret "$SECRET" >/dev/null 2>&1; then
    echo "Secret $SECRET not found in namespace $NAMESPACE. Create it first:"
    echo "  kubectl -n $NAMESPACE create secret generic $SECRET \\"
    echo "    --from-literal=DB_PASSWORD='...' --from-literal=RABBITMQ_PASSWORD='...'"
    exit 1
fi

CURRENT=$(kubectl -n "$NAMESPACE" get deploy "$APP" \
    -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo "not deployed")
echo "Deployed now: $CURRENT"
echo "Building:     $APP:$TAG"
echo "JDK image:    $JDK_IMAGE"
echo "JRE image:    $JRE_IMAGE"
echo

if sudo nerdctl images | grep -q "^$APP  *$TAG "; then
    echo "Image $APP:$TAG already exists."
    echo "Use a new tag, otherwise the pod will not be recreated."
    exit 1
fi

echo "== Build image =="
sudo nerdctl build \
    --build-arg JDK_IMAGE="$JDK_IMAGE" \
    --build-arg JRE_IMAGE="$JRE_IMAGE" \
    --build-arg JAR_SOURCE=build \
    --build-arg PUBLIC_REPOSITORIES=false \
    --build-arg GRADLE_DISTRIBUTION_URL="$GRADLE_DISTRIBUTION_URL" \
    -t "$APP:$TAG" .

echo "== Deploy to cluster =="
helm upgrade --install "$APP" "$SCRIPT_DIR/helm" --namespace "$NAMESPACE" --set TAG="$TAG"
kubectl -n "$NAMESPACE" rollout status deploy/"$APP" --timeout=5m

echo
echo "Done:"
kubectl -n "$NAMESPACE" get pods -l app="$APP"
