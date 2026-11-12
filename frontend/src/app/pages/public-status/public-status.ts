import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../api/monitor-api';
import { PublicStatusPage, StatusPageApi } from '../../api/status-page-api';
import { poll } from '../../shared/polling';
import { clockTime, durationLabel } from '../../shared/time';
import { dayLabel, UptimeBars, uptimeLabel } from '../../shared/uptime-bars';

@Component({
  selector: 'app-public-status',
  imports: [UptimeBars, RouterLink],
  templateUrl: './public-status.html',
  styleUrl: './public-status.scss',
})
export class PublicStatus implements OnInit {
  private readonly api = inject(StatusPageApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly slug = input.required<string>();

  protected readonly page = signal<PublicStatusPage | null>(null);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Names the affected services, so a visitor learns what is broken without scrolling. */
  protected readonly headline = computed(() => {
    const monitors = this.page()?.monitors ?? [];
    const named = (status: string, verb: string) => {
      const names = monitors.filter((m) => m.status === status).map((m) => m.name);
      if (names.length === 0) return `Some services are ${verb}`;
      if (names.length === 1) return `${names[0]} is ${verb}`;
      if (names.length <= 3) return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]} are ${verb}`;
      return `${names.length} services are ${verb}`;
    };
    switch (this.page()?.overall) {
      case 'MAJOR_OUTAGE':
        return { tone: 'down', text: monitors.length > 1 && monitors.every((m) => m.status === 'DOWN') ? 'All services are down' : named('DOWN', 'down') };
      case 'PARTIAL_OUTAGE':
        return { tone: 'down', text: named('DOWN', 'down') };
      case 'DEGRADED':
        return { tone: 'slow', text: named('DEGRADED', 'slow') };
      default:
        return { tone: 'up', text: 'All systems operational' };
    }
  });

  protected readonly statusWord: Record<string, string> = {
    UP: 'Operational', DEGRADED: 'Slower than usual', DOWN: 'Down', PAUSED: 'Paused', UNKNOWN: 'No data yet',
  };
  protected readonly uptimeLabel = uptimeLabel;
  protected readonly dayLabel = dayLabel;
  protected readonly clockTime = clockTime;

  ngOnInit(): void {
    poll(() => this.load(), 60_000, this.destroyRef);
  }

  protected incidentLine(i: PublicStatusPage['incidents'][number]): string {
    const start = new Date(i.startedAt);
    const when = start.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    const length = durationLabel((i.resolvedAt ? Date.parse(i.resolvedAt) : Date.now()) - start.getTime());
    return i.resolvedAt ? `${when}, down for ${length}` : `${when}, down for ${length} so far`;
  }

  private load(): void {
    this.api.publicPage(this.slug()).subscribe({
      next: (page) => {
        this.page.set(page);
        this.error.set(null);
        document.title = `${page.title} status`;
      },
      error: (e) => {
        if (e?.status === 404) this.notFound.set(true);
        else this.error.set(problemOf(e).detail ?? 'Couldn’t load this status page.');
      },
    });
  }

  protected generated(p: PublicStatusPage): string {
    return clockTime(new Date(p.generatedAt));
  }
}
