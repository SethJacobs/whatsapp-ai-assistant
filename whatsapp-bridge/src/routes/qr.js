const express = require('express');
const router = express.Router();

module.exports = (whatsappClient) => {
    router.get('/', (req, res) => {
        const qrData = whatsappClient.getQrCode();

        // Check if request is from a browser (Accept: text/html)
        const acceptsHtml = req.accepts('html');

        if (acceptsHtml && qrData.qr_code) {
            // Return HTML page with QR code displayed
            const html = `
<!DOCTYPE html>
<html>
<head>
    <title>WhatsApp QR Code</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            margin: 0;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        .container {
            background: white;
            padding: 2rem;
            border-radius: 1rem;
            box-shadow: 0 10px 40px rgba(0,0,0,0.2);
            text-align: center;
            color: #333;
        }
        h1 {
            margin: 0 0 1rem 0;
            font-size: 1.5rem;
        }
        .qr-code {
            margin: 1.5rem 0;
            padding: 1rem;
            background: white;
            border-radius: 0.5rem;
        }
        .qr-code img {
            max-width: 100%;
            height: auto;
            display: block;
        }
        .status {
            display: inline-block;
            padding: 0.5rem 1rem;
            border-radius: 2rem;
            font-weight: 600;
            margin-top: 1rem;
        }
        .status.pending {
            background: #fef3c7;
            color: #92400e;
        }
        .status.ready {
            background: #d1fae5;
            color: #065f46;
        }
        .instructions {
            margin-top: 1.5rem;
            font-size: 0.9rem;
            color: #666;
            line-height: 1.6;
        }
        .refresh {
            margin-top: 1rem;
            color: #667eea;
            text-decoration: none;
            font-weight: 500;
        }
        .refresh:hover {
            text-decoration: underline;
        }
    </style>
    <meta http-equiv="refresh" content="5">
</head>
<body>
    <div class="container">
        <h1>📱 WhatsApp Authentication</h1>

        ${qrData.status === 'ready' ?
            '<p style="color: #059669; font-size: 1.2rem;">✅ Connected!</p>' :
            `<div class="qr-code">
                <img src="${qrData.qr_code}" alt="WhatsApp QR Code" />
            </div>
            <div class="status ${qrData.status}">
                Status: ${qrData.status}
            </div>
            <div class="instructions">
                <p>📸 <strong>Scan this QR code with WhatsApp:</strong></p>
                <ol style="text-align: left; display: inline-block;">
                    <li>Open WhatsApp on your phone</li>
                    <li>Tap <strong>Menu</strong> or <strong>Settings</strong></li>
                    <li>Tap <strong>Linked Devices</strong></li>
                    <li>Tap <strong>Link a Device</strong></li>
                    <li>Point your phone at this screen</li>
                </ol>
            </div>`
        }

        <p style="margin-top: 1.5rem; color: #999; font-size: 0.8rem;">
            Page auto-refreshes every 5 seconds
        </p>
    </div>
</body>
</html>`;
            res.send(html);
        } else {
            // Return JSON for API calls
            res.json(qrData);
        }
    });

    return router;
};
