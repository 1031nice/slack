const Redis = require('ioredis');

/**
 * Mass Redis Subscriber
 * Manages multiple Redis SUBSCRIBE connections in a single Node.js process.
 */

class MassClient {
    constructor(config) {
        this.startId = config.startId;
        this.count = config.count;
        this.redisConfig = config.redis;
        this.channelId = config.channelId;
        
        this.clients = [];
        this.stats = {
            connected: 0,
            received: 0,
            latencies: []
        };
    }

    async start() {
        console.log(`[MassClient] Starting ${this.count} Redis subscribers (${this.startId}-${this.startId + this.count - 1})`);
        
        const connectPromises = [];
        for (let i = 0; i < this.count; i++) {
            connectPromises.push(this.connectSingleClient(this.startId + i));
            // Increased stagger to avoid connection burst
            if (i % 50 === 0) await new Promise(r => setTimeout(r, 100)); 
        }
        
        await Promise.allSettled(connectPromises);
        console.log(`[MassClient] All ${this.count} connection attempts finished. Connected: ${this.stats.connected}`);
    }

    connectSingleClient(id) {
        return new Promise((resolve) => {
            const redis = new Redis(this.redisConfig);
            
            // Only count when subscription is confirmed
            redis.subscribe(`channel.${this.channelId}`, (err, count) => {
                if (!err) {
                    this.stats.connected++;
                    resolve();
                }
            });

            redis.on('message', (channel, message) => {
                this.recordMessage(message);
            });

            redis.on('error', (err) => {
                // Ignore connection errors
            });

            this.clients.push(redis);
        });
    }

    recordMessage(message) {
        const receiveTime = Date.now();
        try {
            const body = JSON.parse(message);
            if (body.sentAt) {
                const latency = receiveTime - body.sentAt;
                this.stats.latencies.push(latency);
                this.stats.received++;
            }
        } catch (e) {
            // Not a JSON or missing sentAt
        }
    }

    disconnectAll() {
        this.clients.forEach(c => c.disconnect());
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
        redis: {
            host: '127.0.0.1',
            port: parseInt(args[2]) // Use port from args
        },
        channelId: args[3]
    };

    const massClient = new MassClient(config);
    massClient.start();

    process.on('SIGINT', () => {
        console.log(JSON.stringify(massClient.getReport()));
        massClient.disconnectAll();
        process.exit(0);
    });
}