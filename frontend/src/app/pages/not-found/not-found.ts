import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../auth/auth';
import { Logo } from '../../shared/logo';

/** Any address the app doesn't know, and a monitor id that no longer exists. A ruled sheet, no chart drawing. */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, Logo],
  template: `
    @if (!auth.user() && !embedded()) {
      <header class="top"><a routerLink="/" class="home-link" aria-label="Lampwatch home"><app-logo /></a></header>
    }
    <section class="sheet" [class.lone]="!embedded() && !auth.user()" aria-labelledby="missing-title">
      <h1 id="missing-title">{{ headingText() }}</h1>
      <p>{{ bodyText() }}</p>
      <div class="actions">
        @if (auth.user()) {
          <a class="button primary" routerLink="/">Go to your monitors</a>
        } @else {
          <a class="button primary" routerLink="/">Go to Lampwatch</a>
          <a class="button" routerLink="/signin">Sign in</a>
        }
      </div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .top {
      display: flex;
      align-items: center;
      min-height: 4rem;
      padding: 0 clamp(1rem, 4vw, 2rem);
      background: var(--surface);
      border-bottom: 1px solid var(--line);
    }
    .home-link { display: inline-flex; align-items: center; min-height: 44px; color: var(--ink); text-decoration: none; font-size: 1.125rem; }
    .sheet {
      max-width: 40rem;
      padding-top: var(--space-6);
      border-top: 2px solid var(--ink);
      h1 { margin: 0 0 var(--space-3); }
      p { margin: 0 0 var(--space-6); color: var(--ink-soft); font-size: var(--text-base); max-width: 34rem; }
    }
    .sheet.lone { margin: clamp(2.5rem, 10vh, 6rem) auto 0; padding-inline: 0; width: calc(100% - 2 * clamp(1rem, 4vw, 2rem)); }
    .actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
  `,
})
export class NotFound {
  protected readonly auth = inject(Auth);
  /** True when shown inside another page (a missing monitor), which already has its own frame. */
  readonly embedded = input<boolean | undefined>(false);
  // The router binds route data to inputs and sets unmatched ones to undefined, so defaults live in computeds.
  readonly heading = input<string | undefined>();
  readonly body = input<string | undefined>();
  protected readonly headingText = computed(() => this.heading() ?? 'Nothing charted here');
  protected readonly bodyText = computed(
    () => this.body() ?? 'This address doesn’t match any page. It may have been mistyped, or the page has moved.',
  );
}
