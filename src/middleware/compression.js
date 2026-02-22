const zlib = require('zlib');

/**
 * Middleware for Gzip compression
 * Wraps res.end and res.writeHead to compress the response body if applicable
 */
function compressionMiddleware(req, res, next) {
    const acceptEncoding = req.headers['accept-encoding'] || '';
    if (!acceptEncoding.includes('gzip')) {
        return next();
    }

    const originalEnd = res.end;
    const originalWriteHead = res.writeHead;
    const originalWrite = res.write;

    let chunks = [];

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

        // Don't compress small responses
        if (buffer.length < 512) {
            if (res.statusCode) originalWriteHead.call(res, res.statusCode);
            return originalEnd.call(res, buffer);
        }

        zlib.gzip(buffer, (err, compressed) => {
            if (err) {
                // Fallback to original
                if (res.statusCode) originalWriteHead.call(res, res.statusCode);
                return originalEnd.call(res, buffer);
            }

            res.setHeader('Content-Encoding', 'gzip');
            res.setHeader('Content-Length', compressed.length);

            if (res.statusCode) originalWriteHead.call(res, res.statusCode);
            originalEnd.call(res, compressed);
        });
    };

    next();
}

module.exports = compressionMiddleware;
