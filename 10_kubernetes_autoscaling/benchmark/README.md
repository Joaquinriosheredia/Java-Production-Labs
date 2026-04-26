# Benchmark — Lab 10: Kubernetes Autoscaling

## Metric

**Scale-up trigger time** and **request latency** during scale-up event.

---

## Local Test (without Kubernetes)

```bash
./mvnw spring-boot:run
bash benchmark/run-benchmark.sh
```

## Kubernetes Test

```bash
kubectl apply -f scripts/k8s/deployment.yml
kubectl apply -f scripts/k8s/hpa.yml

# Watch HPA
watch kubectl get hpa lab10-hpa

# Generate load
bash benchmark/run-benchmark.sh
```

---

## Expected Behavior

| Load | Active Requests/Pod | HPA Action |
|------|--------------------|-----------  |
| 5 VUs, workMs=100 | ~5 | No scale (< 10 threshold) |
| 20 VUs, workMs=200 | ~40 | Scale up: 4 pods needed |
| Load stops | Drains over 5min | Scale down to 1 pod |

---

## Probes

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Metrics: `/actuator/prometheus` → `lab_active_requests_gauge`
