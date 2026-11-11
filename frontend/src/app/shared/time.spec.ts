import { intervalLabel, timeAgo } from './time';

describe('timeAgo', () => {
  const now = Date.parse('2026-01-01T12:00:00Z');
  const ago = (seconds: number) => new Date(now - seconds * 1000).toISOString();

  it('formats each range', () => {
    expect(timeAgo(null, now)).toBe('Not checked yet');
    expect(timeAgo(ago(2), now)).toBe('just now');
    expect(timeAgo(ago(45), now)).toBe('45 s ago');
    expect(timeAgo(ago(180), now)).toBe('3 min ago');
    expect(timeAgo(ago(7200), now)).toBe('2 h ago');
    expect(timeAgo(ago(4 * 86400), now)).toBe('4 d ago');
  });

  it('never shows negative times for clock skew', () => {
    expect(timeAgo(ago(-30), now)).toBe('just now');
  });
});

describe('intervalLabel', () => {
  it('uses minutes below an hour', () => {
    expect(intervalLabel(300)).toBe('every 5 min');
    expect(intervalLabel(3600)).toBe('every hour');
  });
});
