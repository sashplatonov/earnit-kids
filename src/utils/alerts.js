const config = require('../config');
const logger = require('./logger');
const { sendErrorAlertEmail } = require('../services/emailService');

/**
 * Send an alert message to Telegram
 * @param {string} message - The message to send
 * @returns {Promise<boolean>} - Success or failure
 */
async function sendTelegramMessage(message) {
    if (!config.TELEGRAM.ENABLED || !config.TELEGRAM.TOKEN || !config.TELEGRAM.CHAT_ID) {
        return false;
    }

    try {
        const url = `https://api.telegram.org/bot${config.TELEGRAM.TOKEN}/sendMessage`;
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                chat_id: config.TELEGRAM.CHAT_ID,
                text: message,
                parse_mode: 'HTML',
                disable_web_page_preview: true
            }),
        });

        if (!response.ok) {
            const error = await response.json();
            logger.warn({ error }, 'Failed to send Telegram message');
            return false;
        }

        return true;
    } catch (err) {
        logger.warn({ err: err.message }, 'Error sending Telegram message');
        return false;
    }
}

/**
 * Send an alert for an error via all enabled channels
 * @param {Error} err - The error object
 * @param {string} context - Optional context (e.g., reqId, method, url)
 */
async function sendAlert(err, context = '') {
    if (config.env === 'test') return;

    const env = config.env.toUpperCase();
    const appUrl = process.env.APP_URL || 'unknown';

    // 1. Prepare and send Telegram message
    if (config.TELEGRAM.ENABLED) {
        let message = `🚨 <b>[${env}] SERVER ERROR</b>\n\n`;
        message += `<b>App:</b> Coins Kids Shop\n`;
        message += `<b>URL:</b> ${appUrl}\n`;
        if (context) message += `<b>Context:</b> ${context}\n`;
        message += `<b>Message:</b> <code>${err.message}</code>\n\n`;

        if (err.stack) {
            const stackLines = err.stack.split('\n').slice(0, 5).join('\n');
            message += `<b>Stack:</b>\n<pre>${stackLines}</pre>`;
        }

        sendTelegramMessage(message).catch(e => {
            logger.warn({ err: e.message }, 'Failed to send Telegram alert');
        });
    }

    // 2. Send Email alert
    if (config.ENABLE_EMAIL_ALERTS) {
        sendErrorAlertEmail(err, context).catch(e => {
            logger.warn({ err: e.message }, 'Failed to send Email alert');
        });
    }
}

module.exports = {
    sendTelegramMessage,
    sendAlert
};
