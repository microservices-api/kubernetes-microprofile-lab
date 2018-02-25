# Deploying the sample directly to a local minikube installation

This is the simplest way for a developer to get the sample up and running locally.

## Before you begin

* Install a Git client to obtain the sample code.
* Install [Maven](https://maven.apache.org/download.cgi) and a Java 8 JDK.
* Install a [Docker](https://docs.docker.com/engine/installation/) engine.

## Install the Microservice Builder Sample application

1. Install minikube and the Microservice Builder fabric as described in [Running Kubernetes in your development environment](https://www.ibm.com/support/knowledgecenter/SS5PWC/setup.html).
1. Install the [Microservice Builder ELK Sample](https://github.com/WASdev/sample.microservicebuilder.helm.elk) - Elasticsearch, Logstash, and Kibana stack used for monitoring metrics.
1. Enable ingress with the command `minikube addons enable ingress`.
1. `git clone` the following project [sample.microservicebuilder.vote](https://github.com/WASdev/sample.microservicebuilder.vote).
1. Run `mvn clean package` sample.microservicebuilder.vote
1. If you have not done so already, ensure that your Docker CLI is targeting the minikube Docker engine with `minikube docker-env`.
1. `docker build -t microservice-vote .`
1. Deploy the microservice from its root directory with the following helm install command `helm install --name=vote chart/microservice-vote`.
1. Use `kubectl get ing` to determine the address of the `web-application-ingress`. Open this location in a web browser to access the sample. 

## Modifying the sample

Once you've made changes to the source code you'll want to rebuild and redeploy the new modules. See [this topic](updating_the_app.md) for some notes about redeployment. You can also explore adding [security](adding_security.md) to the application with OpenID Connect and JSON Web Tokens (JWT).
