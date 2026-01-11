const { spawn } = require('child_process');
const Redis = require('ioredis');

/**
 * Orchestrator for Redis Direct Fan-out Test
 */

const CONFIG = {
    totalClients: 500,      // Scale down to find stable point
    clientsPerProcess: 125, // 4 processes
    redis: {
        host: '127.0.0.1',
        port: 6380
    },
    channelId: 'fanout-test-1',
    payloadSizeKB: 1,        // Deep Dive 02 "Full Payload" assumption
    waitAfterConnectSec: 15
};

async function run() {
    console.log(`\n=== Redis Fan-out Latency Test ===`);
    console.log(`Subscribers: ${CONFIG.totalClients}`);
    console.log(`Payload Size: ${CONFIG.payloadSizeKB} KB`);
    console.log(`Redis Port: ${CONFIG.redis.port}`);
    
    const processes = [];
    const processCount = Math.ceil(CONFIG.totalClients / CONFIG.clientsPerProcess);
    
    console.log(`[Orchestrator] Spawning ${processCount} processes...`);

    for (let i = 0; i < processCount; i++) {
        const startId = i * CONFIG.clientsPerProcess;
        const count = Math.min(CONFIG.clientsPerProcess, CONFIG.totalClients - startId);
        
        const p = spawn('node', [
            'mass-client.js',
            startId.toString(),
            count.toString(),
            CONFIG.redis.port.toString(), // Pass port as 3rd arg
            CONFIG.channelId
        ]);
        
        p.stdoutData = '';
        p.stdout.on('data', (d) => { p.stdoutData += d.toString(); });
        processes.push(p);
    }

    console.log(`[Orchestrator] Waiting ${CONFIG.waitAfterConnectSec}s for connections...`);
    await new Promise(r => setTimeout(r, CONFIG.waitAfterConnectSec * 1000));

    // 3. Publish Message
    const pub = new Redis(CONFIG.redis);
    const payload = {
        sentAt: Date.now(),
        data: 'x'.repeat(CONFIG.payloadSizeKB * 1024)
    };

    console.log('[Orchestrator] Publishing 1KB message...');
    await pub.publish(`channel.${CONFIG.channelId}`, JSON.stringify(payload));
    
    console.log('[Orchestrator] Waiting for delivery...');
    await new Promise(r => setTimeout(r, 5000));

    // 4. Collect Stats
    console.log('[Orchestrator] Collecting results...');
    let totalReceived = 0;
    let maxP99 = 0;

    for (const p of processes) {
        p.kill('SIGINT');
        await new Promise(r => p.on('exit', r));
        
        const lines = p.stdoutData.trim().split('\n');
        const lastLine = lines[lines.length - 1];
        try {
            const report = JSON.parse(lastLine);
            totalReceived += report.received;
            maxP99 = Math.max(maxP99, report.latency.p99);
            console.log(`   Range ${report.processRange}: Connected=${report.connected}, Received=${report.received}, P99=${report.latency.p99}ms`);
        } catch (e) {
            console.error('Failed to parse report');
        }
    }

    console.log(`\n=== FINAL RESULT ===`);
    console.log(`Success Rate: ${(totalReceived/CONFIG.totalClients*100).toFixed(1)}%`);
    console.log(`Worst P99 Latency: ${maxP99}ms`);
    
    pub.disconnect();
}

run();