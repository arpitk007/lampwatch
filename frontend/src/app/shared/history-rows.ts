import { CheckResult } from '../api/monitor-api';
import { plainCheckError } from './plain-error';

export type HistoryResult = 'Passed' | 'Slow' | 'Failed';

/** One line of the check history: a single notable check, or a run of ordinary passes merged together. */
export interface HistoryRow {
  newestAt: string;
  oldestAt: string;
  count: number;
  result: HistoryResult;
  code: string;
  response: string;
  details: string;
}

/**
 * Collapses consecutive ordinary passes into one row, so the failures and slow checks stand out.
 * Slow checks keep a row of their own; a run of failures with the same cause shares one row, so a long
 * outage reads as one line instead of a screen of identical ones. Checks come newest first.
 */
export function historyRows(checks: CheckResult[], slowAfterMs: number | null): HistoryRow[] {
  const rows: HistoryRow[] = [];
  let run: CheckResult[] = [];

  const flush = () => {
    if (run.length === 0) return;
    const times = run.map((c) => c.responseTimeMs).filter((t): t is number => t !== null);
    const codes = new Set(run.map((c) => c.statusCode ?? '–'));
    const lo = times.length ? Math.min(...times) : null;
    const hi = times.length ? Math.max(...times) : null;
    rows.push({
      newestAt: run[0].checkedAt,
      oldestAt: run[run.length - 1].checkedAt,
      count: run.length,
      result: 'Passed',
      code: codes.size === 1 ? String([...codes][0]) : 'mixed',
      response: lo === null ? '–' : lo === hi ? `${lo} ms` : `${lo}–${hi} ms`,
      details: '',
    });
    run = [];
  };

  for (const c of checks) {
    const slow = c.success && slowAfterMs !== null && c.responseTimeMs !== null && c.responseTimeMs > slowAfterMs;
    if (c.success && !slow) {
      run.push(c);
      continue;
    }
    flush();
    const details = plainCheckError(c.errorMessage)?.text ?? '';
    const code = c.statusCode === null ? '–' : String(c.statusCode);
    const response = c.responseTimeMs === null ? '–' : `${c.responseTimeMs} ms`;
    const last = rows[rows.length - 1];
    if (!c.success && last?.result === 'Failed' && last.details === details && last.code === code) {
      last.oldestAt = c.checkedAt;
      last.count++;
      if (last.response !== response) last.response = '–';
      continue;
    }
    rows.push({ newestAt: c.checkedAt, oldestAt: c.checkedAt, count: 1, result: c.success ? 'Slow' : 'Failed', code, response, details });
  }
  flush();
  return rows;
}
