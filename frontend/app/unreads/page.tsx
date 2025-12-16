'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { isValidToken, getAuthToken } from '@/lib/auth';
import UnreadsView from '@/components/UnreadsView';

export default function UnreadsPage() {
  const router = useRouter();

  useEffect(() => {
    const token = getAuthToken();
    if (!token || !isValidToken(token)) {
      router.push('/login');
    }
  }, [router]);

  return <UnreadsView />;
}

