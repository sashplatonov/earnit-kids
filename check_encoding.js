
const { query, pool } = require('./src/db/connection');

async function check() {
    try {
        const res = await query('SHOW client_encoding');
        console.log('Client encoding:', res.rows[0].client_encoding);
        
        const pathRes = await query('SHOW search_path');
        console.log('Search path:', pathRes.rows[0].search_path);
        
        const serverEnc = await query('SELECT pg_encoding_to_char(encoding) FROM pg_database WHERE datname = current_database()');
        console.log('Server database encoding:', serverEnc.rows[0].pg_encoding_to_char);
    } catch (err) {
        console.error('Error:', err.message);
    } finally {
        await pool.end();
    }
}

check();
