# Resource type in Kubernetes

# Namespace

Provides a way to divide resources in a cluster. By default, namespaces do not acts as a network/security boundary.

There are 4 initial default namespaces in k8s: *default, kube-system, kube-node-lease, kube-public.*

Defining a namespace `kubectl create namespace <value>`

`kubectl create namespace define-namespace-cli`

Declaring a namespace

`declare-namespace.yaml`

```yaml
apiVersion: v1
kind: Namespace
metadata:
	name: declare-namespace
```

Create a namespace using this declaration

`kubectl apply -f /first_namespace.yaml`

Delete a namespace stuck in Terminating state

`kubectl get ns <NAME_OF_NAMESPACE> -o json | jq '.spec.finalizers = []' | kubectl replace --raw "/api/v1/namespaces/<NAME_OF_NAMESPACE>/finalize" -f -`

# Pod

Smallest unit of K8s. Pods are Abstraction over containers. Usual configuration is 1 containerized application per pod. Each pod gets its own IP Address.

You almost never creates a pod directly.

Containers within a pod share networking and storage.

![image.png](Resource%20type%20in%20Kubernetes/image.png)

There are many configuration available on Pod resource

- Listening ports
- Health probes
- Resource requests/limits
- Security Context
- Environment variable
- Volumes
- DNS Policies

Define a pod `kubectl run --image=<image name> -n <target namespace> <pod name>`

`kubectl run --image=nginx:1.26.0 -n declare-namespace created-the-wrong-way`

Declaring a pod

`pod_nginx_minimal.yaml`

```yaml
apiVersion: v1
kind: Pod
metadata:
	name: nginx-minimal
spec:
	containers:
		- name: nginx
			image: nginx:1.26.0
```

Create a pod using this declaration

`Kubectl apply -n <target namespace> -f /pod_nginx_minimal.yaml`

Better configuration means better control on Pod

`pod_nginx_better.yaml`

```yaml
apiVersion: v1
kind: Pod
metadata:
	name: nginx-better
	namspace: declare-namespace
spec:
	containers:
		- name: nginx
			image: cgr.dev/chainguard/nginx:latest
			ports:
				- containerPort: 8080
					protocol: TCP
				readinessProbe: # Check if the container is ready to serve traffic
					httpGet: # Perform an HTTP GET request
	         path: / # Path to access on the HTTP server
	         port: 8080 # Port to access on the container
				resources:
					limits:
						memory: "50Mi"
					requests:
						memory: "50Mi"
						cpu: "250m"
				securityContext:
					allowPrivilegeEscalation: false
					privileged: false
	securityContext:
		seccompProfile:
			type: RuntimeDefault
		runAsUser: 1001
		runAsGroup: 1001
		runAsNonRoot: true
```

Access pod in browser

`kubectl port-forward -n declare-namespace nginx-better 8080:8080`

Deleting a namespace deletes all defined resource within it.

`kubectl delete -f declare-namespace.yaml`

# ReplicaSet

Adds the concept of “replicas”. You will almost never create a ReplicaSet directly. **Labels** are the link between ReplicaSet and Pods for identifying the count of active Pods and spin up new ones on deviation in `replicas` and active count.

Minimal configuration for defining a ReplicaSet

`ReplicaSet.nginx-minimal.yaml`

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: nginx-minimal
spec:
  replicas: 3 # Number of desired pods to keep running at all times. 
              # If the number of pods is less than this number, new pods will be created. 
              # ReplicaSet will create new pods until the number of pods is equal to the number specified in the replicas field.
  selector:
    matchLabels:
      app: nginx-minimal
  template:
    metadata:
      labels:
        app: nginx-minimal
    spec:
      containers:
        - name: nginx
          image: nginx:1.26.0
