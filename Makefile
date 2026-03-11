.PHONY: setup test lint spec-check docker-test help

# ─── Default target ───────────────────────────────────────────────────────────
help:
	@echo "Project Chimera — Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  make setup        Resolve dependencies (skip tests)"
	@echo "  make test         Run JUnit 5 tests (expected to FAIL — TDD red phase)"
	@echo "  make lint         Run Checkstyle code quality checks"
	@echo "  make spec-check   Verify code alignment with specs/"
	@echo "  make docker-test  Build Docker image and run tests inside container"

# ─── Core targets ─────────────────────────────────────────────────────────────

## Resolve all Maven dependencies and compile (skip tests)
setup:
	mvn clean install -DskipTests

## Run the JUnit 5 test suite
## NOTE: Tests WILL FAIL — this is intentional (TDD red phase).
## The failing output proves the TDD approach: specs define the goal posts.
test:
	mvn test

## Run Checkstyle code quality checks
lint:
	mvn checkstyle:check

## Verify that source code references spec story IDs and API endpoints
spec-check:
	@echo "Running spec alignment check..."
	@bash scripts/spec_check.sh
	@echo "Spec check complete."

# ─── Bonus: Docker target ─────────────────────────────────────────────────────

## Build Docker image and run tests inside the container
docker-test:
	docker build -t chimera-test:latest .
	docker run --rm chimera-test:latest mvn test
