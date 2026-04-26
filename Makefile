.PHONY: help test-all lab-01 lab-02 lab-03 lab-04 lab-05 \
        lab-06 lab-07 lab-08 lab-09 lab-10 \
        benchmark-01 benchmark-02 benchmark-03 benchmark-04 benchmark-05 \
        benchmark-06 benchmark-07 benchmark-08 benchmark-09 benchmark-10 \
        infra-up infra-down clean

LABS := 01_virtual_threads 02_resilience 03_rate_limiter 04_outbox_kafka \
        05_saga_pattern 06_redis_vs_kafka 07_postgres_tuning 08_kafka_streams \
        09_docker_optimization 10_kubernetes_autoscaling

## ─── Help ─────────────────────────────────────────────────────────────────────

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "\n\033[1mJava Production Labs\033[0m\n\nUsage:\n  make \033[36m<target>\033[0m\n\n\033[1mTargets:\033[0m\n"} \
	/^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

## ─── Infrastructure ──────────────────────────────────────────────────────────

infra-up: ## Start shared infrastructure (Postgres, Redis, Kafka)
	docker compose up -d

infra-down: ## Stop shared infrastructure
	docker compose down -v

## ─── Run Labs ────────────────────────────────────────────────────────────────

lab-01: ## Run Lab 01 — Virtual Threads
	cd 01_virtual_threads && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-02: ## Run Lab 02 — Resilience
	cd 02_resilience && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-03: ## Run Lab 03 — Rate Limiter
	cd 03_rate_limiter && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-04: ## Run Lab 04 — Transactional Outbox + Kafka
	cd 04_outbox_kafka && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-05: ## Run Lab 05 — Saga Pattern
	cd 05_saga_pattern && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-06: ## Run Lab 06 — Redis vs Kafka
	cd 06_redis_vs_kafka && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-07: ## Run Lab 07 — PostgreSQL Tuning
	cd 07_postgres_tuning && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-08: ## Run Lab 08 — Kafka Streams
	cd 08_kafka_streams && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-09: ## Run Lab 09 — Docker Optimization
	cd 09_docker_optimization && docker compose -f docker/docker-compose.yml up -d && ./mvnw spring-boot:run

lab-10: ## Run Lab 10 — Kubernetes Autoscaling
	cd 10_kubernetes_autoscaling && ./mvnw spring-boot:run

## ─── Tests ───────────────────────────────────────────────────────────────────

test-all: ## Run tests for all labs
	@for lab in $(LABS); do \
	  echo "\n\033[1m==> Testing $$lab\033[0m"; \
	  cd $$lab && ./mvnw test -q && cd ..; \
	done

test-01: ## Test Lab 01
	cd 01_virtual_threads && ./mvnw test

test-02: ## Test Lab 02
	cd 02_resilience && ./mvnw test

test-03: ## Test Lab 03
	cd 03_rate_limiter && ./mvnw test

test-04: ## Test Lab 04
	cd 04_outbox_kafka && ./mvnw test

test-05: ## Test Lab 05
	cd 05_saga_pattern && ./mvnw test

test-06: ## Test Lab 06
	cd 06_redis_vs_kafka && ./mvnw test

test-07: ## Test Lab 07
	cd 07_postgres_tuning && ./mvnw test

test-08: ## Test Lab 08
	cd 08_kafka_streams && ./mvnw test

test-09: ## Test Lab 09
	cd 09_docker_optimization && ./mvnw test

test-10: ## Test Lab 10
	cd 10_kubernetes_autoscaling && ./mvnw test

## ─── Benchmarks ──────────────────────────────────────────────────────────────

benchmark-01: ## Benchmark Lab 01 — Virtual Threads (requires k6)
	cd 01_virtual_threads && bash benchmark/run-benchmark.sh

benchmark-02: ## Benchmark Lab 02 — Resilience (requires k6)
	cd 02_resilience && bash benchmark/run-benchmark.sh

benchmark-03: ## Benchmark Lab 03 — Rate Limiter (requires k6)
	cd 03_rate_limiter && bash benchmark/run-benchmark.sh

benchmark-04: ## Benchmark Lab 04 — Outbox Kafka (requires k6)
	cd 04_outbox_kafka && bash benchmark/run-benchmark.sh

benchmark-05: ## Benchmark Lab 05 — Saga Pattern (requires k6)
	cd 05_saga_pattern && bash benchmark/run-benchmark.sh

benchmark-06: ## Benchmark Lab 06 — Redis vs Kafka (requires k6)
	cd 06_redis_vs_kafka && bash benchmark/run-benchmark.sh

benchmark-07: ## Benchmark Lab 07 — Postgres Tuning (requires k6)
	cd 07_postgres_tuning && bash benchmark/run-benchmark.sh

benchmark-08: ## Benchmark Lab 08 — Kafka Streams (requires k6)
	cd 08_kafka_streams && bash benchmark/run-benchmark.sh

benchmark-09: ## Benchmark Lab 09 — Docker Optimization (requires k6)
	cd 09_docker_optimization && bash benchmark/run-benchmark.sh

benchmark-10: ## Benchmark Lab 10 — Kubernetes Autoscaling (requires k6)
	cd 10_kubernetes_autoscaling && bash benchmark/run-benchmark.sh

## ─── Cleanup ─────────────────────────────────────────────────────────────────

clean: ## Clean all build artifacts
	@for lab in $(LABS); do \
	  cd $$lab && ./mvnw clean -q && cd ..; \
	done
