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

- [x] Phase 1: Node.js WhatsApp Bridge
- [ ] Phase 2: Java Backend Foundation
- [ ] Phase 3: Pi-AI-Gateway Integration
- [ ] Phase 4: Tool System
- [ ] Phase 5: Message Routing & Commands
- [ ] Phase 6: Security & Deployment

## License

MIT
