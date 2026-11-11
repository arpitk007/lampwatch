import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from './auth/auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigate(['/signin']);
  }
}
