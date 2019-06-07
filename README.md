
![Overview](images/diagram_general.png)

# MicroProfile Lab with Open Liberty and Minikube

This lab illustrates steps to deploy a MicroProfile application, running in a Open Liberty Docker container into [Minikube](https://kubernetes.io/docs/setup/minikube/)

If you find an issue with the lab instruction you can [report it](https://github.com/microservices-api/kubernetes-microprofile-lab/issues) or better yet, [submit a PR](https://github.com/microservices-api/kubernetes-microprofile-lab/pulls).

For questions/comments about Liberty's Docker container or Helm charts please email [Arthur De Magalhaes](mailto:arthurdm@ca.ibm.com).

# Before you begin

You'll need a few different artifacts to this lab.  Check if you have these installed by running:

```
git --help
mvn --help
java -help
docker --help
kubectl --help
helm --help
minikube --help
```

If any of these is not installed:

* Install [Git client](https://git-scm.com/download/mac)
* Install [Maven](https://maven.apache.org/download.cgi)
* Install [Docker engine](https://docs.docker.com/engine/installation/)
* Install [Java 8](https://java.com/en/download/)
* Install [kubectl](https://www.ibm.com/support/knowledgecenter/SSBS6K_3.1.0/manage_cluster/cfc_cli.html)
* Install [helm](https://www.ibm.com/support/knowledgecenter/SSBS6K_3.1.0/app_center/create_helm_cli.html)
* Install [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/)


# Deploying a MicroProfile application in a Minikube cluster

This lab will walk you through the deployment of our sample MicroProfile Application into a Minikube cluster, which is built on the open source Kubernetes framework. You'll build a MicroProfile application and package it inside a Open Liberty Docker container. You will then utilize a Helm chart that deploys the Liberty container in ICP, with the appropriate service setup, while also deploying and configuring a CouchDB Helm chart that stands up the database that holds the data for this microservice.

## Setting up the cluster

1. Download and setup minikube
1. Start minikube by running `minikube start`
1. Enable ingress with the command `minikube addons enable ingress`
1. Set the Docker CLI to target the minikube Docker engine by running `eval $(minikube docker-env)`
1. Set up helm and tiller by running `helm init`
1. Wait until the following command indicates that the tiller-deploy deployment is available: `kubectl get deployment tiller-deploy --namespace kube-system`   (Note: This could take a few minutes)

## Part 1A: Build the application and Docker container

### Vote Microservice

The vote microservice stores feedback from the sessions and displays how well all sessions were liked in a pie chart.  If the vote service is configured (via server.xml) to connect to a CouchDB database, the votes will be persisted. Otherwise, the vote data will simply be stored in-memory. This sample application is one of the MicroProfile [showcase](https://github.com/eclipse/microprofile-conference/tree/master/microservice-vote) applications.

You can clone the lab artifacts and explore the application:

1. Clone the project into your machine. This is already done on the laptop provided for you in this workshop, so you can skip this step. The cloned folder is under the Home directory (shortcut is on the desktop).
    ```bash
    git clone https://github.com/microservices-api/kubernetes-microprofile-lab.git
    ```
1. Navigate into the sample application directory:
    ```bash
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

By now you should have a general understanding about the application. Now you will see how you can package the sample application into a Docker container by using a Dockerfile that contains instructions on how the image is built.

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
1. Build and tag the Enterprise Docker image:
    ```bash
    cd ..
    docker build -t microservice-enterprise-web:1.0.0  -f EnterpriseDockerfile .
    ```
1. Build and tag the Application Docker image:
    ```bash
    docker build -t microservice-vote:1.0.0  -f ApplicationDockerfile .
    ```
1. You can use the Docker CLI to verify that your image is built.  Remember that this is querying Minikube's internal Docker registry.
    ```bash
    docker images
    ```


## Part 2: Deploy Liberty and CouchDB Helm charts

You can use ICP Dashboard to deploy Helm charts into your Kubernetes cluster through the Catalog page. In addition, you can use Helm command line tool to install a Helm chart which we will do in this part of the lab.

First, let's see what are **Helm charts**. Helm is a package manager for Kubernetes (analogous to `yum` and `apt`). You can use it for managing Kubernetes charts (analogous to `debs` and `rpms`), which are packages of pre-configured Kubernetes resources. Instead of running a bunch of commands or maintaining multiple configuration files to create Kubernetes resources, Helm packages all the resources required to successfully run a service or multiple dependent services in one chart.

Now let's deploy our workload using Helm charts.

### Deploy CouchDB
1. Deploy the CouchDB Helm chart:
    ```bash
    cd helm/database
    helm repo add incubator https://kubernetes-charts-incubator.storage.googleapis.com/
    helm install incubator/couchdb -f db_values.yaml --name couchdb --tls
    ```
    Ensure the CouchDB pod is up and running by executing `kubectl get pods` command. Your output will look similar to the following:
     ```bash
    NAME                                        READY   STATUS    RESTARTS   AGE
    couchdb-couchdb-0                           2/2     Running   0          3m
    ```
    
    You need to wait until the value under `READY` column becomes `2/2`. Re-run the `kubectl get pods` command if necessary.

### Deploy Liberty    
1. Deploy the microservice using the Open Liberty Helm chart:
    ```bash
    cd ../application
    helm repo add ibm-charts https://raw.githubusercontent.com/IBM/charts/master/repo/stable/
    helm install ibm-charts/ibm-open-liberty -f app_overrides.yaml -f enterprise_overrides.yaml --set image.repository=mycluster.icp:8500/<NAMESPACE>/microservice-vote --name vote-<NAMESPACE> --tls
    ```
1. You can view the status of your deployment by running `kubectl get deployments`.  You want to wait until the `microservice-vote-deployment`deployment is available.
1. Use `kubectl get ing | awk 'FNR == 2 {print $3;}'` to determine the address of the application. Note: If the previous command is printing out a port, such as `80`, please wait a few more minutes for the `URL` to be available.  
1. Add `/openapi/ui` to the end of URL to reach the OpenAPI User Interface. For example, `https://<IP>:<PORT>/openapi/ui`.
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
    helm upgrade --tls --recreate-pods --force --reuse-values --set persistentVolume.enabled=true couchdb incubator/couchdb
    ```
1. Let's also upgrade the Liberty release for high availability by increasing the number of replicas:
    ```bash
    helm upgrade --tls --recreate-pods --force --reuse-values --set replicaCount=2 vote-<NAMESPACE> ibm-charts/ibm-open-liberty
    ```
1. List the deployed packages with their chart versions by running:
    ```bash
    helm ls --namespace <NAMESPACE> --tls
    ```
    You can see that the number of revision should be 2 now for couchdb and Liberty.
1. Run the following command to see the state of deployments:
    ```bash
    kubectl get pods
    ```
    You need to wait until the couchdb and Liberty pods become ready. The old pods may be terminating while the new ones start up.

    For Liberty, you will now see 2 pods, since we increased the number of replicas.
1. Refresh the page. You may need to add the security exception again. If you get `Failed to load API defintion` message then try refreshing again. 
1. Now add a new attendee through the OpenAPI UI as before.
1. Now repeat Steps 1-5 in this section to see that even though you delete the couchdb database container, data still gets recovered from the PersistentVolume.

In this part you were introduced to rolling updates. DevOps teams can perform zero-downtime application upgrades, which is an important consideration for production environments.

Congratulations! You finished the lab! You got to use a few powerful tools to interact with Kubernetes to deploy a microservice into IBM Cloud Private. Although this lab is finished but the journey to Kubernetes should not end here! Head to the [Learn more](#learn-more) section to see other great resources.
