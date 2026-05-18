const express = require('express');
const router = express.Router();
const logger = require('../logger');

module.exports = (whatsappClient) => {
    router.post('/', async (req, res) => {
        try {
            const { to, message } = req.body;

            if (!to || !message) {
                return res.status(400).json({
                    error: 'Missing required fields: to, message'
                });
            }

            await whatsappClient.sendMessage(to, message);

            res.json({
                success: true,
                to,
                message: 'Message sent successfully'
            });
        } catch (error) {
            logger.error('Error sending message:', error);
            res.status(500).json({
                error: error.message
            });
        }
    });

    return router;
};
