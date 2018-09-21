
## Step 1: Configure minikube

1. Start minikube by running `minikube start`
1. Enable ingress with the command `minikube addons enable ingress`
1. Set the Docker CLI to target the minikube Docker engine by running `eval $(minikube docker-env)`
1. Set up helm and tiller by running `helm init`
1. Wait until the following command indicates that the tiller-deploy deployment is available: `kubectl get deployment tiller-deploy --namespace kube-system`   (Note: This could take a few minutes)

## Step 2: Build application and container

1. Clone the project into your machine by running `git clone https://github.com/microservices-api/kubernetes-microprofile-lab.git`
1. Build the sample microservice by running `cd kubernetes-microprofile-lab/lab-artifacts` and then  `mvn clean package`
1. Build the docker image by running `docker build -t microservice-vote .`  This image is now availble in minikube's Docker registry. If you run `docker images` you will see all the images in your minikube's registry.

## Step 3: Deploy WebSphere Liberty and Cloudant helm chart

1. If using a lab machine, you may want to run the [cleanup script](#step-6-cleanup) before proceeding, to ensure you have a fresh environment.
1. Deploy the microservice with the following helm install command `helm install --name=vote helm-chart/microservice-vote`
1. You can view the status of your deployment by running `kubectl get deployments`.  You want to wait until both `microservice-vote-deployment` and `vote-ibm-cloudant-dev` deployments are available.
1. Use `kubectl get ing | awk 'FNR == 2 {print $3;}'` to determine the address of the application. Note: If the previous command is printing out a port, such as `80`, please wait a few more minutes for the `URL` to be available.  
1. Prepend `http` and append `/openapi/ui` to that URL and open this location in a web browser to access the application. For example, `http://192.168.99.100/openapi/ui`
1. Congratulations, you have successfully deployed a [MicroProfile](http://microprofile.io/) container into a kubernetes cluster!  The deployment also included a Cloudant container that is used by our microservice, and an ingress layer to provide connectivity into the API.

## Step 4: Explore the application
1. You can now explore and invoke the application the exact same way you did in the ICP environment as instructed in part 3.
*Note:*  If you find that your minikube ingress is taking too long to return the result of the invocation and you get a timeout error, you can bypass the ingress and reach the application via its NodePort layer.  To do that, simply find the NodePort port by running the command `kubectl describe service microservice-vote-service | grep NodePort | awk 'FNR == 2 {print $3;}' | awk -F '/' '{print $1;}'` and then inserting that port in your current URL using `http`, for example `http://192.168.99.100:30698/openapi/ui/`.  If those invocations are still taking long, please wait a few minutes for the deployment to fully initiate. 

## Step 5: Further exploration

1. If you want to update the application, you can change the source code and then run through the steps starting from `Build application and container`.  You'll notice that the OpenAPI UI will get automatically updated!
1.  After playing around with the application you can explore the helm chart to become more familiar with the way WebSphere Liberty is deployed and how it is integrated with the Cloudant subchart.
1.  You can also explore the official helm charts from IBM, available publicly at https://github.com/IBM/charts/tree/master/stable.  You will see there's an official version of the WebSphere Liberty and Open Liberty charts as well.  Try deploying these, along with other charts such as Db2.  
1. Now you have deployed the lab in your local minikube environment!


## Step 6: Cleanup
1. To cleanup the deployment and various related artifacts (configMaps, secrets, etc) from your minikube cluster, simply run `kubernetes-microprofile-lab/lab-artifacts/cleanup.sh`
