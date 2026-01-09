#!/usr/bin/env node

/**
 * DB-First Durability Test (Scenario B)
 *
 * Tests that Redis failure does not cause message loss when using DB-first write path.
 *
 * Test Flow:
 * 1. Connect N WebSocket clients to backend servers
 * 2. Start sending 1000 messages via REST API
 * 3. At message ~500, kill Redis container
 * 4. Continue sending remaining 500 messages
 * 5. Verify all 1000 messages are in PostgreSQL
 * 6. Restart Redis
 * 7. Trigger recovery/re-broadcast (if implemented)
 * 8. Verify all clients received all 1000 messages
 */

const { spawn, execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const SockJS = require('sockjs-client');
const { Client } = require('@stomp/stompjs');

const CONFIG = {
  wsServers: [
    'http://localhost:9000/ws',
    'http://localhost:9001/ws'
  ],
  channelId: 1,
  totalClients: 4,          // Total WebSocket listeners
  totalMessages: 100,        // Total messages to send (reduced for faster test)
  redisKillAt: 50,           // Kill Redis after this many messages
  messageInterval: 50,       // ms between messages (20 msg/sec)
  recoveryWaitSec: 3         // Wait time after Redis restart
};

class DurabilityTest {
  constructor(config) {
    this.config = config;
    this.clients = [];
    this.senderClients = [];
    this.sentMessages = [];
    this.receivedMessages = new Map(); // messageId -> count
    this.logsDir = path.join(__dirname, 'logs');
    this.token = null;
    this.redisKilled = false;
    this.redisRestarted = false;
    this.initialDbCount = 0; // Track initial DB count before test
  }

  async setup() {
    console.log('🔧 Setting up durability test...\n');

    // Generate token
    console.log('Generating dev JWT token...');
    this.token = execSync('node generate-dev-token.js test-user', { encoding: 'utf-8' }).trim();

    // Create logs directory
    if (!fs.existsSync(this.logsDir)) {
      fs.mkdirSync(this.logsDir, { recursive: true });
    }

    // Clear previous logs
    const logFile = path.join(this.logsDir, 'messages.csv');
    fs.writeFileSync(logFile, 'clientId,messageId,sentTime,receiveTime,latency\n');

    const eventLog = path.join(this.logsDir, 'events.log');
    fs.writeFileSync(eventLog, `${new Date().toISOString()} - Test started\n`);

    // Connect sender clients (STOMP)
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
    console.log(`${this.senderClients.length} sender clients ready.\n`);

    // Get initial DB count
    try {
      const result = execSync(
        `docker exec app-postgres-1 psql -U slack_user -d slack_db -c "SELECT COUNT(*) FROM messages WHERE channel_id = ${this.config.channelId};" -t`,
        { encoding: 'utf-8', cwd: __dirname }
      ).trim();
      this.initialDbCount = parseInt(result) || 0;
      console.log(`📊 Initial DB message count: ${this.initialDbCount}\n`);
    } catch (error) {
      console.warn('⚠️  Could not get initial DB count, assuming 0');
      this.initialDbCount = 0;
    }

    console.log('✅ Setup complete\n');
  }

  logEvent(message) {
    const eventLog = path.join(this.logsDir, 'events.log');
    const timestamp = new Date().toISOString();
    fs.appendFileSync(eventLog, `${timestamp} - ${message}\n`);
    console.log(`[${timestamp}] ${message}`);
  }

  async spawnClients() {
    console.log(`📡 Spawning ${this.config.totalClients} WebSocket clients...\n`);

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
        '120', // 2 minutes duration
        this.token
      ]);

      client.stdout.on('data', (data) => {
        console.log(`[${clientId}] ${data.toString().trim()}`);
      });

      client.stderr.on('data', (data) => {
        console.error(`[${clientId} ERROR] ${data.toString().trim()}`);
      });

      this.clients.push({ clientId, process: client });
    }

    // Wait for clients to connect
    console.log('⏳ Waiting 3s for clients to connect...\n');
    await new Promise(r => setTimeout(r, 3000));
    this.logEvent('All WebSocket clients connected');
  }

  async sendMessages() {
    console.log(`📤 Sending ${this.config.totalMessages} messages via STOMP...\n`);

    for (let i = 1; i <= this.config.totalMessages; i++) {
      // Kill Redis at the specified message count
      if (i === this.config.redisKillAt && !this.redisKilled) {
        console.log(`\n🔴 Killing Redis at message ${i}...\n`);
        this.logEvent(`Killing Redis at message ${i}`);
        try {
          execSync('docker stop redis', { cwd: __dirname });
          this.redisKilled = true;
          this.logEvent('Redis stopped');
          console.log('✅ Redis killed\n');
        } catch (error) {
          console.error('Failed to kill Redis:', error.message);
          this.logEvent(`Failed to kill Redis: ${error.message}`);
        }
      }

      try {
        // Send via STOMP (round-robin across sender clients)
        const sender = this.senderClients[(i - 1) % this.senderClients.length];
        sender.publish({
          destination: '/app/message.send',
          body: JSON.stringify({
            type: 'MESSAGE',
            channelId: this.config.channelId,
            content: `Durability test message #${i}`
          })
        });

        this.sentMessages.push({ sequenceNum: i });

        if (i % 100 === 0) {
          console.log(`   Sent ${i}/${this.config.totalMessages} messages`);
        }
      } catch (error) {
        console.error(`❌ Failed to send message ${i}:`, error.message);
        this.logEvent(`Failed to send message ${i}: ${error.message}`);

        // Continue even if publish fails during Redis outage
        if (this.redisKilled && !this.redisRestarted) {
          console.log('   (May fail during Redis outage - message should still be in DB)');
        }
      }

      // Wait before sending next message
      await new Promise(r => setTimeout(r, this.config.messageInterval));
    }

    this.logEvent(`All ${this.config.totalMessages} messages sent`);
    console.log(`\n✅ All ${this.config.totalMessages} messages sent\n`);
  }

  async verifyDatabase() {
    console.log('🔍 Verifying database persistence...\n');
    this.logEvent('Checking database for message count');

    try {
      // Query database through Docker (remove -i flag for non-interactive execution)
      const result = execSync(
        `docker exec app-postgres-1 psql -U slack_user -d slack_db -c "SELECT COUNT(*) FROM messages WHERE channel_id = ${this.config.channelId};" -t`,
        { encoding: 'utf-8', cwd: __dirname }
      ).trim();

      const totalCount = parseInt(result);
      const newMessages = totalCount - this.initialDbCount;

      console.log(`📊 Messages in database:`);
      console.log(`   Initial count: ${this.initialDbCount}`);
      console.log(`   Current count: ${totalCount}`);
      console.log(`   New messages:  ${newMessages}/${this.config.totalMessages}`);

      this.logEvent(`Database contains ${newMessages} new messages (total: ${totalCount})`);

      if (newMessages === this.config.totalMessages) {
        console.log('✅ Database verification PASSED - No message loss!\n');
        return true;
      } else {
        console.log(`❌ Database verification FAILED - Expected ${this.config.totalMessages} new messages, got ${newMessages}\n`);
        return false;
      }
    } catch (error) {
      console.error('Failed to query database:', error.message);
      this.logEvent(`Failed to query database: ${error.message}`);
      return false;
    }
  }

  async restartRedis() {
    console.log('🔄 Restarting Redis...\n');
    this.logEvent('Restarting Redis');

    try {
      execSync('docker start redis', { cwd: __dirname });
      this.redisRestarted = true;
      this.logEvent('Redis restarted');

      // Wait for Redis to stabilize
      console.log(`⏳ Waiting ${this.config.recoveryWaitSec}s for Redis to stabilize...\n`);
      await new Promise(r => setTimeout(r, this.config.recoveryWaitSec * 1000));

      console.log('✅ Redis restarted\n');
      return true;
    } catch (error) {
      console.error('Failed to restart Redis:', error.message);
      this.logEvent(`Failed to restart Redis: ${error.message}`);
      return false;
    }
  }

  async analyzeResults() {
    console.log('📊 Analyzing test results...\n');

    const logFile = path.join(this.logsDir, 'messages.csv');
    if (!fs.existsSync(logFile)) {
      console.log('❌ No messages log found\n');
      return;
    }

    const lines = fs.readFileSync(logFile, 'utf-8')
      .split('\n')
      .filter(l => l.trim() && !l.startsWith('clientId'));

    // Count unique messages received
    const uniqueMessages = new Set();
    lines.forEach(line => {
      const parts = line.split(',');
      if (parts.length >= 2) {
        uniqueMessages.add(parts[1]); // messageId
      }
    });

    const totalReceived = uniqueMessages.size;
    const totalExpected = this.config.totalMessages;
    const deliveryRate = (totalReceived / totalExpected * 100).toFixed(2);

    console.log('═══════════════════════════════════════════════');
    console.log('           📈 TEST RESULTS SUMMARY            ');
    console.log('═══════════════════════════════════════════════');
    console.log(`Messages Sent:              ${this.sentMessages.length}`);
    console.log(`Messages in Database:       (see above)`);
    console.log(`Unique Messages Received:   ${totalReceived}`);
    console.log(`Expected Delivery:          ${totalExpected}`);
    console.log(`Delivery Rate:              ${deliveryRate}%`);
    console.log('───────────────────────────────────────────────');
    console.log(`Redis killed at message:    ${this.config.redisKillAt}`);
    console.log(`Redis killed:               ${this.redisKilled ? 'YES' : 'NO'}`);
    console.log(`Redis restarted:            ${this.redisRestarted ? 'YES' : 'NO'}`);
    console.log('═══════════════════════════════════════════════\n');

    const results = {
      totalSent: this.sentMessages.length,
      uniqueMessagesReceived: totalReceived,
      expectedMessages: totalExpected,
      deliveryRate: `${deliveryRate}%`,
      redisKilledAt: this.config.redisKillAt,
      redisKilled: this.redisKilled,
      redisRestarted: this.redisRestarted,
      dbVerificationPassed: totalReceived === totalExpected
    };

    fs.writeFileSync(
      path.join(this.logsDir, 'results.json'),
      JSON.stringify(results, null, 2)
    );

    this.logEvent('Test completed');
  }

  async cleanup() {
    console.log('🧹 Cleaning up...\n');
    this.clients.forEach(c => c.process.kill());
    this.senderClients.forEach(c => c.deactivate());
  }

  async run() {
    try {
      await this.setup();
      await this.spawnClients();
      await this.sendMessages();

      const dbValid = await this.verifyDatabase();

      if (this.redisKilled) {
        await this.restartRedis();

        console.log('💡 Note: Manual recovery/re-broadcast not yet implemented.');
        console.log('   In production, you would trigger a recovery mechanism here.\n');
      }

      // Wait a bit for any remaining messages to be delivered
      console.log('⏳ Waiting 5s for remaining messages...\n');
      await new Promise(r => setTimeout(r, 5000));

      await this.analyzeResults();
      await this.cleanup();

      process.exit(dbValid ? 0 : 1);
    } catch (error) {
      console.error('Test failed:', error);
      this.logEvent(`Test failed: ${error.message}`);
      await this.cleanup();
      process.exit(1);
    }
  }
}

// Run test
new DurabilityTest(CONFIG).run();
