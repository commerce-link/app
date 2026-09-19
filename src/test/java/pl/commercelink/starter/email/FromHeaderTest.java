package pl.commercelink.starter.email;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class FromHeaderTest {

    private static final String ADDRESS = "noreply@commercelink.pl";

    @Test
    void withoutADisplayNameTheBareAddressIsUsed() {
        // when / then
        assertThat(FromHeader.of(null, ADDRESS)).isEqualTo(ADDRESS);
        assertThat(FromHeader.of("  ", ADDRESS)).isEqualTo(ADDRESS);
    }

    @Test
    void anAsciiNameIsQuoted() {
        // when / then
        assertThat(FromHeader.of("Sklep Demo", ADDRESS)).isEqualTo("\"Sklep Demo\" <noreply@commercelink.pl>");
    }

    @Test
    void quotesAndBackslashesInAStoreNameAreEscapedInsteadOfBreakingTheHeader() {
        // when / then
        assertThat(FromHeader.of("Firma \"ABC\" sp. z o.o.", ADDRESS)).isEqualTo("\"Firma \\\"ABC\\\" sp. z o.o.\" <noreply@commercelink.pl>");
        assertThat(FromHeader.of("Sklep\\", ADDRESS)).isEqualTo("\"Sklep\\\\\" <noreply@commercelink.pl>");
    }

    @Test
    void lineBreaksCannotAddHeaders() {
        // when / then
        assertThat(FromHeader.of("Sklep\r\nBcc: x@example.com", ADDRESS)).isEqualTo("\"Sklep Bcc: x@example.com\" <noreply@commercelink.pl>");
    }

    @Test
    void aPolishNameIsSentAsAnEncodedWord() {
        // when
        String header = FromHeader.of("Sklep Łódź", ADDRESS);

        // then
        String encoded = Base64.getEncoder().encodeToString("Sklep Łódź".getBytes(StandardCharsets.UTF_8));
        assertThat(header).isEqualTo("=?UTF-8?B?" + encoded + "?= <noreply@commercelink.pl>");
    }

    /** RFC 2047 caps an encoded word at 75 characters; a longer Polish name is split into several on whole characters. */
    @Test
    void aLongPolishNameIsSplitIntoEncodedWordsOfAtMost75Characters() {
        // given
        String name = "Hurtownia Łódzka Żółć i Gęśla Jaźń Spółka z ograniczoną odpowiedzialnością";

        // when
        String header = FromHeader.of(name, "noreply@commercelink.pl");

        // then
        String words = header.substring(0, header.indexOf(" <"));
        assertThat(words.split(" ")).hasSizeGreaterThan(1).allSatisfy(word -> assertThat(word.length()).isLessThanOrEqualTo(75));
        java.io.ByteArrayOutputStream decoded = new java.io.ByteArrayOutputStream();
        for (String word : words.split(" ")) {
            assertThat(word).startsWith("=?UTF-8?B?").endsWith("?=");
            decoded.writeBytes(java.util.Base64.getDecoder().decode(word.substring(10, word.length() - 2)));
        }
        // Each word decodes on its own too: a split never cuts a character in half.
        assertThat(decoded.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(name);
        assertThat(header).endsWith(" <noreply@commercelink.pl>");
    }
}
