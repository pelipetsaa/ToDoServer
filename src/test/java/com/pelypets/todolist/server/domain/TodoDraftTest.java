package com.pelypets.todolist.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pelypets.todolist.server.domain.exception.ValidationException;
import com.pelypets.todolist.server.domain.model.DomainRules;
import com.pelypets.todolist.server.domain.model.TodoDraft;
import com.pelypets.todolist.server.domain.model.TodoStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TodoDraftTest {

    private static final Instant DUE = Instant.parse("2026-10-29T10:00:00Z");

    @Test
    void trimsTheTitleAndFillsDefaults() {
        TodoDraft draft = new TodoDraft("  Buy milk  ", null, DUE, null);

        assertThat(draft.title()).isEqualTo("Buy milk");
        assertThat(draft.description()).isEmpty();
        assertThat(draft.status()).isEqualTo(TodoStatus.TODO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankTitle(String title) {
        assertThatThrownBy(() -> new TodoDraft(title, "", DUE, TodoStatus.TODO))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Title must not be blank");
    }

    @Test
    void enforcesLengthLimitsAfterTrimming() {
        String maxTitle = "x".repeat(DomainRules.TITLE_MAX_LENGTH);
        assertThat(new TodoDraft("  " + maxTitle + "  ", "", DUE, null).title()).isEqualTo(maxTitle);

        assertThatThrownBy(() -> new TodoDraft(maxTitle + "x", "", DUE, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new TodoDraft("ok", "d".repeat(DomainRules.DESCRIPTION_MAX_LENGTH + 1), DUE, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void requiresADueDate() {
        assertThatThrownBy(() -> new TodoDraft("ok", "", null, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void emailsAreNormalizedAndValidated() {
        assertThat(DomainRules.normalizeEmail("  New.User@Example.COM ")).isEqualTo("new.user@example.com");
        assertThat(DomainRules.emailError("user@example.com")).isEmpty();
        assertThat(DomainRules.emailError("not-an-email")).contains("Email has an invalid format");
        assertThat(DomainRules.passwordError("short")).isPresent();
        assertThat(DomainRules.passwordError("long-enough")).isEmpty();
    }
}
