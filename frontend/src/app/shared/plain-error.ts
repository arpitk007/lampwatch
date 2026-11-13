/**
 * The checker and the alert sender store raw error text ("ConnectException", "HTTP 503"), which suits
 * a log but not an owner reading it during an outage. These turn it into a sentence; the raw text
 * stays available for anyone who needs it.
 */
export interface PlainError {
  text: string;
  /** The stored text, when it says more than the sentence does. */
  raw: string | null;
}

const HTTP_WORDS: Record<number, string> = {
  400: 'The site rejected the request',
  401: 'The site asked for a login',
  403: 'The site refused access',
  404: 'The page wasn’t found',
  410: 'The page has been removed',
  429: 'The site said too many requests',
  500: 'The site had an internal error',
  502: 'The site’s server got a bad answer from behind it',
  503: 'The site is unavailable',
  504: 'The site’s server timed out waiting behind it',
};

/** Raw exception names, matched anywhere in the text, most specific first. */
const EXCEPTIONS: [RegExp, string][] = [
  [/UnknownHost|UnresolvedAddress|Name or service not known/i, 'The domain name didn’t resolve'],
  [/SSLHandshake|SSLException|certificate|PKIX/i, 'The secure connection failed, usually an expired or invalid certificate'],
  [/HttpTimeout|SocketTimeout|TimeoutException|timed out/i, 'No answer in time'],
  [/ConnectException|Connection refused|NoRouteToHost/i, 'Couldn’t connect to the server'],
  [/Tunnel failed|ProxyException/i, 'Couldn’t reach the site through the network'],
  [/EOFException|Connection reset|closed|IOException/i, 'The connection dropped before an answer came back'],
];

export function plainCheckError(raw: string | null | undefined): PlainError | null {
  if (!raw) return null;
  const http = /^HTTP (\d{3})$/.exec(raw);
  if (http) {
    const code = Number(http[1]);
    const words = HTTP_WORDS[code] ?? (code >= 500 ? 'The site had a server error' : 'The site answered with an error');
    return { text: `${words} (HTTP ${code})`, raw: null };
  }
  const timeout = /^Timed out after (\d+) ms$/.exec(raw);
  if (timeout) return { text: `No answer within ${seconds(Number(timeout[1]))}`, raw: null };
  const keyword = /^Keyword not found: (.*)$/s.exec(raw);
  if (keyword) return { text: `The page loaded but didn’t contain “${keyword[1]}”`, raw: null };
  const blocked = /^Blocked: (.*)$/s.exec(raw);
  if (blocked) return { text: blocked[1], raw: null };
  if (raw === 'Check interrupted') return { text: 'The check was cut short and will run again', raw: null };
  return fromException(raw, 'The check failed');
}

export function plainAlertError(raw: string | null | undefined): PlainError | null {
  if (!raw) return null;
  if (/Telegram is not configured/i.test(raw)) {
    return { text: 'Telegram isn’t set up on this server. Remove this channel, or ask whoever runs Lampwatch to add a bot token.', raw };
  }
  if (/chat not found/i.test(raw)) {
    return { text: 'Telegram doesn’t know this chat. Message the bot first, then check the chat id.', raw };
  }
  if (/blocked by the user|bot was blocked/i.test(raw)) {
    return { text: 'This chat has blocked the bot. Unblock it in Telegram, then send a test.', raw };
  }
  if (/Mail|SMTP|Authentication/i.test(raw)) {
    return { text: 'The mail server didn’t accept the message. Ask whoever runs Lampwatch to check its email settings.', raw };
  }
  return fromException(raw, 'Sending failed');
}

function fromException(raw: string, fallback: string): PlainError {
  const hit = EXCEPTIONS.find(([pattern]) => pattern.test(raw));
  return { text: hit ? hit[1] : fallback, raw };
}

function seconds(ms: number): string {
  const s = ms / 1000;
  return `${Number.isInteger(s) ? s : s.toFixed(1)} s`;
}
