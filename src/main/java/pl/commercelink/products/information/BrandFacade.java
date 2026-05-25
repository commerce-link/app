package pl.commercelink.products.information;

import pl.commercelink.pim.api.PimCatalog;

public final class BrandFacade {

    private static volatile PimCatalog delegate;

    private BrandFacade() {
    }

    public static void initialize(PimCatalog catalog) {
        delegate = catalog;
    }

    public static String unify(String raw) {
        PimCatalog d = delegate;
        return d == null ? raw : d.unifyBrand(raw);
    }

    public static int strength(String brand) {
        PimCatalog d = delegate;
        return d == null ? 1 : d.brandStrength(brand);
    }
}
