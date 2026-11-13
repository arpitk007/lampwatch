import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../api/monitor-api';
import { PublicStatusPage, StatusPageApi } from '../../api/status-page-api';
import { poll } from '../../shared/polling';
import { Logo } from '../../shared/logo';
import { SeaChart } from '../../shared/sea-chart';
import { serviceReading, statusHeadline } from '../../shared/status-headline';
import { clockTime, durationLabel } from '../../shared/time';
import { dayLabel, UptimeBars, uptimeLabel } from '../../shared/uptime-bars';

@Component({
  selector: 'app-public-status',
  imports: [UptimeBars, RouterLink, Logo, SeaChart],
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
  protected readonly headline = computed(() => statusHeadline(this.page()));

  protected reading(m: PublicStatusPage['monitors'][number], page: PublicStatusPage): string {
    return serviceReading(m, page);
  }

  protected readonly statusWord: Record<string, string> = {
    UP: 'Operational', DEGRADED: 'Slower than usual', DOWN: 'Down', PAUSED: 'Paused', UNKNOWN: 'No data yet',
  };
  /** Times on this page are in the visitor's zone; the daily table says UTC, so these say theirs. */
  protected readonly zone = localZone();
  /** The incident list covers 14 days, so only those days can say "no outage". */
  protected readonly outagesKnownFrom = new Date(Date.now() - 13 * 86_400_000).toISOString().slice(0, 10);
  /** UTC days on which each service had an outage open, by service name. */
  protected readonly outageDays = computed(() => {
    const byName = new Map<string, Set<string>>();
    for (const i of this.page()?.incidents ?? []) {
      const days = byName.get(i.monitorName) ?? new Set<string>();
      const end = i.resolvedAt ? Date.parse(i.resolvedAt) : Date.now();
      for (let t = Date.parse(i.startedAt); t < end + 86_400_000; t += 86_400_000) {
        days.add(new Date(Math.min(t, end)).toISOString().slice(0, 10));
      }
      byName.set(i.monitorName, days);
    }
    return byName;
  });
  protected readonly noDays: ReadonlySet<string> = new Set();
  protected readonly uptimeLabel = uptimeLabel;
  protected readonly dayLabel = dayLabel;
  protected readonly clockTime = clockTime;

  ngOnInit(): void {
    poll(() => this.load(), 60_000, this.destroyRef);
  }

  protected startedLabel(i: PublicStatusPage['incidents'][number]): string {
    return new Date(i.startedAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  protected lengthLabel(i: PublicStatusPage['incidents'][number]): string {
    const length = durationLabel((i.resolvedAt ? Date.parse(i.resolvedAt) : Date.now()) - Date.parse(i.startedAt));
    return i.resolvedAt ? length : `${length} so far`;
  }

  private load(): void {
    this.api.publicPage(this.slug()).subscribe({
      next: (page) => {
        this.page.set(page);
        this.error.set(null);
        document.title = /\bstatus$/i.test(page.title.trim()) ? page.title : `${page.title} status`;
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

/** "IST", "GMT+5:30": the short name of the visitor's own time zone. */
function localZone(): string {
  const part = new Intl.DateTimeFormat([], { timeZoneName: 'short' }).formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
  return part?.value ?? 'local time';
}
