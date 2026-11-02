package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.FeedItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedRowTest {

    private static final SupplierInfo SUPPLIER = new SupplierInfo("SupplierX", SupplierType.Distributor, 7, "PL",
            new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));

    private record RequiredOnly(String ean, String mfn, String brand) implements FeedItem {
        public String getEan() { return ean; }
        public String getMfn() { return mfn; }
        public String getBrand() { return brand; }
        public String getName() { return "GeForce RTX"; }
        public String getCategory() { return "Komputery > Karty graficzne"; }
        public double getNetPrice() { return 99.99; }
        public String getCurrency() { return "PLN"; }
        public int getQty() { return 3; }
    }

    private static FeedItem withEveryValue() {
        return new FeedItem() {
            public String getEan() { return "5901234123457"; }
            public String getMfn() { return "MFN-1"; }
            public String getBrand() { return "ASUS"; }
            public String getName() { return "GeForce RTX"; }
            public String getCategory() { return "Komputery > Karty graficzne"; }
            public double getNetPrice() { return 99.99; }
            public String getCurrency() { return "EUR"; }
            public int getQty() { return 3; }
            public int getLeadTimeDays() { return 4; }
            public boolean isSellable() { return false; }
            public boolean isInStock() { return true; }
            public boolean isInDelivery() { return true; }
            public String getSku() { return "0101"; }
            public Integer getNetWeightInGrams() { return 1200; }
            public Integer getGrossWeightInGrams() { return 1500; }
        };
    }

    @Test
    void offerAndProductGetTheSameBrand() {
        // given
        FeedItem feedItem = new RequiredOnly("5901234123457", "MFN-1", "ASUS");

        // when
        ParsedRow parsed = ParsedRow.of(feedItem, SUPPLIER);

        // then
        assertEquals("ASUS", parsed.item().brand());
        assertEquals("ASUS", parsed.product().brand());
    }

    @Test
    void supplierNameAndAccuracyComeFromSupplierInfo() {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("5901234123457", "MFN-1", "ASUS"), SUPPLIER);

        // then
        assertEquals("SupplierX", parsed.item().supplier());
        assertEquals(7, parsed.product().dataAccuracyScore());
    }

    @Test
    void codesPriceStockAndNameComeFromTheFeedItem() {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("5901234123457", "MFN-1", "ASUS"), SUPPLIER);

        // then
        assertEquals("5901234123457", parsed.item().ean());
        assertEquals("MFN-1", parsed.item().mfn());
        assertEquals(99.99, parsed.item().netPrice());
        assertEquals("PLN", parsed.item().currency());
        assertEquals(3, parsed.item().qty());
        assertEquals("5901234123457", parsed.product().ean());
        assertEquals("MFN-1", parsed.product().mfn());
        assertEquals("GeForce RTX", parsed.product().name());
    }

    @Test
    void skuWeightsAndCategoryComeFromTheFeedItem() {
        // when
        ParsedRow parsed = ParsedRow.of(withEveryValue(), SUPPLIER);

        // then
        assertEquals("0101", parsed.item().sku());
        assertEquals(1200, parsed.product().netWeightInGrams());
        assertEquals(1500, parsed.product().grossWeightInGrams());
        assertEquals("Komputery > Karty graficzne", parsed.product().rawCategory());
    }

    @Test
    void leadTimeAndFlagsComeFromTheFeedItem() {
        // when
        ParsedRow parsed = ParsedRow.of(withEveryValue(), SUPPLIER);

        // then
        assertEquals(4, parsed.item().leadTimeDays());
        assertFalse(parsed.item().sellable());
        assertTrue(parsed.item().inStock());
        assertTrue(parsed.item().inDelivery());
    }

    @Test
    void feedItemWithOnlyRequiredValuesGetsDefaults() {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("5901234123457", "MFN-1", "ASUS"), SUPPLIER);

        // then
        assertEquals(0, parsed.item().leadTimeDays());
        assertTrue(parsed.item().sellable());
        assertFalse(parsed.item().inStock());
        assertFalse(parsed.item().inDelivery());
        assertNull(parsed.item().sku());
        assertNull(parsed.product().netWeightInGrams());
        assertNull(parsed.product().grossWeightInGrams());
    }

    @Test
    void eanAndMfnAreUnified() {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("0590123412345", "mfn 1", "ASUS"), SUPPLIER);

        // then
        assertEquals("590123412345", parsed.item().ean());
        assertEquals("MFN1", parsed.item().mfn());
        assertEquals("590123412345", parsed.product().ean());
        assertEquals("MFN1", parsed.product().mfn());
    }

    @ParameterizedTest
    @ValueSource(strings = {"No name", "NoName", "No-Name", "OEM", "Inne", "Inny", "Brak", "Sonstige", "Diverse",
            "Mix", "Unknown", "Other", "N/A", "None", "-", " no name ", "NONAME"})
    void placeholderBrandLeavesTheRowWithoutBrand(String placeholder) {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("5901234123457", "MFN-1", placeholder), SUPPLIER);

        // then
        assertNull(parsed.item().brand());
        assertNull(parsed.product().brand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankBrandLeavesTheRowWithoutBrand(String blank) {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("5901234123457", "MFN-1", blank), SUPPLIER);

        // then
        assertNull(parsed.item().brand());
        assertNull(parsed.product().brand());
    }

    @Test
    void brandMerelyContainingAPlaceholderWordIsKept() {
        // when
        ParsedRow parsed = ParsedRow.of(new RequiredOnly("5901234123457", "MFN-1", "Microsoft (OEM)"), SUPPLIER);

        // then
        assertEquals("Microsoft (OEM)", parsed.item().brand());
        assertEquals("Microsoft (OEM)", parsed.product().brand());
    }

    @Test
    void csvRowIsAssembledFromTheParsedFeedItem() {
        // given
        CsvRowParser parser = row -> new RequiredOnly(row[0], "MFN-1", "ASUS");

        // when
        Optional<ParsedRow> parsed = ParsedRow.tryParse(parser, new String[]{"5901234123457"}, SUPPLIER);

        // then
        assertTrue(parsed.isPresent());
        assertEquals("5901234123457", parsed.get().item().ean());
        assertEquals("SupplierX", parsed.get().item().supplier());
    }

    @Test
    void csvRowSkippedByTheParserIsNotAssembled() {
        // given
        CsvRowParser parser = row -> null;

        // when
        Optional<ParsedRow> parsed = ParsedRow.tryParse(parser, new String[]{"x"}, SUPPLIER);

        // then
        assertTrue(parsed.isEmpty());
    }

    @Test
    void csvRowFailingInParseIsNotAssembled() {
        // given
        CsvRowParser parser = row -> {
            throw new NumberFormatException("abc");
        };

        // when
        Optional<ParsedRow> parsed = ParsedRow.tryParse(parser, new String[]{"x"}, SUPPLIER);

        // then
        assertTrue(parsed.isEmpty());
    }

    @Test
    void csvRowFailingInAGetterIsNotAssembled() {
        // given
        CsvRowParser parser = row -> new FeedItem() {
            public String getEan() { return "5901234123457"; }
            public String getMfn() { return "MFN-1"; }
            public String getBrand() { return "ASUS"; }
            public String getName() { return "GeForce RTX"; }
            public String getCategory() { return null; }
            public double getNetPrice() { return Double.parseDouble("abc"); }
            public String getCurrency() { return "PLN"; }
            public int getQty() { return 1; }
        };

        // when
        Optional<ParsedRow> parsed = ParsedRow.tryParse(parser, new String[]{"x"}, SUPPLIER);

        // then
        assertTrue(parsed.isEmpty());
    }
}
