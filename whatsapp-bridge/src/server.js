const express = require('express');
const WhatsAppClient = require('./whatsapp-client');
const logger = require('./logger');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Initialize WhatsApp client
const whatsappClient = new WhatsAppClient();

// Routes
const sendRoute = require('./routes/send')(whatsappClient);
const qrRoute = require('./routes/qr')(whatsappClient);
const webhookRoute = require('./routes/webhook')(whatsappClient);
const healthRoute = require('./routes/health')(whatsappClient);

app.use('/bridge/send', sendRoute);
app.use('/bridge/qr', qrRoute);
app.use('/bridge/webhook', webhookRoute);
app.use('/bridge/health', healthRoute);

// Root endpoint
app.get('/', (req, res) => {
    res.json({
        name: 'WhatsApp Bridge API',
        version: '1.0.0',
        endpoints: {
            send: 'POST /bridge/send',
            qr: 'GET /bridge/qr',
            webhook: 'POST /bridge/webhook',
            health: 'GET /bridge/health'
        }
    });
});

// Error handler
app.use((err, req, res, next) => {
    logger.error('Unhandled error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: err.message
    });
});

// Start server
app.listen(PORT, async () => {
    logger.info(`WhatsApp Bridge listening on port ${PORT}`);

    // Initialize WhatsApp client
    try {
        await whatsappClient.initialize();
    } catch (error) {
        logger.error('Failed to initialize WhatsApp client:', error);
    }
});

// Graceful shutdown
process.on('SIGINT', async () => {
    logger.info('Shutting down gracefully...');
    if (whatsappClient.client) {
        await whatsappClient.client.destroy();
    }
    process.exit(0);
});

process.on('SIGTERM', async () => {
    logger.info('Shutting down gracefully...');
    if (whatsappClient.client) {
        await whatsappClient.client.destroy();
    }
    process.exit(0);
});
