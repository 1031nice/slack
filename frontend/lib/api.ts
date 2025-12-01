const API_BASE_URL = 'http://localhost:8080/api';

export interface TokenExchangeRequest {
  code: string;
  redirectUri: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

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

export interface WorkspaceInviteRequest {
  email: string;
}

export interface WorkspaceInviteResponse {
  id: number;
  workspaceId: number;
  email: string;
  token: string;
  status: string;
  expiresAt: string;
  createdAt: string;
}

export interface AcceptInvitationRequest {
  token: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public statusText?: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function fetchChannels(workspaceId: number, token: string): Promise<Channel[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/workspaces/${workspaceId}/channels`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new ApiError('Authentication failed. Please log in again.', 401, response.statusText);
      } else if (response.status === 403) {
        throw new ApiError('You do not have permission to access this resource.', 403, response.statusText);
      } else if (response.status >= 500) {
        throw new ApiError('Server error. Please try again later.', response.status, response.statusText);
      } else {
        throw new ApiError(`Failed to fetch channels: ${response.statusText}`, response.status, response.statusText);
      }
    }

    return response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new ApiError('Network error. Please check your connection.', 0, 'Network Error');
    }
    throw new ApiError('An unexpected error occurred while fetching channels.');
  }
}

export async function fetchMessages(
  channelId: number,
  token: string,
  limit?: number,
  before?: number
): Promise<Message[]> {
  try {
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
      if (response.status === 401) {
        throw new ApiError('Authentication failed. Please log in again.', 401, response.statusText);
      } else if (response.status === 403) {
        throw new ApiError('You do not have permission to access this resource.', 403, response.statusText);
      } else if (response.status === 404) {
        throw new ApiError('Channel not found.', 404, response.statusText);
      } else if (response.status >= 500) {
        throw new ApiError('Server error. Please try again later.', response.status, response.statusText);
      } else {
        throw new ApiError(`Failed to fetch messages: ${response.statusText}`, response.status, response.statusText);
      }
    }

    return response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new ApiError('Network error. Please check your connection.', 0, 'Network Error');
    }
    throw new ApiError('An unexpected error occurred while fetching messages.');
  }
}

export async function exchangeToken(request: TokenExchangeRequest): Promise<LoginResponse> {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      if (response.status === 401 || response.status === 400) {
        throw new ApiError('Invalid authorization code.', response.status, response.statusText);
      } else if (response.status >= 500) {
        throw new ApiError('Server error. Please try again later.', response.status, response.statusText);
      } else {
        throw new ApiError(`Token exchange failed: ${response.statusText}`, response.status, response.statusText);
      }
    }

    return response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new ApiError('Network error. Please check your connection.', 0, 'Network Error');
    }
    throw new ApiError('An unexpected error occurred during token exchange.');
  }
}

export async function inviteUserToWorkspace(
  workspaceId: number,
  request: WorkspaceInviteRequest,
  token: string
): Promise<WorkspaceInviteResponse> {
  try {
    const response = await fetch(`${API_BASE_URL}/workspaces/${workspaceId}/invitations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new ApiError('Authentication failed. Please log in again.', 401, response.statusText);
      } else if (response.status === 403) {
        throw new ApiError('You do not have permission to invite users to this workspace.', 403, response.statusText);
      } else if (response.status === 400) {
        const errorData = await response.json().catch(() => ({}));
        throw new ApiError(errorData.message || 'Invalid request. Please check your input.', 400, response.statusText);
      } else if (response.status >= 500) {
        throw new ApiError('Server error. Please try again later.', response.status, response.statusText);
      } else {
        throw new ApiError(`Failed to invite user: ${response.statusText}`, response.status, response.statusText);
      }
    }

    return response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new ApiError('Network error. Please check your connection.', 0, 'Network Error');
    }
    throw new ApiError('An unexpected error occurred while inviting user.');
  }
}

export async function acceptInvitation(request: AcceptInvitationRequest, token: string): Promise<number> {
  try {
    const response = await fetch(`${API_BASE_URL}/workspaces/invitations/accept`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new ApiError('Authentication failed. Please log in again.', 401, response.statusText);
      } else if (response.status === 400) {
        const errorData = await response.json().catch(() => ({}));
        throw new ApiError(errorData.message || 'Invalid invitation token.', 400, response.statusText);
      } else if (response.status === 404) {
        throw new ApiError('Invitation not found or has expired.', 404, response.statusText);
      } else if (response.status >= 500) {
        throw new ApiError('Server error. Please try again later.', response.status, response.statusText);
      } else {
        throw new ApiError(`Failed to accept invitation: ${response.statusText}`, response.status, response.statusText);
      }
    }

    return response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new ApiError('Network error. Please check your connection.', 0, 'Network Error');
    }
    throw new ApiError('An unexpected error occurred while accepting invitation.');
  }
}
