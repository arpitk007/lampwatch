import { Component, computed, input } from '@angular/core';
import { MonitorStatus } from '../api/monitor-api';

/** Status as shape + word + color, so it never relies on color alone. */
@Component({
  selector: 'app-status-badge',
  template: `
    <span class="badge" [class]="'badge ' + tone()">
      <svg class="mark" viewBox="0 0 12 12" aria-hidden="true">
        @switch (tone()) {
          @case ('up') { <circle cx="6" cy="6" r="5" /> }
          @case ('down') { <path d="M6 1 L11 10.5 L1 10.5 Z" /> }
          @case ('slow') { <path d="M6 0.8 L11.2 6 L6 11.2 L0.8 6 Z" /> }
          @case ('paused') { <rect x="2" y="1.5" width="2.8" height="9" /><rect x="7.2" y="1.5" width="2.8" height="9" /> }
          @default { <circle cx="6" cy="6" r="4.2" fill="none" stroke-width="1.6" /> }
        }
      </svg>
      {{ label() }}
    </span>
  `,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.4em;
      font-weight: 600;
      font-size: var(--text-sm);
      white-space: nowrap;
    }
    .mark { width: 0.75em; height: 0.75em; fill: currentColor; stroke: currentColor; }
    .up { color: var(--up); }
    .down { color: var(--down); }
    .slow { color: var(--slow); }
    .paused, .waiting { color: var(--muted); }
  `,
})
export class StatusBadge {
  readonly status = input.required<MonitorStatus>();
  readonly paused = input(false);

  protected readonly tone = computed(() => {
    if (this.paused()) return 'paused';
    switch (this.status()) {
      case 'UP':
        return 'up';
      case 'DOWN':
        return 'down';
      case 'DEGRADED':
        return 'slow';
      default:
        return 'waiting';
    }
  });

  protected readonly label = computed(
    () => ({ up: 'Up', slow: 'Slow', down: 'Down', paused: 'Paused', waiting: 'Waiting' })[this.tone()],
  );
}
