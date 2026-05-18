#!/bin/bash
set -e

# WhatsApp AI Assistant - Backup Script
# Backs up database and WhatsApp session

BACKUP_DIR=${1:-./backups}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/whatsapp-ai-backup-$TIMESTAMP.tar.gz"

echo "💾 WhatsApp AI Assistant - Backup"
echo "================================="
echo ""

# Create backup directory
mkdir -p "$BACKUP_DIR"

echo "📦 Creating backup..."
echo "   Timestamp: $TIMESTAMP"
echo "   Output: $BACKUP_FILE"
echo ""

# Create tar archive
tar -czf "$BACKUP_FILE" \
    --exclude='whatsapp-bridge/node_modules' \
    --exclude='java-backend/target' \
    data/ \
    whatsapp-bridge/.wwebjs_auth/ \
    .env \
    2>/dev/null || true

if [ -f "$BACKUP_FILE" ]; then
    BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    echo "✅ Backup complete!"
    echo "   File: $BACKUP_FILE"
    echo "   Size: $BACKUP_SIZE"
    echo ""
    echo "📋 To restore:"
    echo "   tar -xzf $BACKUP_FILE"
else
    echo "❌ Backup failed"
    exit 1
fi

# Clean up old backups (keep last 7)
echo "🧹 Cleaning old backups (keeping last 7)..."
ls -t "$BACKUP_DIR"/whatsapp-ai-backup-*.tar.gz 2>/dev/null | tail -n +8 | xargs -r rm
echo "✅ Cleanup complete"
