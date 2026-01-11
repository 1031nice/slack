const { Client } = require('@stomp/stompjs');
const SockJS = require('sockjs-client');
const fs = require('fs');

/**
 * Mass WebSocket Client
 * Manages multiple STOMP connections in a single Node.js process.
 * Used for high-concurrency testing (Fan-out).
 */

class MassClient {
    constructor(config) {
        this.startId = config.startId; // e.g., 1000
        this.count = config.count;     // e.g., 500
        this.serverUrl = config.serverUrl;
        this.channelId = config.channelId;
        this.token = config.token;
        this.logFile = config.logFile;
        
        this.clients = [];
        this.stats = {
            connected: 0,
            received: 0,
            latencies: []
        };
    }

    async start() {
        console.log(`[MassClient] Starting ${this.count} clients (${this.startId}-${this.startId + this.count - 1}) connecting to ${this.serverUrl}`);
        
        const connectPromises = [];
        for (let i = 0; i < this.count; i++) {
            connectPromises.push(this.connectSingleClient(this.startId + i));
            // Small stagger to avoid overwhelming the server during handshake
            if (i % 50 === 0) await new Promise(r => setTimeout(r, 100)); 
        }
        
        await Promise.allSettled(connectPromises);
        console.log(`[MassClient] All ${this.count} connection attempts finished. Connected: ${this.stats.connected}`);
    }

    connectSingleClient(id) {
        return new Promise((resolve, reject) => {
            const httpUrl = this.serverUrl.replace('ws://', 'http://');
            const client = new Client({
                webSocketFactory: () => new SockJS(httpUrl),
                connectHeaders: { Authorization: `Bearer ${this.token}` },
                debug: () => {}, // Silence debug logs to save CPU
                reconnectDelay: 0,
            });

            client.onConnect = () => {
                this.stats.connected++;
                client.subscribe(`/topic/channel.${this.channelId}`, (msg) => {
                    this.recordMessage(id, msg);
                });
                resolve();
            };

            client.onStompError = (frame) => {
                // console.error(`[Client ${id}] Error: ${frame.headers['message']}`);
                reject(frame);
            };

            client.activate();
            this.clients.push(client);
        });
    }

    recordMessage(clientId, msgFrame) {
        const receiveTime = Date.now();
        try {
            const body = JSON.parse(msgFrame.body);
            // Assuming server sends 'createdAt' or we calculate from send time if passed in body
            // For simple latency, we might need the sender to include a 'sentAt' timestamp in content or utilize createdAt
            
            const sentTime = body.createdAt ? new Date(body.createdAt).getTime() : 0;
            if (sentTime > 0) {
                const latency = receiveTime - sentTime;
                this.stats.latencies.push(latency);
                this.stats.received++;
                
                // Optional: Log to CSV only if needed (high I/O)
                // fs.appendFileSync(this.logFile, `${clientId},${body.messageId},${sentTime},${receiveTime},${latency}\n`);
            }
        } catch (e) {
            console.error('Error parsing message', e);
        }
    }

    disconnectAll() {
        this.clients.forEach(c => c.deactivate());
    }

    getReport() {
        const sorted = this.stats.latencies.sort((a, b) => a - b);
        const p50 = sorted[Math.floor(sorted.length * 0.50)] || 0;
        const p95 = sorted[Math.floor(sorted.length * 0.95)] || 0;
        const p99 = sorted[Math.floor(sorted.length * 0.99)] || 0;
        const avg = sorted.reduce((a, b) => a + b, 0) / sorted.length || 0;

        return {
            processRange: `${this.startId}-${this.startId + this.count - 1}`,
            connected: this.stats.connected,
            received: this.stats.received,
            latency: { avg: Math.round(avg), p50, p95, p99, max: sorted[sorted.length-1] || 0 }
        };
    }
}

// CLI Entry point
if (require.main === module) {
    const args = process.argv.slice(2);
    const config = {
        startId: parseInt(args[0]),
        count: parseInt(args[1]),
        serverUrl: args[2],
        channelId: args[3],
        token: args[4] // Simple dev token
    };

    const massClient = new MassClient(config);
    
    // Keep alive until killed
    massClient.start();

    // Handle signals to report and exit
    process.on('SIGINT', () => {
        console.log(JSON.stringify(massClient.getReport()));
        massClient.disconnectAll();
        process.exit(0);
    });
}
