import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/ws';

export interface WebSocketMessage {
  type: 'MESSAGE' | 'JOIN' | 'LEAVE' | 'ERROR';
  channelId?: number;
  messageId?: number;
  userId?: number;
  content?: string;
  createdAt?: string;
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
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('WebSocket connected');
        onConnect();
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

    const subscription = this.client.subscribe(
      `/topic/channel.${channelId}`,
      (message) => {
        try {
          const data: WebSocketMessage = JSON.parse(message.body);
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

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

