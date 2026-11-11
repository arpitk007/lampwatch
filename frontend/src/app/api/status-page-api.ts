import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface StatusPageSettings {
  slug: string;
  title: string;
  monitorIds: number[];
}

export type Overall = 'OPERATIONAL' | 'DEGRADED' | 'PARTIAL_OUTAGE' | 'MAJOR_OUTAGE';

export interface PublicStatusPage {
  title: string;
  overall: Overall;
  generatedAt: string;
  monitors: {
    name: string;
    status: 'UNKNOWN' | 'UP' | 'DEGRADED' | 'DOWN' | 'PAUSED';
    uptime24h: number | null;
    uptime30d: number | null;
    days: { date: string; uptime: number | null }[];
  }[];
  incidents: { monitorName: string; startedAt: string; resolvedAt: string | null }[];
}

@Injectable({ providedIn: 'root' })
export class StatusPageApi {
  private readonly http = inject(HttpClient);

  mine(): Observable<StatusPageSettings> {
    return this.http.get<StatusPageSettings>('/api/status-page');
  }

  save(settings: StatusPageSettings): Observable<StatusPageSettings> {
    return this.http.put<StatusPageSettings>('/api/status-page', settings);
  }

  remove(): Observable<void> {
    return this.http.delete<void>('/api/status-page');
  }

  publicPage(slug: string): Observable<PublicStatusPage> {
    return this.http.get<PublicStatusPage>(`/api/public/status-pages/${encodeURIComponent(slug)}`);
  }
}
