#!/usr/bin/env bash
# =============================================================================
# Project Chimera — Spec Alignment Check
# =============================================================================
# Verifies that source code stays aligned with specs/.
# Exits 0 if all checks pass, non-zero if gaps are found.
# Run via: make spec-check
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPECS_DIR="$ROOT/specs"
SRC_DIR="$ROOT/src/main/java"
TESTS_DIR="$ROOT/tests"
FAIL=0

# Colour codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Colour

pass() { echo -e "${GREEN}  ✔ $1${NC}"; }
warn() { echo -e "${YELLOW}  ⚠ $1${NC}"; }
fail() { echo -e "${RED}  ✘ $1${NC}"; FAIL=1; }

echo ""
echo "════════════════════════════════════════════════════"
echo " Project Chimera — Spec Alignment Check"
echo "════════════════════════════════════════════════════"
echo ""

# ─── Check 1: All spec files exist ───────────────────────────────────────────
echo "► Check 1: Required spec files exist"
for f in "_meta.md" "functional.md" "technical.md" "openclaw_integration.md"; do
    if [[ -f "$SPECS_DIR/$f" ]]; then
        pass "$f exists"
    else
        fail "$f MISSING from specs/"
    fi
done
echo ""

# ─── Check 2: Functional story IDs referenced in tests or source ─────────────
echo "► Check 2: Spec story IDs referenced in codebase"
STORY_IDS=$(grep -oE '[OTCPEH]-[0-9]{3}' "$SPECS_DIR/functional.md" | sort -u)
for story_id in $STORY_IDS; do
    if grep -rq "$story_id" "$SRC_DIR" "$TESTS_DIR" 2>/dev/null; then
        pass "$story_id referenced in source or tests"
    else
        warn "$story_id has no reference in src/ or tests/ yet (implementation pending)"
    fi
done
echo ""

# ─── Check 3: REST API endpoints referenced in source ────────────────────────
echo "► Check 3: REST API endpoints referenced in source"
ENDPOINTS=("POST /cycles" "GET /cycles" "GET /trends" "GET /drafts" "PATCH /drafts" "GET /publications" "GET /agents")
for endpoint in "${ENDPOINTS[@]}"; do
    METHOD=$(echo "$endpoint" | awk '{print $1}')
    PATH_PART=$(echo "$endpoint" | awk '{print $2}' | tr -d '/')
    if grep -rq "$PATH_PART" "$SRC_DIR" 2>/dev/null; then
        pass "$endpoint — path '/$PATH_PART' referenced in src/"
    else
        warn "$endpoint — no @RequestMapping for '/$PATH_PART' found yet (implementation pending)"
    fi
done
echo ""

# ─── Check 4: Environment variables from specs/technical.md in application.yaml ─
echo "► Check 4: Required environment variables declared in application.yaml"
YAML_FILE="$ROOT/src/main/resources/application.yaml"
ENV_VARS=("DATABASE_URL" "DATABASE_USERNAME" "DATABASE_PASSWORD" "ANTHROPIC_API_KEY" "NOTIFICATION_WEBHOOK_URL")
for var in "${ENV_VARS[@]}"; do
    if [[ -f "$YAML_FILE" ]] && grep -q "$var" "$YAML_FILE"; then
        pass "$var referenced in application.yaml"
    else
        fail "$var NOT referenced in application.yaml (required by specs/technical.md Section 5)"
    fi
done
echo ""

# ─── Check 5: Java Records used for DTOs ─────────────────────────────────────
echo "► Check 5: Model DTOs are Java Records (not POJOs)"
MODEL_DIR="$SRC_DIR/com/chimera/model"
if [[ -d "$MODEL_DIR" ]]; then
    for java_file in "$MODEL_DIR"/*.java; do
        filename=$(basename "$java_file")
        if grep -q "^public record " "$java_file"; then
            pass "$filename — is a Java Record"
        elif grep -q "^public enum " "$java_file"; then
            pass "$filename — is an enum (OK)"
        else
            fail "$filename — is NOT a Java Record. All model DTOs must be Records for OCC safety"
        fi
    done
else
    warn "src/main/java/com/chimera/model/ not found yet"
fi
echo ""

# ─── Check 6: CLAUDE.md exists and has required sections ─────────────────────
echo "► Check 6: CLAUDE.md contains required sections"
CLAUDE_FILE="$ROOT/CLAUDE.md"
REQUIRED_SECTIONS=("Prime Directive" "Java-Specific Directives" "Traceability" "Project Context")
if [[ -f "$CLAUDE_FILE" ]]; then
    for section in "${REQUIRED_SECTIONS[@]}"; do
        if grep -q "$section" "$CLAUDE_FILE"; then
            pass "CLAUDE.md — '$section' section present"
        else
            fail "CLAUDE.md — '$section' section MISSING"
        fi
    done
else
    fail "CLAUDE.md does not exist"
fi
echo ""

# ─── Check 7: skills/ directory has at least 2 skills ───────────────────────
echo "► Check 7: skills/ directory has at least 2 skill READMEs"
SKILLS_DIR="$ROOT/skills"
if [[ -d "$SKILLS_DIR" ]]; then
    SKILL_COUNT=$(find "$SKILLS_DIR" -name "README.md" -not -path "$SKILLS_DIR/README.md" | wc -l | tr -d ' ')
    if [[ "$SKILL_COUNT" -ge 2 ]]; then
        pass "skills/ — $SKILL_COUNT skill READMEs found"
    else
        fail "skills/ — only $SKILL_COUNT skill READMEs found (need at least 2)"
    fi
else
    fail "skills/ directory does not exist"
fi
echo ""

# ─── Summary ──────────────────────────────────────────────────────────────────
echo "════════════════════════════════════════════════════"
if [[ "$FAIL" -eq 0 ]]; then
    echo -e "${GREEN} All spec alignment checks passed.${NC}"
else
    echo -e "${RED} Spec alignment gaps detected. See failures above.${NC}"
    echo ""
    echo " Note: 'warn' items are expected during the TDD red phase (no implementation yet)."
    echo " 'fail' items indicate structural problems that must be fixed."
fi
echo "════════════════════════════════════════════════════"
echo ""

exit $FAIL
