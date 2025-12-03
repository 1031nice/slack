import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/ws';

export interface WebSocketMessage {
  type: 'MESSAGE' | 'JOIN' | 'LEAVE' | 'ERROR' | 'ACK' | 'RESEND';
  channelId?: number;
  messageId?: number;
  userId?: number;
  content?: string;
  createdAt?: string;
  sequenceNumber?: number;
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
  // 채널별 마지막 수신한 시퀀스 번호 추적
  private lastSequenceNumbers: Map<number, number> = new Map();

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

    const subscription = this.client.subscribe(
      `/topic/channel.${channelId}`,
      (message) => {
        try {
          const data: WebSocketMessage = JSON.parse(message.body);
          
          // MESSAGE 타입인 경우 ACK 전송 및 시퀀스 번호 업데이트
          if (data.type === 'MESSAGE' && data.sequenceNumber !== undefined && data.channelId !== undefined) {
            // 마지막 시퀀스 번호 업데이트
            const currentLast = this.lastSequenceNumbers.get(data.channelId) || 0;
            if (data.sequenceNumber > currentLast) {
              this.lastSequenceNumbers.set(data.channelId, data.sequenceNumber);
            }
            
            this.sendAck(data.sequenceNumber, data.messageId);
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

  /**
   * 메시지 수신 확인 (ACK)을 서버에 전송합니다.
   * 
   * @param sequenceNumber 수신한 메시지의 시퀀스 번호
   * @param messageId 수신한 메시지의 ID
   */
  private sendAck(sequenceNumber: number, messageId?: number) {
    if (!this.client || !this.client.connected) {
      console.error('WebSocket not connected, cannot send ACK');
      return;
    }

    try {
      const ackMessage: WebSocketMessage = {
        type: 'ACK',
        sequenceNumber,
        messageId,
        ackId: `ack-${Date.now()}-${sequenceNumber}`, // 고유한 ACK ID 생성
      };

      this.client.publish({
        destination: '/app/message.ack',
        body: JSON.stringify(ackMessage),
      });
    } catch (error) {
      console.error('Error sending ACK:', error);
    }
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

  /**
   * 재연결 시 누락된 메시지를 요청합니다.
   * 모든 구독 중인 채널에 대해 마지막 수신한 시퀀스 번호 이후의 메시지를 요청합니다.
   */
  private requestMissedMessages() {
    if (!this.client || !this.client.connected) {
      return;
    }

    // 모든 구독 중인 채널에 대해 누락 메시지 요청
    this.lastSequenceNumbers.forEach((lastSequence, channelId) => {
      try {
        const resendMessage: WebSocketMessage = {
          type: 'RESEND',
          channelId,
          sequenceNumber: lastSequence,
        };

        this.client!.publish({
          destination: '/app/message.resend',
          body: JSON.stringify(resendMessage),
        });
        
        console.log(`Requested missed messages for channel ${channelId} after sequence ${lastSequence}`);
      } catch (error) {
        console.error(`Error requesting missed messages for channel ${channelId}:`, error);
      }
    });
  }

  /**
   * 채널 구독을 시작할 때 호출하여 초기 시퀀스 번호를 설정합니다.
   * 
   * @param channelId 채널 ID
   * @param initialSequenceNumber 초기 시퀀스 번호 (없으면 0)
   */
  public setInitialSequenceNumber(channelId: number, initialSequenceNumber: number = 0) {
    if (!this.lastSequenceNumbers.has(channelId)) {
      this.lastSequenceNumbers.set(channelId, initialSequenceNumber);
    }
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

