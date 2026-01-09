#!/usr/bin/env node

/**
 * STOMP WebSocket Load Test Client
 *
 * Each client:
 * 1. Connects to a specific backend instance via STOMP
 * 2. Subscribes to a test channel
 * 3. Logs all received messages with timestamps
 * 4. Reports delivery statistics
 */

const { Client } = require('@stomp/stompjs');
const SockJS = require('sockjs-client');
const fs = require('fs');

class WebSocketTestClient {
  constructor(clientId, serverUrl, channelId, logFile, token) {
    this.clientId = clientId;
    this.serverUrl = serverUrl;
    this.channelId = channelId;
    this.logFile = logFile;
    this.token = token;
    this.client = null;
    this.receivedMessages = new Set();
    this.latencies = [];
    this.connected = false;
  }

  async connect() {
    return new Promise((resolve, reject) => {
      // Convert ws:// to http:// for SockJS
      const httpUrl = this.serverUrl.replace('ws://', 'http://');

      this.client = new Client({
        webSocketFactory: () => new SockJS(httpUrl),
        connectHeaders: {
          Authorization: `Bearer ${this.token}`
        },
        debug: (str) => {
          // Suppress debug logs
        },
        reconnectDelay: 0, // Disable auto-reconnect
        heartbeatIncoming: 0,
        heartbeatOutgoing: 0,
        onConnect: (frame) => {
          console.log(`[Client ${this.clientId}] Connected to ${this.serverUrl}`);
          this.connected = true;

          // Subscribe to channel messages
          this.client.subscribe(`/topic/channel.${this.channelId}`, (message) => {
            const receiveTime = Date.now();
            const messageData = JSON.parse(message.body);

            // Server sends: { messageId, createdAt, content, ... }
            const messageId = messageData.messageId;
            const sentTime = messageData.createdAt ? new Date(messageData.createdAt).getTime() : receiveTime;
            const latency = receiveTime - sentTime;

            if (messageId) {
              this.receivedMessages.add(messageId);
              this.latencies.push(latency);

              // Log to file
              fs.appendFileSync(this.logFile,
                `${this.clientId},${messageId},${sentTime},${receiveTime},${latency}\n`
              );
            } else {
              console.warn(`[Client ${this.clientId}] Received message without messageId:`, messageData);
            }
          });

          resolve();
        },
        onStompError: (frame) => {
          console.error(`[Client ${this.clientId}] STOMP error:`, frame.headers['message']);
          reject(new Error(frame.headers['message']));
        },
        onWebSocketError: (event) => {
          console.error(`[Client ${this.clientId}] WebSocket error:`, event);
          reject(event);
        },
        onDisconnect: () => {
          console.log(`[Client ${this.clientId}] Disconnected`);
          this.connected = false;
        }
      });

      this.client.activate();
    });
  }

  getStats() {
    const sorted = this.latencies.sort((a, b) => a - b);
    const p50 = sorted[Math.floor(sorted.length * 0.5)] || 0;
    const p95 = sorted[Math.floor(sorted.length * 0.95)] || 0;
    const p99 = sorted[Math.floor(sorted.length * 0.99)] || 0;

    return {
      clientId: this.clientId,
      serverUrl: this.serverUrl,
      messagesReceived: this.receivedMessages.size,
      latency: {
        p50,
        p95,
        p99,
        avg: sorted.reduce((a, b) => a + b, 0) / sorted.length || 0
      }
    };
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
    }
  }
}

// Main execution
async function main() {
  const args = process.argv.slice(2);
  const clientId = args[0] || '1';
  const serverUrl = args[1] || 'ws://localhost:9000/ws';
  const channelId = args[2] || '1';
  const logFile = args[3] || './logs/client.log';
  const duration = parseInt(args[4] || '60') * 1000; // seconds to ms
  const token = args[5] || ''; // Dev JWT token

  console.log(`Starting client ${clientId} connecting to ${serverUrl}`);
  console.log(`Channel: ${channelId}, Duration: ${duration}ms`);

  const client = new WebSocketTestClient(clientId, serverUrl, channelId, logFile, token);

  try {
    await client.connect();

    // Wait for specified duration
    await new Promise(resolve => setTimeout(resolve, duration));

    // Print statistics
    const stats = client.getStats();
    console.log('\n=== Statistics ===');
    console.log(JSON.stringify(stats, null, 2));

    // Write stats to file
    const statsFile = `./logs/stats-${clientId}.json`;
    fs.writeFileSync(statsFile, JSON.stringify(stats, null, 2));

    client.disconnect();
    process.exit(0);
  } catch (error) {
    console.error('Failed to run client:', error);
    process.exit(1);
  }
}

main();
