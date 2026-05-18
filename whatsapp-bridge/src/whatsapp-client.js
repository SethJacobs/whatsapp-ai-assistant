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
            // Ignore status updates only
            if (message.from === 'status@broadcast') {
                return;
            }

            const isGroup = message.from.includes('@g.us');
            const source = isGroup ? `group ${message.from}` : message.from;
            logger.info(`Received message from ${source}: ${message.body}`);

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
            const isGroup = message.from.includes('@g.us');

            let payload = {
                from: message.from,
                text: message.body,
                timestamp: message.timestamp,
                isGroup: isGroup
            };

            if (isGroup) {
                // Group message
                const chat = await message.getChat();
                const contact = await message.getContact();

                payload.groupId = message.from;
                payload.groupName = chat.name || 'Unknown Group';
                payload.participant = message.author; // Sender's phone number
                payload.name = contact.pushname || contact.name || message.author;

                logger.debug(`Group message: ${chat.name} from ${contact.pushname || message.author}`);
            } else {
                // Individual message
                const contact = await message.getContact();
                payload.name = contact.pushname || contact.name || message.from;
            }

            const response = await fetch(this.webhookUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                logger.error(`Webhook returned ${response.status}`);
            } else {
                logger.debug(`Message forwarded to webhook successfully`);
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

        // Format chat ID (ensure it's in WhatsApp format)
        let chatId = to;
        if (!to.includes('@')) {
            // Individual chat - add @c.us
            chatId = to.replace(/[^0-9]/g, '') + '@c.us';
        }
        // Groups already have @g.us, so pass through as-is

        await this.client.sendMessage(chatId, message);

        const isGroup = chatId.includes('@g.us');
        logger.info(`Message sent to ${isGroup ? 'group' : 'individual'} ${chatId}`);
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

        // Try to get battery level if available (not all versions support this)
        let battery = null;
        try {
            if (typeof this.client.getBatteryLevel === 'function') {
                battery = await this.client.getBatteryLevel();
            }
        } catch (error) {
            // Battery level not available, skip it
        }

        return {
            phone: info.wid.user,
            platform: info.platform,
            pushname: info.pushname,
            battery: battery
        };
    }
}

module.exports = WhatsAppClient;