```

Test ReplicaSet Controller

We get these pods after applying above configuration

`kubectl apply -f ReplicaSet.nginx-minimal.yaml`

Get active ReplicaSet 

`kubectl get rs`

![image.png](Resource%20type%20in%20Kubernetes/image%201.png)

![image.png](Resource%20type%20in%20Kubernetes/image%202.png)

Deleting a pod, ReplicaSet controller will initiate a new pod using this configuration.

`kubectl delete pod nginx-minimal-9wb46`

And now check active pods, notice deleted pod is replaced by a new one.

![image.png](Resource%20type%20in%20Kubernetes/image%203.png)

Pods naming convention in ReplicaSet

*<labels>**-**<5 digit random hash>*

# Deployment

Providing features such as rolling updates and rollbacks. Updating defined configurations will not trigger a restart on ReplicaSet or Pod.

So to create a concept of rollout and rollback, we layer the ReplicaSet with Deployment.

Minimal configuration for a Deployment resource

`Deployment.nginx-minimal.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment # Only diffrence from a ReplicaSet resource
metadata:
  name: nginx-minimal
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx-minimal
  template:
    metadata:
      labels:
        app: nginx-minimal
    spec:
      containers:
        - name: nginx
          image: nginx:1.26.0
```

Create this deployment

`kubectl apply -f Deployment.nginx-minimal.yaml`

Get created deployments

`kubectl get deployments`

Get details of Deployment

`kubectl describe deployment nginx-minimal`

*Initial state*

![image.png](Resource%20type%20in%20Kubernetes/image%204.png)

Rollout update in deployment definition with

`kubectl restart rollout deployment nginx-minimal`

Note, we have a backup last active ReplicaSet, in case we need to rollback the release.

Check rollout status:

`kubectl rollout status deployment/nginx-minimal`

Update image tag on a running deployment:

`kubectl set image deployment/nginx-minimal nginx=nginx:1.27.0`

Get revisions on a deployment

`kubectl rollout history deployment/nginx-minimal`

*Rollout state*

![image.png](Resource%20type%20in%20Kubernetes/image%205.png)

# Service

How should we serve the user traffic to the applications.

We use **Service** resource that serves as a internal load balancer between replicas. 

Types:

**ClusterIP:** This is Default type of a service. Accessible only within the cluster. It is going to provide a stable IP address, that will route traffic to any number of replicas containing appropriate labels.

**NodePort:** It is going to ****listen to each node within the cluster, such that you can route the traffic from outside the cluster into the cluster. 

LoadBalancer: Will use cloud controller manager component of Kubernetes to talk to cloud API of cloud service provider and provision a load balancer within their system. That load balancer will be used to bring traffic into cluster.

Get service along with their selector

`kubectl get service -o wide`

![image.png](Resource%20type%20in%20Kubernetes/image%206.png)

`Deployment`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-minimal
  namespace: 04--service
spec:
  replicas: 3
  selector:
    matchLabels:
      baz: pod-label
    spec:
      containers:
        - name: nginx
          image: nginx:1.26.0
```

`clusterIp`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-clusterip 
spec:
  type: ClusterIP # This is the default value
  selector:
    app: pod-label
  ports:
    - protocol: TCP
      port: 80 # Port the service is listening on
      targetPort: 80 # Port the container is listening on (if unset, defaults to equal port value)
```

`NodePort`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-nodeport
spec:
  type: NodePort
  selector:
    app: pod-label
  ports:
    - protocol: TCP
      port: 80 # Port the service is listening on
      targetPort: 80 # Port the container is listening on (if unset, defaults to equal port value)
      # nodePort: 30XXX (if unset, kubernetes will assign a port within 30000-32767)
```

`LoadBalancer`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-loadbalancer
spec:
  type: LoadBalancer # Will only work if cluster is configured to provision one from an external source (e.g. cloud provider)
  selector:
    app: pod-label
  ports:
    - protocol: TCP
      port: 80 # Port the service is listening on
      targetPort: 80 # Port the container is listening on (if unset, defaults to equal port value)
