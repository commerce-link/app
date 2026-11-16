package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictingOffersTest {

    private static final String EAN_7600 = "4895106293618";
    private static final String EAN_9060 = "4895106296381";
    private static final String CODE_7600 = "11324-01-20G";
    private static final String CODE_9060 = "11350-03-20G";

    @Test
    void offerWhoseEanAndCodePointToDifferentProductsIsConflicting() {
        // given
        InventoryItem kosatec7600 = offer("Kosatec", "Sapphire", EAN_7600, CODE_7600, 1843);
        InventoryItem kosatec9060 = offer("Kosatec", "Sapphire", EAN_9060, CODE_9060, 2613);
        InventoryItem incom9060 = offer("IncomGroup", "Sapphire", EAN_9060, CODE_9060, 2663);
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        List<InventoryItem> own = List.of(kosatec7600, kosatec9060, incom9060, elko);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, sapphireWitnesses(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isTrue();
        assertThat(result.contains(kosatec7600)).isFalse();
        assertThat(result.contains(kosatec9060)).isFalse();
        assertThat(result.contains(incom9060)).isFalse();
        assertThat(result.size()).isEqualTo(1);
    }

    @Test
    void oneConfirmationOnTheEanSideIsNotEnough() {
        // given
        InventoryItem kosatec7600 = offer("Kosatec", "Sapphire", EAN_7600, CODE_7600, 1843);
        InventoryItem kosatec9060 = offer("Kosatec", "Sapphire", EAN_9060, CODE_9060, 2613);
        InventoryItem incom9060 = offer("IncomGroup", "Sapphire", EAN_9060, CODE_9060, 2663);
        InventoryItem wave9060 = offer("Wave", "Sapphire", EAN_9060, CODE_9060, 2700);
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        List<InventoryItem> own = List.of(kosatec7600, kosatec9060, incom9060, wave9060, elko);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, List.of(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isFalse();
    }

    @Test
    void emptyWitnessesDoNotFail() {
        // given
        InventoryItem only = offer("Kosatec", "Sapphire", EAN_7600, CODE_7600, 1843);
        List<InventoryItem> own = List.of(only);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, List.of(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.size()).isZero();
    }

    @Test
    void codeWrittenDifferentlyIsNotAConflict() {
        // given
        InventoryItem odd = offer("Elko", "TP-Link", "6935364000001", "SG3428X-M2", 500);
        List<InventoryItem> pool = new ArrayList<>(List.of(odd));
        pool.addAll(holders("TP-Link", "6935364000001", "TL-SG3428X-M2", 510, "Asbis", "Morele"));
        pool.addAll(holders("TP-Link", "6935364000002", "SG3428X-M2", 520, "Wave", "Action"));

        // when
        ConflictingOffers result = ConflictingOffers.of(pool, List.of(), SharedManufacturerCodes.of(pool));

        // then
        assertThat(result.contains(odd)).isFalse();
    }

    @Test
    void sameCodeUnderAnotherBrandSpellingIsNotAConflict() {
        // given
        InventoryItem hpe = offer("Elko", "HPE", "1957000000001", "P12345-B21", 900);
        List<InventoryItem> pool = new ArrayList<>(List.of(hpe));
        pool.addAll(holders("HP", "1957000000001", "P12345-B21", 910, "Asbis", "Morele"));

        // when
        ConflictingOffers result = ConflictingOffers.of(pool, List.of(), SharedManufacturerCodes.of(pool));

        // then
        assertThat(result.contains(hpe)).isFalse();
    }

    @Test
    void anotherSupplierWithTheSameEanAndCodeMakesItLegitimate() {
        // given
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        InventoryItem agreeing = offer("Asbis", "Sapphire", EAN_7600, CODE_9060, 3500);
        List<InventoryItem> own = List.of(elko, agreeing);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, sapphireWitnesses(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isFalse();
    }

    @Test
    void sidesLinkedByAnotherOfferAreOneProduct() {
        // given
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        InventoryItem linking = offer("Ingram", "Sapphire", EAN_9060, CODE_7600, 3000);
        List<InventoryItem> own = List.of(elko, linking);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, sapphireWitnesses(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isFalse();
    }

    @Test
    void conflictStaysAmongThousandsOfUnrelatedWitnesses() {
        // given
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        List<InventoryItem> own = List.of(elko);
        List<InventoryItem> witnesses = new ArrayList<>(unrelatedWitnesses(5000));
        witnesses.addAll(sapphireWitnesses());
        witnesses.addAll(unrelatedWitnesses(5000));

        // when
        ConflictingOffers result = ConflictingOffers.of(own, witnesses, SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isTrue();
    }

    @Test
    void witnessLinkingTheSidesIsFoundAlthoughItSharesNoKeyWithTheScope() {
        // given: the link holds the EAN of one confirmed side and the code of the other, neither of them the
        // scope offer's own, so it is related to the scope only through the confirming witnesses
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        List<InventoryItem> own = List.of(elko);
        List<InventoryItem> witnesses = new ArrayList<>(unrelatedWitnesses(1000));
        witnesses.addAll(sapphireWitnesses());
        witnesses.add(offer("Ingram", "Sapphire", EAN_9060, CODE_7600, 3000));

        // when
        ConflictingOffers result = ConflictingOffers.of(own, witnesses, SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isFalse();
    }

    @Test
    void witnessesConfirmUnderAnotherSpellingOfTheBrand() {
        // given
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        List<InventoryItem> own = List.of(elko);
        List<InventoryItem> witnesses = new ArrayList<>();
        witnesses.addAll(holders(" SAPPHIRE ", EAN_7600, CODE_7600, 1800, "Asbis", "Morele"));
        witnesses.addAll(holders("sapphire", EAN_9060, CODE_9060, 2600, "Asbis", "Wave"));

        // when
        ConflictingOffers result = ConflictingOffers.of(own, witnesses, SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isTrue();
    }

    @Test
    void noNameAndBrandlessOffersAreSkipped() {
        // given
        InventoryItem noName = offer("Elko", "NoName", EAN_7600, CODE_9060, 3563);
        InventoryItem brandless = offer("Elko", null, EAN_7600, CODE_9060, 3563);
        List<InventoryItem> own = List.of(noName, brandless);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, sapphireWitnesses(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.size()).isZero();
    }

    @Test
    void offerUnderAVariantCodeIsSkipped() {
        // given: the code is shared by two EANs of the same supplier, so it names a model and not a product
        InventoryItem elko = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        InventoryItem elkoOtherEan = offer("Elko", "Sapphire", "4895106200000", CODE_9060, 3563);
        List<InventoryItem> own = List.of(elko, elkoOtherEan);

        // when
        ConflictingOffers result = ConflictingOffers.of(own, sapphireWitnesses(), SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(elko)).isFalse();
    }

    @Test
    void theSameSupplierInBothScopesCountsOnce() {
        // given: Wave is connected globally and as the store's own, which is still one supplier
        InventoryItem odd = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        InventoryItem ownWave = offer("Wave-ab12cd34", "Sapphire", EAN_9060, CODE_9060, 2700);
        InventoryItem globalWave = offer("Wave", "Sapphire", EAN_9060, CODE_9060, 2700);
        List<InventoryItem> own = List.of(odd, ownWave);
        List<InventoryItem> witnesses = new ArrayList<>(List.of(globalWave));
        witnesses.addAll(holders("Sapphire", EAN_7600, CODE_7600, 1800, "Asbis", "Morele"));

        // when
        ConflictingOffers result = ConflictingOffers.of(own, witnesses, SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(odd)).isFalse();
    }

    @Test
    void manualFeedsAreSeparateSuppliers() {
        // given
        InventoryItem odd = offer("Elko", "Sapphire", EAN_7600, CODE_9060, 3563);
        InventoryItem manualA = offer("manual-aaaaaaaa", "Sapphire", EAN_9060, CODE_9060, 2700);
        InventoryItem manualB = offer("manual-bbbbbbbb", "Sapphire", EAN_9060, CODE_9060, 2710);
        List<InventoryItem> own = List.of(odd, manualA, manualB);
        List<InventoryItem> witnesses = holders("Sapphire", EAN_7600, CODE_7600, 1800, "Asbis", "Morele");

        // when
        ConflictingOffers result = ConflictingOffers.of(own, witnesses, SharedManufacturerCodes.of(own));

        // then
        assertThat(result.contains(odd)).isTrue();
    }

    @Test
    void offerWithoutEanIsIgnoredWithoutError() {
        // given
        InventoryItem noEan = offer("Elko", "Sapphire", null, CODE_9060, 3563);
        List<InventoryItem> pool = new ArrayList<>(List.of(noEan));
        pool.addAll(sapphireWitnesses());

        // when
        ConflictingOffers result = ConflictingOffers.of(pool, List.of(), SharedManufacturerCodes.of(pool));

        // then
        assertThat(result.contains(noEan)).isFalse();
    }

    @Test
    void offerWithoutCodeIsIgnoredWithoutError() {
        // given
        InventoryItem noCode = offer("Elko", "Sapphire", EAN_7600, null, 3563);
        List<InventoryItem> pool = new ArrayList<>(List.of(noCode));
        pool.addAll(sapphireWitnesses());

        // when
        ConflictingOffers result = ConflictingOffers.of(pool, List.of(), SharedManufacturerCodes.of(pool));

        // then
        assertThat(result.contains(noCode)).isFalse();
    }

    private List<InventoryItem> sapphireWitnesses() {
        List<InventoryItem> witnesses = new ArrayList<>();
        witnesses.addAll(holders("Sapphire", EAN_7600, CODE_7600, 1800, "Asbis", "Morele"));
        witnesses.addAll(holders("Sapphire", EAN_9060, CODE_9060, 2600, "Asbis", "Wave"));
        return witnesses;
    }

    private List<InventoryItem> unrelatedWitnesses(int count) {
        List<InventoryItem> witnesses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String ean = String.format("59%011d", i);
            witnesses.addAll(holders("Brand" + (i % 50), ean, "CODE-" + i, 100 + i, "Asbis", "Morele", "Wave"));
        }
        return witnesses;
    }

    private List<InventoryItem> holders(String brand, String ean, String code, double price, String... suppliers) {
        return java.util.Arrays.stream(suppliers).map(s -> offer(s, brand, ean, code, price)).toList();
    }

    private InventoryItem offer(String supplier, String brand, String ean, String mfn, double netPrice) {
        return new InventoryItem(brand, null, ean, mfn, supplier, netPrice, "PLN", 5, 1, true, true, false);
    }
}
