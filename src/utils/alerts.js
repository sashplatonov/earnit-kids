/** @file Alerts utility helpers */
const fs = require('fs');
const path = require('path');
const config = require('../config');
const logger = require('./logger');
const { sendErrorAlertEmail } = require('../services/emailService');

/**
 * Send an alert message to Telegram
 * @param {string} message - The message to send
 * @returns {Promise<boolean>} - Success or failure
 */
/**
 * Send an alert message to Telegram
 * @param {string} message - The message to send
 * @returns {Promise<boolean>} - Success or failure
 */
async function sendTelegramMessage(message, options = {}) {
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
                disable_web_page_preview: true,
                disable_notification: options.silent || false
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
 * Send a document/file to Telegram
 * @param {string} filePath - Path to the file
 * @param {string} caption - Optional caption
 * @returns {Promise<boolean>} - Success or failure
 */
function buildTelegramDocumentFormData(filePath, caption, options = {}) {
    const formData = new FormData();
    formData.append('chat_id', config.TELEGRAM.CHAT_ID);
    const fileContent = fs.readFileSync(filePath);
    formData.append('document', new Blob([fileContent]), path.basename(filePath));
    if (caption) formData.append('caption', caption);
    const parseMode = options.parseMode ?? 'HTML';
    if (parseMode) formData.append('parse_mode', parseMode);
    if (options.silent) formData.append('disable_notification', 'true');
    return formData;
}

async function sendTelegramDocument(filePath, caption = '', options = {}) {
    if (!config.TELEGRAM.ENABLED || !config.TELEGRAM.TOKEN || !config.TELEGRAM.CHAT_ID) {
        return false;
    }

    try {
        const formData = buildTelegramDocumentFormData(filePath, caption, options);
        const url = `https://api.telegram.org/bot${config.TELEGRAM.TOKEN}/sendDocument`;
        const response = await fetch(url, {
            method: 'POST',
            body: formData,
        });

        if (!response.ok) {
            const error = await response.json();
            logger.warn({ error }, 'Failed to send Telegram document');
            return false;
        }

        return true;
    } catch (err) {
        logger.warn({ err: err.message }, 'Error sending Telegram document');
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
    sendTelegramDocument,
    sendAlert
};
