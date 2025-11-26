'use client';

import { useEffect, useRef } from 'react';
import { Message } from '@/lib/api';

interface MessageListProps {
  messages: Message[];
}

export default function MessageList({ messages }: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // 새 메시지가 추가되면 스크롤을 맨 아래로 이동
  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  // 초기 로드 시 스크롤을 맨 아래로 이동
  useEffect(() => {
    if (containerRef.current && messages.length > 0) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, []);

  return (
    <div ref={containerRef} className="flex-1 overflow-y-auto p-4 space-y-4">
      {messages.length === 0 ? (
        <div className="text-gray-500 text-center mt-8">No messages yet</div>
      ) : (
        <>
          {messages.map((message) => (
            <div key={message.id} className="mb-4">
              <div className="flex items-start space-x-2">
                <div className="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center text-white text-sm font-bold">
                  {message.userId}
                </div>
                <div className="flex-1">
                  <div className="flex items-center space-x-2 mb-1">
                    <span className="font-semibold">User {message.userId}</span>
                    <span className="text-xs text-gray-500">
                      {new Date(message.createdAt).toLocaleTimeString()}
                    </span>
                  </div>
                  <div className="text-gray-800">{message.content}</div>
                </div>
              </div>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </>
      )}
    </div>
  );
}

