import { durationLabel, intervalLabel, timeAgo } from './time';

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

describe('durationLabel', () => {
  it('uses the two largest units', () => {
    expect(durationLabel(45_000)).toBe('45 s');
    expect(durationLabel(12 * 60_000)).toBe('12 min');
    expect(durationLabel(95 * 60_000)).toBe('1 h 35 min');
    expect(durationLabel(2 * 3_600_000)).toBe('2 h');
    expect(durationLabel(52 * 3_600_000)).toBe('2 d 4 h');
  });
});
