import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { catchError, firstValueFrom, of, throwError } from 'rxjs';

export interface User {
  id: number;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);

  /** undefined until the first /me answer, then the user or null. */
  readonly user = signal<User | null | undefined>(undefined);

  /** Asks the server once per page load; also gives the browser its XSRF-TOKEN cookie. */
  async ensureLoaded(): Promise<User | null> {
    const known = this.user();
    if (known !== undefined) return known;
    return this.refresh();
  }

  async refresh(): Promise<User | null> {
    const user = await firstValueFrom(
      this.http.get<User>('/api/auth/me').pipe(catchError(() => of(null))),
    );
    this.user.set(user);
    return user;
  }

  async signIn(email: string, password: string): Promise<User> {
    await this.ensureCsrfCookie();
    const user = await firstValueFrom(this.http.post<User>('/api/auth/login', { email, password }));
    this.user.set(user);
    return user;
  }

  async signUp(email: string, password: string): Promise<User> {
    await this.ensureCsrfCookie();
    const user = await firstValueFrom(this.http.post<User>('/api/auth/signup', { email, password }));
    this.user.set(user);
    return user;
  }

  async signOut(): Promise<void> {
    await firstValueFrom(this.http.post('/api/auth/logout', null).pipe(catchError(() => of(null))));
    this.user.set(null);
    await this.refresh(); // sign-out clears the CSRF cookie; fetch a fresh one for the next sign-in
  }

  /** The server rotates the CSRF cookie on sign-out; without it the next POST would be rejected. */
  private async ensureCsrfCookie(): Promise<void> {
    if (!document.cookie.split('; ').some((c) => c.startsWith('XSRF-TOKEN='))) {
      await this.refresh();
    }
  }
}

/** Pages that need an account. Sends signed-out visitors to sign in, then back. */
export const signedIn: CanActivateFn = async (_route, state) => {
  const router = inject(Router); // inject() only works before the first await
  const user = await inject(Auth).ensureLoaded();
  return user ? true : router.createUrlTree(['/signin'], { queryParams: { next: state.url } });
};

/** Matches only for visitors who aren't signed in, so `/` can be the homepage for them and the monitor list for users. */
export const guest: CanMatchFn = async () => !(await inject(Auth).ensureLoaded());

/** Sign-in and sign-up pages. Signed-in users go straight to their monitors. */
export const signedOut: CanActivateFn = async () => {
  const router = inject(Router);
  const user = await inject(Auth).ensureLoaded();
  return user ? router.createUrlTree(['/']) : true;
};

/** When the session expires mid-use, any 401 from the API sends the user to sign in. */
export const sessionExpired: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !req.url.startsWith('/api/auth/')) {
        auth.user.set(null);
        router.navigate(['/signin'], { queryParams: { next: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
