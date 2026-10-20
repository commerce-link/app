package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.commercelink.stores.BankAccount;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** SWIFT and bank name are optional on a bank account (a Polish IBAN needs neither), so the customer pages hide empty ones. */
class ClientBankAccountTemplateTest {

    @ParameterizedTest
    @CsvSource({"clientOrder.html, view.bankAccount"})
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

    @Test
    void offerHidesUnsetOptionalAccountDetailsAndGroupsTheIban() {
        // when
        String html = ClientOfferRenderer.newOffer().render();

        // then
        assertThat(html).contains("<dd>PL61 1090 1014 0000 0712 1981 2874")
                .doesNotContain("<dt>Bank</dt>")
                .doesNotContain("SWIFT")
                .doesNotContain("<dt>Currency</dt>");
    }

    @Test
    void offerShowsOptionalAccountDetailsWhenSet() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        BankAccount account = renderer.store().getDefaultBankAccount();
        account.setBankName("Santander Bank Polska");
        account.setSwiftCode("WBKPPLPP");
        account.setCurrency("EUR");

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("<dd>Santander Bank Polska · SWIFT WBKPPLPP</dd>")
                .contains("<dt>Currency</dt>")
                .contains("<dd>EUR</dd>");
    }

    @Test
    void offerShowsSwiftAloneWithoutABankName() {
        // given
        ClientOfferRenderer renderer = ClientOfferRenderer.newOffer();
        renderer.store().getDefaultBankAccount().setSwiftCode("WBKPPLPP");

        // when
        String html = renderer.render();

        // then
        assertThat(html).contains("<dd>SWIFT WBKPPLPP</dd>");
    }
}
