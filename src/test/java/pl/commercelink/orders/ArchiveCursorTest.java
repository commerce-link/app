package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveCursorTest {

    @Test
    void encodedCursorDecodesToTheSameValues() {
        // given
        ArchiveCursor cursor = new ArchiveCursor("2026-10-08T18:42:11.123456", "8c1d02aa-0000-4000-8000-000000000001");

        // when
        String token = cursor.encode();

        // then
        assertThat(token).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(ArchiveCursor.decode(token)).contains(cursor);
    }

    @Test
    void garbageTokensDecodeToNothing() {
        // when / then
        assertThat(ArchiveCursor.decode(null)).isEmpty();
        assertThat(ArchiveCursor.decode("")).isEmpty();
        assertThat(ArchiveCursor.decode("%%%")).isEmpty();
        assertThat(ArchiveCursor.decode(new ArchiveCursor("2026-10-08", "").encode())).isEmpty();
        assertThat(ArchiveCursor.decode(java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("no-separator".getBytes()))).isEmpty();
    }

    @Test
    void cursorWithAnOrderedAtThatIsNoDateDecodesToNothing() {
        // when / then
        assertThat(ArchiveCursor.decode(new ArchiveCursor("yesterday", "8c1d02aa").encode())).isEmpty();
        assertThat(ArchiveCursor.decode(new ArchiveCursor("2026-10-08", "8c1d02aa").encode())).isPresent();
    }
}
