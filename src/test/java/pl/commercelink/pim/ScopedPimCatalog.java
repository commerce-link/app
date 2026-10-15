package pl.commercelink.pim;

import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.pim.api.PimLookup;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.mockito.Mockito.mock;

/**
 * A {@link PimCatalog} mock backed by a real {@link PimEntryIndex}: {@code forStore(id)} returns the index's view of that
 * store, and the catalog's own lookups are the global view, as in the real catalog.
 */
public final class ScopedPimCatalog {

    public static final String GLOBAL_GTIN = "5900000000590";
    public static final String STORE_GTIN = "5900000000591";

    private ScopedPimCatalog() {
    }

    public static PimCatalog of(PimEntry... entries) {
        PimEntryIndex index = new PimEntryIndex(List.of(entries), UnaryOperator.identity(), UnaryOperator.identity());
        return mock(PimCatalog.class, invocation -> {
            if (invocation.getMethod().getName().equals("forStore")) {
                return index.forStore(invocation.getArgument(0));
            }
            if (invocation.getMethod().getDeclaringClass() == PimLookup.class) {
                return invocation.getMethod().invoke(index.global(), invocation.getArguments());
            }
            return null;
        });
    }

    public static PimEntry globalEntry(String pimId, String gtin, String mpn, String category) {
        return new PimEntry(pimId, identifiers(gtin, mpn), "Brand", "Global " + pimId, category, null, true, 1000, 1200);
    }

    public static PimEntry storeEntry(String pimId, String storeId, String gtin, String mpn, String category) {
        return new PimEntry(pimId, identifiers(gtin, mpn), "Brand", "Store " + pimId, category, null, true, 2000, 2200,
                null, storeId);
    }

    public static List<PimIdentifier> identifiers(String gtin, String mpn) {
        return mpn == null
                ? List.of(new PimIdentifier(gtin, PimIdentifierType.GTIN))
                : List.of(new PimIdentifier(gtin, PimIdentifierType.GTIN), new PimIdentifier(mpn, PimIdentifierType.MPN));
    }
}
