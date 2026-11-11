package com.uptime.monitor.checks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Stops monitors from being used for server-side request forgery (SSRF): anyone can
 * sign up and enter a URL, and the checker fetches it from inside our network. Without
 * this, a monitor could probe localhost, the database, other internal services, or the
 * cloud metadata endpoint (169.254.169.254) that hands out credentials.
 *
 * Every hostname is resolved and every address checked, at save time and again right
 * before each request and each redirect hop, so a domain re-pointed at an internal
 * address later is still caught. The remaining gap, DNS that changes between this check
 * and the connect (rebinding), is closed at the network layer on the server.
 */
@Component
public class UrlGuard {

    public static class BlockedUrlException extends Exception {
        public BlockedUrlException(String message) {
            super(message);
        }
    }

    private final boolean allowPrivate;

    public UrlGuard(@Value("${uptime.allow-private-targets:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /** Throws when the URL is malformed or any address its host resolves to is not public. */
    public void check(URI uri) throws BlockedUrlException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BlockedUrlException("Only http and https URLs can be checked");
        }
        if (uri.getRawUserInfo() != null) {
            throw new BlockedUrlException("URLs with a username or password aren't allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BlockedUrlException("The URL has no host");
        }
        if (allowPrivate) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return; // nothing to connect to; the check itself will fail and report it
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new BlockedUrlException(host + " points to a private or reserved address, which can't be monitored");
            }
        }
    }

    public static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet6Address) {
            if (isIpv4Mapped(b)) {
                return isBlockedIpv4(new byte[]{b[12], b[13], b[14], b[15]});
            }
            return (b[0] & 0xfe) == 0xfc; // fc00::/7 unique local
        }
        return address instanceof Inet4Address && isBlockedIpv4(b);
    }

    private static boolean isBlockedIpv4(byte[] b) {
        int a0 = b[0] & 0xff, a1 = b[1] & 0xff, a2 = b[2] & 0xff;
        return a0 == 0                                   // 0.0.0.0/8 "this network"
                || a0 == 10                              // private
                || a0 == 127                             // loopback
                || (a0 == 100 && (a1 & 0xc0) == 64)      // 100.64.0.0/10 carrier-grade NAT
                || (a0 == 169 && a1 == 254)              // link-local, includes cloud metadata
                || (a0 == 172 && (a1 & 0xf0) == 16)      // private
                || (a0 == 192 && a1 == 0 && a2 == 0)     // 192.0.0.0/24 protocol assignments
                || (a0 == 192 && a1 == 168)              // private
                || (a0 == 198 && (a1 & 0xfe) == 18)      // 198.18.0.0/15 benchmarking
                || a0 >= 224;                            // multicast, reserved, broadcast
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    }
}
