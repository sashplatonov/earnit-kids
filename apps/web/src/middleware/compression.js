/** @file Compression middleware — gzip wraps raw http.ServerResponse */
'use strict';

const zlib = require('zlib');

/**
 * Middleware for Gzip compression.
 * Wraps res.end and res.writeHead to compress the response body if applicable.
 */
function compressionMiddleware(req, res, next) {
    const acceptEncoding = req.headers['accept-encoding'] || '';
    if (!acceptEncoding.includes('gzip')) {
        return next();
    }

    const originalEnd = res.end.bind(res);
    const originalWriteHead = res.writeHead.bind(res);
    const originalWrite = res.write;

    const chunks = [];

    res.write = function (chunk) {
        if (chunk) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        return true;
    };

    res.writeHead = function (status, headers) {
        res.statusCode = status;
        if (headers) {
            for (const [key, value] of Object.entries(headers)) {
                res.setHeader(key, value);
            }
        }
    };

    res.end = function (chunk) {
        if (chunk) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));

        const buffer = Buffer.concat(chunks);

        if (buffer.length < 512) {
            if (res.statusCode) originalWriteHead(res.statusCode);
            return originalEnd(buffer);
        }

        zlib.gzip(buffer, (err, compressed) => {
            if (err) {
                if (res.statusCode) originalWriteHead(res.statusCode);
                return originalEnd(buffer);
            }

            res.setHeader('Content-Encoding', 'gzip');
            res.setHeader('Content-Length', compressed.length);

            if (res.statusCode) originalWriteHead(res.statusCode);
            originalEnd(compressed);
        });
    };

    next();
}

module.exports = compressionMiddleware;