```

## Testing Services

- Use `ClusterIP` service to access its managed pod. Service and pods are connected by `selector` attribute.
    1. create a curl pod within `04--service` namespace and access it shell instance `kubectl run curl-pod -it -n 04--service --rm --image=curlimages/curl --command -- sh`
    2. within shell run `curl nginx-clusterip:80`
    
    Result will be a response from nginx server.
    
- Access the `ClusterIP` service outside of namespace.
    1. create a curl pod in `default` namespace and access it shell instance `kubectl run curl-pod -it -n default --rm --image=curlimages/curl --command -- sh`
    2. within shell run `curl nginx-clusterip:80`, a 404 response is expected to happen.
    3. To access a service outside namespace, use full fledged name `<service-name>.<namespace>.svc.cluster.local`
    4. within shell run `curl nginx-clusterip.04--service.svc.cluster.local`
    
    Result will be a response from nginx server.
    
- Access `NodePort` service
    1. `NodePort` is networked to access from outside of cluster. We will use port-forward command to access `NodePort` service.
    2. Use this command to forward calls on 8080 port on host machine to 80 port of `nginx-nodeport` service `kubectl port-forward service/nginx-nodeport 8080:80`
    3. Curl from host machine, to access application.

# Job

Adds concept of tracking “completions”. As we have seen Deployment is a state-less long running process, but to have a one or more completions for a particular container, we use Job.

Get Jobs

`kubectl get jobs`

Get explanation on template attribute

`kubectl explain job.spec.backoffLimit`

*Specifies the number of retries before marking this job failed. Defaults to
6*

Get logs of a pod

`kubectl log <pod-name>`

All this Pod do is issue current date-time to standard output.

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: echo-date-minimal
spec:
  template:
    spec:
      containers:
        - name: echo
          image: busybox:1.36.1
          command: ["date"]
      restartPolicy: Never
  backoffLimit: 1
```

# CronJob

Adds concept of “scheduling” a Job.

An online tool to create value for schedule attribute. https://crontab.guru/

A Job run and execute on its creation, however, a CronJob executes on scheduled time.

Create this CronJob: `kubectl apply -f CronJob.echo-date-minimal.yaml`

After a minutes, we can check if a CronJob have been executed.

`kubectl get jobs`

While debugging, it can be tedious to wait for a scheduled time, to check if configuration correct. We can create a job out of CronJob configuration.

`kubectl create job --from=cronjob/<existing-cronjob-name> <job-name>`

`kubectl create job --from=cronjob/echo-date-minimal manually-triggered`

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: echo-date-minimal
spec:
  schedule: "* * * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: echo
              image: busybox:1.36.1
              command: ["date"]
          restartPolicy: Never
      backoffLimit: 1

```

# DaemonSet

*Intent: Another type of application we want to run on to cluster is having an instance on each of different node. Things like cluster storage daemon, monitoring the node or log aggregations actions can be performed by this type of resource.*

By default runs an instance of specified pod on all nodes in the cluster, except for control plane node. We can modify the configuration to run instance on a subset of worker node or control plane node also.

Apply this DaemonSet configuration

`kubectl apply -f DaemonSet.fluentd-minimal.yaml`

Get details on created pods along with nodes `kubectl get pods -o wide`

```yaml
NAME                    READY   STATUS    RESTARTS   AGE   IP           NODE           NOMINATED NODE   READINESS GATES
fluentd-minimal-6pr2r   1/1     Running   0          21s   10.244.2.9   kind-worker    <none>           <none>
fluentd-minimal-9nclv   1/1     Running   0          21s   10.244.1.9   kind-worker2   <none>           <none>
```

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentd-minimal
  namespace: 04--daemonset
spec:
  selector:
    matchLabels:
      app: fluentd
  template:
    metadata:
      labels:
        app: fluentd
    spec:
      containers:
        - name: fluentd
          image: fluentd:v1.16-1
```

# StatefulSet

Similar to Deployment, except:

- Pods get sticky identity (pod-0, pod-1, etc..).
- Each pod mount separate volumes, however, in case of deployment we will get persistence volume per replica.
- Rollout behaviour is ordered.

Enables configuring workloads that requires state management (e.g. primary and read-replica database)

We can use this type of workload, when we want to maintain state in between replicas. 

In this example, we are using a concept of init container. Init container will run a bash script, that stores a HTML file onto a path in the file system. This file system is shared volume between init and primary container.

By doing this, the init container will store the HTML snippet containing the ordinal number of the pod, into volume. When primary container comes up, it will load that HTML file.

