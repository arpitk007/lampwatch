import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Auth } from './auth';

describe('Auth', () => {
  let auth: Auth;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(Auth);
    http = TestBed.inject(HttpTestingController);
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  afterEach(() => http.verify());

  it('treats a 401 from /me as signed out', async () => {
    const loaded = auth.ensureLoaded();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(await loaded).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('asks the server only once per page load', async () => {
    const first = auth.ensureLoaded();
    http.expectOne('/api/auth/me').flush({ id: 1, email: 'dev@example.com' });
    await first;
    expect(await auth.ensureLoaded()).toEqual({ id: 1, email: 'dev@example.com' });
    http.expectNone('/api/auth/me');
  });

  it('fetches a CSRF cookie before signing in when there is none', async () => {
    const signingIn = auth.signIn('dev@example.com', 'correct-horse-battery');
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await new Promise((resolve) => setTimeout(resolve)); // let the /me promise settle
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ email: 'dev@example.com', password: 'correct-horse-battery' });
    login.flush({ id: 7, email: 'dev@example.com' });
    expect(await signingIn).toEqual({ id: 7, email: 'dev@example.com' });
    expect(auth.user()?.id).toBe(7);
  });
});
