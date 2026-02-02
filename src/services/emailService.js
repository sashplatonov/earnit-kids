const fs = require('fs');
const path = require('path');

let nodemailer;
try {
    nodemailer = require('nodemailer');
} catch (e) {
    console.warn('Nodemailer not found. Email will be logged to console and data/emails.log');
}

async function sendEmail({ to, subject, text, html }) {
    const host = process.env.EMAIL_HOST;
    const port = process.env.EMAIL_PORT || 587;
    const user = process.env.EMAIL_USER;
    const pass = process.env.EMAIL_PASS;
    const from = process.env.EMAIL_FROM || user;

    console.log(`[EMAIL SENDING] To: ${to}, Subject: ${subject}`);

    if (nodemailer && host && user && pass) {
        try {
            const transporter = nodemailer.createTransport({
                host: host,
                port: port,
                secure: port == 465,
                auth: {
                    user: user,
                    pass: pass
                }
            });

            const info = await transporter.sendMail({
                from: from,
                to: to,
                subject: subject,
                text: text,
                html: html
            });

            console.log(`[EMAIL SENT] Message ID: ${info.messageId}`);
            return { success: true, messageId: info.messageId };
        } catch (error) {
            console.error('[EMAIL ERROR]', error);
            return { success: false, error: error.message };
        }
    } else {
        const { DATA_DIR } = require('../config');
        const logEntry = `
========================================
Date: ${new Date().toISOString()}
To: ${to}
Subject: ${subject}
----------------------------------------
${text}
========================================
`;
        const logFile = path.join(DATA_DIR, 'emails.log');
        if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
        fs.appendFileSync(logFile, logEntry);

        console.log('[EMAIL MOCK] Email saved to emails.log in DATA_DIR. Configure SMTP in .env to send real emails.');
        return { success: true, mock: true };
    }
}

module.exports = { sendEmail };