This example uses $HOSTNAME env variable to concatenate replica ordinal value into HTML snippet. This ordinal value changes per replica, so we get unique HTML response per replica connection.

One example is loading one replica as MySQL DB as Primary and other replicas as read-only replicas.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginxs # Plural because it sets up DNS for each replica in the StatefulSet (e.g. nginx-0.nginxs.default.svc.cluster.local)
spec:
  type: ClusterIP
  clusterIP: None # This makes it a "headless" service
  selector:
    app: nginx
  ports:
    - protocol: TCP
      port: 80
      targetPort: 80
```

Each StatefulSet replica, is expected to have a headless service attached to it. By setting `clusterIP: None`, Kubernetes will not create an internal IP address that load-balances across resources, instead we will able to address each pod via DNS.

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nginx-with-init-conainer
spec:
  serviceName: nginxs
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      initContainers:
        - name: populate-default-html
          image: nginx:1.26.0
          # Nginx is a silly example to use for a stateful application (you should use a deployment for nginx)
          # but this demonstrates how you can use an init container to pre-populate a pod specific config file
          # For example, you might configure a database StatefulSet with some pods having read/write access, and
          # others only providing read access.
          #
          # See: https://kubernetes.io/docs/tasks/run-application/run-replicated-stateful-application/
          command:
            - bash
            - "-c"
            - |
              set -ex
              [[ $HOSTNAME =~ -([0-9]+)$ ]] || exit 1
              ordinal=${BASH_REMATCH[1]}
              echo "<h1>Hello from pod $ordinal</h1>" >  /usr/share/nginx/html/index.html
          volumeMounts:
            - name: data
              mountPath: /usr/share/nginx/html
      containers:
        - name: nginx
          image: nginx:1.26.0
          volumeMounts:
            - name: data
              mountPath: /usr/share/nginx/html
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: "standard"
        resources:
          requests:
            storage: 100Mi
```

# ConfigMap

Enables environment specific configuration to be decoupled from container images

Two primary styles

- Property like (MY_ENV_VAR = “MY_VALUE”)
- File like (conf.yml = <multi-line string>)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: property-like-keys
data:
  NAME: YourAppName
  VERSION: 1.0.0
  AUTHOR: YourName
```

Get stored into environment of the container. To inspect a container environment variables:

`kubectl exec configmap-example -c nginx -- printenv`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: file-like-keys
data:
  conf.yml: |
    name: YourAppName
    version: 1.0.0
    author: YourName
```

Get stored into file system of attached volume. To concatenate content of config file of a container into shell

`kubectl exec configmap-example -c nginx -- cat /etc/config/conf.yml`

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: configmap-example
spec:
  containers:
    - name: nginx
      image: nginx:1.26.0
      volumeMounts:
        - name: configmap-file-like-keys
          mountPath: /etc/config
      envFrom:
        - configMapRef:
            name: property-like-keys
  volumes:
    - name: configmap-file-like-keys
      configMap:
        name: file-like-keys

```

# Secrets

Similar to ConfigMap with one main difference:

- Data is base64 encoded (this is to support binary data and is not a security mechanism)

Because they are separate resource type, they can be managed with specific authorization policies. 

`kubectl get secrets <secret-name>` 

`-o yaml` : need file format output

`| yq` : get colour formatted output

`'data.foo' | base64 -d` : get base64 decoded value of foo property.
`kubectl get secrets string-data -o yaml | yq '.data.foo' | base64 -d`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: string-data
type: Opaque
stringData:
  foo: bar
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: secret-example
spec:
  containers:
    - name: nginx
      image: nginx:1.26.0
      volumeMounts:
        - name: secret-base64-data
          mountPath: /etc/config
      env:
        - name: ENV_VAR_FROM_SECRET
          valueFrom:
            secretKeyRef:
              name: base64-data
              key: foo
  imagePullSecrets:
    # Not necessary since example uses a public image, but including to show how
    # you would use a registry credential secret to access a private image
    - name: dockerconfigjson
  volumes:
    - name: secret-base64-data
      secret:
        secretName: base64-data
```

