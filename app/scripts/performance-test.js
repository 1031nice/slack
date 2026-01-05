import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const messagesSent = new Counter('messages_sent');
const messagesReceived = new Counter('messages_received');
const messageLatency = new Trend('message_latency_ms');
const connectionErrors = new Rate('connection_errors');
const messageErrors = new Rate('message_errors');

// Test configuration
export const options = {
  stages: [
    { duration: '10s', target: __ENV.CONNECTIONS || 10 }, // Ramp up
    { duration: '60s', target: __ENV.CONNECTIONS || 10 }, // Stay at target
    { duration: '10s', target: 0 }, // Ramp down
  ],
  thresholds: {
    message_latency_ms: ['p(95)<1000', 'p(99)<2000'],
    connection_errors: ['rate<0.1'],
    message_errors: ['rate<0.05'],
  },
};

// WebSocket URL - SockJS endpoint
const WS_URL = __ENV.WS_URL || 'http://localhost:8080/ws';
const TOKEN = __ENV.TOKEN || '';
const CHANNEL_ID = parseInt(__ENV.CHANNEL_ID || '1');
const MESSAGES_PER_SEC = parseFloat(__ENV.MESSAGES_PER_SEC || '1');

// STOMP frame helpers
function createStompFrame(command, headers, body) {
  let frame = command + '\n';
  for (const [key, value] of Object.entries(headers || {})) {
    frame += `${key}:${value}\n`;
  }
  frame += '\n';
  if (body) {
    frame += body;
  }
  frame += '\0';
  return frame;
}

function parseStompFrame(data) {
  const parts = data.split('\n\n');
  if (parts.length < 2) return null;
  
  const headerPart = parts[0];
  const body = parts.slice(1).join('\n\n').replace(/\0$/, '');
  
  const lines = headerPart.split('\n');
  const command = lines[0];
  const headers = {};
  
  for (let i = 1; i < lines.length; i++) {
    const colonIndex = lines[i].indexOf(':');
    if (colonIndex > 0) {
      const key = lines[i].substring(0, colonIndex);
      const value = lines[i].substring(colonIndex + 1);
      headers[key] = value;
    }
  }
  
  return { command, headers, body };
}

export default function () {
  if (!TOKEN) {
    console.error('TOKEN environment variable is required');
    return;
  }

  // SockJS uses a specific URL format
  const sockjsUrl = `${WS_URL}/websocket`;
  const params = {
    headers: {
      'Authorization': `Bearer ${TOKEN}`,
    },
  };

  let connected = false;
  let subscribed = false;
  let messageCount = 0;
  const maxMessages = Math.floor(60 * MESSAGES_PER_SEC);
  const messageInterval = 1000 / MESSAGES_PER_SEC;

  // Connect to WebSocket (SockJS)
  const response = ws.connect(sockjsUrl, params, function (socket) {
    socket.on('open', function () {
      // Send CONNECT frame
      const connectFrame = createStompFrame('CONNECT', {
        'accept-version': '1.1,1.0',
        'heart-beat': '10000,10000',
        'Authorization': `Bearer ${TOKEN}`,
      });
      socket.send(connectFrame);
    });

    socket.on('message', function (data) {
      try {
        const frame = parseStompFrame(data);
        if (!frame) return;

        if (frame.command === 'CONNECTED') {
          connected = true;
          // Subscribe to channel
          const subscribeFrame = createStompFrame('SUBSCRIBE', {
            'id': `sub-${CHANNEL_ID}`,
            'destination': `/topic/channel.${CHANNEL_ID}`,
          });
          socket.send(subscribeFrame);
          subscribed = true;

          // Start sending messages
          const sendMessages = function () {
            if (messageCount >= maxMessages) {
              return;
            }

            const messageBody = JSON.stringify({
              type: 'MESSAGE',
              channelId: CHANNEL_ID,
              content: `Test message ${messageCount} from k6 at ${new Date().toISOString()}`,
            });

            const sendFrame = createStompFrame('SEND', {
              'destination': '/app/message.send',
              'content-type': 'application/json',
            }, messageBody);
            socket.send(sendFrame);
            messagesSent.add(1);
            messageCount++;

            if (messageCount < maxMessages) {
              setTimeout(sendMessages, messageInterval);
            }
          };

          // Start sending after a short delay
          setTimeout(sendMessages, 100);
        } else if (frame.command === 'MESSAGE' && subscribed) {
          // Received message from server
          try {
            const message = JSON.parse(frame.body);
            if (message.type === 'MESSAGE' && message.messageId) {
              messagesReceived.add(1);
              
              // Calculate latency if we have timestamp
              if (message.createdAt) {
                const receivedTime = Date.now();
                const sentTime = new Date(message.createdAt).getTime();
                const latency = receivedTime - sentTime;
                if (latency > 0 && latency < 60000) { // Sanity check
                  messageLatency.add(latency);
                }
              }
            }
          } catch (e) {
            // Not a JSON message, might be STOMP error
            messageErrors.add(1);
          }
        } else if (frame.command === 'ERROR') {
          console.error('STOMP error:', frame.body);
          messageErrors.add(1);
        }
      } catch (e) {
        console.error('Error parsing STOMP frame:', e);
        messageErrors.add(1);
      }
    });

    socket.on('error', function (e) {
      console.error('WebSocket error:', e);
      connectionErrors.add(1);
    });

    socket.on('close', function () {
      // Connection closed
    });
  });

  check(response, {
    'WebSocket connection successful': (r) => r && r.status === 101,
  });

  if (!response || response.status !== 101) {
    connectionErrors.add(1);
    return;
  }

  // Keep connection alive for test duration
  sleep(70);
}

export function handleSummary(data) {
  const summary = {
    test: {
      url: WS_URL,
      connections: __ENV.CONNECTIONS || 10,
      messagesPerSec: MESSAGES_PER_SEC,
      duration: '60s',
    },
    metrics: {
      messages_sent: data.metrics.messages_sent?.values?.count || 0,
      messages_received: data.metrics.messages_received?.values?.count || 0,
      throughput: (data.metrics.messages_received?.values?.count || 0) / 60,
      latency: {
        avg: data.metrics.message_latency_ms?.values?.avg || 0,
        p50: data.metrics.message_latency_ms?.values?.['p(50)'] || 0,
        p95: data.metrics.message_latency_ms?.values?.['p(95)'] || 0,
        p99: data.metrics.message_latency_ms?.values?.['p(99)'] || 0,
      },
      connection_errors: data.metrics.connection_errors?.values?.rate || 0,
      message_errors: data.metrics.message_errors?.values?.rate || 0,
    },
  };

  return {
    stdout: JSON.stringify(summary, null, 2),
    'summary.json': JSON.stringify(summary, null, 2),
  };
}

