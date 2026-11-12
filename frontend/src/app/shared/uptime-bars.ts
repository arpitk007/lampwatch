import { Component, computed, input, signal } from '@angular/core';

export interface DayUptime {
  date: string;
  uptime: number | null;
}

type Tone = 'full' | 'minor' | 'major' | 'none';

/**
 * One bar per day. Color shows the band (no downtime, under 1%, 1% or more, no data),
 * major-downtime days are also striped, and every day is readable by hover, keyboard
 * (arrow keys) or the table, so nothing depends on color alone.
 */
@Component({
  selector: 'app-uptime-bars',
  template: `
    <div class="bars" tabindex="0" role="group" [attr.aria-label]="summary() + '. Use the arrow keys to read each day.'"
         (keydown)="onKey($event)" (focus)="active.set(days().length - 1)" (blur)="active.set(null)"
         (pointerleave)="active.set(null)">
      @for (d of view(); track d.date; let i = $index) {
        <span class="col" [class.active]="active() === i" (pointerenter)="active.set(i)">
          <span class="bar" [class]="d.tone"></span>
        </span>
      }
      @if (activeDay(); as d) {
        <span class="tip" [style.left]="tipLeft()" aria-hidden="true">{{ d.label }}</span>
      }
    </div>
    <span class="visually-hidden" aria-live="polite">{{ activeDay()?.label ?? '' }}</span>
  `,
  styles: `
    :host { display: block; }
    .bars {
      position: relative;
      display: grid;
      grid-auto-flow: column;
      grid-auto-columns: 1fr;
      height: 32px;
    }
    .col { display: flex; height: 100%; padding: 0 1px; }
    .bar { display: block; width: 100%; border-radius: 3px; }
    .full { background: var(--up); }
    .minor { background: var(--slow-mark); }
    .major { background: repeating-linear-gradient(135deg, var(--down) 0 3px, var(--down-soft) 3px 5px); }
    .none { background: var(--strip-empty); }
    .col.active .bar { outline: 2px solid var(--ink); outline-offset: 1px; }
    .bars:focus-visible { outline-offset: 4px; }
    .tip {
      position: absolute;
      bottom: calc(100% + 8px);
      transform: translateX(-50%);
      padding: 0.35rem 0.6rem;
      background: var(--tooltip-bg);
      color: var(--tooltip-ink);
      font-size: var(--text-xs);
      white-space: nowrap;
      border-radius: 4px;
      pointer-events: none;
      z-index: 5;
    }
  `,
})
export class UptimeBars {
  readonly days = input.required<DayUptime[]>();
  /** The service is down right now: today's rollup lags, so its bar shows the live outage instead of "no data". */
  readonly downNow = input(false);
  protected readonly active = signal<number | null>(null);

  protected readonly view = computed(() => {
    const days = this.days();
    return days.map((d, i) =>
      this.downNow() && i === days.length - 1
        ? { date: d.date, tone: 'major' as Tone, label: `${dayLabel(d.date)}: down now` }
        : { date: d.date, tone: tone(d.uptime), label: `${dayLabel(d.date)}: ${uptimeLabel(d.uptime)}` },
    );
  });

  protected readonly activeDay = computed(() => {
    const i = this.active();
    return i === null ? null : (this.view()[i] ?? null);
  });

  protected readonly tipLeft = computed(() => {
    const pct = (((this.active() ?? 0) + 0.5) / this.view().length) * 100;
    return `clamp(6rem, ${pct}%, calc(100% - 6rem))`;
  });

  protected readonly summary = computed(() => {
    const days = this.days();
    const bad = days.filter((d) => d.uptime !== null && d.uptime < 100).length;
    return bad === 0 ? `No downtime in the last ${days.length} days` : `Downtime on ${bad} of the last ${days.length} days`;
  });

  protected onKey(event: KeyboardEvent): void {
    const last = this.view().length - 1;
    const current = this.active() ?? last;
    const next = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: last }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    this.active.set(Math.min(last, Math.max(0, next)));
  }
}

export function tone(uptime: number | null): Tone {
  if (uptime === null) return 'none';
  if (uptime >= 100) return 'full';
  return uptime >= 99 ? 'minor' : 'major';
}

export function uptimeLabel(uptime: number | null): string {
  return uptime === null ? 'no data' : `${uptime.toFixed(uptime >= 100 ? 0 : 2)}% uptime`;
}

export function dayLabel(date: string): string {
  return new Date(date + 'T00:00:00Z').toLocaleDateString([], { month: 'short', day: 'numeric', timeZone: 'UTC' });
}
