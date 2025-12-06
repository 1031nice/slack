#!/usr/bin/env node

/**
 * v0.3 Performance Test Script (Node.js)
 * 
 * Tests WebSocket performance with STOMP protocol
 * Supports both single server and multi-server (load balancer) testing
 */

const SockJS = require('sockjs-client');
const { Client } = require('@stomp/stompjs');
const readline = require('readline');

// Configuration from environment variables
const WS_URL = process.env.WS_URL || 'http://localhost:8080/ws';
const TOKEN = process.env.TOKEN || '';
const CHANNEL_ID = parseInt(process.env.CHANNEL_ID || '1');
const CONNECTIONS = parseInt(process.env.CONNECTIONS || '10');
const DURATION = parseInt(process.env.DURATION || '60'); // seconds
const MESSAGES_PER_SEC = parseFloat(process.env.MESSAGES_PER_SEC || '1');

if (!TOKEN) {
  console.error('Error: TOKEN environment variable is required');
  process.exit(1);
}

// Metrics
const metrics = {
  connectionsAttempted: 0,
  connectionsSuccessful: 0,
  connectionsFailed: 0,
  messagesSent: 0,
  messagesReceived: 0,
  latencies: [],
  errors: [],
  startTime: null,
  endTime: null,
};

// STOMP clients
const clients = [];

