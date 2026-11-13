import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CheckResult } from '../../api/monitor-api';
import { DEMO_SLUG, GITHUB_URL } from '../../config';
import { PublicStatusPage, StatusPageApi } from '../../api/status-page-api';
import { HeartbeatStrip } from '../../shared/heartbeat-strip';
import { Logo } from '../../shared/logo';
import { serviceReading, statusHeadline } from '../../shared/status-headline';
import { UptimeBars, uptimeLabel } from '../../shared/uptime-bars';
import { ChartLight, LIGHT_FOR_STATUS, LightState, SeaChart } from '../../shared/sea-chart';
import { clockTime } from '../../shared/time';



interface BulletinLine {
  name: string;
  state: LightState;
  reading: string;
}


/** Shown only when the demo page can't be loaded, and labelled as sample data wherever it appears. */
const SAMPLE: PublicStatusPage = {
  title: 'Sample',
  overall: 'PARTIAL_OUTAGE',
  generatedAt: new Date().toISOString(),
  monitors: [
    { name: 'Storefront', status: 'UP', uptime24h: 100, uptime30d: 99.98, days: [] },
    { name: 'Checkout API', status: 'DOWN', uptime24h: 91.2, uptime30d: 99.41, days: [] },
    { name: 'Search', status: 'DEGRADED', uptime24h: 100, uptime30d: 99.9, days: [] },
    { name: 'Docs', status: 'UP', uptime24h: 100, uptime30d: 100, days: [] },
  ],
  incidents: [{ monitorName: 'Checkout API', startedAt: new Date(Date.now() - 42 * 60_000).toISOString(), resolvedAt: null }],
};

/** An illustrative check history for the "how an outage is called" walkthrough: passes, three misses, two passes. */
const EXAMPLE_CHECKS: CheckResult[] = [180, 190, 175, 210, 185, null, null, null, null, 240, 205, 190].map((ms, i) => ({
  checkedAt: new Date(Date.UTC(2026, 0, 1, 9, i)).toISOString(),
  success: ms !== null,
  statusCode: ms !== null ? 200 : 503,
  responseTimeMs: ms,
  errorMessage: ms !== null ? null : 'HTTP 503',
}));

@Component({
  selector: 'app-home',
  imports: [ReactiveFormsModule, RouterLink, Logo, SeaChart, HeartbeatStrip, UptimeBars],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly api = inject(StatusPageApi);
  private readonly router = inject(Router);

  protected readonly demoSlug = DEMO_SLUG;
  protected readonly github = GITHUB_URL;
  protected readonly exampleChecks = EXAMPLE_CHECKS;

  protected readonly page = signal<PublicStatusPage | null>(null);
  /** The demo status page's own headline, for the live excerpt below the fold. */
  protected readonly pageHeadline = computed(() => statusHeadline(this.page()));
  protected readonly isSample = signal(false);
  protected readonly readAt = signal<Date | null>(null);
  protected readonly highlighted = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.email, Validators.maxLength(254)]],
  });

  protected readonly lights = computed<ChartLight[]>(() =>
    (this.page()?.monitors ?? []).map((m) => ({ name: m.name, state: LIGHT_FOR_STATUS[m.status] })),
  );

  /** One fixed-form line per service, the way a forecast reads each sea area. */
  protected readonly bulletin = computed<BulletinLine[]>(() => {
    const page = this.page();
    if (!page) return [];
    return page.monitors.map((m) => ({ name: m.name, state: LIGHT_FOR_STATUS[m.status], reading: serviceReading(m, page) }));
  });

  protected readonly uptimeLabel = uptimeLabel;
  /** The chart light each state shows, as on the status page itself. */
  protected readonly characteristic: Record<string, string> = { UP: 'Fl G 5s', DEGRADED: 'Fl(2) Y 8s', DOWN: 'Q R', PAUSED: 'Unlit', UNKNOWN: 'Unlit' };

  protected readonly statusWord: Record<string, string> = {
    UP: 'Operational', DEGRADED: 'Slower than usual', DOWN: 'Down', PAUSED: 'Paused', UNKNOWN: 'No data yet',
  };

  protected readonly downCount = computed(() => this.bulletin().filter((l) => l.state === 'down').length);

  constructor() {
    this.api.publicPage(DEMO_SLUG).subscribe({
      next: (page) => this.show(page, false),
      error: () => this.show(SAMPLE, true),
    });
  }

  protected start(): void {
    const email = this.form.controls.email;
    if (email.invalid) {
      email.markAsTouched();
      return;
    }
    this.router.navigate(['/signup'], { queryParams: email.value ? { email: email.value.trim() } : {} });
  }

  private show(page: PublicStatusPage, sample: boolean): void {
    this.page.set(page);
    this.isSample.set(sample);
    this.readAt.set(sample ? new Date() : new Date(page.generatedAt));
  }

  protected clock(date: Date): string {
    return clockTime(date);
  }
}
