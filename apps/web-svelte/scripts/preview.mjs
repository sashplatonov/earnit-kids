import process from 'node:process';

function applyCliOverrides(argv) {
    for (let index = 0; index < argv.length; index += 1) {
        const arg = argv[index];

        if (arg === '--host' && argv[index + 1]) {
            process.env.HOST = argv[index + 1];
            index += 1;
            continue;
        }

        if (arg === '--port' && argv[index + 1]) {
            process.env.PORT = argv[index + 1];
            index += 1;
        }
    }
}

applyCliOverrides(process.argv.slice(2));

if (!process.env.HOST) {
    process.env.HOST = '0.0.0.0';
}

if (!process.env.PORT) {
    process.env.PORT = '4174';
}

await import('../build/index.js');