# Ingress

*How to route traffic from outside the cluster into the cluster and hand over to various services.*

Enables routing traffic to many ClusterIP services via single external LoadBalancer.

Many implementations to chose from: Ingress-nginx, HAProxy, Kong, Istio, Traefik.

Only officially supports layer 7 routing (e.g. http, https) but some implementations allow for layer 4 routing (TCP, UDP) with additional configuration.

Get Ingress resources

`kubectl get ingress`

To install Ingress-Controller via helm

```yaml
helm upgrade --install ingress-nginx ingress-nginx \
          --repo https://kubernetes.github.io/ingress-nginx \
          --namespace ingress-nginx \
          --create-namespace \
          --version 4.10.1
```

![image.png](Resource%20type%20in%20Kubernetes/image%207.png)

Requests from Ingress controller comes to this Ingress resource → `Ingress.minimal-nginx.yaml` 

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: minimal-nginx
  # Can use
  # annotations:
  #   kubernetes.io/ingress.class: "nginx"
spec:
  ingressClassName: nginx
  rules:
    - host: "ingress-example-nginx.com"
      http:
        paths:
          - path: /
            pathType: ImplementationSpecific
            backend:
              service:
                name: nginx-clusterip # It is being used as a pointer to backend service.
                port:
                  number: 80

```

To `CluserIP` Service →

`Service.nginx-clusterip.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-clusterip
spec:
  type: ClusterIP # This is the default value
  selector:
    app: nginx-pod-label # This is selector for identifying replica set.
  ports:
    - protocol: TCP
      port: 80 # Port the service is listening on
      targetPort: 80 # Port the container is listening on (if unset, defaults to equal port value)

```

To Deployment

`Deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-minimal
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx-pod-label
  template:
    metadata:
      labels:
        app: nginx-pod-label
    spec:
      containers:
        - name: nginx
          image: nginx:1.26.0

```

# Persistence Volume & Persistence Volume Claim

**Kubernetes volumes**

Ephemeral Storage: Containers can use the `temporary filesystem` (tmpfs) to read and write files.

Ephemeral Volume: An ephemeral `Volume's` lifetime is coupled to the `Pod`. It enables safe container restarts and sharing of data between containers within a `Pod`.

![image.png](Resource%20type%20in%20Kubernetes/image%208.png)

Persistence Volume: K8s Provides API for creating, managing and consuming storage that lives beyond the live of an individual pod, or even cluster. A `PV` is cluster-wide: it can be attached to any Pod running on any Node in the cluster.

