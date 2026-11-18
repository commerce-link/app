package pl.commercelink.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;

/**
 * The brand and code pairs of the global PIM entries flagged to be joined by EAN only, kept between calls so the
 * per-row feed path reads them without walking the PIM index each time.
 */
@Component
@RequiredArgsConstructor
public class EanOnlyCodes {

    private final PimCatalog pimCatalog;
    private final InventoryMatchingProperties matchingProperties;

    // the first use and refresh() compute under one lock, so a slow first use cannot overwrite a newer refresh
    private final Object computeLock = new Object();
    private volatile VariantCodes codes;

    public VariantCodes current() {
        VariantCodes known = codes;
        if (known != null) {
            return known;
        }
        synchronized (computeLock) {
            if (codes == null) {
                codes = compute();
            }
            return codes;
        }
    }

    public VariantCodes refresh() {
        synchronized (computeLock) {
            VariantCodes computed = compute();
            codes = computed;
            return computed;
        }
    }

    private VariantCodes compute() {
        return matchingProperties.pimEanOnlyFlag()
                ? PimEanOnlyCodes.of(pimCatalog.withGlobalData().findAll())
                : VariantCodes.none();
    }
}
