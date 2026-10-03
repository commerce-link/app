package pl.commercelink.baskets;

import java.util.List;

/** One page of full baskets, how many match in total, and the page actually shown (clamped to the last one). */
public record OfferListResult(List<Basket> rows, int total, int page) {
}
