package pl.commercelink.offer.imports;

import com.stripe.exception.StripeException;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.imports.OrderReferenceType;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.IOException;
import java.util.List;

public interface OfferImporter {
    List<BasketItem> importOffer(OfferCreationDto source) throws IOException, StripeException;
    String getType();
}