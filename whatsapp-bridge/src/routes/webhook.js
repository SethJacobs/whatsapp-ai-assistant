const express = require('express');
const router = express.Router();
const logger = require('../logger');

module.exports = (whatsappClient) => {
    router.post('/', (req, res) => {
        try {
            const { webhook_url } = req.body;

            if (!webhook_url) {
                return res.status(400).json({
                    error: 'Missing webhook_url'
                });
            }

            whatsappClient.setWebhook(webhook_url);

            res.json({
                success: true,
                webhook_url,
                message: 'Webhook URL configured'
            });
        } catch (error) {
            logger.error('Error setting webhook:', error);
            res.status(500).json({
                error: error.message
            });
        }
    });

    return router;
};
