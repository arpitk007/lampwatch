import { Component, input } from '@angular/core';

/**
 * The Lampwatch mark: the nautical-chart symbol for a lit light, a position dot with a magenta flare.
 * `tone` swaps the dot to white on dark grounds; the wordmark is optional.
 */
@Component({
  selector: 'app-logo',
  template: `
    <svg class="mark" viewBox="0 0 32 32" aria-hidden="true">
      <path class="flare" d="M11.2 19.6C13.4 12.6 19 6.4 26.2 3.9c1.6-.6 2.7.7 2 2.2C25 13 18.9 18.4 12.9 21.3z" />
      <circle class="dot" cx="9.4" cy="23.6" r="3.6" />
    </svg>
    @if (wordmark()) { <span class="word">Lampwatch</span> }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 0.5em; line-height: 1; }
    .mark { width: 1.5em; height: 1.5em; flex: none; }
    .flare { fill: var(--accent); }
    .dot { fill: var(--ink); }
    :host(.on-dark) .flare { fill: #e0418f; }
    :host(.on-dark) .dot { fill: var(--on-ink); }
    .word { font-family: var(--font-chart); font-size: 1.25em; letter-spacing: -0.01em; }
  `,
})
export class Logo {
  readonly wordmark = input(true);
}
