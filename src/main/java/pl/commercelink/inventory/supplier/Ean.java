package pl.commercelink.inventory.supplier;

import pl.commercelink.externaloffers.OfferEans;

final class Ean {

    private Ean() {
    }

    static boolean isValid(String ean) {
        String unified = OfferEans.unify(ean);
        return OfferEans.isValid(unified) && unified.chars().distinct().count() > 1;
    }
}
