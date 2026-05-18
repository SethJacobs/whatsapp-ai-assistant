# WhatsApp AI Assistant

Lightweight WhatsApp-based AI assistant that integrates with pi-ai-gateway for intelligent routing between cloud and local LLM models.

## Architecture

```
WhatsApp Mobile App
    ↓
Node.js WhatsApp Bridge (Port 3000)
    ↓
Java Spring Boot Backend (Port 8081)
    ↓
Pi-AI-Gateway (via nginx DNS)
```

## Features

- 🤖 Conversational AI via WhatsApp
- 🔧 Extensible tool/skill system
- 🔒 Phone number allowlist security
- 📊 System monitoring tools
- 🐳 Docker container management
- 💬 Multi-turn conversations with context

## Quick Start

### Prerequisites

- Node.js 18+
- Java 21+
- Docker & Docker Compose
- Pi-AI-Gateway running

### Phase 1: WhatsApp Bridge (Current)

```bash
cd whatsapp-bridge
npm install
npm start
```

The bridge will display a QR code - scan it with WhatsApp mobile app to authenticate.

### Test the Bridge

```bash
# Get QR code
curl http://localhost:3000/bridge/qr

# Check health
curl http://localhost:3000/bridge/health

# Send a test message (after authentication)
curl -X POST http://localhost:3000/bridge/send \
  -H 'Content-Type: application/json' \
  -d '{"to": "+1234567890", "message": "Hello from bridge!"}'
```

## API Endpoints

### WhatsApp Bridge (Port 3000)

- `GET /` - API information
- `POST /bridge/send` - Send WhatsApp message
- `GET /bridge/qr` - Get QR code for authentication
- `POST /bridge/webhook` - Configure webhook for incoming messages
- `GET /bridge/health` - Health check

## Development Roadmap

- [x] Phase 1: Node.js WhatsApp Bridge ✅
- [x] Phase 2: Java Backend Foundation ✅
- [x] Phase 3: Pi-AI-Gateway Integration ✅
- [ ] Phase 4: Tool System
- [ ] Phase 5: Message Routing & Commands
- [ ] Phase 6: Security & Deployment

## Deployment on Pi

```bash
# Clone repo on Pi
git clone <repo-url> /home/pi/whatsapp-ai-assistant
cd /home/pi/whatsapp-ai-assistant

# Create .env file with your configuration
cat > .env <<EOF
ADMIN_PHONE=+1234567890
GATEWAY_BASE_URL=http://ai-gateway.local
EOF

# Run installation script
./deploy/install.sh

# The script will:
# 1. Build Docker images
# 2. Start WhatsApp bridge and display QR code
# 3. Wait for you to scan QR code
# 4. Start Java backend
# 5. Verify all services are running
```

## Architecture Status

**Phase 1, 2 & 3 Complete:**
- ✅ WhatsApp bridge with QR authentication
- ✅ Message sending/receiving via REST API
- ✅ Java Spring Boot backend with SQLite
- ✅ Database entities for conversations & security
- ✅ Webhook processing with async handling
- ✅ Docker Compose orchestration
- ✅ **Pi-AI-Gateway integration**
- ✅ **OpenAI-compatible request/response models**
- ✅ **Multi-turn conversation with context (20 messages, 2hr timeout)**
- ✅ **Circuit breaker resilience**
- ✅ **Fully functional AI chat via WhatsApp**

**Coming Next (Phase 4):**
- Tool/skill system with plugin architecture
- Tool registry and execution engine
- Initial built-in tools (system info, Docker, gateway status)
- Security permissions model

## License

MIT
