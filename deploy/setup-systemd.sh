#!/bin/bash
set -e

# Setup systemd service for WhatsApp AI Assistant
# This enables auto-start on boot

echo "⚙️  Setting up systemd service..."
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "❌ This script must be run as root (use sudo)"
    exit 1
fi

# Get the actual user who invoked sudo
ACTUAL_USER=${SUDO_USER:-$USER}
ACTUAL_HOME=$(eval echo ~$ACTUAL_USER)

# Determine installation directory
INSTALL_DIR=${1:-$ACTUAL_HOME/whatsapp-ai-assistant}

if [ ! -d "$INSTALL_DIR" ]; then
    echo "❌ Directory not found: $INSTALL_DIR"
    echo "   Usage: sudo ./setup-systemd.sh [installation-directory]"
    exit 1
fi

echo "📁 Installation directory: $INSTALL_DIR"
echo "👤 Running as user: $ACTUAL_USER"
echo ""

# Detect docker-compose command
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# Create systemd service file
SERVICE_FILE="/etc/systemd/system/whatsapp-ai.service"

cat > $SERVICE_FILE <<EOF
[Unit]
Description=WhatsApp AI Assistant
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$INSTALL_DIR
User=$ACTUAL_USER
ExecStartPre=/bin/sleep 10
ExecStart=$COMPOSE_CMD up -d
ExecStop=$COMPOSE_CMD down
TimeoutStartSec=300
Restart=on-failure
RestartSec=30

[Install]
WantedBy=multi-user.target
EOF

echo "✅ Service file created: $SERVICE_FILE"

# Reload systemd
systemctl daemon-reload

# Enable service
systemctl enable whatsapp-ai.service
echo "✅ Service enabled (will start on boot)"

echo ""
echo "📋 Service management commands:"
echo "   Start:   sudo systemctl start whatsapp-ai"
echo "   Stop:    sudo systemctl stop whatsapp-ai"
echo "   Status:  sudo systemctl status whatsapp-ai"
echo "   Logs:    journalctl -u whatsapp-ai -f"
echo "   Disable: sudo systemctl disable whatsapp-ai"
echo ""

read -p "Start service now? (Y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    systemctl start whatsapp-ai.service
    echo "✅ Service started"
    sleep 3
    systemctl status whatsapp-ai.service --no-pager
fi
