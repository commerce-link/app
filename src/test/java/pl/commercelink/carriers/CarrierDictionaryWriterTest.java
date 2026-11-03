package pl.commercelink.carriers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierDictionaryWriterTest {

    private static final String MORELE = CarrierDictionaries.marketplaceImport("Morele");
    private static final String SHOPER = CarrierDictionaries.supplier("store-1", "Shoper-1");

    private final InMemoryCarrierDictionaries repository = new InMemoryCarrierDictionaries();
    private final CarrierDictionaryWriter writer = new CarrierDictionaryWriter(repository);

    @Test
    void savesTheNormalizedPairsInOrderAndSkipsEmptyRows() {
        // when
        CarrierDictionaryWriter.Result result = writer.save(MORELE, List.of(
                new CarrierMapping("  7 ", "Poczta Polska"),
                new CarrierMapping(null, null),
                new CarrierMapping("Kurier   DPD", " DPD ")), null);

        // then
        assertThat(result.isSaved()).isTrue();
        assertThat(repository.get(MORELE).getMappings())
                .extracting(CarrierMapping::getText, CarrierMapping::getCarrier)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("7", "Poczta Polska"),
                        org.assertj.core.groups.Tuple.tuple("Kurier DPD", "DPD"));
    }

    @Test
    void refusesAHalfFilledPair() {
        // when
        CarrierDictionaryWriter.Result result = writer.save(MORELE, List.of(new CarrierMapping("7", null)), null);

        // then
        assertThat(result.refusal()).isEqualTo(CarrierDictionaryWriter.Refusal.EMPTY_PAIR);
        assertThat(repository.find(MORELE)).isEmpty();
    }

    @Test
    void refusesTheSamePairTwice() {
        // when
        CarrierDictionaryWriter.Result result = writer.save(MORELE, List.of(
                new CarrierMapping("7", "Poczta Polska"), new CarrierMapping("7", "poczta polska")), null);

        // then
        assertThat(result.refusal()).isEqualTo(CarrierDictionaryWriter.Refusal.DUPLICATE_PAIR);
    }

    @Test
    void allowsOneMarketplaceCodeForTwoCarriers() {
        // when
        CarrierDictionaryWriter.Result result = writer.save(MORELE, List.of(
                new CarrierMapping("1", "InPost"), new CarrierMapping("1", "InPost Kurier")), null);

        // then
        assertThat(result.isSaved()).isTrue();
    }

    @Test
    void refusesASupplierMethodNamingTwoCarriers() {
        // when
        CarrierDictionaryWriter.Result result = writer.save(SHOPER, List.of(
                new CarrierMapping("5", "DPD"), new CarrierMapping("5", "InPost")), null);

        // then
        assertThat(result.refusal()).isEqualTo(CarrierDictionaryWriter.Refusal.AMBIGUOUS_SUPPLIER_TEXT);
        assertThat(result.text()).isEqualTo("5");
    }
}
