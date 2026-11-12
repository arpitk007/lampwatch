import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { Auth } from './auth/auth';
import { ConfirmDialog } from './shared/confirm';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ConfirmDialog],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);

  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map((e) => e.urlAfterRedirects)),
    { initialValue: this.router.url },
  );

  /** Public status pages are seen by visitors, so they never show the app chrome, even to a signed-in owner. */
  protected readonly showShell = computed(() => !!this.auth.user() && !this.url().startsWith('/s/'));

  protected async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigate(['/signin']);
  }
}
