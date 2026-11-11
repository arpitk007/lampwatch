import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CheckType = 'HTTP' | 'KEYWORD';
export type MonitorStatus = 'UNKNOWN' | 'UP' | 'DEGRADED' | 'DOWN';

export interface Monitor {
  id: number;
  name: string;
  url: string;
  checkType: CheckType;
  keyword: string | null;
  intervalSeconds: number;
  timeoutMs: number;
  status: MonitorStatus;
  paused: boolean;
  lastCheckedAt: string | null;
  nextCheckAt: string;
  createdAt: string;
}

export interface MonitorInput {
  name: string;
  url: string;
  checkType: CheckType;
  keyword: string | null;
  intervalSeconds: number;
  timeoutMs: number;
}

export interface CheckResult {
  checkedAt: string;
  success: boolean;
  statusCode: number | null;
  responseTimeMs: number | null;
  errorMessage: string | null;
}

/** Shape of the backend's RFC 9457 problem responses. */
export interface Problem {
  detail?: string;
  errors?: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class MonitorApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/monitors';

  list(): Observable<Monitor[]> {
    return this.http.get<Monitor[]>(this.base);
  }

  get(id: number): Observable<Monitor> {
    return this.http.get<Monitor>(`${this.base}/${id}`);
  }

  create(input: MonitorInput): Observable<Monitor> {
    return this.http.post<Monitor>(this.base, input);
  }

  update(id: number, input: MonitorInput): Observable<Monitor> {
    return this.http.put<Monitor>(`${this.base}/${id}`, input);
  }

  pause(id: number): Observable<Monitor> {
    return this.http.post<Monitor>(`${this.base}/${id}/pause`, null);
  }

  resume(id: number): Observable<Monitor> {
    return this.http.post<Monitor>(`${this.base}/${id}/resume`, null);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Newest first. */
  checks(id: number, limit = 50): Observable<CheckResult[]> {
    return this.http.get<CheckResult[]>(`${this.base}/${id}/checks`, { params: { limit } });
  }

  /** Latest results for every monitor, keyed by monitor id, oldest first. */
  heartbeats(limit = 30): Observable<Record<string, CheckResult[]>> {
    return this.http.get<Record<string, CheckResult[]>>(`${this.base}/heartbeats`, { params: { limit } });
  }
}

export function problemOf(error: unknown): Problem {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { detail: 'Can’t reach the server. Check that the backend is running, then try again.' };
    }
    if (error.error && typeof error.error === 'object') {
      return error.error as Problem;
    }
    return { detail: `The server answered with HTTP ${error.status}.` };
  }
  return { detail: 'Something unexpected went wrong. Try again.' };
}
