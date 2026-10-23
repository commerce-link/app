package pl.commercelink.web.publicpage;

import pl.commercelink.stores.BrandPalette;

/** What the frame of a customer-facing page (public-layout.html) needs to know about the store. */
public record PublicPage(
        String storeId,
        String storeName,
        String logoUrl,
        BrandPalette brand,
        String sellerName,
        String sellerTaxId,
        String sellerAddress,
        String contactEmail,
        String contactPhone,
        String language,
        String otherLanguage,
        String otherLanguageHref) {

    public boolean hasSeller() {
        return sellerName != null;
    }

    public boolean hasContact() {
        return contactEmail != null || contactPhone != null;
    }
}
