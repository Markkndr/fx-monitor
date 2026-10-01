package com.currencyexchange.security;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Decides which secret signs JWTs. An explicitly configured secret (the {@code JWT_SECRET}
 * environment variable) always wins. Otherwise a random 512-bit secret is generated on first
 * run and stored in a per-user file, so every install gets its own key, nothing secret lives
 * in the repository, and issued tokens stay valid across restarts.
 */
@Slf4j
final class JwtSecretResolver {

    /** HS512 requires a key of at least 512 bits. */
    static final int MIN_SECRET_BYTES = 64;

    private static final int GENERATED_SECRET_BYTES = 64;

    private JwtSecretResolver() {
    }

    static String resolve(String configuredSecret, Path secretFile) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            if (configuredSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
                        + " bytes (512 bits) for HS512");
            }
            return configuredSecret;
        }
        try {
            if (Files.exists(secretFile)) {
                return readExisting(secretFile);
            }
            return generate(secretFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read or create JWT secret file " + secretFile, e);
        }
    }

    private static String generate(Path secretFile) throws IOException {
        byte[] random = new byte[GENERATED_SECRET_BYTES];
        new SecureRandom().nextBytes(random);
        String secret = Base64.getEncoder().encodeToString(random);

        if (secretFile.getParent() != null) {
            Files.createDirectories(secretFile.getParent());
        }
        try {
            // CREATE_NEW: if another instance created the file first, use theirs instead.
            Files.writeString(secretFile, secret, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            return readExisting(secretFile);
        }
        restrictToOwner(secretFile);
        log.info("Generated a new JWT signing secret at {}", secretFile);
        return secret;
    }

    private static String readExisting(Path secretFile) throws IOException {
        String secret = Files.readString(secretFile).trim();
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret file " + secretFile + " holds a key shorter than "
                    + MIN_SECRET_BYTES + " bytes; delete it to have a new one generated");
        }
        return secret;
    }

    /** Owner-only permissions on POSIX systems; on Windows the user profile ACL already applies. */
    private static void restrictToOwner(Path secretFile) {
        try {
            Files.setPosixFilePermissions(secretFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Could not set owner-only permissions on {}: {}", secretFile, e.getMessage());
        }
    }
}
