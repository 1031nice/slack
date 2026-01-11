const { spawn, execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const { Client } = require('@stomp/stompjs');
const SockJS = require('sockjs-client');

/**
 * Orchestrator for Massive Fan-out Test
 * 1. Spawns N 'mass-client.js' processes
 * 2. Connects a Sender client
 * 3. Sends a message
 * 4. Aggregates results
 */

const CONFIG = {
    totalClients: 1000,      // Scale this up (e.g., 1000, 5000)
    clientsPerProcess: 200,  // Node.js limits (keep under 500 per process usually)
    serverUrl: 'http://localhost:9000/ws', // Target single server for now
    channelId: 'fanout-test-1',
    token: 'test-user',
    waitAfterConnectSec: 10 // Increased wait time for 1000 connections
};

async function run() {
    console.log(`[Orchestrator] Preparing test with ${CONFIG.totalClients} clients...`);
    
    // 1. Setup logs
    const logsDir = path.join(__dirname, 'logs');
    if (!fs.existsSync(logsDir)) fs.mkdirSync(logsDir, { recursive: true });

    // 2. Spawn Clients
    const processes = [];
    const processCount = Math.ceil(CONFIG.totalClients / CONFIG.clientsPerProcess);
    
    console.log(`[Orchestrator] Spawning ${processCount} processes (${CONFIG.clientsPerProcess} clients each)...`);

    for (let i = 0; i < processCount; i++) {
        const startId = i * CONFIG.clientsPerProcess;
        const count = Math.min(CONFIG.clientsPerProcess, CONFIG.totalClients - startId);
        
        const p = spawn('node', [
            'mass-client.js',
            startId.toString(),
            count.toString(),
            CONFIG.serverUrl,
            CONFIG.channelId,
            CONFIG.token
        ], {
            stdio: ['ignore', 'pipe', 'pipe'] // Pipe stdout to capture JSON report later
        });
        
        // Collect output buffer
        p.stdoutData = '';
        p.stdout.on('data', (d) => { p.stdoutData += d.toString(); });
        p.stderr.on('data', (d) => { console.error(`[Proc ${i}] ${d}`); });
        
        processes.push(p);
    }

    // Wait for connections (heuristic)
    console.log(`[Orchestrator] Waiting ${CONFIG.waitAfterConnectSec}s for connections to stabilize...`);
    await new Promise(r => setTimeout(r, CONFIG.waitAfterConnectSec * 1000));

    // 3. Send Message
    console.log('[Orchestrator] Sending Broadcast Message...');
    await sendMessage(CONFIG.serverUrl, CONFIG.channelId, CONFIG.token);
    
    // Wait for delivery
    console.log('[Orchestrator] Waiting 5s for delivery...');
    await new Promise(r => setTimeout(r, 5000));

    // 4. Terminate and Collect
    console.log('[Orchestrator] Stopping clients and collecting stats...');
    let totalReceived = 0;
    let allLatencies = [];

    for (const p of processes) {
        p.kill('SIGINT'); // Trigger report generation in mass-client.js
        await new Promise(r => p.on('exit', r)); // Wait for exit
        
        // Parse the last line of stdout which should be the JSON report
        const lines = p.stdoutData.trim().split('\n');
        const lastLine = lines[lines.length - 1];
        try {
            const report = JSON.parse(lastLine);
            totalReceived += report.received;
            // In a real rigorous test we'd merge raw histograms, but here we just grab counts
            // For true latency, we'd need to log to files and merge. 
            // Let's just trust the individual reports for now.
            console.log(`   Process ${report.processRange}: Connected=${report.connected}, Received=${report.received}, P99=${report.latency.p99}ms`);
        } catch (e) {
            console.error('Failed to parse report from process:', lastLine);
        }
    }

    console.log(`\n=== FINAL RESULT ===`);
    console.log(`Total Clients: ${CONFIG.totalClients}`);
    console.log(`Total Received: ${totalReceived}`);
    console.log(`Delivery Rate: ${(totalReceived/CONFIG.totalClients*100).toFixed(1)}%`);
}

function sendMessage(url, channelId, token) {
    return new Promise((resolve) => {
        const client = new Client({
            webSocketFactory: () => new SockJS(url.replace('ws://', 'http://')),
            connectHeaders: { Authorization: `Bearer ${token}` },
            onConnect: () => {
                const payload = {
                    content: `Fanout Test ${Date.now()}`,
                    // Ideally we inject a precise 'createdAt' here if the server trusts us, 
                    // but usually the server stamps it. We'll rely on server timestamp for now.
                };
                client.publish({ destination: '/app/message.send', body: JSON.stringify({
                    channelId: channelId,
                    content: payload.content,
                    type: 'MESSAGE'
                })});
                client.deactivate();
                resolve();
            }
        });
        client.activate();
    });
}

run();
