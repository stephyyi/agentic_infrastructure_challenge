# =============================================================================
# Project Chimera — Dockerfile
# Multi-stage build: stage 1 compiles, stage 2 runs tests
# =============================================================================
# Usage:
#   docker build -t chimera-test .
#   docker run --rm chimera-test            # runs mvn test inside container
#
# Or via Makefile:
#   make docker-test
# =============================================================================

# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy dependency manifest first for better Docker layer caching
COPY pom.xml .

# Pre-download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -q

# Copy source and tests
COPY src ./src
COPY tests ./tests
COPY checkstyle.xml .

# Compile (skip tests in build stage)
RUN mvn clean compile test-compile -DskipTests -q

# ─── Stage 2: Test runner ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS test

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy compiled artifacts and everything needed to run tests
COPY --from=build /app /app

# Default command: run the JUnit 5 test suite
# Tests are expected to FAIL (TDD red phase) — the exit code will be non-zero.
# That is intentional and proves the TDD approach.
CMD ["mvn", "test"]
