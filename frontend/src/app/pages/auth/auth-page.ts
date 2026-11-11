import { Component, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../api/monitor-api';
import { Auth } from '../../auth/auth';

@Component({
  selector: 'app-auth-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.html',
  styleUrl: './auth-page.scss',
})
export class AuthPage {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  /** From route data. */
  readonly mode = input<'signin' | 'signup'>('signin');
  /** From the query string: where to go after signing in. */
  readonly next = input<string | undefined>(undefined);

  protected readonly isSignUp = computed(() => this.mode() === 'signup');

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required]],
  });

  protected readonly busy = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected emailError(): string | null {
    const server = this.serverErrors()['email'];
    if (server) return `Email ${server}.`;
    const c = this.form.controls.email;
    if (!c.touched || c.valid) return null;
    return c.hasError('required') ? 'Enter your email.' : 'Enter an email like name@example.com.';
  }

  protected passwordError(): string | null {
    const server = this.serverErrors()['password'];
    if (server) return `Password ${server}.`;
    const c = this.form.controls.password;
    if (!c.touched) return null;
    if (c.hasError('required')) return 'Enter your password.';
    if (this.isSignUp() && (c.value.length < 10 || c.value.length > 72)) return 'Use 10 to 72 characters.';
    return null;
  }

  protected async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.emailError() || this.passwordError()) return;

    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.formError.set(null);
    this.serverErrors.set({});
    try {
      if (this.isSignUp()) {
        await this.auth.signUp(email.trim(), password);
      } else {
        await this.auth.signIn(email.trim(), password);
      }
      await this.router.navigateByUrl(this.safeNext());
    } catch (e) {
      const problem = problemOf(e);
      this.serverErrors.set(problem.errors ?? {});
      this.formError.set(problem.errors ? null : (problem.detail ?? 'That didn’t work. Try again.'));
      this.busy.set(false);
    }
  }

  /** Only same-site paths, so a crafted ?next= link can't send users to another site. */
  private safeNext(): string {
    const next = this.next();
    return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
  }
}
