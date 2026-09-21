package pl.commercelink.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledExecutionCounterTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Mock
    private DailyScheduleExecutionCountRepository repository;

    private ScheduledExecutionCounter counterAt(String instant) {
        return new ScheduledExecutionCounter(repository, Clock.fixed(Instant.parse(instant), WARSAW));
    }

    @Test
    void incrementsTheCounterOfTheCurrentDay() {
        // when
        counterAt("2026-09-16T10:23:11Z").countCompleted("store-1", ScheduledExecution.ORDERS_IMPORT, "Allegro");

        // then
        verify(repository).increment("store-1", LocalDate.of(2026, 9, 16), ScheduledExecution.ORDERS_IMPORT, "Allegro");
    }

    @Test
    void theDayIsTakenInTheScheduleTimezoneNotUtc() {
        // when
        counterAt("2026-09-16T22:30:00Z").countCompleted("store-1", ScheduledExecution.SUPPLIER_FEED, "Wortmann");

        // then
        verify(repository).increment("store-1", LocalDate.of(2026, 9, 17), ScheduledExecution.SUPPLIER_FEED, "Wortmann");
    }

    @Test
    void storageFailureIsLoggedAndSwallowed() {
        // given
        doThrow(new RuntimeException("dynamodb unavailable"))
                .when(repository).increment(anyString(), any(LocalDate.class), any(ScheduledExecution.class), anyString());

        // when / then
        assertThatCode(() -> counterAt("2026-09-16T10:23:11Z").countCompleted("store-1", ScheduledExecution.PRICELIST, "catalog-1"))
                .doesNotThrowAnyException();
    }
}
