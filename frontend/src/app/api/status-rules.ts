import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';

export interface StatusRules {
  slowAfterMs: number;
  failuresToDown: number;
  passesToRecover: number;
}

/** The server's Up / Slow / Down thresholds, fetched once per session; null until loaded. */
@Injectable({ providedIn: 'root' })
export class StatusRulesStore {
  private readonly http = inject(HttpClient);
  private requested = false;
  private readonly rules = signal<StatusRules | null>(null);

  get(): StatusRules | null {
    if (!this.requested) {
      this.requested = true;
      this.http.get<StatusRules>('/api/settings/status-rules').subscribe({
        next: (r) => this.rules.set(r),
        error: () => (this.requested = false), // retry on the next read, e.g. after signing in
      });
    }
    return this.rules();
  }
}

/** "3 s" or "2.5 s". */
export function secondsLabel(ms: number): string {
  return `${Number((ms / 1000).toFixed(1))} s`;
}
