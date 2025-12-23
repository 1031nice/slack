import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:9000/ws';

export interface WebSocketMessage {
  type: 'MESSAGE' | 'JOIN' | 'LEAVE' | 'ERROR' | 'ACK' | 'RESEND';
  channelId?: number;
  messageId?: number;
  userId?: number;
  content?: string;
  createdAt?: string;
  sequenceNumber?: number;
  timestampId?: string;  // Timestamp-based message ID (unique per channel)
  ackId?: string;
}

export class WebSocketError extends Error {
  constructor(
    message: string,
    public code?: string,
    public originalError?: any
  ) {
    super(message);
    this.name = 'WebSocketError';
  }
}

export class WebSocketClient {
  private client: Client | null = null;
  private token: string | null = null;
  // Phase 3: Track last seen timestamp per channel for reconnection
  private lastSeenTimestamps: Map<number, string> = new Map();
  // Track timestampIds for deduplication (handled by useMessageBuffer)
  private seenTimestampIds: Map<number, Set<string>> = new Map();

  connect(token: string, onConnect: () => void, onError: (error: WebSocketError) => void) {
    this.token = token;
    
    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as any,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        console.log('STOMP:', str);
      },
      reconnectDelay: 5000,
      // Phase 3: 10-second heartbeat for gap detection
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        console.log('WebSocket connected');
        onConnect();
        // 재연결 시 모든 구독 중인 채널에 대해 누락 메시지 요청
        this.requestMissedMessages();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
        let errorMessage = 'WebSocket connection error';
        
        if (frame.headers && frame.headers['message']) {
          const message = frame.headers['message'];
          if (message.includes('401') || message.includes('Unauthorized')) {
            errorMessage = 'Authentication failed. Please check your token.';
          } else if (message.includes('403') || message.includes('Forbidden')) {
            errorMessage = 'You do not have permission to connect.';
          } else {
            errorMessage = `Connection error: ${message}`;
          }
        }
        
        onError(new WebSocketError(errorMessage, frame.command, frame));
      },
      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
        let errorMessage = 'Failed to connect to WebSocket server';
        
        if (event.type === 'error') {
          errorMessage = 'Network error. Please check your connection and try again.';
        }
        
        onError(new WebSocketError(errorMessage, 'WEBSOCKET_ERROR', event));
      },
    });

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
  }

  subscribeToChannel(channelId: number, callback: (message: WebSocketMessage) => void) {
    if (!this.client || !this.client.connected) {
      console.error('WebSocket not connected');
      return () => {};
    }

    // Initialize timestampId Set for this channel if not exists
    if (!this.seenTimestampIds.has(channelId)) {
      this.seenTimestampIds.set(channelId, new Set());
    }

    const subscription = this.client.subscribe(
      `/topic/channel.${channelId}`,
      (message) => {
        try {
          const data: WebSocketMessage = JSON.parse(message.body);

          // MESSAGE 타입인 경우 timestamp 업데이트
          if (data.type === 'MESSAGE' && data.channelId !== undefined) {
            // Phase 3: Track timestampId for deduplication and reconnection
            if (data.timestampId) {
              const channelTimestampIds = this.seenTimestampIds.get(data.channelId);
              if (channelTimestampIds) {
                channelTimestampIds.add(data.timestampId);
              }
              // Update last seen timestamp for this channel
              this.lastSeenTimestamps.set(data.channelId, data.timestampId);
            }

            // Phase 3: createdAt as fallback if no timestampId
            if (!data.timestampId && data.createdAt) {
              this.lastSeenTimestamps.set(data.channelId, data.createdAt);
            }
          }

          callback(data);
        } catch (error) {
          console.error('Error parsing message:', error);
        }
      }
    );

    return () => {
      subscription.unsubscribe();
    };
  }

  // Phase 3: ACK removed - no longer needed with timestamp-based ordering

  sendMessage(channelId: number, content: string): boolean {
    if (!this.client || !this.client.connected) {
      console.error('WebSocket not connected');
      return false;
    }

    try {
      const message: WebSocketMessage = {
        type: 'MESSAGE',
        channelId,
        content,
      };

      this.client.publish({
        destination: '/app/message.send',
        body: JSON.stringify(message),
      });
      return true;
    } catch (error) {
      console.error('Error sending message:', error);
      return false;
    }
  }

  /**
   * Phase 3: Request missed messages using timestamp-based approach.
   * Requests all messages after the last seen timestamp for each channel.
   */
  private requestMissedMessages() {
    if (!this.client || !this.client.connected) {
      return;
    }

    // Request missed messages for all subscribed channels
    this.lastSeenTimestamps.forEach((lastTimestamp, channelId) => {
      try {
        const resendMessage: WebSocketMessage = {
          type: 'RESEND',
          channelId,
          createdAt: lastTimestamp, // Use timestamp instead of sequence number
        };

        this.client!.publish({
          destination: '/app/message.resend',
          body: JSON.stringify(resendMessage),
        });

        console.log(`[Phase 3] Requested missed messages for channel ${channelId} after timestamp ${lastTimestamp}`);
      } catch (error) {
        console.error(`Error requesting missed messages for channel ${channelId}:`, error);
      }
    });
  }

  /**
   * Phase 3: Set initial timestamp for a channel when starting subscription.
   *
   * @param channelId Channel ID
   * @param initialTimestamp Initial timestamp (ISO string or timestampId)
   */
  public setInitialTimestamp(channelId: number, initialTimestamp?: string) {
    if (initialTimestamp && !this.lastSeenTimestamps.has(channelId)) {
      this.lastSeenTimestamps.set(channelId, initialTimestamp);
    }
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

