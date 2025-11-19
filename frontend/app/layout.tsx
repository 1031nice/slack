import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Slack App',
  description: 'Slack clone application for learning',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  )
}

