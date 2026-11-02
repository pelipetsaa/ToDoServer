package com.pelypets.todolist.server.domain.model;

import com.pelypets.todolist.server.domain.exception.ValidationException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Field rules shared by the domain objects. Messages are part of the API and match the Ktor server. */
public final class DomainRules {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int TITLE_MAX_LENGTH = 200;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private DomainRules() {}

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public static Optional<String> emailError(String email) {
        if (email.isBlank()) return Optional.of("Email must not be blank");
        if (email.length() > EMAIL_MAX_LENGTH) {
            return Optional.of("Email must be at most " + EMAIL_MAX_LENGTH + " characters");
        }
        if (!EMAIL.matcher(email).matches()) return Optional.of("Email has an invalid format");
        return Optional.empty();
    }

    public static Optional<String> passwordError(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            return Optional.of("Password must be at least " + PASSWORD_MIN_LENGTH + " characters");
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            return Optional.of("Password must be at most " + PASSWORD_MAX_LENGTH + " characters");
        }
        return Optional.empty();
    }

    public static Optional<String> todoError(String title, String description) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty()) return Optional.of("Title must not be blank");
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            return Optional.of("Title must be at most " + TITLE_MAX_LENGTH + " characters");
        }
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            return Optional.of("Description must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        return Optional.empty();
    }

    public static void require(Optional<String> error) {
        error.ifPresent(message -> {
            throw new ValidationException(message);
        });
    }
}