[AWS blog on Volumes](https://aws.amazon.com/blogs/storage/persistent-storage-for-kubernetes/#:~:text=Persistent%20volume%20claims&Kubernetes%20has%20an%20additional%20layer,%3A%20the%20PersistentVolumeClaim%20(PVC)).

![image.png](Resource%20type%20in%20Kubernetes/image%209.png)

A `PV` is an abstract component, and actual physical storage must come from somewhere. → `CSI`, `NFS`, `Local`

$Access Modes/ Node/ Time Unit.$

- ReadWriteOnce
- ReadOnlyMany
- ReadWriteMany

`Persistence Volume Claim` 

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: manual-pv-kind
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Mi
  selector:
    matchLabels:
      name: manual-kind
  storageClassName: standard
```

Get persisted volumes: `Kubectl get pv`

Get persisted volumes claims: `kubectl get pvc`

`Persistence Volume`

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: manual-kind-worker
  labels:
    name: manual-kind
spec:
  accessModes:
    - ReadWriteOnce
  capacity:
    storage: 100Mi
  storageClassName: standard
  local: # Backend of PV component 
    path: /some/path/in/container # Replace with the path to your local storage
  nodeAffinity:
    required:
      nodeSelectorTerms:
        - matchExpressions:
            - key: kubernetes.io/hostname
              operator: In
              values:
                - kind-worker
```

Pods consuming the Volume storage

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: manual-pv-and-pvc
spec:
  containers:
    - name: nginx
      image: nginx:1.26.0
      volumeMounts:
        - name: storage
          mountPath: /some/mount/path
  volumes:
    - name: storage
      persistentVolumeClaim:
        claimName: manual-pv-kind

```

**Persistence Volume Claim:** K8s has an additional layer of abstraction necessary for attaching `PV` to `Pod`. `PV` represents the actual storage volume, and the `PVC` represents the request for storage that a `Pod` makes. Kubernetes was built with the idea that a `PV` object should belong to the cluster administrator scope, whereas a `PVC` object belong to the application developer scope.
A `Pod` cannot mount a `PV` object directly, it needs to ask for storage through `PVC`. A `PVC` and `PV` have one-to-one mapping (a `PV` can only be associated with a single `PVC`)

In contrast between Deployment and StatefulSet, replicas of a deployments gets same mounted volume, but each StatefulSet gets a separate volume for each.

**PV Reclaim Policy**: Retain, Recycle and Delete (weather k8s should delete `PV` when `PVC` is deleted) default reclaim policy is "Delete". With the "Retain" policy, if a user deletes a `PVC`, the corresponding `PV` will not be deleted.

**PVC Retention Policy:** specifies what should happen to `PVC` when underlying consumers of `PVC` are scaled or deleted.

```yaml
apiVersion: apps/v1
kind: StatefulSet
...
spec:
persistentVolumeClaimRetentionPolicy:
	whenDeleted: Retain
	whenScaled: Delete
```

# RBAC (Service Account, Role, Role Binding)

- Provides app or users access to query Kubernetes APIs
- Access can be granted by namespace OR cluster wide

This job queries Kubernetes APIs to get all pods at cluster level, without proper access rights results in pod failures.

`k get pods`

```yaml
NAME                   READY   STATUS   RESTARTS   AGE
no-permissions-4krbg   0/1     Error    0          14s
```

`k logs no-permissions-4krbg`

```yaml
Error from server (Forbidden): pods is forbidden: User "system:serviceaccount:04--rbac:default" cannot list resource "pods" in API group "" at the cluster scope
```

`failed-Job-no-permission.yaml` 

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: no-permissions
spec:
  template:
    spec:
      containers:
        - name: kubectl
          image: cgr.dev/chainguard/kubectl
          args: ["get", "pods", "-A"]
      restartPolicy: Never # Note we did not mentioned any service account here
  backoffLimit: 1
```

This configurations allows a pod to `get`, `list`, `watch` pods within pod’s namespace, under condition that pod uses a service account `namespaced-pod-reader`.

1. `ServiceAccount`: Application Pods, system components, and entities inside and outside the cluster can use a specific `ServiceAccount` credentials to identify as authenticated worker, triggered by a workload or automation.
Each service account is bound to a Kubernetes namespace. Every namespace gets a `default` `ServiceAccount` upon creation.
2. `Role`: A Role sets permissions on namespaced resources (like Pods). When you create a Role, you have to specify the namespace it belongs in.
3. `Role Binding`: grants the permissions defined in a role to *subjects* (users, groups, or service accounts). A `RoleBinding` grants permissions within a specific namespace.
4. A worker consumes the `ServiceAccount` to get access rights of a `Role`

![image.png](Resource%20type%20in%20Kubernetes/image%2010.png)

In a similar context, `ClusterRole` and **`ClusterRoleBinding`** are resources that can be used to grant permission cluster wide resource (nodes, `/healthz` endpoint, Pods across all namespaces, secrets).

`pods` is the namespaced resource for Pod resources, and `log` is a subresource of `pods`. To represent this in an RBAC role, use a slash (`/`) to delimit the resource and subresource. To allow a subject to read `pods` and also access the `log` subresource for each of those Pods.

https://kubernetes.io/docs/reference/access-authn-authz/rbac/#clusterrolebinding-example

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
namespace: default
name: pod-and-pod-logs-reader
rules:
-apiGroups: [""]
resources: ["pods", "pods/log"]
verbs: ["get", "list"]
```

Some more built in resources

- LimitRange
- NetworkPolicy
- MutatingWebhookConfiguration
- ValidatingWebhookConfiguration
- HorizontalPodAutoscaler
- CustomResourceDefination