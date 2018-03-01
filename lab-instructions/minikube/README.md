# Deploying the sample directly to a local minikube installation

This is the simplest way for a developer to get the sample up and running locally.

## Before you begin

* Install a Git client to obtain the sample code.
* Install [Maven](https://maven.apache.org/download.cgi) and a Java 8 JDK.
* Install a [Docker](https://docs.docker.com/engine/installation/) engine.

## Install the Microservice Builder Sample application

1. Install minikube and the Microservice Builder fabric as described in [Running Kubernetes in your development environment](https://www.ibm.com/support/knowledgecenter/SS5PWC/setup.html).
1. Enable ingress with the command `minikube addons enable ingress`
1. Clone the project into your machine by running `git clone https://github.com/microservices-api/kubernetes-microprofile-lab.git`
1. Build the sample microservice by running `cd ubernetes-microprofile-lab/lab-artifacts` and then  `mvn clean package`
1. If you have not done so already, ensure that your Docker CLI is targeting the minikube Docker engine with `minikube docker-env`.
1. Build the docker image by running `docker build -t microservice-vote .`
1. Deploy the microservice with the following helm install command `helm install --name=vote helm-chart/microservice-vote`
1. Use `kubectl get ing` to determine the address of the `web-application-ingress`  Open this location in a web browser to access the sample. 
