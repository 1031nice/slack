const API_BASE_URL = 'http://localhost:8080/api';

export interface Channel {
  id: number;
  workspaceId: number;
  name: string;
  type: 'PUBLIC' | 'PRIVATE';
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface Message {
  id: number;
  channelId: number;
  userId: number;
  content: string;
  parentMessageId: number | null;
  createdAt: string;
  updatedAt: string;
}

export async function fetchChannels(workspaceId: number, token: string): Promise<Channel[]> {
  const response = await fetch(`${API_BASE_URL}/workspaces/${workspaceId}/channels`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch channels');
  }

  return response.json();
}

export async function fetchMessages(
  channelId: number,
  token: string,
  limit?: number,
  before?: number
): Promise<Message[]> {
  const params = new URLSearchParams();
  if (limit) params.append('limit', limit.toString());
  if (before) params.append('before', before.toString());

  const response = await fetch(
    `${API_BASE_URL}/channels/${channelId}/messages?${params.toString()}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error('Failed to fetch messages');
  }

  return response.json();
}

