package pl.commercelink.offer.imports;

import pl.commercelink.baskets.BasketItem;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.IOException;
import java.util.List;

public interface OfferImporter {
    List<BasketItem> importOffer(OfferCreationDto source) throws IOException;
    String getType();
}