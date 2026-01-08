#!/usr/bin/env node

/**
 * Light Orchestrator for Multi-Server Broadcast Test
 */

const { spawn, execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const SockJS = require('sockjs-client');
const { Client } = require('@stomp/stompjs');

// === 가벼운 테스트 설정 ===
const CONFIG = {
  wsServers: [
    'http://localhost:9000/ws',
    'http://localhost:9001/ws',
    'http://localhost:9002/ws',
    'http://localhost:9003/ws'
  ],
  channelId: 1,
  totalClients: 4,        // 서버당 1개씩 총 4개 (기본값)
  messagesPerSecond: 1,   // 초당 1개 (기본값)
  testDurationSec: 10,    // 10초 실행
  warmupSec: 3            // 대기 시간
};

class TestOrchestrator {
  constructor(config) {
    this.config = config;
    this.clients = [];
    this.sentCount = 0;
    this.logsDir = path.join(__dirname, 'logs');
    this.token = null;
    this.senderClients = [];
  }

  async setup() {
    console.log('Generating dev JWT token...');
    this.token = execSync('node generate-dev-token.js test-user', { encoding: 'utf-8' }).trim();
    
    if (!fs.existsSync(this.logsDir)) {
      fs.mkdirSync(this.logsDir, { recursive: true });
    }

    // 기존 로그 삭제 및 헤더 추가
    const logFile = path.join(this.logsDir, 'messages.csv');
    fs.writeFileSync(logFile, 'clientId,messageId,sentTime,receiveTime,latency\n');

    console.log('Connecting sender clients...');
    for (const wsUrl of this.config.wsServers) {
        const client = new Client({
            webSocketFactory: () => new SockJS(wsUrl),
            connectHeaders: { Authorization: `Bearer ${this.token}` },
            reconnectDelay: 0,
            debug: () => {}
        });
        
        await new Promise((resolve) => {
            client.onConnect = () => {
                this.senderClients.push(client);
                resolve();
            };
            client.activate();
        });
    }
    console.log(`${this.senderClients.length} senders ready.`);
  }

  async spawnClients() {
    console.log(`Spawning ${this.config.totalClients} clients...`);
    for (let i = 0; i < this.config.totalClients; i++) {
      const serverUrl = this.config.wsServers[i % this.config.wsServers.length];
      const clientId = `client-${i + 1}`;
      const logFile = path.join(this.logsDir, 'messages.csv');

      const client = spawn('node', [
        'ws-client.js',
        clientId,
        serverUrl,
        this.config.channelId.toString(),
        logFile,
        (this.config.testDurationSec + 5).toString(),
        this.token
      ]);

      client.stderr.on('data', (data) => console.error(`[${clientId}] ${data}`));
      this.clients.push({ clientId, process: client });
    }
    await new Promise(r => setTimeout(r, this.config.warmupSec * 1000));
  }

  async sendMessages() {
    const total = this.config.messagesPerSecond * this.config.testDurationSec;
    console.log(`Sending ${total} messages...`);

    return new Promise((resolve) => {
      let count = 0;
      const interval = setInterval(() => {
        if (count >= total) {
          clearInterval(interval);
          this.sentCount = count;
          resolve();
          return;
        }

        const sender = this.senderClients[count % this.senderClients.length];
        sender.publish({
          destination: '/app/message.send',
          body: JSON.stringify({
            type: 'MESSAGE',
            channelId: this.config.channelId,
            content: `Test message #${count + 1}`
          })
        });
        count++;
      }, 1000 / this.config.messagesPerSecond);
    });
  }

  async finish() {
    console.log('Waiting for clients to finish...');
    await Promise.all(this.clients.map(c => new Promise(r => c.process.on('exit', r))));
    this.senderClients.forEach(c => c.deactivate());
    console.log('Test finished.');
  }

  async analyzeResults() {
    console.log('\n📊 Analyzing results...\n');

    const logFile = path.join(this.logsDir, 'messages.csv');
    if (!fs.existsSync(logFile)) {
      console.log('❌ No results file found');
      return;
    }

    const lines = fs.readFileSync(logFile, 'utf-8').split('\n').filter(l => l.trim() && !l.startsWith('clientId'));

    if (lines.length === 0) {
      console.log('❌ No messages received');
      return;
    }

    const latencies = lines.map(line => {
      const parts = line.split(',');
      return parseInt(parts[4]);
    }).filter(l => !isNaN(l));

    latencies.sort((a, b) => a - b);

    const totalSent = this.sentCount;
    const totalReceived = lines.length;
    const deliveryRate = (totalReceived / (totalSent * this.config.totalClients) * 100).toFixed(2);

    const avgLatency = latencies.reduce((a, b) => a + b, 0) / latencies.length;
    const p50 = latencies[Math.floor(latencies.length * 0.5)];
    const p95 = latencies[Math.floor(latencies.length * 0.95)];
    const p99 = latencies[Math.floor(latencies.length * 0.99)];

    const results = {
      totalSent,
      totalReceived,
      expectedReceived: totalSent * this.config.totalClients,
      deliveryRate: `${deliveryRate}%`,
      latency: {
        avg: Math.round(avgLatency),
        p50,
        p95,
        p99,
        min: latencies[0],
        max: latencies[latencies.length - 1]
      }
    };

    // Save to JSON
    fs.writeFileSync(
      path.join(this.logsDir, 'results.json'),
      JSON.stringify(results, null, 2)
    );

    // Print to console
    console.log('📈 Test Results:');
    console.log(`   Messages Sent:     ${totalSent}`);
    console.log(`   Messages Received: ${totalReceived} / ${totalSent * this.config.totalClients} (${deliveryRate}%)`);
    console.log(`\n⏱️  Latency (ms):`);
    console.log(`   Average: ${Math.round(avgLatency)}ms`);
    console.log(`   P50:     ${p50}ms`);
    console.log(`   P95:     ${p95}ms`);
    console.log(`   P99:     ${p99}ms`);
    console.log(`   Min:     ${latencies[0]}ms`);
    console.log(`   Max:     ${latencies[latencies.length - 1]}ms`);
    console.log('');
  }

  async run() {
    try {
      await this.setup();
      await this.spawnClients();
      await this.sendMessages();
      await this.finish();
      await this.analyzeResults();
      process.exit(0);
    } catch (e) {
      console.error(e);
      process.exit(1);
    }
  }
}

new TestOrchestrator(CONFIG).run();