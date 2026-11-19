// Temporary: delete it, together with MatchingDifferentialTest and the Reference* classes, in the first change that
// alters matching on purpose.
package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.brand.NoNameBrand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Seeded random pools of offers and PIM entries for {@link MatchingDifferentialTest}. Every value comes from a small
 * dictionary (EANs, codes, brands, suppliers, prices around the 50 PLN and 2x thresholds), so offers collide on their
 * keys often enough to exercise every matching rule.
 */
final class MatchingPoolGenerator {

    private static final List<String> EANS = List.of(
            "5901000000011", "5901000000028", "5901000000035", "5901000000042", "5901000000059", "5901000000066");
    // the first two differ only by notation
    private static final List<String> CODES = List.of("AB-1234", "AB1234", "CX-77", "ZT9000");
    private static final List<String> BRANDS = List.of("Acme", " acme ", "Bolt", "", NoNameBrand.NAME);
    private static final List<String> MATCHABLE_BRANDS = List.of("Acme", " acme ", "Bolt");
    private static final List<String> GLOBAL_SUPPLIERS = List.of("Kosatec", "Elko", "IncomGroup");
    // an own instance of a globally connected adapter type, and a manual feed
    private static final List<String> OWN_SUPPLIERS = List.of("Kosatec-k7f3a9c2", "manual:H1");
    private static final double[] PRICES = {1, 10, 40, 49, 51, 60, 90, 99, 101, 110, 190, 210, 400, 1000};

    private final Random random;
    private int scenarioNumber;

    MatchingPoolGenerator(long seed) {
        this.random = new Random(seed);
    }

    record Scenario(List<InventoryItem> globalOffers, List<PimEntry> globalPim, VariantCodes globalEanOnly,
                    List<InventoryItem> ownOffers, List<PimEntry> storePim, VariantCodes storeEanOnly) {
    }

    /** A product the offers of one scenario cluster around; offers deviate from it in single keys. */
    private record Product(String ean, String code, String brand, int priceIndex) {
    }

    Scenario next() {
        scenarioNumber++;
        List<String> eans = new ArrayList<>(EANS);
        Collections.shuffle(eans, random);

        // global products take EANs from the front of the shuffled list; own-only products reuse a global product's
        // brand and code under an EAN from the back, which no global product has (the case linking is about)
        List<String> codes = new ArrayList<>(CODES);
        Collections.shuffle(codes, random);
        List<Product> globalProducts = new ArrayList<>();
        int globalProductCount = 2 + random.nextInt(3);
        for (int i = 0; i < globalProductCount; i++) {
            // a code of its own per product: codes meet only through the offers that deviate from their product
            String brand = random.nextInt(5) == 0 ? pick(BRANDS) : pick(MATCHABLE_BRANDS);
            globalProducts.add(new Product(eans.get(i), codes.get(i), brand, random.nextInt(PRICES.length)));
        }
        List<Product> ownProducts = new ArrayList<>(globalProducts);
        int ownOnlyCount = 1 + random.nextInt(Math.min(3, EANS.size() - globalProductCount));
        Product model = pick(globalProducts);
        for (int i = 0; i < ownOnlyCount; i++) {
            // often the same model again: two own products under one brand and code, both pointing at one global group
            if (random.nextBoolean()) {
                model = pick(globalProducts);
            }
            int priceIndex = clampPrice(model.priceIndex() + random.nextInt(5) - 2);
            ownProducts.add(new Product(eans.get(EANS.size() - 1 - i), model.code(), model.brand(), priceIndex));
        }

        boolean emptyOwn = scenarioNumber % 20 == 0;
        boolean emptyGlobal = scenarioNumber % 20 == 10;
        int globalCount = emptyGlobal ? 0 : random.nextInt(15);
        int ownCount = emptyOwn ? 0 : random.nextInt(9);

        List<InventoryItem> globalOffers = offers(globalCount, globalProducts, GLOBAL_SUPPLIERS, false, "G");
        // half of the stores sell only products the global pool lacks
        List<Product> ownOnlyProducts = ownProducts.subList(globalProductCount, ownProducts.size());
        List<InventoryItem> ownOffers = offers(ownCount, random.nextBoolean() ? ownOnlyProducts
                : ownProducts.subList(globalProductCount / 2, ownProducts.size()), OWN_SUPPLIERS, true, "O");

        List<PimEntry> globalPim = pimEntries("PIM-G", globalProducts);
        // half the time only entries of the own-only products: two of them under one brand and code stay two own groups
        List<PimEntry> storePim = pimEntries("PIM-S", random.nextBoolean() ? ownOnlyProducts : ownProducts);
        VariantCodes globalEanOnly = eanOnlyPairs(random.nextInt(3));
        VariantCodes storeEanOnly = eanOnlyPairs(random.nextInt(2));
        return new Scenario(globalOffers, globalPim, globalEanOnly, ownOffers, storePim, storeEanOnly);
    }

