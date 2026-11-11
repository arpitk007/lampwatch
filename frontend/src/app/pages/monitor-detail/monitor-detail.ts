import { Component, computed, DestroyRef, inject, input, numberAttribute, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import { CheckResult, Monitor, MonitorApi, problemOf } from '../../api/monitor-api';
import { HeartbeatStrip } from '../../shared/heartbeat-strip';
import { poll } from '../../shared/polling';
import { StatusBadge } from '../../shared/status-badge';
import { clockTime, intervalLabel, timeAgo } from '../../shared/time';

@Component({
  selector: 'app-monitor-detail',
  imports: [RouterLink, HeartbeatStrip, StatusBadge],
  templateUrl: './monitor-detail.html',
  styleUrl: './monitor-detail.scss',
})
export class MonitorDetail implements OnInit {
  private readonly api = inject(MonitorApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly monitor = signal<Monitor | null>(null);
  /** Newest first, as the API returns them. */
  protected readonly checks = signal<CheckResult[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly updatedAt = signal<Date | null>(null);

  protected readonly oldestFirst = computed(() => [...this.checks()].reverse());
  protected readonly stats = computed(() => {
    const checks = this.checks();
    if (checks.length === 0) return null;
    const passed = checks.filter((c) => c.success);
    const times = passed.map((c) => c.responseTimeMs ?? 0);
    return {
      uptime: ((passed.length / checks.length) * 100).toFixed(passed.length === checks.length ? 0 : 1),
      average: times.length ? Math.round(times.reduce((a, b) => a + b, 0) / times.length) : null,
      count: checks.length,
    };
  });

  protected readonly timeAgo = timeAgo;
  protected readonly intervalLabel = intervalLabel;
  protected readonly clockTime = clockTime;

  ngOnInit(): void {
    // Not in the constructor: the route id input is only set by now.
    poll(() => this.load(), 15_000, this.destroyRef);
  }

  protected now(): number {
    return this.updatedAt()?.getTime() ?? Date.now();
  }

  protected togglePause(m: Monitor): void {
    this.act(m.paused ? this.api.resume(m.id) : this.api.pause(m.id), (updated) => this.monitor.set(updated));
  }

  protected remove(m: Monitor): void {
    if (!confirm(`Delete “${m.name}” and all of its check history? This can’t be undone.`)) return;
    this.act(this.api.delete(m.id), () => this.router.navigate(['/']));
  }

  private act<T>(request: Observable<T>, done: (value: T) => void): void {
    this.busy.set(true);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        done(value);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(problemOf(e).detail ?? 'That didn’t work. Try again.');
      },
    });
  }

  private load(): void {
    forkJoin({ monitor: this.api.get(this.id()), checks: this.api.checks(this.id(), 90) }).subscribe({
      next: ({ monitor, checks }) => {
        this.monitor.set(monitor);
        this.checks.set(checks);
        this.error.set(null);
        this.updatedAt.set(new Date());
      },
      error: (e) => this.error.set(problemOf(e).detail ?? 'Couldn’t load this monitor.'),
    });
  }
}
