package com.uptime.monitor.checks;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlGuardTest {

    private final UrlGuard guard = new UrlGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1",
            "::1", "fc00::1", "fd12:3456::1", "fe80::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1"})
    void blocksPrivateAndReservedAddresses(String ip) throws Exception {
        assertThat(UrlGuard.isBlocked(InetAddress.getByName(ip))).as(ip).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8"})
    void allowsPublicAddresses(String ip) throws Exception {
        assertThat(UrlGuard.isBlocked(InetAddress.getByName(ip))).as(ip).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/", "http://169.254.169.254/latest/meta-data/", "http://[::1]/",
            "ftp://example.com/file", "http://admin:pw@example.com/", "file:///etc/passwd"})
    void rejectsDangerousUrls(String url) {
        assertThatThrownBy(() -> guard.check(URI.create(url))).isInstanceOf(UrlGuard.BlockedUrlException.class);
    }
}
