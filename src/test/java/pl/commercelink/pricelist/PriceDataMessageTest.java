package pl.commercelink.pricelist;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceDataMessageTest {

    @Test
    void emptyBodyMeansGlobalRunOfToday() {
        // given
        String body = "";

        // when
        PriceDataMessage message = PriceDataMessage.parse(body);

        // then
        assertFalse(message.isForStore());
        assertEquals(LocalDate.now(), message.dayOrToday());
    }

    @Test
    void nullBodyMeansGlobalRun() {
        // when
        PriceDataMessage message = PriceDataMessage.parse(null);

        // then
        assertFalse(message.isForStore());
    }

    @Test
    void bodyThatIsNotJsonMeansGlobalRun() {
        // when
        PriceDataMessage message = PriceDataMessage.parse("run now");

        // then
        assertFalse(message.isForStore());
    }

    @Test
    void jsonWithStoreIdMeansStoreRunOfTheGivenDay() {
        // given
        String body = "{\"storeId\":\"store-1\",\"date\":\"2026-10-07\"}";

        // when
        PriceDataMessage message = PriceDataMessage.parse(body);

        // then
        assertTrue(message.isForStore());
        assertEquals("store-1", message.storeId());
        assertEquals(LocalDate.of(2026, 10, 7), message.dayOrToday());
    }

    @Test
    void unknownPropertiesAreIgnored() {
        // given
        String body = "{\"storeId\":\"store-1\",\"somethingElse\":1}";

        // when
        PriceDataMessage message = PriceDataMessage.parse(body);

        // then
        assertEquals("store-1", message.storeId());
    }

    @Test
    void unreadableDayFallsBackToToday() {
        // given
        String body = "{\"storeId\":\"store-1\",\"date\":\"yesterday\"}";

        // when
        PriceDataMessage message = PriceDataMessage.parse(body);

        // then
        assertEquals(LocalDate.now(), message.dayOrToday());
    }

    @Test
    void storeMessageSurvivesSerializationToTheQueue() throws Exception {
        // given
        PriceDataMessage sent = PriceDataMessage.forStore("store-1", LocalDate.of(2026, 10, 7));

        // when
        PriceDataMessage received = PriceDataMessage.parse(new ObjectMapper().writeValueAsString(sent));

        // then
        assertEquals(sent, received);
    }
}
