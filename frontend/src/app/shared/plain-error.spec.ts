import { plainAlertError, plainCheckError } from './plain-error';

describe('plainCheckError', () => {
  it('names HTTP failures in words and keeps the code', () => {
    expect(plainCheckError('HTTP 503')).toEqual({ text: 'The site is unavailable (HTTP 503)', raw: null });
    expect(plainCheckError('HTTP 599')?.text).toBe('The site had a server error (HTTP 599)');
  });

  it('turns timeouts and missing keywords into sentences', () => {
    expect(plainCheckError('Timed out after 5000 ms')?.text).toBe('No answer within 5 s');
    expect(plainCheckError('Keyword not found: Welcome')?.text).toBe('The page loaded but didn’t contain “Welcome”');
  });

  it('translates exception names and keeps the raw text', () => {
    expect(plainCheckError('ConnectException')).toEqual({ text: 'Couldn’t connect to the server', raw: 'ConnectException' });
    expect(plainCheckError('java.net.UnknownHostException: nope.example')?.text).toBe('The domain name didn’t resolve');
    expect(plainCheckError('IOException: Tunnel failed, got: 403')?.text).toBe('Couldn’t reach the site through the network');
  });

  it('returns null when there is no error', () => {
    expect(plainCheckError(null)).toBeNull();
  });
});

describe('plainAlertError', () => {
  it('gives the missing Telegram setup a way out', () => {
    expect(plainAlertError('IllegalStateException: Telegram is not configured on this server')?.text).toContain('Remove this channel');
  });
});
