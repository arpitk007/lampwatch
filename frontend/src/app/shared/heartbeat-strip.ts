import { plainCheckError } from './plain-error';
import { Component, computed, input, signal } from '@angular/core';
import { CheckResult } from '../api/monitor-api';

interface Bar {
  kind: 'up' | 'slow' | 'down' | 'empty';
  height: number;
  label: string;
}

/**
 * The last N checks as vertical bars, newest on the right.
 * Passed checks scale with response time against `slowAfterMs`, so every strip on a page shares one scale
 * and a full-height bar means "at the Slow threshold". Slow checks are full height and solid amber;
 * failed checks are full height, red and striped, so each state differs by texture as well as color.
 * Each bar's column is its hover target; with `interactive`, arrow keys read each check.
 */
@Component({
  selector: 'app-heartbeat-strip',
  template: `
    <div
      class="strip"
      [style.height.px]="height()"
      [attr.role]="interactive() ? 'group' : 'img'"
      [attr.tabindex]="interactive() ? 0 : null"
      [attr.aria-label]="interactive() ? summary() + '. Use the arrow keys to read each check.' : summary()"
      (pointerleave)="active.set(null)"
      (keydown)="onKey($event)"
      (focus)="onFocus()"
      (blur)="active.set(null)"
    >
      @for (bar of bars(); track $index) {
        <span class="col" [class.active]="active() === $index" (pointerenter)="hover($index)">
          <span class="bar" [class]="bar.kind" [style.height.%]="bar.height"></span>
        </span>
      }
      @if (activeBar(); as bar) {
        <span class="tip" [style.left]="tipLeft()" aria-hidden="true">{{ bar.label }}</span>
      }
    </div>
    @if (interactive()) {
      <span class="visually-hidden" aria-live="polite">{{ activeBar()?.label ?? '' }}</span>
    }
  `,
  styles: `
    :host { display: block; }
    .strip {
      position: relative;
      display: grid;
      grid-auto-flow: column;
      grid-auto-columns: 1fr;
      width: 100%;
      border-bottom: 1px solid var(--line);
    }
    .col {
      display: flex;
      align-items: flex-end;
      height: 100%;
      padding: 0 1px; /* the 2px surface gap between bars, inside the hit target */
    }
    .bar {
      display: block;
      width: 100%;
      min-height: 3px;
      border-radius: 4px 4px 0 0;
    }
    .up { background: var(--up); }
    .slow { background: var(--slow-mark); }
    .down {
      background: repeating-linear-gradient(135deg, var(--down) 0 3px, var(--down-soft) 3px 5px);
    }
    .empty { background: var(--strip-empty); }
    .col.active .bar { outline: 2px solid var(--ink); outline-offset: 1px; }
    .tip {
      position: absolute;
      bottom: calc(100% + 8px);
      transform: translateX(-50%);
      padding: 0.35rem 0.6rem;
      background: var(--tooltip-bg);
      color: var(--tooltip-ink);
      font-size: var(--text-xs);
      line-height: 1.35;
      white-space: nowrap;
      border-radius: 4px;
      pointer-events: none;
      z-index: 5;
    }
    .strip:focus-visible { outline-offset: 4px; }
  `,
})
export class HeartbeatStrip {
  /** Oldest first. */
  readonly checks = input.required<CheckResult[]>();
  readonly slots = input(30);
  readonly height = input(28);
  readonly interactive = input(false);
  /** The Slow threshold; until it is known, bars scale to the slowest check in the strip. */
  readonly slowAfterMs = input<number | null>(null);

  protected readonly active = signal<number | null>(null);

  protected readonly bars = computed<Bar[]>(() => {
    const recent = this.checks().slice(-this.slots());
    const scale = this.slowAfterMs() ?? Math.max(1, ...recent.map((c) => c.responseTimeMs ?? 0));
    const padding: Bar[] = Array.from({ length: this.slots() - recent.length }, () => ({
      kind: 'empty',
      height: 18,
      label: '',
    }));
    const bars: Bar[] = recent.map((c) => {
      const when = new Date(c.checkedAt).toLocaleString([], {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
      });
      if (!c.success) {
        return { kind: 'down', height: 100, label: `${when}: failed, ${plainCheckError(c.errorMessage)?.text ?? 'no response'}` };
      }
      const ms = c.responseTimeMs ?? 0;
      const threshold = this.slowAfterMs();
      if (threshold !== null && ms > threshold) {
        return { kind: 'slow', height: 100, label: `${when}: passed in ${ms} ms, slow` };
      }
      return { kind: 'up', height: 30 + 70 * Math.min(1, ms / scale), label: `${when}: passed in ${ms} ms` };
    });
    return [...padding, ...bars];
  });

  protected readonly activeBar = computed(() => {
    const i = this.active();
    const bar = i === null ? undefined : this.bars()[i];
    return bar && bar.kind !== 'empty' ? bar : null;
  });

  /** Keeps the tooltip inside the strip near both ends. */
  protected readonly tipLeft = computed(() => {
    const i = this.active() ?? 0;
    const pct = ((i + 0.5) / this.bars().length) * 100;
    return `clamp(7rem, ${pct}%, calc(100% - 7rem))`;
  });

  protected readonly summary = computed(() => {
    const recent = this.checks().slice(-this.slots());
    if (recent.length === 0) return 'No checks yet';
    const passed = recent.filter((c) => c.success).length;
    return `${passed} of the last ${recent.length} checks passed`;
  });

  private get firstReal(): number {
    return this.slots() - Math.min(this.checks().length, this.slots());
  }

  protected hover(i: number): void {
    this.active.set(i >= this.firstReal ? i : null);
  }

  protected onFocus(): void {
    if (this.checks().length > 0) this.active.set(this.bars().length - 1);
  }

  protected onKey(event: KeyboardEvent): void {
    if (!this.interactive() || this.checks().length === 0) return;
    const last = this.bars().length - 1;
    const current = this.active() ?? last;
    const next = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: this.firstReal, End: last }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    this.active.set(Math.min(last, Math.max(this.firstReal, next)));
  }
}
