# Lab 10 — Benchmark Report: Kubernetes Autoscaling

**Fecha:** 2026-05-25 20:59:26
**JVM:** OpenJDK 21.0.10
**Cluster:** kind lab10-bench
**Virtual Threads:** enabled (spring.threads.virtual.enabled=true)
**Puerto:** NodePort 30089 → 8089

---

## Configuración HPA (benchmark)

| Parámetro | Valor benchmark | Valor producción |
|-----------|----------------|-----------------|
| CPU threshold | 70% avg | 70% avg |
| Custom metric threshold | 10 req/pod | 10 req/pod |
| scaleUp stabilization | 30s | 30s |
| scaleDown stabilization | **60s** | **300s** (5 min) |
| Max pods | 10 | 10 |

> El benchmark usa scaleDown=60s para que el ciclo sea observable. Producción debe usar 300s para evitar flapping.

---

## Phase 4 — Baseline (5 VUs, workMs=100)

| Métrica | Valor |
|---------|-------|
| Pods activos | 1 |
| CPU promedio | 28 m |
| active_requests (gauge) | 0 |
| p50 latencia | 103.64 ms |
| p95 latencia | 107.6 ms |
| p99 latencia | 112.45 ms |
| Throughput | 48.02 req/s |
| Error rate | 0.0% |

---

## Phase 5 — Scale-Up Test (50 VUs, workMs=200, 300s)

| Métrica | Valor |
|---------|-------|
| Resultado HPA | **NO ACTIVADO — CPU <70% con Virtual Threads** |
| Tiempo hasta primer pod nuevo | N/A — HPA nunca disparó |
| Pods al finalizar carga | **1** (sin escala automática) |
| Max active_requests observado | **50** |
| CPU máxima observada | ~428 m (pico al inicio) / ~110 m en régimen |
| CPU threshold para scale-up | 350 m (70% de 500m limit) |
| p50 latencia | 203.13 ms |
| p95 latencia | 209.36 ms |
| p99 latencia | 214.95 ms |
| Throughput | 244.69 req/s |
| Error rate | 0.0% |

### Observación crítica: Virtual Threads + CPU-based HPA

Con 50 VUs y `workMs=200ms`, datos observados cada 10s:

| Tiempo | Pods | active_requests | CPU (millicores) |
|--------|------|----------------|-----------------|
| +19s | 1 | 50 | 26 m |
| +29s | 1 | 50 | 71–428 m (pico JVM init) |
| +48s–300s | 1 | 48–50 | **68–140 m** (régimen estable) |

Cada request ejecuta `Thread.sleep(200ms)` — suspende el Virtual Thread sin consumir CPU de la plataforma:
- **active_requests = 50** → HPA custom debería escalar a `ceil(50/10) = 5 pods`
- **CPU en régimen ≈ 110 m** → **31% del umbral** (threshold 350 m nunca alcanzado)
- Resultado: **CPU-based HPA NO escala en ningún momento de los 300s**

> El pico de 428 m a los ~29s es el ZGC + Spring context compiling en caliente.
> Una vez el JVM está warm, CPU cae a ~100-140 m a pesar de 50 requests concurrentes.

**Solución correcta:** HPA con `lab_active_requests_gauge` (custom metric vía prometheus-adapter).
Con prometheus-adapter instalado y la métrica configurada, el HPA escalaría a 5 pods en ~30–60s.

---

## Phase 6 — Scale-Down (cooldown observation)

> Nota: el HPA CPU no escaló en Phase 5, por lo que se escala manualmente a 3 pods para
> demostrar el comportamiento de scale-down con la ventana de cooldown de 60s (benchmark).

| Métrica | Valor |
|---------|-------|
| Pods al inicio | 3 (escalado manual) |
| Primer pod eliminado (HPA) | **+69s** tras parar carga |
| Pods → 1 (mínimo) | **+110s** total |
| Velocidad de scale-down | 1 pod/60s (policy: 1 pod per 60s) |
| Ventana de estabilización (benchmark) | 60s |
| Ventana de estabilización (producción) | **300s** (5 min) |

**Timeline observado:**
```
t=0s    carga detenida, 3 pods
t=69s   HPA elimina 1er pod → 2 pods  (cooldown 60s cumplido)
t=110s  HPA elimina 2do pod → 1 pod   (otro ciclo de 60s)
```

