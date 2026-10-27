package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SellingPriceHistoryTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 10, 3);

    @Test
    void mergedHistoryTakesTheDailyMinimum() {
        // given
        SellingPriceHistory clone = new SellingPriceHistory("c", Map.of(DAY_1, 100L, DAY_2, 300L));
        SellingPriceHistory source = new SellingPriceHistory("s", Map.of(DAY_1, 200L, DAY_2, 250L, DAY_3, 400L));

        // when
        SellingPriceHistory merged = clone.mergedWith(source);

        // then
        assertEquals(Map.of(DAY_1, 100L, DAY_2, 250L, DAY_3, 400L), merged.getDailyLowestPrices());
        assertEquals("c", merged.getPimId());
        assertEquals(Map.of(DAY_1, 100L, DAY_2, 300L), clone.getDailyLowestPrices());
        assertEquals(Map.of(DAY_1, 200L, DAY_2, 250L, DAY_3, 400L), source.getDailyLowestPrices());
    }

    @Test
    void mergingWithNothingIsACopy() {
        // given
        SellingPriceHistory history = new SellingPriceHistory("c", Map.of(DAY_1, 100L));

        // when
        SellingPriceHistory merged = history.mergedWith(null);

        // then
        assertNotSame(history, merged);
        assertEquals(history.getDailyLowestPrices(), merged.getDailyLowestPrices());
    }
}
