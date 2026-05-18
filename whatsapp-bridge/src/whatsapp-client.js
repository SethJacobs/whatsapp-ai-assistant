const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode');
const qrcodeTerminal = require('qrcode-terminal');
const logger = require('./logger');

class WhatsAppClient {
    constructor() {
        this.client = null;
        this.qrCode = null;
        this.isReady = false;
        this.webhookUrl = null;
        this.initializeClient();
    }

    initializeClient() {
        this.client = new Client({
            authStrategy: new LocalAuth({
                dataPath: './.wwebjs_auth'
            }),
            puppeteer: {
                headless: true,
                args: [
                    '--no-sandbox',
                    '--disable-setuid-sandbox',
                    '--disable-dev-shm-usage',
                    '--disable-accelerated-2d-canvas',
                    '--no-first-run',
                    '--no-zygote',
                    '--disable-gpu'
                ]
            }
        });

        this.setupEventHandlers();
    }

    setupEventHandlers() {
        this.client.on('qr', (qr) => {
            logger.info('QR Code received, scan to authenticate');

            // Display in terminal
            qrcodeTerminal.generate(qr, { small: true });

            // Generate data URI for API
            qrcode.toDataURL(qr, (err, url) => {
                if (err) {
                    logger.error('Failed to generate QR code:', err);
                } else {
                    this.qrCode = url;
                }
            });
        });

        this.client.on('ready', () => {
            logger.info('WhatsApp client is ready!');
            this.isReady = true;
            this.qrCode = null;
        });

        this.client.on('authenticated', () => {
            logger.info('WhatsApp client authenticated');
        });

        this.client.on('auth_failure', (msg) => {
            logger.error('Authentication failure:', msg);
        });

        this.client.on('disconnected', (reason) => {
            logger.warn('WhatsApp client disconnected:', reason);
            this.isReady = false;
        });

        this.client.on('message', async (message) => {
            await this.handleIncomingMessage(message);
        });

        this.client.on('message_create', (message) => {
            // Log sent messages
            if (message.fromMe) {
                logger.debug('Message sent:', message.body);
            }
        });
    }

    async handleIncomingMessage(message) {
        try {
            // Ignore group messages and status updates
            if (message.from.includes('@g.us') || message.from === 'status@broadcast') {
                return;
            }

            logger.info(`Received message from ${message.from}: ${message.body}`);

            // Forward to webhook if configured
            if (this.webhookUrl) {
                await this.forwardToWebhook(message);
            }
        } catch (error) {
            logger.error('Error handling incoming message:', error);
        }
    }

    async forwardToWebhook(message) {
        try {
            const contact = await message.getContact();

            const payload = {
                from: message.from,
                name: contact.pushname || contact.name || message.from,
                text: message.body,
                timestamp: message.timestamp,
                isGroup: message.from.includes('@g.us')
            };

            const response = await fetch(this.webhookUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                logger.error(`Webhook returned ${response.status}`);
            }
        } catch (error) {
            logger.error('Error forwarding to webhook:', error);
        }
    }

    async initialize() {
        logger.info('Initializing WhatsApp client...');
        await this.client.initialize();
    }

    async sendMessage(to, message) {
        if (!this.isReady) {
            throw new Error('WhatsApp client is not ready');
        }

        // Format phone number (ensure it's in WhatsApp format)
        let chatId = to;
        if (!to.includes('@')) {
            chatId = to.replace(/[^0-9]/g, '') + '@c.us';
        }

        await this.client.sendMessage(chatId, message);
        logger.info(`Message sent to ${chatId}`);
    }

    setWebhook(url) {
        this.webhookUrl = url;
        logger.info(`Webhook URL set to: ${url}`);
    }

    getStatus() {
        return {
            ready: this.isReady,
            hasQr: !!this.qrCode,
            authenticated: this.client?.authStrategy?.authenticated || false
        };
    }

    getQrCode() {
        return {
            qr_code: this.qrCode,
            status: this.isReady ? 'ready' : (this.qrCode ? 'pending' : 'initializing')
        };
    }

    async getInfo() {
        if (!this.isReady) {
            return null;
        }

        const info = this.client.info;
        const battery = await this.client.getBatteryLevel();

        return {
            phone: info.wid.user,
            platform: info.platform,
            pushname: info.pushname,
            battery: battery
        };
    }
}

module.exports = WhatsAppClient;