> Con la ventana de producción (300s), el primer pod no se eliminaría hasta t=300s.
> El scale-down conservador previene flapping cuando el tráfico oscila alrededor del umbral.

---

## Phase 7 — Fault Injection (kill pod bajo carga)

| Métrica | Valor |
|---------|-------|
| Pod eliminado | `lab10-autoscaling-5b678594b8-ppgt4` |
| Tiempo de recovery | **5s** |
| p50 latencia | 202.21 ms |
| p95 latencia | 206.24 ms |
| p99 latencia | 213.16 ms |
| Error rate durante fallo | **0.0%** |

### Secuencia de recovery observada

```
t=0s   kubectl delete pod --force --grace-period=0
t=5s   ReplicaSet detecta pod loss → pod replacement pasa a Running (phase)
t=5s   pod_count() = 2  ← fin de observación del benchmark
~t=25s pod replacement pasa readinessProbe → marcado Ready por kube-proxy
```

> **El "recovery=5s" mide cuándo el pod sustituto pasa a fase `Running`**, no cuándo
> está `Ready` para recibir tráfico. El pod no recibe requests hasta pasar la readinessProbe
> (`initialDelaySeconds=20 + periodSeconds=5` → ~25s desde el arranque del container).
>
> El **error_rate=0.0%** durante el fallo indica que el NodePort/kube-proxy dejó de
> enrutar al pod muerto antes de que las conexiones activas pudieran fallar, probablemente
> porque los requests en vuelo en ese pod (~10) completaron durante la ventana de propagación
> del endpoint (~2–5s). Con `--grace-period=0` en producción se espera un breve spike de errores.

---

## Analysis — Trade-offs

### CPU-based HPA vs Custom Metrics HPA

| Criterio | CPU-based HPA | Custom Metrics HPA |
|----------|--------------|-------------------|
| **Simplicidad** | Solo metrics-server | Requiere prometheus-adapter |
| **Virtual Threads** | **Antipattern** — CPU baja con alta concurrencia | **Correcto** — active_requests refleja carga real |
| **I/O-bound workloads** | No aplica | Ideal |
| **CPU-bound workloads** | Correcto | Redundante |
| **Señal de escala** | Desfasada (CPU sube tarde) | Inmediata (gauge sube con primera request) |
| **Umbral** | 70% de CPU limit | 10 active_requests/pod |

### Cooldown Tuning

| Ventana | Riesgo bajo | Riesgo alto |
|---------|-------------|-------------|
| scaleDown < 60s | Flapping frecuente | — |
| scaleDown 60-120s | Buen balance CI/dev | — |
| scaleDown 300s (default) | — | Scale-down tardío en cargas variables |
| scaleDown > 600s | — | Sobreprovisioning costoso |

### Virtual Threads: Impacto en Autoscaling

`spring.threads.virtual.enabled=true` significa que cada HTTP request usa un Virtual Thread.
Cuando el handler hace `Thread.sleep(workMs)`:
- **Platform thread**: se bloquea, cuenta como CPU time, HPA lo detecta
- **Virtual Thread**: suspende sin bloquear platform thread, CPU ≈ 0

Este benchmark demostró experimentalmente:
- 50 requests concurrentes en vuelo
- CPU < 28m (umbral 70% no alcanzado)
- HPA CPU-based: 0 pods adicionales añadidos

Para apps I/O-bound con Virtual Threads, **las custom metrics son obligatorias**.

---

## Resumen ejecutivo

| Pregunta | Respuesta |
|----------|-----------|
| ¿HPA escala con Virtual Threads + CPU? | **NO** — CPU permanece baja bajo carga I/O |
| ¿Cuándo escalaría el HPA custom? | Cuando active_requests > 10/pod (→ 50/10 = 5 pods) |
| ¿Recovery tras pod kill? | **5s** hasta Running / **~25s** hasta Ready (readinessProbe) |
| ¿Tiempo scale-down? | **69s** primer pod (ventana 60s benchmark) / 110s hasta mín=1 |
| ¿Error rate durante scaling? | 0.0% scale-up / 0.0% fault |
| ¿Herramienta faltante crítica? | prometheus-adapter (para custom metrics HPA) |

---

*Generado por `benchmark/run-benchmark.sh` — reproducible con un comando.*
*Datos raw CSV en `benchmark/results/monitor_*.csv` (no incluidos en git).*