function createClient(index) {
  return new Promise((resolve, reject) => {
    metrics.connectionsAttempted++;
    
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${TOKEN}`,
      },
      reconnectDelay: 0, // Disable auto-reconnect for testing
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        metrics.connectionsSuccessful++;
        resolve(client);
      },
      onStompError: (frame) => {
        metrics.connectionsFailed++;
        metrics.errors.push(`STOMP error: ${frame.headers?.message || 'Unknown error'}`);
        reject(new Error(frame.headers?.message || 'STOMP connection failed'));
      },
      onWebSocketError: (event) => {
        metrics.connectionsFailed++;
        metrics.errors.push(`WebSocket error: ${event.type || 'Unknown error'}`);
        reject(new Error('WebSocket connection failed'));
      },
    });

    client.activate();
    
    // Timeout after 10 seconds
    setTimeout(() => {
      if (!client.connected) {
        metrics.connectionsFailed++;
        client.deactivate();
        reject(new Error('Connection timeout'));
      }
    }, 10000);
  });
}

function runTest() {
  console.log('Starting performance test...');
  console.log(`URL: ${WS_URL}`);
  console.log(`Connections: ${CONNECTIONS}`);
  console.log(`Duration: ${DURATION}s`);
  console.log(`Messages per second: ${MESSAGES_PER_SEC}`);
  console.log(`Channel ID: ${CHANNEL_ID}`);
  console.log('');

  metrics.startTime = Date.now();

  // Create all connections
  const connectionPromises = [];
  for (let i = 0; i < CONNECTIONS; i++) {
    connectionPromises.push(
      createClient(i)
        .then((client) => {
          clients.push(client);
          return setupClient(client, i);
        })
        .catch((error) => {
          console.error(`Connection ${i} failed:`, error.message);
        })
    );
  }

  Promise.all(connectionPromises)
    .then(() => {
      console.log(`\nConnected: ${metrics.connectionsSuccessful}/${CONNECTIONS}`);
      console.log('Test running...\n');

      // Run test for specified duration
      setTimeout(() => {
        stopTest();
      }, DURATION * 1000);
    })
    .catch((error) => {
      console.error('Test setup failed:', error);
      stopTest();
    });
}

function setupClient(client, index) {
  return new Promise((resolve) => {
    let sendInterval = null;
    let firstMessageReceived = false;

    // Subscribe to channel
    const subscription = client.subscribe(`/topic/channel.${CHANNEL_ID}`, (message) => {
      try {
        const data = JSON.parse(message.body);

        // Log first message received for debugging
        if (!firstMessageReceived) {
          firstMessageReceived = true;
          console.log(`Connection ${index} received first message:`, {
            type: data.type,
            messageId: data.messageId,
            channelId: data.channelId
          });
        }

        // Count all MESSAGE type messages
        if (data.type === 'MESSAGE') {
          metrics.messagesReceived++;

          // Calculate latency if createdAt is available
          if (data.createdAt) {
            const receivedTime = Date.now();
            const sentTime = new Date(data.createdAt).getTime();
            const latency = receivedTime - sentTime;
            if (latency > 0 && latency < 60000) {
              metrics.latencies.push(latency);
            }
          }
        }
      } catch (error) {
        metrics.errors.push(`Parse error: ${error.message}`);
        console.error(`Connection ${index} parse error:`, error.message);
      }
    });

    // Store subscription for later cleanup
    client._testSubscription = subscription;

    console.log(`Connection ${index} subscribed to /topic/channel.${CHANNEL_ID}`);

    // Wait longer for subscription to be fully established
    // STOMP subscription is asynchronous and needs time to propagate to server
    setTimeout(() => {
      console.log(`Connection ${index} starting to send messages`);

      // Send messages at specified rate
      const messageInterval = 1000 / MESSAGES_PER_SEC;
      let messageCount = 0;
      const maxMessages = Math.floor(DURATION * MESSAGES_PER_SEC);

      sendInterval = setInterval(() => {
        if (messageCount >= maxMessages || !client.connected) {
          clearInterval(sendInterval);
          return;
        }

        const messageBody = JSON.stringify({
          type: 'MESSAGE',
          channelId: CHANNEL_ID,
          content: `Test message ${messageCount} from connection ${index} at ${new Date().toISOString()}`,
        });

        try {
          client.publish({
            destination: '/app/message.send',
            body: messageBody,
          });

          metrics.messagesSent++;
          messageCount++;

          // Log first few messages for debugging
          if (messageCount <= 2) {
            console.log(`Connection ${index} sent message ${messageCount}`);
          }
        } catch (error) {
          metrics.errors.push(`Send error: ${error.message}`);
          console.error(`Connection ${index} send error:`, error.message);
        }
      }, messageInterval);

      resolve();
    }, 1000); // Increased wait time to 1000ms for subscription to be ready
  });
}

function stopTest() {
  console.log('\nStopping test...');
  
  metrics.endTime = Date.now();

  // Disconnect all clients
  clients.forEach((client) => {
    if (client.connected) {
      client.deactivate();
    }
  });

  // Calculate statistics
  const testDuration = (metrics.endTime - metrics.startTime) / 1000; // seconds
  const throughput = metrics.messagesReceived / testDuration;
  
  const latencies = metrics.latencies.sort((a, b) => a - b);
  const p50 = latencies[Math.floor(latencies.length * 0.5)] || 0;
  const p95 = latencies[Math.floor(latencies.length * 0.95)] || 0;
  const p99 = latencies[Math.floor(latencies.length * 0.99)] || 0;
  const avgLatency = latencies.length > 0 
    ? latencies.reduce((a, b) => a + b, 0) / latencies.length 
    : 0;

  // Print results
  console.log('\n=== Test Results ===');
  console.log(`Test Duration: ${testDuration.toFixed(2)} seconds`);
  console.log(`\nConnections:`);
  console.log(`  Attempted: ${metrics.connectionsAttempted}`);
  console.log(`  Successful: ${metrics.connectionsSuccessful}`);
  console.log(`  Failed: ${metrics.connectionsFailed}`);
  console.log(`\nMessages:`);
  console.log(`  Sent: ${metrics.messagesSent}`);
  console.log(`  Received: ${metrics.messagesReceived}`);
  console.log(`  Throughput: ${throughput.toFixed(2)} messages/sec`);
  console.log(`\nLatency (ms):`);
  console.log(`  Average: ${avgLatency.toFixed(2)}`);
  console.log(`  P50: ${p50.toFixed(2)}`);
  console.log(`  P95: ${p95.toFixed(2)}`);
  console.log(`  P99: ${p99.toFixed(2)}`);
  console.log(`\nErrors: ${metrics.errors.length}`);
  if (metrics.errors.length > 0) {
    console.log('  Sample errors:');
    metrics.errors.slice(0, 5).forEach((error) => {
      console.log(`    - ${error}`);
    });
  }

  // Validate test results
  const messageReceiveRate = metrics.messagesSent > 0
    ? (metrics.messagesReceived / metrics.messagesSent * 100).toFixed(2)
    : 0;

  console.log(`\nMessage Receive Rate: ${messageReceiveRate}%`);

  // Warning if no messages received
  if (metrics.messagesReceived === 0 && metrics.messagesSent > 0) {
    console.log('\n⚠️  WARNING: No messages received! This indicates a problem with:');
    console.log('  1. Message broadcasting (check RedisMessagePublisher/Subscriber)');
    console.log('  2. Subscription setup (check STOMP subscription)');
    console.log('  3. Message routing (check /app/message.send → /topic/channel.X)');
    console.log('  4. Backend authentication/authorization');
  }

  // Output JSON for parsing
  const result = {
    test: {
      url: WS_URL,
      connections: CONNECTIONS,
      duration: DURATION,
      messagesPerSec: MESSAGES_PER_SEC,
      channelId: CHANNEL_ID,
    },
    metrics: {
      connectionsAttempted: metrics.connectionsAttempted,
      connectionsSuccessful: metrics.connectionsSuccessful,
      connectionsFailed: metrics.connectionsFailed,
      messagesSent: metrics.messagesSent,
      messagesReceived: metrics.messagesReceived,
      messageReceiveRate: parseFloat(messageReceiveRate),
      throughput: parseFloat(throughput.toFixed(2)),
      latency: {
        avg: parseFloat(avgLatency.toFixed(2)),
        p50: parseFloat(p50.toFixed(2)),
        p95: parseFloat(p95.toFixed(2)),
        p99: parseFloat(p99.toFixed(2)),
      },
      errorCount: metrics.errors.length,
      sampleErrors: metrics.errors.slice(0, 5),
    },
  };

  console.log('\n=== JSON Result ===');
  console.log(JSON.stringify(result, null, 2));

  // Exit with error code if test failed
  if (metrics.messagesReceived === 0 && metrics.messagesSent > 0) {
    console.log('\n❌ Test FAILED: No messages received');
    process.exit(1);
  }

  console.log('\n✅ Test PASSED');
  process.exit(0);
}

// Handle graceful shutdown
process.on('SIGINT', () => {
  console.log('\nReceived SIGINT, stopping test...');
  stopTest();
});

process.on('SIGTERM', () => {
  console.log('\nReceived SIGTERM, stopping test...');
  stopTest();
});

// Run test
runTest();

