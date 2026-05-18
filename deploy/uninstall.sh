#!/bin/bash
set -e

# WhatsApp AI Assistant - Uninstallation Script
# Stops and removes all containers, images, and optionally data

echo "🗑️  WhatsApp AI Assistant - Uninstallation"
echo "=========================================="
echo ""

# Detect docker-compose command
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# Stop and remove containers
echo "🛑 Stopping containers..."
$COMPOSE_CMD down

echo "✅ Containers stopped and removed"
echo ""

# Ask about data deletion
read -p "❓ Delete all data (database, WhatsApp session)? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🗑️  Removing data..."
    rm -rf data/
    rm -rf whatsapp-bridge/.wwebjs_auth/
    rm -rf whatsapp-bridge/.wwebjs_cache/
    echo "✅ Data removed"
else
    echo "⏭️  Keeping data (can be restored on reinstall)"
fi

echo ""

# Ask about image deletion
read -p "❓ Remove Docker images? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🗑️  Removing images..."
    docker rmi whatsapp-ai-assistant-whatsapp-bridge:latest 2>/dev/null || true
    docker rmi whatsapp-ai-assistant-java-backend:latest 2>/dev/null || true
    echo "✅ Images removed"
fi

echo ""
echo "✅ Uninstallation complete!"
