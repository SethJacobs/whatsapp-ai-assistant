#!/bin/bash

# WhatsApp AI Assistant - Health Check Script
# Checks status of all services and reports issues

echo "🏥 WhatsApp AI Assistant - Health Check"
echo "========================================"
echo ""

BRIDGE_URL="http://localhost:3000"
BACKEND_URL="http://localhost:8081"

# Detect docker-compose command
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# Check Docker containers
echo "🐳 Docker Containers:"
$COMPOSE_CMD ps
echo ""

# Check WhatsApp Bridge
echo "📱 WhatsApp Bridge:"
if curl -sf "$BRIDGE_URL/bridge/health" > /dev/null; then
    HEALTH=$(curl -s "$BRIDGE_URL/bridge/health" | jq -r '.status' 2>/dev/null || echo "unknown")
    echo "   Status: ✅ $HEALTH"

    # Check if authenticated
    AUTHENTICATED=$(curl -s "$BRIDGE_URL/bridge/health" | jq -r '.authenticated' 2>/dev/null || echo "unknown")
    if [ "$AUTHENTICATED" = "true" ]; then
        echo "   WhatsApp: ✅ Authenticated"
    else
        echo "   WhatsApp: ⚠️  Not authenticated - scan QR code"
        echo "   Get QR: curl $BRIDGE_URL/bridge/qr"
    fi
else
    echo "   Status: ❌ Not responding"
fi
echo ""

# Check Java Backend
echo "☕ Java Backend:"
if curl -sf "$BACKEND_URL/actuator/health" > /dev/null; then
    HEALTH=$(curl -s "$BACKEND_URL/actuator/health" | jq -r '.status' 2>/dev/null || echo "unknown")
    echo "   Status: ✅ $HEALTH"
else
    echo "   Status: ❌ Not responding"
fi
echo ""

# Check Gateway connectivity
echo "🌐 AI Gateway:"
source .env 2>/dev/null || true
GATEWAY_URL=${GATEWAY_BASE_URL:-http://ai-gateway.local}

if curl -sf --max-time 5 "$GATEWAY_URL/health" > /dev/null 2>&1; then
    echo "   Status: ✅ Reachable"
    echo "   URL: $GATEWAY_URL"
else
    echo "   Status: ⚠️  Not reachable"
    echo "   URL: $GATEWAY_URL"
    echo "   Note: This is expected if gateway is not on this machine"
fi
echo ""

# Check disk space
echo "💾 Disk Space:"
df -h . | tail -1 | awk '{print "   Used: "$3" / "$2" ("$5")"}'
echo ""

# Check memory
echo "🧠 Memory Usage:"
free -h | grep "Mem:" | awk '{print "   Used: "$3" / "$2}'
echo ""

# Recent logs
echo "📋 Recent Errors (last 5 minutes):"
ERROR_COUNT=$($COMPOSE_CMD logs --since 5m 2>&1 | grep -i error | wc -l)
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo "   ⚠️  Found $ERROR_COUNT errors"
    echo "   View with: $COMPOSE_CMD logs --since 5m | grep -i error"
else
    echo "   ✅ No errors"
fi
echo ""

echo "✅ Health check complete"
