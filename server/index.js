import { OmniMindServer } from './server.js';

function parseArgs() {
    const args = process.argv.slice(2);
    const options = {
        port: 8080,
        host: '127.0.0.1',
        lanMode: false,
        authToken: '',
        modelsDir: undefined
    };

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--port' && args[i + 1]) {
            options.port = parseInt(args[++i], 10);
        } else if (args[i] === '--host' && args[i + 1]) {
            options.host = args[++i];
        } else if (args[i] === '--lan') {
            options.lanMode = true;
        } else if (args[i] === '--auth' && args[i + 1]) {
            options.authToken = args[++i];
        } else if (args[i] === '--models-dir' && args[i + 1]) {
            options.modelsDir = args[++i];
        }
    }
    return options;
}

const options = parseArgs();
const server = new OmniMindServer(options);

server.start().then(port => {
    console.log(`========================================`);
    console.log(`OmniMind Local Inference Host v0.1.0`);
    console.log(`Listening on http://${options.lanMode ? '0.0.0.0' : options.host}:${port}`);
    console.log(`LAN Mode: ${options.lanMode ? 'Enabled' : 'Disabled (Localhost only)'}`);
    console.log(`Auth Required: ${options.authToken ? 'Yes' : 'No'}`);
    console.log(`========================================`);
});

process.on('SIGINT', async () => {
    console.log('\nShutting down OmniMind server gracefully...');
    await server.stop();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    await server.stop();
    process.exit(0);
});
