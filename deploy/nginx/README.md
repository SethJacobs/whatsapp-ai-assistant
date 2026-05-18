# Nginx Configuration for WhatsApp AI Assistant

## Installation

Copy these config files to your nginx conf.d directory:

```bash
cd ~/home-server
sudo cp whatsapp-ai-assistant/deploy/nginx/*.conf nginx/conf.d/
sudo docker compose restart nginx-proxy
```

Or if using symlinks:
```bash
cd ~/home-server/nginx/conf.d
sudo ln -s ../../whatsapp-ai-assistant/deploy/nginx/whatsapp-bridge.conf .
sudo ln -s ../../whatsapp-ai-assistant/deploy/nginx/whatsapp-ai-backend.conf .
sudo docker compose restart nginx-proxy
```

## Domains

### WhatsApp Bridge
- **URL:** http://whatsapp.jacobsfamjam.dpdns.org
- **Purpose:** QR code access, bridge management
- **Endpoints:**
  - `/` - Bridge API
  - `/bridge/qr` - Get QR code for WhatsApp authentication
  - `/bridge/health` - Health check
  - `/bridge/send` - Send messages (POST)

### WhatsApp AI Backend
- **URL:** http://whatsapp-api.jacobsfamjam.dpdns.org
- **Purpose:** Backend API, health monitoring
- **Endpoints:**
  - `/actuator/health` - Spring Boot health check
  - `/webhook/message` - WhatsApp webhook (internal only)

## Security Notes

1. **Webhook Protection:** The `/webhook` endpoint is restricted to internal networks only
2. **Health Checks:** Health check endpoints don't write to access logs
3. **No External Auth:** Consider adding authentication for production use

## DNS Configuration

Add these A records to your DDNS:
```
whatsapp.jacobsfamjam.dpdns.org → Your Pi's IP
whatsapp-api.jacobsfamjam.dpdns.org → Your Pi's IP
```

Or use wildcards:
```
*.jacobsfamjam.dpdns.org → Your Pi's IP
```

## Internal vs External Access

**Internal (from home network):**
- WhatsApp Bridge: http://10.10.200.30:3000
- Backend: http://10.10.200.31:8081

**External (from internet):**
- WhatsApp Bridge: http://whatsapp.jacobsfamjam.dpdns.org
- Backend: http://whatsapp-api.jacobsfamjam.dpdns.org

## Testing

```bash
# Test WhatsApp Bridge
curl http://whatsapp.jacobsfamjam.dpdns.org/bridge/health

# Get QR code
curl http://whatsapp.jacobsfamjam.dpdns.org/bridge/qr

# Test Backend
curl http://whatsapp-api.jacobsfamjam.dpdns.org/actuator/health
```

## Troubleshooting

**502 Bad Gateway:**
- Check containers are running: `docker compose ps`
- Check internal IPs: `docker inspect whatsapp-bridge | grep IPAddress`
- Check nginx logs: `docker compose logs nginx-proxy`

**Timeout:**
- Backend may be processing LLM request (up to 180s timeout)
- Check backend logs: `docker compose logs whatsapp-ai-backend`

**404 Not Found:**
- Verify DNS is resolving to your Pi
- Check nginx config syntax: `docker compose exec nginx-proxy nginx -t`
- Restart nginx: `docker compose restart nginx-proxy`
