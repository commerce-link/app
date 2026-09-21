package pl.commercelink.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** SWIFT and bank name are optional on a bank account (a Polish IBAN needs neither), so the customer pages hide empty ones. */
class ClientBankAccountTemplateTest {

    @ParameterizedTest
    @CsvSource({"clientOffer.html, bankAccount", "clientOrder.html, view.bankAccount"})
    void optionalBankDetailsAreShownOnlyWhenSet(String template, String account) throws Exception {
        // given
        String html = Files.readString(Path.of("src/main/resources/templates", template), StandardCharsets.UTF_8);

        // when / then
        for (String field : new String[]{"swiftCode", "bankName", "currency"}) {
            Pattern guardedItem = Pattern.compile("<li[^>]*th:if=\"\\$\\{[^}]*" + Pattern.quote(account + "." + field)
                    + "[^}]*}\"[^>]*>\\s*<strong[^>]*>\\s*</strong>\\s*<span th:text=\"\\$\\{" + Pattern.quote(account + "." + field) + "}\"");
            assertThat(guardedItem.matcher(html).find()).as(template + " " + field).isTrue();
        }
    }
}
