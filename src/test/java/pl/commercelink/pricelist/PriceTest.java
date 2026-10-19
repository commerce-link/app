package pl.commercelink.pricelist;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.commercelink.invoicing.api.Price;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

class PriceTest {

    @ParameterizedTest(name = "gross {0} -> net {1}")
    @CsvSource({
            "100, 81.30",
            "1823.44, 1482.47",
            "2033, 1652.85",
            "4847.33, 3940.92",
            "391.12, 317.98",
            "12.48, 10.15"
    })
    void shouldCalculatePriceNetFromGross(double gross, double expectedNet) {
        Price price = Price.fromGross(gross, DEFAULT_VAT_RATE);
        assertThat(price.netValue()).isEqualTo(expectedNet);
    }

    @ParameterizedTest(name = "net {0} -> gross {1}")
    @CsvSource({
            "81.30, 100.00",
            "1482.47, 1823.44",
            "1652.85, 2033.01",
            "3940.92, 4847.33",
            "317.98, 391.12",
            "10.15, 12.48"
    })
    void shouldCalculatePriceGrossFromNet(double net, double expectedGross) {
        Price price = Price.fromNet(net);
        assertThat(price.grossValue()).isEqualTo(expectedGross);
    }
}
