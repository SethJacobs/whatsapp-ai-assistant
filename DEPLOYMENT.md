# WhatsApp AI Assistant - Deployment Guide

This guide covers deploying the WhatsApp AI Assistant to a Raspberry Pi.

## Prerequisites

- Raspberry Pi (3B+ or newer recommended)
- Raspberry Pi OS (64-bit recommended)
- Docker and Docker Compose installed
- Network access to pi-ai-gateway
- WhatsApp account with linked device available

## Quick Install

1. **Clone the repository:**
   ```bash
   git clone <repo-url> ~/whatsapp-ai-assistant
   cd ~/whatsapp-ai-assistant
   ```

2. **Configure environment:**
   ```bash
   cp .env.example .env
   nano .env
   ```

   Set at minimum:
   ```bash
   ADMIN_PHONE=+1234567890  # Your phone number in E.164 format
   GATEWAY_BASE_URL=http://ai-gateway.local  # Your gateway URL
   ```

3. **Run installation:**
   ```bash
   cd deploy
   ./install.sh
   ```

4. **Authenticate WhatsApp:**
   ```bash
   curl http://localhost:3000/bridge/qr
   ```

   Scan the QR code with WhatsApp:
   - Open WhatsApp on your phone
   - Go to Settings > Linked Devices
   - Tap "Link a Device"
   - Scan the QR code displayed

5. **Test the assistant:**
   Send a WhatsApp message to your authenticated number:
   ```
   /help
   ```

## System Configuration

### Auto-Start on Boot

To enable automatic startup:

```bash
cd ~/whatsapp-ai-assistant/deploy
sudo ./setup-systemd.sh
```

This creates a systemd service that:
- Starts automatically on boot
- Restarts on failure
- Waits for Docker and network

Manage the service:
```bash
sudo systemctl start whatsapp-ai    # Start now
sudo systemctl stop whatsapp-ai     # Stop
sudo systemctl status whatsapp-ai   # Check status
sudo systemctl restart whatsapp-ai  # Restart
journalctl -u whatsapp-ai -f        # View logs
```

## Backup and Restore

### Create Backup

```bash
cd ~/whatsapp-ai-assistant/deploy
./backup.sh [backup-directory]
```

Backups include:
- Database (conversations, contacts)
- WhatsApp session data
- Configuration (.env)

Automatic retention: Last 7 backups kept

### Restore from Backup

```bash
cd ~/whatsapp-ai-assistant/deploy
./restore.sh backups/whatsapp-ai-backup-YYYYMMDD_HHMMSS.tar.gz
```

## Health Monitoring

Check system health:

```bash
cd ~/whatsapp-ai-assistant/deploy
./health-check.sh
```

This checks:
- Docker container status
- WhatsApp authentication
- Backend health
- Gateway connectivity
- Disk and memory usage
- Recent errors

## Maintenance

### View Logs

```bash
docker compose logs -f                  # All services
docker compose logs -f java-backend     # Backend only
docker compose logs -f whatsapp-bridge  # Bridge only
```

### Restart Services

```bash
docker compose restart                  # Restart all
docker compose restart java-backend     # Restart backend only
docker compose restart whatsapp-bridge  # Restart bridge only
```

### Update to Latest Version

```bash
cd ~/whatsapp-ai-assistant
git pull
docker compose build
docker compose up -d
```

## Uninstallation

```bash
cd ~/whatsapp-ai-assistant/deploy
./uninstall.sh
```

This will:
1. Stop and remove containers
2. Optionally delete data
3. Optionally remove Docker images

## Troubleshooting

### WhatsApp Not Authenticated

1. Check if bridge is running:
   ```bash
   docker compose ps
   ```

2. Get fresh QR code:
   ```bash
   curl http://localhost:3000/bridge/qr
   ```

3. Restart bridge if needed:
   ```bash
   docker compose restart whatsapp-bridge
   ```

### Backend Not Responding

1. Check logs:
   ```bash
   docker compose logs java-backend
   ```

2. Check health:
   ```bash
   curl http://localhost:8081/actuator/health
   ```

3. Verify environment variables:
   ```bash
   docker compose config
   ```

### Gateway Not Reachable

1. Verify gateway URL:
   ```bash
   cat .env | grep GATEWAY_BASE_URL
   ```

2. Test connectivity:
   ```bash
   curl http://ai-gateway.local/health
   ```

3. Check nginx/DNS configuration if using DNS name

### Out of Memory

The backend limits memory to 512MB. If needed, adjust in `docker-compose.yml`:

```yaml
java-backend:
  mem_limit: 1g  # Increase to 1GB
```

### Out of Disk Space

1. Check disk usage:
   ```bash
   df -h
   ```

2. Clean Docker images:
   ```bash
   docker system prune -a
   ```

3. Remove old backups:
   ```bash
   rm backups/whatsapp-ai-backup-*.tar.gz
   ```

## Security Notes

- Keep `.env` file secure - it contains admin phone numbers
- Only authorized phone numbers can use the assistant
- Grant permissions carefully (see Permission Model below)
- Regular backups recommended
- Keep Docker and Pi OS updated

## Permission Model

Permissions are in `category:action` format:

- `system:read` - View system status
- `docker:read` - View Docker containers
- `docker:write` - Manage Docker containers
- `gateway:read` - View gateway status

Admin phones (from `.env`) automatically get all permissions.

## Performance

Expected resource usage on Raspberry Pi 4:
- RAM: 400-600MB
- CPU: 10-30% idle, up to 80% during LLM calls
- Disk: ~100MB + database growth
- Network: Varies with usage

## Advanced Configuration

### Custom Gateway Models

Modify gateway configuration to expose specific models. The assistant will use whatever models are available through the gateway.

### Additional Admin Phones

Add multiple admins in `.env`:
```bash
ADDITIONAL_ADMIN_PHONES=+1234567890,+1987654321
```

### Docker Network

The assistant creates a bridge network `whatsapp-ai` for inter-container communication. To connect other services:

```yaml
networks:
  - whatsapp-ai

external: true
```

## Support

For issues or questions:
1. Check logs first
2. Run health check
3. Review troubleshooting section
4. Check GitHub issues
