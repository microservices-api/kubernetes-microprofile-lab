# MicroProfile Application Helm Chart

## Microservice - Vote

This helm chart deploys a sample MicroProfile Application called "Vote", which
illustrates a microservice that manages the votes pertaining to a conference session.

It is part of a larger set of microservices, which can be view [here](https://github.com/eclipse/microprofile-conference).

You can deploy this helm chart as-is, meaning that you do not need to override
any of the defaults.

The chart will:
*  Deploy a sub-chart that installs and configures a Cloudant pod.
*  Create the necessary config maps, secrets and persistent volumes.
*  Deploy a docker container containing WebSphere Liberty and the MicroProfile Vote application.

After deployment, you can interact with the application by running the built-in
MicroProfile OpenAPI User Interface, available at `https://<app-ingress-url>/openapi/ui`
