#!/bin/sh

kubectl delete deployment microservice-vote-service
kubectl delete service microservice-vote-service
kubectl delete persistentvolume cloudant-pvc
kubectl delete secret cloudant-secret
kubectl delete configmap vappdown
kubectl delete ingresses.extension microservice-vote-ingress
kubectl delete deployments.extension microservice-vote-deployment
helm delete vote
helm del --purge vote
