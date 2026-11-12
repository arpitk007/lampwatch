import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { Auth } from './auth/auth';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows the sidebar with a brand link to the monitor list when signed in', async () => {
    TestBed.inject(Auth).user.set({ id: 1, email: 'a@example.com' });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const brand = (fixture.nativeElement as HTMLElement).querySelector('.rail .brand');
    expect(brand?.textContent).toContain('Uptime');
    expect(brand?.getAttribute('href')).toBe('/');
  });

  it('leaves the chrome to the page when signed out', async () => {
    TestBed.inject(Auth).user.set(null);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.rail')).toBeNull();
  });
});
