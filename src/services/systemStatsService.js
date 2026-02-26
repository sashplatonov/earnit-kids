/** @file System statistics helpers for the super-admin dashboard */
const os = require('os');

function toFixedNumber(value, decimals = 2) {
    if (typeof value !== 'number' || Number.isNaN(value)) {
        return null;
    }
    return parseFloat(value.toFixed(decimals));
}

function getSystemOverview() {
    const memory = process.memoryUsage();
    const uptimeSec = Math.round(process.uptime());
    const [load1, load5, load15] = os.loadavg();

    return {
        process: {
            uptimeSec,
            rssBytes: memory.rss,
            heapUsedBytes: memory.heapUsed,
            heapTotalBytes: memory.heapTotal
        },
        os: {
            loadAvg1: toFixedNumber(load1),
            loadAvg5: toFixedNumber(load5),
            loadAvg15: toFixedNumber(load15),
            cpuCount: os.cpus().length,
            totalMemBytes: os.totalmem(),
            freeMemBytes: os.freemem()
        }
    };
}

module.exports = {
    getSystemOverview
};
