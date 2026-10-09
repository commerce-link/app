package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.OrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfilmentCommitTest {

    @Mock
    private OrderItem waitingForSupplier;
    @Mock
    private OrderItem reserved;
    @Mock
    private OrderItem reservationFailed;
    @Mock
    private OrderItem service;

    @Test
    void countsOnlyItemsThatReachedAllocationOrTheWarehouse() {
        // given
        when(waitingForSupplier.isProduct()).thenReturn(true);
        when(waitingForSupplier.isInAllocation()).thenReturn(true);
        when(reserved.isProduct()).thenReturn(true);
        when(reserved.isWarehouseFulfilled()).thenReturn(true);
        when(reservationFailed.isProduct()).thenReturn(true);
        when(service.isService()).thenReturn(true);
        when(service.isWarehouseFulfilled()).thenReturn(true);

        // when
        FulfilmentCommit commit = FulfilmentCommit.of(List.of(waitingForSupplier, reserved, reservationFailed, service));

        // then
        assertThat(commit).isEqualTo(new FulfilmentCommit(1, 1));
        assertThat(commit.isEmpty()).isFalse();
    }

    @Test
    void nothingSavedIsEmpty() {
        // when
        FulfilmentCommit commit = FulfilmentCommit.of(List.of());

        // then
        assertThat(commit.isEmpty()).isTrue();
    }
}
