const fs = require('fs');
const path = require('path');
const FormData = require('form-data');
const Mailgun = require('mailgun.js');

const mailgun = new Mailgun(FormData);
let mg;

try {
    if (process.env.MAILGUN_API_KEY) {
        mg = mailgun.client({
            username: 'api',
            key: process.env.MAILGUN_API_KEY,
            url: process.env.MAILGUN_Endpoint || "https://api.mailgun.net" // Defaults to US
        });
    } else {
        console.warn('MAILGUN_API_KEY not found in env. Email sending will be mocked.');
    }
} catch (e) {
    console.warn('Failed to initialize Mailgun client:', e.message);
}

// Load email content configuration
let emailContentConfig = {};
try {
    emailContentConfig = require('../config/emailContent.json');
} catch (e) {
    console.warn('Failed to load emailContent.json:', e.message);
}

function getContent(key, data) {
    const config = emailContentConfig[key] || { subject: 'Notification', text: 'Message from Coins Kids Shop' };
    let text = config.text;

    // Replace placeholders in text
    if (text && data) {
        for (const [k, v] of Object.entries(data)) {
            text = text.replace(new RegExp(`{{${k}}}`, 'g'), v);
        }
    }

    return {
        subject: config.subject,
        text: text
    };
}

function getTemplate(templateName, data) {
    try {
        const templatePath = path.join(__dirname, '../templates', `${templateName}.html`);
        let content = fs.readFileSync(templatePath, 'utf8');

        // Simple placeholder replacement {{key}}
        for (const [key, value] of Object.entries(data)) {
            content = content.replace(new RegExp(`{{${key}}}`, 'g'), value);
        }

        return content;
    } catch (error) {
        console.error(`Error loading template ${templateName}:`, error);
        return null;
    }
}

async function sendEmail({ to, subject, html, text }) {
    const domain = process.env.MAILGUN_DOMAIN;
    const from = process.env.MAILGUN_FROM || `Coins Kids Shop <postmaster@${domain}>`;

    console.log(`[EMAIL SENDING] To: ${to}, Subject: ${subject}`);

    if (mg && domain) {
        try {
            const msgData = {
                from: from,
                to: Array.isArray(to) ? to : [to],
                subject: subject,
                text: text || 'Please enable HTML to view this message',
                html: html
            };

            const response = await mg.messages.create(domain, msgData);

            console.log(`[EMAIL SENT] ID: ${response.id}, Status: ${response.status}`);
            return { success: true, id: response.id };
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
${text || '(HTML Content)'}
========================================
`;
        const logFile = path.join(DATA_DIR, 'emails.log');
        if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
        fs.appendFileSync(logFile, logEntry);

        console.log('[EMAIL MOCK] Saved to emails.log (Mailgun not configured)');
        return { success: true, mock: true };
    }
}

async function sendVerificationEmail(to, link) {
    const key = 'verificationEmail';
    const html = getTemplate(key, { link });
    const { subject, text } = getContent(key, { link });

    return sendEmail({
        to,
        subject,
        html,
        text
    });
}

async function sendPasswordResetEmail(to, link) {
    const key = 'passwordResetEmail';
    const html = getTemplate(key, { link });
    const { subject, text } = getContent(key, { link });

    return sendEmail({
        to,
        subject,
        html,
        text
    });
}

async function sendSuperAdminRecoveryEmail(to, password, loginLink) {
    const key = 'superAdminRecovery';
    const html = getTemplate(key, { password, loginLink });
    const { subject, text } = getContent(key, { password, loginLink });

    return sendEmail({
        to,
        subject,
        html,
        text
    });
}

module.exports = {
    sendEmail,
    sendVerificationEmail,
    sendPasswordResetEmail,
    sendSuperAdminRecoveryEmail
};
