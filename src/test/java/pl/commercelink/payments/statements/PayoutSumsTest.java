package pl.commercelink.payments.statements;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutSumsTest {

    private static final PayoutChannel FOUR_RATES = new PayoutChannel("BANK", null, PaymentSource.Installments, true, null,
            List.of(1.0, 2.0, 3.0, 4.0));

    @Test
    void keepsTwoWaysOfMakingTheSameSumToTellThatThereAreSeveral() {
        // given
        PayoutSums sums = new PayoutSums(new BigDecimal("1940.00"));
        List<BigDecimal> fees = PayoutChannels.fees(FOUR_RATES, new BigDecimal("1000.00"), null);

        // when
        sums.add("first", new BigDecimal("1000.00"), fees);
        sums.add("second", new BigDecimal("1000.00"), fees);

        // then
        assertThat(sums.exact()).hasSize(2);
        assertThat(sums.undecided()).isFalse();
    }

    @Test
    void givesUpOnceTheSumsOutgrowTheBound() {
        // given
        PayoutSums sums = new PayoutSums(new BigDecimal("1000000.00"));

        // when
        for (int index = 0; index < 8; index++) {
            BigDecimal owed = new BigDecimal("1000.00").add(new BigDecimal("7.31").multiply(BigDecimal.valueOf(index)));
            sums.add("order-" + index, owed, PayoutChannels.fees(FOUR_RATES, owed, null));
        }

        // then
        assertThat(sums.undecided()).isTrue();
    }

    @Test
    void dropsTheSumsAboveThePayoutUntilNoneIsLeft() {
        // given
        PayoutSums sums = new PayoutSums(new BigDecimal("500.00"));

        // when
        sums.add("large", new BigDecimal("1000.00"), PayoutChannels.fees(FOUR_RATES, new BigDecimal("1000.00"), null));

        // then
        assertThat(sums.exhausted()).isTrue();
        assertThat(sums.exact()).isEmpty();
    }
}
