#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/pi/whatsapp-ai-assistant"

echo "=== WhatsApp AI Assistant Installer ==="

# 1. Check secrets
echo "[1/4] Checking secrets..."
cd "${PROJECT_DIR}"
if [ -f .env ]; then
    echo "  .env found"
elif command -v op &> /dev/null; then
    op inject -i .env.tpl -o .env --force
    echo "  Secrets injected from 1Password"
else
    echo "  ERROR: No .env file found. Create one with:"
    echo "    ADMIN_PHONE=+12125551234"
    echo "    GATEWAY_BASE_URL=http://ai-gateway.local"
    exit 1
fi

# 2. Build Docker images
echo "[2/4] Building Docker images..."
docker compose build

# 3. Initialize WhatsApp authentication
echo "[3/4] Starting WhatsApp bridge..."
docker compose up -d whatsapp-bridge

echo "  Waiting for QR code (10 seconds)..."
sleep 10

# Display QR code
echo "📱 Scan this QR code with WhatsApp:"
docker compose logs whatsapp-bridge | grep -A 30 "QR Code"

read -p "Press Enter after scanning the QR code..."

# 4. Start Java backend
echo "[4/4] Starting Java backend..."
docker compose up -d whatsapp-ai

# Verify
sleep 5
echo ""
echo "=== Verification ==="
curl -sf http://localhost:8081/health && echo "✓ Java backend: OK" || echo "✗ Java backend: FAILED"
curl -sf http://localhost:3000/bridge/health && echo "✓ WhatsApp bridge: OK" || echo "✗ WhatsApp bridge: FAILED"

echo ""
echo "✅ Installation complete!"
echo ""
echo "Test by sending a WhatsApp message to the authenticated number."
echo ""
echo "View logs:"
echo "  docker compose logs -f whatsapp-ai"
echo "  docker compose logs -f whatsapp-bridge"
