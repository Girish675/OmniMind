import { OmniMindServer } from '../server/server.js';
import path from 'node:path';
import { spawn } from 'node:child_process';

const server = new OmniMindServer({
    port: 8080,
    staticDir: path.join(process.cwd(), 'web', 'dist')
});

async function runDesktop() {
    console.log('[OmniMind Desktop] Initializing local inference host...');
    const port = await server.start();
    const appUrl = `http://127.0.0.1:${port}`;

    console.log(`[OmniMind Desktop] Server running at ${appUrl}`);
    console.log('[OmniMind Desktop] Launching native desktop window...');

    // On Windows, if electron runtime is present, launch electron window
    // otherwise launch system default browser directly in app mode
    const electronExe = 'C:\\Users\\gpedired\\AppData\\Local\\Programs\\Antigravity\\Antigravity.exe';
    
    // Launch dedicated window or default browser
    let child;
    if (process.platform === 'win32') {
        // Launch Microsoft Edge or Chrome in app-window mode
        const chromePaths = [
            'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
            'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
        ];
        
        let browserExe = null;
        for (const p of chromePaths) {
            try {
                if (fs.existsSync(p)) {
                    browserExe = p;
                    break;
                }
            } catch (_) {}
        }

        if (browserExe) {
            child = spawn(browserExe, [`--app=${appUrl}`, '--window-size=1200,800'], {
                detached: false,
                stdio: 'ignore'
            });
        } else {
            // Fallback start command
            spawn('cmd', ['/c', 'start', appUrl], { stdio: 'ignore' });
        }
    }

    const cleanup = async () => {
        console.log('[OmniMind Desktop] Shutting down desktop host...');
        await server.stop();
        process.exit(0);
    };

    process.on('SIGINT', cleanup);
    process.on('SIGTERM', cleanup);
    if (child) {
        child.on('exit', cleanup);
    }
}

import fs from 'node:fs';
runDesktop().catch(err => {
    console.error('[OmniMind Desktop] Fatal error:', err);
    process.exit(1);
});
