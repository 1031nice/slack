const TOKEN_STORAGE_KEY = 'slack_auth_token';
const AUTH_SERVER_URL = process.env.NEXT_PUBLIC_AUTH_SERVER_URL || 'http://localhost:8081';

/**
 * 로컬 스토리지에서 인증 토큰을 가져옵니다.
 */
export function getAuthToken(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  
  // 환경 변수에서 먼저 확인 (개발용)
  const envToken = process.env.NEXT_PUBLIC_AUTH_TOKEN;
  if (envToken) {
    return envToken;
  }
  
  // 로컬 스토리지에서 가져오기
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/**
 * 로컬 스토리지에 인증 토큰을 저장합니다.
 */
export function setAuthToken(token: string): void {
  if (typeof window === 'undefined') {
    return;
  }
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

/**
 * 로컬 스토리지에서 인증 토큰을 제거합니다.
 */
export function removeAuthToken(): void {
  if (typeof window === 'undefined') {
    return;
  }
  localStorage.removeItem(TOKEN_STORAGE_KEY);
}

/**
 * 토큰이 유효한지 확인합니다 (기본적인 형식 검증만 수행).
 */
export function isValidToken(token: string | null): boolean {
  if (!token) {
    return false;
  }
  // JWT 토큰은 보통 3개의 부분으로 구성되어 있습니다 (header.payload.signature)
  return token.split('.').length === 3;
}

