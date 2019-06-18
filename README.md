
![Overview](images/diagram_general.png)

# MicroProfile Lab with Open Liberty and OKD

This lab illustrates steps to deploy a MicroProfile application, running in a Open Liberty Docker container into [OKD](https://www.okd.io) using Open Liberty Operator.

If you find an issue with the lab instruction you can [report it](https://github.com/microservices-api/kubernetes-microprofile-lab/issues) or better yet, [submit a PR](https://github.com/microservices-api/kubernetes-microprofile-lab/pulls).

For questions/comments about Open Liberty Docker container or Open Liberty Operator please email [Arthur De Magalhaes](mailto:arthurdm@ca.ibm.com).

# Before you begin

You'll need a few different artifacts to this lab.  Check if you have these installed by running:

```bash
git --help
mvn --help
java -help
docker --help
kubectl --help
oc --help
```

If any of these are not installed:

* Install [Git client](https://git-scm.com/download/mac)
* Install [Maven](https://maven.apache.org/download.cgi)
* Install [Docker engine](https://docs.docker.com/engine/installation/)
* Install [Java 8](https://java.com/en/download/)
* Install [kubectl](https://github.com/openshift/origin/releases/download/v3.11.0/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit.tar.gz)
* Install [oc](https://github.com/openshift/origin/releases/download/v3.11.0/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit.tar.gz)

## What is OKD?

From [okd.io](https://www.okd.io):
>OKD is a distribution of Kubernetes optimized for continuous application development and multi-tenant deployment. OKD adds developer and operations-centric tools on top of Kubernetes to enable rapid application development, easy deployment and scaling, and long-term lifecycle maintenance for small and large teams. OKD is the upstream Kubernetes distribution embedded in Red Hat OpenShift. OKD embeds Kubernetes and extends it with security and other integrated concepts. OKD is also referred to as Origin in github and in the documentation. An OKD release corresponds to the Kubernetes distribution - for example, OKD 1.10 includes Kubernetes 1.10. If you are looking for enterprise-level support, or information on partner certification, Red Hat also offers Red Hat OpenShift Container Platform.

## What are Operators?

From [Red Hat](https://www.redhat.com/en/blog/introducing-operator-framework-building-apps-kubernetes):
> An Operator is a method of packaging, deploying and managing a Kubernetes application. A Kubernetes application is an application that is both deployed on Kubernetes and managed using the Kubernetes APIs and kubectl tooling. To be able to make the most of Kubernetes, you need a set of cohesive APIs to extend in order to service and manage your applications that run on Kubernetes. You can think of Operators as the runtime that manages this type of application on Kubernetes.

# Deploying a MicroProfile application in an OKD cluster

This lab will walk you through the deployment of our sample MicroProfile Application into an OKD cluster. You'll build a MicroProfile application and package it inside a Open Liberty Docker container. You will then utilize an operator that deploys an Open Liberty container to OKD, with the appropriate service setup, while also deploying and configuring a CouchDB operator that stands up the a database that holds data for this microservice.

## Setting up the cluster

To install OKD on RHEL or CentOS, follow instructions describe [here](https://github.com/gshipley/installcentos#installation). Ensure SELinux is set to _permissive_.

## Part 1A: Build the application and Docker container

### Vote Microservice

The vote microservice stores feedback from the sessions and displays how well all sessions were liked in a pie chart.  If the vote service is configured (via `server.xml`) to connect to a CouchDB database, the votes will be persisted. Otherwise, the vote data will simply be stored in-memory. This sample application is one of the MicroProfile [showcase](https://github.com/eclipse/microprofile-conference/tree/master/microservice-vote) applications.

You can clone the lab artifacts and explore the application:

1. Clone the project into your machine.
    ```console
    git clone https://github.com/microservices-api/kubernetes-microprofile-lab.git
    ```
1. Navigate into the sample application directory:
    ```console
    cd kubernetes-microprofile-lab/lab-artifacts/application
    ```
1. See if you can find where technologies described below are used in the application.

### Usage of technologies

* **JAX-RS** is used to to define the endpoints for the application, and performs JSON data binding on incoming and outgoing JSON data so that the rest of the code can utilize the data as POJOs.

* **CDI** is used to instantiate data access objects (DAO's) and manage invocation of life cycle operations such as `@PostConstricut`.

* **JSON-P** is used to implement custom JAX-RS MessageBodyReader/Writer classes for binding between JSON and POJO.

* **MicroProfile Config** is used to inject CouchDB's URL, username and password to the application.

* **MicroProfile Fault-Tolerance** is used in the CouchAttendeeDAO and CouchSessionRatingDAO to:

  * impose timeouts on various operations using `@Timeout`.
  * automatically retry failed operations using `@Retry`.
  * limit the maximum resources allocated to parallel operations using `@Bulkhead`.

* **MicroProfile Health** is used to provide an UP/DOWN health check of the service.  The following health checks are implemented:

  * HashMapDAO to determine if the in-memory storage is accessible (which is always) and gives an example of an UP status.
  * CouchAttendeeDAO to determine if it can connect to the database backend.
  * CouchSessionDAO to determine if it can connect to the database backend.

* **MicroProfile Metrics** is used to gather metrics about the time it takes the HashMapDAO objects to complete their operations, and to keep a count of the amount of times each REST endpoint is requested.

### Dockerizing Vote Microservice

By now you should have a general understanding about the application. Now, you will see how you can package the sample application into a Docker container by using a Dockerfile that contains instructions on how the image is built.

In this lab we demonstrate a best-practice pattern which separates the concerns between the enterprise architect and the developer.  We first build a Docker image that will act as our `enterprise base image`, which in a company would be the shared curated image that all developers must start from - this allows for consistent and compliance across the enterprise.  We then build the developer's Docker image, which starts from the enterprise base image and adds only the application and related configuration.

The following steps will build the sample application and create a Docker image that includes the vote microservice:

1. Navigate into the sample application directory if you are not already:
    ```bash
    cd kubernetes-microprofile-lab/lab-artifacts/application
    ```
1. Build the sample application:
    ```bash
    mvn clean package
    ```
1. Navigate into the `lab-artifacts` directory
    ```bash
    cd ..
    ```
1. Build and tag the Enterprise Docker image:
    ```bash
    cd ..
    docker build -t microservice-enterprise-web:1.0.0  -f EnterpriseDockerfile .
    ```
1. Build and tag the Application Docker image:
    ```bash
    docker build -t microservice-vote:1.0.0  -f ApplicationDockerfile .
    ```
1. You can use the Docker CLI to verify that your image is built.
    ```bash
    docker images
    ```

## Part 1B: Upload the Docker image to OKD's internal registry

We will use OKD's internal Docker registry to host our image.

1. Ensure your `oc` client is logged into OKD. Replace `<USERNAME>`, `<PASSWORD>` and `<CLUSTER_IP>` with appropriate values:
    ```bash
    oc login --username=<USERNAME> --password=<PASSWORD> https://console.<CLUSTER_IP>.nip.io:8443
    ```
1. Create a new project in OKD which will host our application:
    ```bash
    oc new-project myproject
    ```
1. Log into the Docker registry:
    ```bash
    docker login -u $(oc whoami) -p $(oc whoami -t) docker-registry-default.apps.<CLUSTER_IP>.nip.io
    ```
1. Tag the Docker image:
    ```bash
    docker tag microservice-vote:1.0.0 docker-registry-default.apps.<CLUSTER_IP>.nip.io/microservice-vote:1.0.0
    ```
1. Now that you're logged in the registry, you can `docker push` your tagged image (`microservice-vote`) into the ICP Docker registry:
    ```bash
    docker push docker-registry-default.apps.<CLUSTER_IP>.nip.io/microservice-vote:1.0.0
    ```
1. Your image is now available in the Docker registry in OKD. You can verify this through the OKD's Registry Dashboard at `https://registry-console-default.apps.<CLUSTER_IP>.nip.io/registry`.

## Part 2: Deploy Liberty and CouchDB Operators

In this part of the lab you will use the Helm command line tool to install a Helm chart.

First, let's see what are **Helm charts**. Helm is a package manager for Kubernetes (analogous to `yum` and `apt`). You can use it for managing Kubernetes charts (analogous to `debs` and `rpms`), which are packages of pre-configured Kubernetes resources. Instead of running a bunch of commands or maintaining multiple configuration files to create Kubernetes resources, Helm packages all the resources required to successfully run a service or multiple dependent services in one chart.

Now let's deploy our workload using Helm charts.

### Deploy CouchDB

In this section we will deploy CouchDB Helm chart. OKD does not come with tiller. So we need to install tiller first.

1. Create a project for Tiller
    ```bash
    oc new-project tiller
    ```
    If you already have `tiller` project, switch to the project:
    ```bash
    oc project tiller
    ```
1. Download Helm CLI and install the Helm client locally:

    Linux:
    ```bash
    curl -s https://storage.googleapis.com/kubernetes-helm/helm-v2.14.1-linux-amd64.tar.gz | tar xz
    cd linux-amd64
    ```
    OSX:
    ```bash
    curl -s https://storage.googleapis.com/kubernetes-helm/ lm-v2.14.1-darwin-amd64.tar.gz | tar xz
    cd darwin-amd64
    ```

    Now configure the Helm client locally:
    ```bash
    sudo mv helm /usr/local/bin
    sudo chmod a+x /usr/local/bin/helm
    ./helm init --client-only
    ```
1. Install the Tiller server:
    ```bash
    oc process -f https://github.com/openshift/origin/raw/master/examples/helm/tiller-template.yaml -p TILLER_NAMESPACE="tiller" -p HELM_VERSION=v2.14.1 | oc create -f -
    oc rollout status deployment tiller
    ```
1. If things go well, the following commands should run successfully:
    ```bash
    helm version
    ```

Now that the Helm is configured locally and on OKD, you can deploy CouchDB Helm chart.
1. Navigate to `lab-artifacts/helm/database`:
    ```bash
    cd lab-artifacts/helm/database
    ```
1. Deploy the CouchDB Helm chart:
    ```bash
    helm repo add incubator https://kubernetes-charts-incubator.storage.googleapis.com/
    helm install incubator/couchdb -f db_values.yaml --name couchdb 
    ```
    Ensure the CouchDB pod is up and running by executing `kubectl get pods` command. Your output will look similar to the following:
     ```bash
    NAME                            READY   STATUS    RESTARTS   AGE
    couchdb-couchdb-0               2/2     Running   0          3m
    ```

    You need to wait until the value under `READY` column becomes `2/2`. Re-run the `kubectl get pods` command if necessary.

### Deploy Liberty

#### Install Open Liberty artifacts

1. Navigate to Open Liberty Operator artifact directory:
    ```bash
    cd lab-artifacts/operator/open-liberty-operator
    ```
1. Install Open Liberty Operator artifacts:
    ```bash
    kubectl apply -f olm/open-liberty-crd.yaml
    kubectl apply -f deploy/service_account.yaml
    kubectl apply -f deploy/role.yaml
    kubectl apply -f deploy/role_binding.yaml
    kubectl apply -f deploy/operator.yaml
    ```
1. Creating a custom Security Context Constraints (SCC). SCC controls the actions that a pod can perform and what it has the ability to access.
    ```bash
    kubectl apply -f deploy/ibm-open-liberty-scc.yaml --validate=false
    ```
1. Grant the default namespace's service account access to the newly created SCC, `ibm-open-liberty-scc`. Update `<namespace>` with the appropriate namespace:
    ```bash
    oc adm policy add-scc-to-group ibm-open-liberty-scc system:serviceaccounts:<namespace>
    ```

#### Deploy application

1. Deploy the microservice application using the provided CR:
    ```bash
    cd ../application
    kubectl apply -f application-cr.yaml
    ```
1. You can view the status of your deployment by running `kubectl get deployments`.  If the deployment is not coming up after a few minutes one way to debug what happened is to query the pods with `kubectl get pods` and then fetch the logs of the Liberty pod with `kubectl logs <pod>`.
1. Use `kubectl get ing | awk 'FNR == 2 {print $3;}'` to determine the address of the application. Note: If the previous command is printing out a port, such as `80`, please wait a few more minutes for the `URL` to be available.  
1. Add `/openapi/ui` to the end of URL to reach the OpenAPI User Interface. For example, `https://<IP>:<PORT>/openapi/ui`.
1. If you find that your minikube ingress is taking too long to return the result of the invocation and you get a timeout error, you can bypass the ingress and reach the application via its NodePort layer.  To do that, simply find the NodePort port by finding out your service name with `kubectl get services` and then running the command `kubectl describe service <myservice> | grep NodePort | awk 'FNR == 2 {print $3;}' | awk -F '/' '{print $1;}'` and then inserting that port in your current URL using `http`, for example `http://192.168.99.100:30698/openapi/ui/`.  If those invocations are still taking long, please wait a few minutes for the deployment to fully initiate. 
1. Congratulations! You have successfully deployed a [MicroProfile](http://microprofile.io/) container into a Kubernetes cluster!

## Part 3: Explore the application

The `vote` application is using various MicroProfile specifications.  The `/openapi` endpoint of the application exposes the [MicroProfile OpenAPI](http://download.eclipse.org/microprofile/microprofile-open-api-1.0.1/microprofile-openapi-spec.html) specification.  The `/openapi/ui` endpoint is a value-add from Liberty.  This UI allows developers and API consumers to invoke the API right from the browser!

1. Expand the `POST /attendee` endpoint and click the `Try it out` button.
1. Place your username (e.g. userX) in the `id` field, and place your name in the `name` field.
    ![image](images/post_screenshot.png)
1. Click on the `Execute` button.  Scroll down and you'll see the `curl` command that was used, the `Requested URL` and then details of the response.  This entry has now been saved into the CouchDB database that our microservice is using.
    ![image](images/post_result.png)
1. Now expand the `GET /attendee/{id}`, click the `Try it out` button, and type into the textbox the `id` you entered from the previous step.
1. Click on `Execute` and inspect that the `Respond body` contains the same name that you created in step 2. You successfully triggered a fetch from our microservice into the CouchDB database.
1. Feel free to explore the other APIs and play around with the microservice!

## Part 4: Update the Helm release

In this part of the lab you will practice how to make changes to the Helm release you just deployed on the cluster using the Helm CLI.

So far, the database you deployed stores the data inside the container running the database. This means if the container gets deleted or restarted for any reason, all the data stored in the database would be lost.

In order to store the data outside of the database container, you would need to enable data persistence through the Helm chart. When you enable persistence, the database would store the data in a PersistentVolume. A PersistentVolume (PV) is a piece of storage in the cluster that has been provisioned by an administrator or by an automatic provisioner.

The steps below would guide you how to enable persistence for your database:

1. In [Part 3](#Part-3-Explore-the-application), you would've observed that calling `GET /attendee/{id}` returns the `name` you specified. Calling `GET` would read the data from the database.
1. Find the name of the pod that is running the database container:
    ```bash
    kubectl get pods
    ```
    You should see a pod name similar to `couchdb-couchdb-0`.
1. Delete the CouchDB pod to delete the container running the database.
    ```bash
    kubectl delete pod couchdb-couchdb-0
    ```
1. Run the following command to see the state of deployments:
    ```bash
    kubectl get pods
    ```
    You should get an output similar to the following:
    ```bash
    NAME                                        READY   STATUS    RESTARTS   AGE
    couchdb-couchdb-0                           2/2     Running   0          3m
    vote-userx-ibm-open-5b44d988bd-kqrjn   1/1     Running   0          3m
    ```
    Again, you need to wait until the couchdb pod is ready. Wait until the value under `READY` column becomes `2/2`. 

1. Call again the `GET /attendee/{id}` endpoint from the OpenAPI UI page and see that the server does not return the attendee you created anymore. Instead, it returns 404. That's because the data was stored in the couchdb pod and was lost when the pod was deleted. Let's upgrade our release to add persistence.
1. Now let's enable persistence for our database:
    ```bash
    helm upgrade  --recreate-pods --force --reuse-values --set persistentVolume.enabled=true couchdb incubator/couchdb
    ```
1. Let's also upgrade the Liberty release for high availability by increasing the number of replicas:
    ```bash
    helm upgrade  --recreate-pods --force --reuse-values --set replicaCount=2 <release_name> ibm-charts/ibm-open-liberty
    ```
1. List the deployed packages with their chart versions by running:
    ```bash
    helm ls
    ```
    You can see that the number of revision should be 2 now for couchdb and Liberty.
1. Run the following command to see the state of deployments:
    ```bash
    kubectl get pods
    ```
    You need to wait until the couchdb and Liberty pods become ready. The old pods may be terminating while the new ones start up.

    For Liberty, you will now see 2 pods, since we increased the number of replicas.
1. Refresh the page. You may need to add the security exception again. If you get `Failed to load API definition` message then try refreshing again.
1. Now add a new attendee through the OpenAPI UI as before.
1. Now repeat Steps 1-5 in this section to see that even though you delete the couchdb database container, data still gets recovered from the PersistentVolume.

In this part you were introduced to rolling updates. DevOps teams can perform zero-downtime application upgrades, which is an important consideration for production environments.

Congratulations! You finished the lab! You got to use a few powerful tools to interact with Kubernetes to deploy a microservice into IBM Cloud Private. Although this lab is finished but the journey to Kubernetes should not end here! Head to the [Learn more](#learn-more) section to see other great resources.
