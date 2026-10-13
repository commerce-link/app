package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RmaCursorTest {

    @Test
    void anEncodedCursorDecodesToTheSamePosition() {
        // given
        RMA rma = new RMA();
        rma.setRmaId("e3b0c442-98fc-4c14-9afb-f4c8996fb924");
        rma.setCreatedAt(LocalDateTime.of(2026, 10, 13, 9, 5, 30, 123_000_000));
        RmaCursor cursor = RmaCursor.of(rma);

        // when
        RmaCursor decoded = RmaCursor.decode(cursor.encode()).orElseThrow();

        // then
        assertThat(decoded).isEqualTo(cursor);
        assertThat(decoded.position()).isEqualTo("2026-10-13T09:05:30.123#e3b0c442-98fc-4c14-9afb-f4c8996fb924");
    }

    @Test
    void anUndatedRmaHasACursorToo() {
        // given
        RMA rma = new RMA();
        rma.setRmaId("a1");

        // when / then
        assertThat(RmaCursor.decode(RmaCursor.of(rma).encode())).contains(new RmaCursor("0000-01-01T00:00", "a1"));
    }

    @Test
    void anEditedTokenIsIgnored() {
        // when / then
        assertThat(RmaCursor.decode("not-base64!")).isEmpty();
        assertThat(RmaCursor.decode(new RmaCursor("yesterday", "a1").encode())).isEmpty();
        assertThat(RmaCursor.decode(new RmaCursor("2026-10-13T09:05", "a1#CLOSED").encode())).isEmpty();
        assertThat(RmaCursor.decode(null)).isEmpty();
    }
}
