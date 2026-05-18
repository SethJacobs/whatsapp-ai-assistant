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

## Usage

### Commands

Send these commands via WhatsApp:

```
/help
  - Shows available commands and usage examples

/status
  - System info: CPU, memory, disk, uptime, temperature

/docker ps
  - List running Docker containers

/docker ps -a
  - List all containers (including stopped)

/gateway status
  - Check Pi-AI-Gateway health and routing info
```

### Natural Language

Just ask questions naturally:

```
"Show me running containers"
"What's the CPU usage?"
"Check the gateway status"
"How much free memory is there?"
```

The assistant will:
1. Try to parse as a command first (fast, deterministic)
2. Fall back to LLM consultation if not a command
3. Automatically call appropriate tools based on intent
4. Maintain conversation context for follow-up questions

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
- [x] Phase 4: Tool System ✅
- [x] Phase 5: Message Routing & Commands ✅
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

**Phase 1-5 Complete:**
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
- ✅ **Tool/skill system with plugin architecture**
- ✅ **ToolRegistry with auto-discovery**
- ✅ **ToolExecutor with timeout and security**
- ✅ **Multi-turn tool calling (OpenAI function calling format)**
- ✅ **Built-in tools: SystemInfo, DockerPs, GatewayStatus**
- ✅ **Permission-based security model**
- ✅ **Deterministic command parsing (/help, /status, /docker, /gateway)**
- ✅ **LLM fallback for natural language queries**
- ✅ **Intelligent message routing**

**Coming Next (Phase 6):**
- Production security hardening
- 1Password secrets integration
- Docker deployment scripts
- Systemd service files
- Pi installation automation

## License

MIT
