const express = require('express');
const router = express.Router();

module.exports = (whatsappClient) => {
    router.get('/', async (req, res) => {
        const status = whatsappClient.getStatus();
        const info = await whatsappClient.getInfo();

        res.json({
            status: status.ready ? 'ok' : 'not_ready',
            ...status,
            info
        });
    });

    return router;
};
