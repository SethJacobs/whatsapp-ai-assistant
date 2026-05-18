# Integrating WhatsApp AI Assistant into Home Server

## Add to Your docker-compose.yml

Add these services to your existing `~/home-server/docker-compose.yml`:

```yaml
  whatsapp-bridge:
    build:
      context: /home/pi/home-server/whatsapp-ai-assistant/whatsapp-bridge
      dockerfile: Dockerfile
    container_name: whatsapp-bridge
    restart: unless-stopped
    ports:
      - "3002:3000"  # External access for QR code
    volumes:
      - ./whatsapp-ai-assistant/whatsapp-bridge/.wwebjs_auth:/app/.wwebjs_auth
      - ./whatsapp-ai-assistant/whatsapp-bridge/.wwebjs_cache:/app/.wwebjs_cache
    environment:
      - NODE_ENV=production
      - WEBHOOK_URL=http://whatsapp-ai-backend:8081/webhook/message
      - LOG_LEVEL=info
    networks:
      vpn_net:
        ipv4_address: 10.10.200.30
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/bridge/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  whatsapp-ai-backend:
    build:
      context: /home/pi/home-server/whatsapp-ai-assistant/java-backend
      dockerfile: Dockerfile
    container_name: whatsapp-ai-backend
    restart: unless-stopped
    ports:
      - "8084:8081"  # External access if needed
    volumes:
      - ./whatsapp-ai-assistant/data:/app/data
      - /var/run/docker.sock:/var/run/docker.sock:ro
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - GATEWAY_BASE_URL=http://ai-gateway:8080
      - ADMIN_PHONE=${WHATSAPP_ADMIN_PHONE}
      - ADDITIONAL_ADMIN_PHONES=${WHATSAPP_ADDITIONAL_ADMINS:-}
      - DB_PATH=/app/data/whatsapp-ai.db
    networks:
      vpn_net:
        ipv4_address: 10.10.200.31
    depends_on:
      whatsapp-bridge:
        condition: service_healthy
      ai-gateway:
        condition: service_started
    mem_limit: 512m
    cpus: "1.0"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

## Setup Steps

### 1. Clone the repo on your Pi:
```bash
cd ~/home-server
git clone https://github.com/SethJacobs/whatsapp-ai-assistant.git
cd whatsapp-ai-assistant
```

### 2. Add to your `.env` file:
```bash
# WhatsApp AI Assistant
WHATSAPP_ADMIN_PHONE=+1234567890
WHATSAPP_ADDITIONAL_ADMINS=+1987654321
```

### 3. Create data directory:
```bash
mkdir -p ~/home-server/whatsapp-ai-assistant/data
mkdir -p ~/home-server/whatsapp-ai-assistant/whatsapp-bridge/.wwebjs_auth
mkdir -p ~/home-server/whatsapp-ai-assistant/whatsapp-bridge/.wwebjs_cache
```

### 4. Build and start services:
```bash
cd ~/home-server
docker compose build whatsapp-bridge whatsapp-ai-backend
docker compose up -d whatsapp-bridge whatsapp-ai-backend
```

### 5. Get QR code to authenticate:
```bash
curl http://localhost:3002/bridge/qr
```

Or access via browser: `http://raspberrypi.local:3002/bridge/qr`

### 6. Scan QR code with WhatsApp mobile app:
- Open WhatsApp on your phone
- Go to Settings > Linked Devices
- Tap "Link a Device"
- Scan the QR code

### 7. Test it:
Send a WhatsApp message to the authenticated number:
```
/help
```

## Network Integration

The services are integrated into your `vpn_net`:
- **whatsapp-bridge**: `10.10.200.30` (port 3002 external)
- **whatsapp-ai-backend**: `10.10.200.31` (port 8084 external)
- **ai-gateway**: `10.10.200.x` (already exists, used for LLM routing)

## Nginx Proxy Configuration (Optional)

Add to your nginx config if you want external access:

```nginx
# /home/pi/home-server/nginx/conf.d/whatsapp.conf

server {
    listen 80;
    server_name whatsapp.jacobsfamjam.dpdns.org;

    location / {
        proxy_pass http://10.10.200.30:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Then restart nginx:
```bash
docker compose restart nginx-proxy
```

## Homepage Dashboard Integration (Optional)

Add to your `~/home-server/homepage/config/services.yaml`:

```yaml
- AI Services:
    - WhatsApp Assistant:
        icon: whatsapp
        href: http://raspberrypi.local:3002
        description: WhatsApp AI Assistant Bridge
        widget:
          type: whatsapp-bridge
          url: http://10.10.200.30:3000
          endpoint: /bridge/health

    - AI Gateway:
        icon: mdi-router-wireless
        href: http://raspberrypi.local:8080
        description: Local/Cloud LLM Router
        widget:
          type: ai-gateway
          url: http://10.10.200.x:8080
          endpoint: /health
```

## Resource Usage

Expected on Raspberry Pi 4:
- **whatsapp-bridge**: ~150-200MB RAM, 5-10% CPU
- **whatsapp-ai-backend**: ~400-500MB RAM (limited to 512MB), 10-30% CPU
- **Combined**: ~600MB RAM, 15-40% CPU

Total home-server stack will be around 2-3GB RAM with all services.

## Monitoring with Homepage

The assistant integrates with your existing monitoring:
- Docker socket access for container status
- Health checks visible in homepage
- Logs via `docker compose logs -f whatsapp-ai-backend`

## Available Tools

The assistant can interact with your home server:
- `/status` - System info (CPU, memory, disk, temp)
- `/docker` - Show running containers
- `/docker all` - Show all containers
- `/gateway` - Check AI gateway status

Future integrations possible:
- Home Assistant control
- Laundry status notifications
- Paperless document queries
- Immich photo searches

## Auto-Start on Boot

The services use `restart: unless-stopped`, so they'll automatically start when your Pi boots up, just like your other services.

## Backup Integration

Add to your backup script:
```bash
# Backup WhatsApp AI data
tar -czf whatsapp-ai-backup-$(date +%Y%m%d).tar.gz \
  ~/home-server/whatsapp-ai-assistant/data \
  ~/home-server/whatsapp-ai-assistant/whatsapp-bridge/.wwebjs_auth
```

## Troubleshooting

**Services not starting:**
```bash
docker compose logs whatsapp-bridge
docker compose logs whatsapp-ai-backend
```

**Health check:**
```bash
curl http://10.10.200.30:3000/bridge/health
curl http://10.10.200.31:8081/actuator/health
```

**Rebuild after updates:**
```bash
cd ~/home-server
git -C whatsapp-ai-assistant pull
docker compose build whatsapp-bridge whatsapp-ai-backend
docker compose up -d whatsapp-bridge whatsapp-ai-backend
```

## Network Diagram

```
Internet
    ↓
nginx-proxy (80/443)
    ↓
vpn_net (10.10.200.0/24)
    ├─ pihole (10.10.200.x)
    ├─ homeassistant (8123)
    ├─ immich (10.10.200.10)
    ├─ ai-gateway (10.10.200.x:8080)
    ├─ whatsapp-bridge (10.10.200.30:3000)
    └─ whatsapp-ai-backend (10.10.200.31:8081)
         ↓
    Uses ai-gateway for LLM
    Uses Docker socket for tools
```

The WhatsApp assistant is now fully integrated with your home server infrastructure!
