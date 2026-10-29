package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the shared confirm-dialog fragment (used by every destructive dashboard action) through a
 * real Thymeleaf engine to prove the CSRF token field resolves and renders once CSRF is enabled --
 * a broken ${_csrf.token} expression would 500 every page that includes it.
 */
class ConfirmDialogCsrfRenderingTest {

    private String render() {
        Context context = new Context();
        context.setVariable("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token-abc"));
        return EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{fragments/confirm-dialog :: dialog}\"></div>", context);
    }

    @Test
    void confirmDialogCarriesTheCsrfTokenField() {
        // when
        String html = render();

        // then
        assertThat(html).contains("name=\"_csrf\"");
        assertThat(html).contains("value=\"test-token-abc\"");
        assertThat(html).doesNotContain("??");
    }
}