    /**
     * @param stickySuppliers whether a product is mostly sold by one supplier of the pool: with only two own suppliers,
     *                        random ones would list nearly every own code under several EANs (a shared code)
     */
    private List<InventoryItem> offers(int count, List<Product> products, List<String> suppliers, boolean stickySuppliers,
                                       String skuPrefix) {
        List<InventoryItem> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (!offers.isEmpty() && random.nextInt(15) == 0) {
                // an equal record under another identity: groups hold offers by equality, the guards by identity
                InventoryItem copy = pick(offers);
                offers.add(item(copy.ean(), copy.mfn(), copy.netPrice(), copy.supplier(), copy.brand(), copy.sku()));
                continue;
            }
            String sku = skuPrefix + i;
            if (random.nextInt(8) == 0) {
                offers.add(item(pick(EANS), pick(CODES), PRICES[random.nextInt(PRICES.length)], pick(suppliers),
                        pick(BRANDS), sku));
                continue;
            }
            int productIndex = random.nextInt(products.size());
            Product product = products.get(productIndex);
            String supplier = stickySuppliers && random.nextInt(8) > 0
                    ? suppliers.get(productIndex % suppliers.size()) : pick(suppliers);
            // a deviating key mostly belongs to another product of the pool: it bridges two products
            String ean = random.nextInt(6) == 0 ? (random.nextInt(3) > 0 ? pick(products).ean() : pick(EANS)) : product.ean();
            String code = random.nextInt(5) == 0 ? (random.nextInt(3) > 0 ? pick(products).code() : pick(CODES)) : product.code();
            String brand = random.nextInt(8) == 0 ? pick(BRANDS) : product.brand();
            double price = PRICES[clampPrice(product.priceIndex() + spread())];
            offers.add(item(ean, code, price, supplier, brand, sku));
        }
        return offers;
    }

    // mostly close to the product's price, sometimes far: the price controls must both keep and split groups
    private int spread() {
        int roll = random.nextInt(10);
        if (roll < 5) {
            return random.nextInt(3) - 1;
        }
        if (roll < 8) {
            return random.nextInt(7) - 3;
        }
        return random.nextInt(13) - 6;
    }

    private List<PimEntry> pimEntries(String idPrefix, List<Product> products) {
        int count = random.nextInt(4);
        List<PimEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Product product = pick(products);
            String brand = random.nextInt(4) == 0 ? pick(BRANDS) : product.brand();
            List<PimIdentifier> identifiers = new ArrayList<>();
            int gtins = random.nextInt(3);
            for (int g = 0; g < gtins; g++) {
                identifiers.add(gtin(g == 0 && random.nextInt(3) > 0 ? product.ean() : pick(EANS)));
            }
            // half without codes: an entry joining by EAN alone keeps products of one code apart
            int mpns = random.nextBoolean() ? 0 : random.nextInt(3);
            for (int m = 0; m < mpns; m++) {
                identifiers.add(mpn(m == 0 && random.nextInt(3) > 0 ? product.code() : pick(CODES)));
            }
            entries.add(pimEntry(idPrefix + i, brand, identifiers));
        }
        return entries;
    }

    private VariantCodes eanOnlyPairs(int count) {
        Set<VariantCodes.BrandCode> pairs = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String brand = pick(MATCHABLE_BRANDS);
            String code = pick(CODES);
            pairs.add(new VariantCodes.BrandCode(InventoryItem.brandKeyOf(brand), InventoryItem.codeOf(code)));
        }
        return VariantCodes.of(pairs);
    }

    private int clampPrice(int index) {
        return Math.max(0, Math.min(PRICES.length - 1, index));
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private static InventoryItem item(String ean, String mfn, double netPrice, String supplier, String brand, String sku) {
        return new InventoryItem(brand, sku, ean, mfn, supplier, netPrice, "PLN", 5, 1, true, false, false);
    }

    private static PimEntry pimEntry(String pimId, String brand, List<PimIdentifier> identifiers) {
        return new PimEntry(pimId, List.copyOf(identifiers), brand, "Name", "Category", "Subcategory", true, null, null);
    }

    private static PimIdentifier gtin(String value) {
        return new PimIdentifier(value, PimIdentifierType.GTIN);
    }

    private static PimIdentifier mpn(String value) {
        return new PimIdentifier(value, PimIdentifierType.MPN);
    }
}
