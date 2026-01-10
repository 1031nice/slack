import { useState, useCallback } from 'react';
import { Message } from '@/lib/api';

/**
 * Phase 2: Client-Side Ordering (Soft Ordering)
 *
 * This hook implements:
 * - Immediate insertion of new messages (Soft Ordering)
 * - Sorting by createdAt timestamp to handle out-of-order delivery
 *
 * Strategy:
 * - When a message arrives, insert it into the correct position immediately.
 * - Zero latency overhead (unlike the previous 200ms buffer strategy).
 */
export function useMessageBuffer(initialMessages: Message[] = []) {
  // Display messages (always sorted)
  const [displayMessages, setDisplayMessages] = useState<Message[]>(initialMessages);

  /**
   * Sort messages by createdAt timestamp (ascending order - oldest first)
   */
  const sortMessagesByTimestamp = useCallback((messages: Message[]): Message[] => {
    return [...messages].sort((a, b) => {
      const timeA = new Date(a.createdAt).getTime();
      const timeB = new Date(b.createdAt).getTime();
      
      // If timestamps are identical, use ID as tie-breaker (Snowflake/ULID logic)
      if (timeA === timeB) {
        // Fallback for string comparison if IDs are strings
        return (a.id || '').toString().localeCompare((b.id || '').toString());
      }
      return timeA - timeB;
    });
  }, []);

  /**
   * Add a new message: Immediate Sorted Insertion
   */
  const addMessage = useCallback((message: Message) => {
    setDisplayMessages(prev => {
      // Deduplication check
      if (prev.some(m => m.id === message.id || (m.timestampId && m.timestampId === message.timestampId))) {
        return prev;
      }
      
      const newMessages = [...prev, message];
      return sortMessagesByTimestamp(newMessages);
    });
  }, [sortMessagesByTimestamp]);

  /**
   * Replace all messages (used when loading initial messages from API)
   */
  const setMessages = useCallback((messages: Message[]) => {
    setDisplayMessages(sortMessagesByTimestamp(messages));
  }, [sortMessagesByTimestamp]);

  return {
    messages: displayMessages,
    addMessage,
    setMessages,
  };
}
