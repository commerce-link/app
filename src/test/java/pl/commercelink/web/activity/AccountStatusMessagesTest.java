package pl.commercelink.web.activity;

import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

/** Formats the account status texts the way Spring does: through MessageFormat only when there are arguments. */
class AccountStatusMessagesTest {

    private static String pl(String key, Object... args) {
        return format(Locale.forLanguageTag("pl"), key, args);
    }

    private static String en(String key, Object... args) {
        return format(Locale.ENGLISH, key, args);
    }

    private static String format(Locale locale, String key, Object... args) {
        String message = ResourceBundle.getBundle("messages", locale).getString(key);
        return args.length == 0 ? message : new MessageFormat(message, locale).format(args);
    }

    @Test
    void polishPillUsesTheRightFormOfTheVerbAndNoun() {
        // when / then
        assertThat(pl("account.status.pill.ending", 0L)).isEqualTo("Okres próbny · kończy się dziś");
        assertThat(pl("account.status.pill.ending", 1L)).isEqualTo("Okres próbny · został 1 dzień");
        assertThat(pl("account.status.pill.ending", 2L)).isEqualTo("Okres próbny · zostały 2 dni");
        assertThat(pl("account.status.pill.ending", 3L)).isEqualTo("Okres próbny · zostały 3 dni");
        assertThat(pl("account.status.pill.ending", 5L)).isEqualTo("Okres próbny · zostało 5 dni");
        assertThat(pl("account.status.pill.trial", 14L)).isEqualTo("Okres próbny · 14 dni");
        assertThat(pl("account.status.pill.short", 0L)).isEqualTo("dziś");
        assertThat(pl("account.status.pill.short", 9L)).isEqualTo("9");
    }

    @Test
    void englishTextsKeepTheirApostrophes() {
        // when / then
        assertThat(en("account.status.pop.ended.text", "01.10.2026", "15.10.2026", 13L))
                .isEqualTo("The trial ended on 01.10.2026. The store's data will be deleted for good on 15.10.2026 (in 13 days).");
        assertThat(en("account.status.alert.ended.text")).endsWith("The store's data will be deleted for good on");
        assertThat(en("account.status.pill.ending", 1L)).isEqualTo("Trial · 1 day left");
        assertThat(en("account.status.alert.ending.title", 0L)).isEqualTo("Your trial ends today");
    }

    @Test
    void deletionDateCountsDownToToday() {
        // when / then
        assertThat(pl("account.status.alert.ended.deletion", "15.10.2026", 13L)).isEqualTo("15.10.2026 (za 13 dni)");
        assertThat(pl("account.status.alert.ended.deletion", "15.10.2026", 1L)).isEqualTo("15.10.2026 (za 1 dzień)");
        assertThat(pl("account.status.alert.ended.deletion", "15.10.2026", 0L)).isEqualTo("15.10.2026 (dziś)");
    }
}
