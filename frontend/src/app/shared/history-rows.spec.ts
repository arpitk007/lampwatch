import { CheckResult } from '../api/monitor-api';
import { historyRows } from './history-rows';

const check = (min: number, ms: number | null, success = true, code: number | null = 200): CheckResult => ({
  checkedAt: new Date(Date.UTC(2026, 10, 12, 12, 60 - min)).toISOString(),
  success,
  statusCode: code,
  responseTimeMs: ms,
  errorMessage: success ? null : 'Timed out',
});

describe('historyRows', () => {
  it('merges a run of ordinary passes into one row with the response range', () => {
    const rows = historyRows([check(1, 300), check(2, 280), check(3, 410)], 3000);
    expect(rows.length).toBe(1);
    expect(rows[0]).toEqual(expect.objectContaining({ count: 3, result: 'Passed', code: '200', response: '280–410 ms' }));
  });

  it('gives failed and slow checks a row of their own, splitting the runs around them', () => {
    const rows = historyRows([check(1, 300), check(2, null, false, null), check(3, 3500), check(4, 290), check(5, 310)], 3000);
    expect(rows.map((r) => [r.result, r.count])).toEqual([['Passed', 1], ['Failed', 1], ['Slow', 1], ['Passed', 2]]);
    expect(rows[1].details).toBe('No answer in time');
  });

  it('merges a run of failures with the same cause into one row', () => {
    const rows = historyRows([check(1, null, false, null), check(2, null, false, null), check(3, null, false, null), check(4, 300)], 3000);
    expect(rows.map((r) => [r.result, r.count])).toEqual([['Failed', 3], ['Passed', 1]]);
  });

  it('marks a run with different status codes as mixed', () => {
    const rows = historyRows([check(1, 300, true, 200), check(2, 300, true, 301)], null);
    expect(rows[0].code).toBe('mixed');
  });
});
