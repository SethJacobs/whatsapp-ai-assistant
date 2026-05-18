#!/bin/bash
set -e

# WhatsApp AI Assistant - Installation Script for Raspberry Pi
# This script sets up the WhatsApp AI assistant on a Raspberry Pi

echo "🚀 WhatsApp AI Assistant - Installation"
echo "========================================"
echo ""

# Check if running on Pi
if [ ! -f /proc/cpuinfo ] || ! grep -q "Raspberry Pi" /proc/cpuinfo 2>/dev/null; then
    echo "⚠️  Warning: This doesn't appear to be a Raspberry Pi"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    echo "❌ Docker not found. Please install Docker first."
    echo "   Run: curl -fsSL https://get.docker.com | sh"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose not found. Please install Docker Compose."
    exit 1
fi

echo "✅ Prerequisites satisfied"
echo ""

# Check for .env file
if [ ! -f .env ]; then
    echo "📝 Creating .env file..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "⚠️  Please edit .env and set your ADMIN_PHONE and other configuration"
        echo "   nano .env"
        read -p "Press Enter when ready to continue..."
    else
        echo "❌ .env.example not found. Please create .env manually."
        exit 1
    fi
fi

# Validate .env
source .env
if [ -z "$ADMIN_PHONE" ]; then
    echo "❌ ADMIN_PHONE not set in .env file"
    exit 1
fi

if [[ ! $ADMIN_PHONE =~ ^\+[0-9]{10,15}$ ]]; then
    echo "❌ ADMIN_PHONE must be in E.164 format (e.g., +1234567890)"
    exit 1
fi

echo "✅ Configuration validated"
echo "   Admin phone: $ADMIN_PHONE"
echo ""

# Create directories
echo "📁 Creating directories..."
mkdir -p data
mkdir -p whatsapp-bridge/.wwebjs_auth
mkdir -p whatsapp-bridge/.wwebjs_cache
echo "✅ Directories created"
echo ""

# Build images
echo "🔨 Building Docker images..."
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

$COMPOSE_CMD build
echo "✅ Images built"
echo ""

# Start services
echo "🚀 Starting services..."
$COMPOSE_CMD up -d

# Wait for services to be healthy
echo ""
echo "⏳ Waiting for services to start..."
sleep 10

# Check health
echo ""
echo "🔍 Checking service health..."

BRIDGE_HEALTHY=false
BACKEND_HEALTHY=false

for i in {1..30}; do
    if curl -sf http://localhost:3000/bridge/health > /dev/null 2>&1; then
        BRIDGE_HEALTHY=true
    fi

    if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
        BACKEND_HEALTHY=true
    fi

    if [ "$BRIDGE_HEALTHY" = true ] && [ "$BACKEND_HEALTHY" = true ]; then
        break
    fi

    echo "  Attempt $i/30..."
    sleep 2
done

echo ""
if [ "$BRIDGE_HEALTHY" = true ]; then
    echo "✅ WhatsApp Bridge is healthy"
else
    echo "❌ WhatsApp Bridge failed to start"
fi

if [ "$BACKEND_HEALTHY" = true ]; then
    echo "✅ Java Backend is healthy"
else
    echo "❌ Java Backend failed to start"
fi

if [ "$BRIDGE_HEALTHY" = true ] && [ "$BACKEND_HEALTHY" = true ]; then
    echo ""
    echo "🎉 Installation complete!"
    echo ""
    echo "📱 Next steps:"
    echo "   1. Get the WhatsApp QR code:"
    echo "      curl http://localhost:3000/bridge/qr"
    echo ""
    echo "   2. Scan the QR code with your WhatsApp mobile app:"
    echo "      WhatsApp > Settings > Linked Devices > Link a Device"
    echo ""
    echo "   3. Send a message to your WhatsApp number to test"
    echo ""
    echo "📊 Useful commands:"
    echo "   View logs:    $COMPOSE_CMD logs -f"
    echo "   Stop:         $COMPOSE_CMD stop"
    echo "   Restart:      $COMPOSE_CMD restart"
    echo "   Status:       $COMPOSE_CMD ps"
    echo ""
else
    echo ""
    echo "❌ Installation completed with errors"
    echo "   Check logs: $COMPOSE_CMD logs"
    exit 1
fi
