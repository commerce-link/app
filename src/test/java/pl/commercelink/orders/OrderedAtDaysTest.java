package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderedAtDaysTest {

    private final Map<String, AttributeValue> values = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    @Test
    void bothDaysRunFromTheBareFirstDayToTheEndOfTheLastOne() {
        // given
        OrderedAtDays days = new OrderedAtDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        // when
        String condition = days.keyCondition("storeId = :storeId", values, names);

        // then
        assertThat(condition).isEqualTo("storeId = :storeId AND #orderedAt BETWEEN :from AND :toEnd");
        assertThat(values.get(":from").getS()).isEqualTo("2026-10-01");
        assertThat(values.get(":toEnd").getS()).isEqualTo("2026-10-31T\uffff");
        assertThat(names).containsEntry("#orderedAt", "orderedAt");
    }

    @Test
    void onlyTheFirstDayStartsFromTheBareDate() {
        // given
        OrderedAtDays days = new OrderedAtDays(LocalDate.of(2026, 10, 1), null);

        // when
        String condition = days.keyCondition("discountCode = :code", values, names);

        // then
        assertThat(condition).isEqualTo("discountCode = :code AND #orderedAt >= :from");
        assertThat(values).containsOnlyKeys(":from");
    }

    @Test
    void onlyTheLastDayEndsBeforeTheNextDay() {
        // given
        OrderedAtDays days = new OrderedAtDays(null, LocalDate.of(2026, 10, 31));

        // when
        String condition = days.keyCondition("storeId = :storeId", values, names);

        // then
        assertThat(condition).isEqualTo("storeId = :storeId AND #orderedAt < :toNext");
        assertThat(values.get(":toNext").getS()).isEqualTo("2026-11-01");
    }

    @Test
    void withoutDaysNothingIsAdded() {
        // given
        OrderedAtDays days = new OrderedAtDays(null, null);

        // when
        String condition = days.keyCondition("storeId = :storeId", values, names);

        // then
        assertThat(condition).isEqualTo("storeId = :storeId");
        assertThat(values).isEmpty();
        assertThat(names).isEmpty();
    }
}
