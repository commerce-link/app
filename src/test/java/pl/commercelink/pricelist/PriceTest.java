package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Price;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

class PriceTest {

    @Test
    void shouldCalculatePriceNetFromGross100() {
        Price price = Price.fromGross(100, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(81.30);
    }

    @Test
    void shouldCalculatePriceNetFromGross1823_44() {
        Price price = Price.fromGross(1823.44, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(1482.47);
    }

    @Test
    void shouldCalculatePriceNetFromGross2033() {
        Price price = Price.fromGross(2033, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(1652.85);
    }

    @Test
    void shouldCalculatePriceNetFromGross4847_33() {
        Price price = Price.fromGross(4847.33, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(3940.92);
    }

    @Test
    void shouldCalculatePriceNetFromGross391_12() {
        Price price = Price.fromGross(391.12, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(317.98);
    }

    @Test
    void shouldCalculatePriceNetFromGross12_48() {
        Price price = Price.fromGross(12.48, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(10.15);
    }

    @Test
    void shouldCalculatePriceGrossFromNet81_30() {
        Price price = Price.fromNet(81.30);
        assertThat(price.grossValue()).isEqualTo(100.00);
    }

    @Test
    void shouldCalculatePriceGrossFromNet1482_47() {
        Price price = Price.fromNet(1482.47);
        assertThat(price.grossValue()).isEqualTo(1823.44);
    }

    @Test
    void shouldCalculatePriceGrossFromNet1652_85() {
        Price price = Price.fromNet(1652.85);
        assertThat(price.grossValue()).isEqualTo(2033.01);
    }

    @Test
    void shouldCalculatePriceGrossFromNet3940_92() {
        Price price = Price.fromNet(3940.92);
        assertThat(price.grossValue()).isEqualTo(4847.33);
    }

    @Test
    void shouldCalculatePriceGrossFromNet317_98() {
        Price price = Price.fromNet(317.98);
        assertThat(price.grossValue()).isEqualTo(391.12);
    }

    @Test
    void shouldCalculatePriceGrossFromNet10_15() {
        Price price = Price.fromNet(10.15);
        assertThat(price.grossValue()).isEqualTo(12.48);
    }
}
