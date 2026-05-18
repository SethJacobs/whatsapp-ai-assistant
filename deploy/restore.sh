#!/bin/bash
set -e

# WhatsApp AI Assistant - Restore Script
# Restores from backup

if [ -z "$1" ]; then
    echo "Usage: ./restore.sh <backup-file.tar.gz>"
    echo ""
    echo "Available backups:"
    ls -lh backups/whatsapp-ai-backup-*.tar.gz 2>/dev/null || echo "  No backups found"
    exit 1
fi

BACKUP_FILE=$1

if [ ! -f "$BACKUP_FILE" ]; then
    echo "❌ Backup file not found: $BACKUP_FILE"
    exit 1
fi

echo "📦 WhatsApp AI Assistant - Restore"
echo "=================================="
echo ""
echo "⚠️  WARNING: This will overwrite existing data!"
echo "   Backup file: $BACKUP_FILE"
echo ""

read -p "Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted"
    exit 0
fi

# Detect docker-compose command
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# Stop services if running
echo "🛑 Stopping services..."
$COMPOSE_CMD down 2>/dev/null || true

# Extract backup
echo "📂 Restoring from backup..."
tar -xzf "$BACKUP_FILE"

echo "✅ Restore complete!"
echo ""
echo "🚀 Start services with:"
echo "   $COMPOSE_CMD up -d"
