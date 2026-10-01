package com.currencyexchange.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretResolverTest {

    private static final String CONFIGURED =
            "a-configured-secret-that-is-comfortably-longer-than-sixty-four-bytes-for-hs512";

    @TempDir
    Path dir;

    @Test
    @DisplayName("a configured secret wins and no secret file is created")
    void configuredSecretWins() {
        Path file = dir.resolve("jwt-secret");

        assertThat(JwtSecretResolver.resolve(CONFIGURED, file)).isEqualTo(CONFIGURED);
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("rejects a configured secret shorter than 512 bits")
    void rejectsShortConfiguredSecret() {
        assertThatThrownBy(() -> JwtSecretResolver.resolve("too-short", dir.resolve("jwt-secret")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 bytes");
    }

    @Test
    @DisplayName("generates a strong secret on first run, creating parent directories")
    void generatesSecretWhenNoneConfigured() throws Exception {
        Path file = dir.resolve("nested/.fx-monitor/jwt-secret");

        String secret = JwtSecretResolver.resolve("", file);

        assertThat(file).exists();
        assertThat(Files.readString(file)).isEqualTo(secret);
        assertThat(secret.getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThanOrEqualTo(JwtSecretResolver.MIN_SECRET_BYTES);
    }

    @Test
    @DisplayName("reuses the stored secret on later runs so tokens survive restarts")
    void reusesStoredSecret() {
        Path file = dir.resolve("jwt-secret");

        String first = JwtSecretResolver.resolve(null, file);
        String second = JwtSecretResolver.resolve(null, file);

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("each install generates a different secret")
    void installsGetDistinctSecrets() {
        String a = JwtSecretResolver.resolve(null, dir.resolve("a/jwt-secret"));
        String b = JwtSecretResolver.resolve(null, dir.resolve("b/jwt-secret"));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("refuses a stored secret that is too short instead of signing with a weak key")
    void rejectsShortStoredSecret() throws Exception {
        Path file = dir.resolve("jwt-secret");
        Files.writeString(file, "short");

        assertThatThrownBy(() -> JwtSecretResolver.resolve(null, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delete it");
    }
}
