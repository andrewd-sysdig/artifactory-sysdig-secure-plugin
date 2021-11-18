Sysdig Artifactory Plugin - Admission Controller Scanning Service
===============================

The Sysdig Artifactory Plugin can utilise the Sysdig Admission Controller Scanning Service for local scanning.

Configuration
-----------------
1. Configure the Sysdig Admission Controller Scanning Service to be accessible by Artifactory
2. Optional: Sysdig Admission Controller Articatory Credentials

Sysdig Admission Controller Scanning Service
-----------------
Configuration the Sysdig Admission Controller Scanning Service with the below example service for external accessibility.

```yaml
apiVersion: v1
kind: Service
metadata:
  labels:
    app.kubernetes.io/component: scanner
    app.kubernetes.io/instance: sysdig-admission-controller
    app.kubernetes.io/name: admission-controller
  name: sysdig-admission-controller-scanner-external
  namespace: sysdig-admission-controller
spec:
  ports:
  - name: https
    port: 8080
    protocol: TCP
    targetPort: http
  selector:
    app.kubernetes.io/component: scanner
    app.kubernetes.io/instance: sysdig-admission-controller
    app.kubernetes.io/name: admission-controller
  type: LoadBalancer
```

Optional: Sysdig Admission Controller Articatory Credentials
-----------------
Configuration the Sysdig Admission Controller Scanning Service to have authenticated access to Artifactory.

Create secret with credentials using .dockercfg
```
kubectl -n sysdig-admission-controller create secret generic sysdig-admission-controller-registry-auth --from-file=.dockercfg
```



