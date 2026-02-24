/** @file Cache utility helpers */
/**
 * Simple in-memory cache with TTL and prefix-based invalidation
 */
class LocalCache {
    constructor(defaultTtl = 60000) {
        this.cache = new Map();
        this.defaultTtl = defaultTtl;
    }

    /**
     * Get value from cache
     * @param {string} key 
     * @returns {any}
     */
    get(key) {
        const entry = this.cache.get(key);
        if (!entry) return null;

        if (Date.now() > entry.expiry) {
            this.cache.delete(key);
            return null;
        }

        return entry.value;
    }

    /**
     * Set value in cache
     * @param {string} key 
     * @param {any} value 
     * @param {number} ttl - milliseconds
     */
    set(key, value, ttl = this.defaultTtl) {
        this.cache.set(key, {
            value,
            expiry: Date.now() + ttl
        });
    }

    /**
     * Delete specific key
     * @param {string} key 
     */
    delete(key) {
        this.cache.delete(key);
    }

    /**
     * Delete all keys starting with prefix
     * Useful for invalidating all family data entries: "familyData:familyId"
     * @param {string} prefix 
     */
    invalidatePrefix(prefix) {
        for (const key of this.cache.keys()) {
            if (key.startsWith(prefix)) {
                this.cache.delete(key);
            }
        }
    }

    clear() {
        this.cache.clear();
    }
}

module.exports = new LocalCache();
