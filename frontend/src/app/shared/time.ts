/** "just now", "45 s ago", "3 min ago", "2 h ago", "4 d ago". */
export function timeAgo(iso: string | null, now: number): string {
  if (!iso) {
    return 'Not checked yet';
  }
  const seconds = Math.max(0, Math.round((now - Date.parse(iso)) / 1000));
  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds} s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} h ago`;
  return `${Math.round(hours / 24)} d ago`;
}

export function clockTime(date: Date): string {
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function intervalLabel(seconds: number): string {
  return seconds < 3600 ? `every ${seconds / 60} min` : 'every hour';
}
