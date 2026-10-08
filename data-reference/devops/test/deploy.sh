#!/bin/bash
# Build the data-reference image and deploy it to k3s on s00-0000-rvl8.
# Usage: ./deploy.sh v1
#
# There is no Docker here: nerdctl builds the image straight into containerd of k3s,
# so no save/import steps. Socket and namespace are set in /etc/nerdctl/nerdctl.toml.
#
# Lives next to Dockerfile, app.jar and the helm/ directory.

set -e

TAG=${1:?Specify a tag, for example: ./deploy.sh v1}
NAMESPACE=rvl-test
APP=data-reference
SECRET=$APP-secret

cd "$(dirname "$(readlink -f "$0")")"

if [ ! -f app.jar ]; then
    echo "No app.jar in $(pwd): copy the built jar next to Dockerfile."
    exit 1
fi

# Passwords of the database and the broker are not in the chart: the Secret is created once by hand.
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
echo

if sudo nerdctl images | grep -q "^$APP  *$TAG "; then
    echo "Image $APP:$TAG already exists."
    echo "Use a new tag, otherwise the pod will not be recreated."
    exit 1
fi

echo "== Build image =="
sudo nerdctl build -t "$APP:$TAG" .

echo "== Deploy to cluster =="
helm upgrade --install "$APP" ./helm --namespace "$NAMESPACE" --set TAG="$TAG"
kubectl -n "$NAMESPACE" rollout status deploy/"$APP" --timeout=5m

echo
echo "Done:"
kubectl -n "$NAMESPACE" get pods -l app="$APP"
