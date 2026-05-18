const express = require('express');
const router = express.Router();

module.exports = (whatsappClient) => {
    router.get('/', (req, res) => {
        const qrData = whatsappClient.getQrCode();
        res.json(qrData);
    });

    return router;
};
