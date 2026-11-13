import { Component, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { Auth } from './auth/auth';
import { AlertHealth } from './shared/alert-health';
import { ConfirmDialog } from './shared/confirm';
import { Logo } from './shared/logo';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ConfirmDialog, Logo],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);
  protected readonly alertHealth = inject(AlertHealth);

  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map((e) => e.urlAfterRedirects)),
    { initialValue: this.router.url },
  );

  /** Public status pages are seen by visitors, so they never show the app chrome, even to a signed-in owner. */
  /** Splits an address at the @, so the sidebar can show it on two clean lines instead of cutting it. */
  protected emailParts(email: string): { local: string; domain: string } {
    const at = email.lastIndexOf('@');
    return at < 0 ? { local: email, domain: '' } : { local: email.slice(0, at), domain: email.slice(at) };
  }

  protected readonly showShell = computed(() => !!this.auth.user() && !this.url().startsWith('/s/'));
  /** The signed-out homepage and public status pages draw their own header, main and footer landmarks. */
  protected readonly isHome = computed(
    () => (!this.auth.user() && this.url().split('?')[0] === '/') || this.url().startsWith('/s/'),
  );

  constructor() {
    // Re-read channel health on every app navigation, so the rail marker follows fixes made elsewhere.
    effect(() => {
      this.url();
      if (this.showShell()) untracked(() => this.alertHealth.refresh());
    });
  }

  protected async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigate(['/signin']);
  }
}
