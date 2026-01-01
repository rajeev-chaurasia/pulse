#!/bin/bash
cd "$(dirname "$0")/.."
set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check command existence
check_cmd() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}❌ Error: $1 is not installed or not in PATH.${NC}"
        exit 1
    fi
}

# Function to wait for a port
wait_for_port() {
    local host=$1
    local port=$2
    local retries=30
    local wait_time=2

    echo -n "⏳ Waiting for $host:$port to be ready..."
    for ((i=1; i<=retries; i++)); do
        if nc -z "$host" "$port" 2>/dev/null; then
            echo -e " ${GREEN}✅ Connected!${NC}"
            return 0
        fi
        echo -n "."
        sleep "$wait_time"
    done
    echo -e " ${RED}❌ Timeout waiting for $host:$port.${NC}"
    exit 1
}

# Cleanup function to kill background processes on exit
cleanup() {
    echo -e "\n${YELLOW}🧹 Cleaning up background processes...${NC}"
    if [ -n "$INGESTION_PID" ]; then kill "$INGESTION_PID" 2>/dev/null || true; fi
    if [ -n "$DASHBOARD_PID" ]; then kill "$DASHBOARD_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT

# -----------------------------------------------------------------------------
# 1. PRE-FLIGHT CHECKS
# -----------------------------------------------------------------------------
echo -e "${GREEN}🔍 Performing pre-flight checks...${NC}"
check_cmd docker
check_cmd java
check_cmd python3
check_cmd nc

# -----------------------------------------------------------------------------
# 2. TEARDOWN (Docker & Local Ports)
# -----------------------------------------------------------------------------
echo -e "\n${YELLOW}🛑 tearing down existing environment...${NC}"
docker-compose -f application-backend/docker-compose.yml down -v --remove-orphans

echo -e "${YELLOW}🧹 Clearing ports 8080 and 3000...${NC}"
lsof -ti:8080 | xargs kill -9 2>/dev/null || true
lsof -ti:3000 | xargs kill -9 2>/dev/null || true

# -----------------------------------------------------------------------------
# 3. BUILD
# -----------------------------------------------------------------------------
echo -e "\n${GREEN}🏗️  Building Backend Projects...${NC}"
(cd application-backend && ./gradlew build -x test)

echo -e "\n${GREEN}🎨 Building Frontend...${NC}"
check_cmd npm
(cd application-backend/pulse-dashboard && npm install && npm run build)

# -----------------------------------------------------------------------------
# 4. INFRASTRUCTURE STARTUP
# -----------------------------------------------------------------------------
echo -e "\n${GREEN}🚀 Starting Infrastructure (Kafka, Flink, DynamoDB)...${NC}"
docker-compose -f application-backend/docker-compose.yml up -d

# Wait for essential ports
wait_for_port localhost 9094  # Kafka (External)
wait_for_port localhost 8081  # Flink Dashboard
wait_for_port localhost 8000  # DynamoDB Local

echo -e "test"
echo -e "zzz"
sleep 5 # Extra buffer for internal services

# -----------------------------------------------------------------------------
# 5. FLINK JOB DEPLOYMENT
# -----------------------------------------------------------------------------
echo -e "\n${GREEN}⚡ Submitting Flink Job to Cluster...${NC}"
# Copy jar to container
docker cp application-backend/pulse-processor/build/libs/pulse-processor-all.jar pulse-jobmanager:/job.jar

# Retry logic for job submission (sometimes JobManager is up but not ready to accept jobs)
MAX_RETRIES=5
for ((i=1; i<=MAX_RETRIES; i++)); do
    if docker exec pulse-jobmanager flink run -d -c com.pulse.processor.MatchingJob /job.jar; then
        echo -e "${GREEN}✅ Flink Job Submitted Successfully!${NC}"
        break
    else
        echo -e "${YELLOW}⚠️  Job submission failed (attempt $i/$MAX_RETRIES). Retrying in 5s...${NC}"
        sleep 5
    fi
    if [ $i -eq $MAX_RETRIES ]; then
        echo -e "${RED}❌ Failed to submit Flink job after multiple attempts.${NC}"
        exit 1
    fi
done

# -----------------------------------------------------------------------------
# 6. INGESTION SERVICE STARTUP
# -----------------------------------------------------------------------------
echo -e "\n${GREEN}🔌 Starting Ingestion Service...${NC}"
java -jar application-backend/pulse-ingestion/build/libs/pulse-ingestion.jar &
INGESTION_PID=$!
echo -e "✅ Ingestion Service running with PID: $INGESTION_PID"

# -----------------------------------------------------------------------------
# 7. DASHBOARD LAUNCH
# -----------------------------------------------------------------------------
echo -e "\n${GREEN}📊 Launching Dashboard...${NC}"
# Serve the dashboard using Vite Preview on port 3000
(cd application-backend/pulse-dashboard && npm run preview -- --port 3000 --host) &
DASHBOARD_PID=$!

wait_for_port localhost 3000

echo -e "✅ Dashboard Server running at http://localhost:3000"
echo -e "${GREEN}🌍 System works! Opening Dashboard...${NC}"

# Open browser (Mac/Linux compatible)
if [[ "$OSTYPE" == "darwin"* ]]; then
    open http://localhost:3000
elif command -v xdg-open &> /dev/null; then
    xdg-open http://localhost:3000
fi

echo -e "\n${YELLOW}Press [CTRL+C] to stop the local servers.${NC}"
wait
