#!/bin/bash

# v0.3 Performance Test Runner
# Runs performance tests for both single server and multi-server configurations

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Load environment variables
if [ -f .env.perf-test ]; then
    source .env.perf-test
    echo -e "${GREEN}Loaded environment variables from .env.perf-test${NC}"
else
    echo -e "${YELLOW}Warning: .env.perf-test not found. Using default values.${NC}"
fi

# Configuration
TOKEN=${TOKEN:-""}
CHANNEL_ID=${CHANNEL_ID:-"1"}
CONNECTIONS=${CONNECTIONS:-"20"}  # Increased from 10 to 20 for more realistic load
DURATION=${DURATION:-"30"}        # Reduced from 60 to 30 for faster iteration
MESSAGES_PER_SEC=${MESSAGES_PER_SEC:-"5"}  # Increased from 1 to 5 for more load

# Auto-generate token if not provided
if [ -z "$TOKEN" ]; then
    echo -e "${YELLOW}Token not found. Attempting to generate one...${NC}"
    
    AUTH_SERVER_URL=${AUTH_SERVER_URL:-"http://localhost:8081"}
    CLIENT_ID="test-client"
    CLIENT_SECRET="test-secret-key"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_SERVER_URL/oauth2/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -u "$CLIENT_ID:$CLIENT_SECRET" \
      -d "grant_type=client_credentials" \
      -d "scope=read write" 2>&1) || true
    
    # Extract HTTP code and body (macOS compatible)
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" = "200" ] && echo "$BODY" | grep -q "access_token"; then
        TOKEN=$(echo "$BODY" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
        if [ -n "$TOKEN" ]; then
            echo -e "${GREEN}✓ Successfully obtained token${NC}"
            export TOKEN
        else
            echo -e "${RED}✗ Failed to extract token from response${NC}"
            echo "Please run ./scripts/generate-test-token.sh first or set TOKEN environment variable"
            exit 1
        fi
    else
        echo -e "${RED}✗ Failed to get token (HTTP $HTTP_CODE)${NC}"
        echo "Please run ./scripts/generate-test-token.sh first or set TOKEN environment variable"
        exit 1
    fi
fi

# Test configurations to run
# Format: test_name|ws_url|description
declare -a TEST_CONFIGS=(
    "single|http://localhost:9000/ws|Single Server (Port 9000)"
    "multi|http://localhost:8888/ws|Multi Server (Nginx Load Balancer)"
)

# Results directory
RESULTS_DIR="$PROJECT_ROOT/local/performance-results"
mkdir -p "$RESULTS_DIR"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo -e "${GREEN}=== v0.3 Performance Test Runner ===${NC}"
echo ""

# Regenerate token to ensure it's valid (OAuth2 server may have restarted)
echo -e "${YELLOW}Regenerating test token...${NC}"
if [ -f "$SCRIPT_DIR/generate-test-token.sh" ]; then
    TOKEN_OUTPUT=$("$SCRIPT_DIR/generate-test-token.sh" 2>&1)
    # Extract token from the output (line starting with "Token:")
    NEW_TOKEN=$(echo "$TOKEN_OUTPUT" | grep "^Token:" | awk '{print $2}')
    if [ -n "$NEW_TOKEN" ]; then
        export TOKEN="$NEW_TOKEN"
        # Update .env.perf-test with new token
        sed -i.bak "s|^TOKEN=.*|TOKEN=$NEW_TOKEN|" "$PROJECT_ROOT/.env.perf-test"
        echo -e "${GREEN}✓ Token regenerated successfully${NC}"
    else
        echo -e "${YELLOW}⚠ Could not extract new token, using existing TOKEN from environment${NC}"
    fi
else
    echo -e "${YELLOW}⚠ generate-test-token.sh not found, using existing TOKEN from environment${NC}"
fi
echo ""

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Error: TOKEN is required. Set it in .env.perf-test or as environment variable.${NC}"
    exit 1
fi

echo "Configuration:"
echo "  Token: ${TOKEN:0:20}..."
echo "  Channel ID: $CHANNEL_ID"
echo "  Connections: $CONNECTIONS"
echo "  Duration: ${DURATION}s"
echo "  Messages/sec: $MESSAGES_PER_SEC"
echo ""

# Check if Node.js dependencies are installed
if [ ! -d "$SCRIPT_DIR/node_modules/@stomp" ]; then
    echo -e "${YELLOW}Installing Node.js dependencies for performance tests...${NC}"
    cd "$SCRIPT_DIR"
    if [ ! -f "package.json" ]; then
        echo -e "${RED}Error: package.json not found in scripts directory${NC}"
        exit 1
    fi
    npm install
    cd "$PROJECT_ROOT"
fi

# Function to run a single test
run_test() {
    local test_name=$1
    local ws_url=$2
    local description=$3
    
    echo -e "${GREEN}Running test: $description${NC}"
    echo "  URL: $ws_url"
    
    export WS_URL="$ws_url"
    export TOKEN="$TOKEN"
    export CHANNEL_ID="$CHANNEL_ID"
    export CONNECTIONS="$CONNECTIONS"
    export DURATION="$DURATION"
    export MESSAGES_PER_SEC="$MESSAGES_PER_SEC"
    
    local result_file="$RESULTS_DIR/${test_name}_${TIMESTAMP}.json"
    
    # Run Node.js test script
    node "$SCRIPT_DIR/performance-test-node.js" > "$result_file" 2>&1
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Test completed successfully${NC}"
        echo "  Results saved to: $result_file"
        
        # Extract JSON result from output
        grep -A 1000 "=== JSON Result ===" "$result_file" | tail -n +2 > "$result_file.tmp" || true
        if [ -s "$result_file.tmp" ]; then
            mv "$result_file.tmp" "$result_file"
        fi
    else
        echo -e "${RED}Test failed${NC}"
        echo "  Check logs in: $result_file"
    fi
    
    echo ""
    sleep 2  # Brief pause between tests
}

# Run all test configurations
for config in "${TEST_CONFIGS[@]}"; do
    IFS='|' read -r test_name ws_url description <<< "$config"
    run_test "$test_name" "$ws_url" "$description"
done

echo -e "${GREEN}=== All tests completed ===${NC}"
echo "Results saved in: $RESULTS_DIR"
echo ""
echo "To view results:"
echo "  cat $RESULTS_DIR/*_${TIMESTAMP}.json"

