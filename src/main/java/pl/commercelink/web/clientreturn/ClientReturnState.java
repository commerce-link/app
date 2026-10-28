package pl.commercelink.web.clientreturn;

import java.util.Locale;

/** What the customer's return page says and offers: exactly one per RMA that has a page (ClientReturnView.of). */
public enum ClientReturnState {
    BOOK,
    BOOK_UNAVAILABLE,
    STORE_BOOKS,
    BOOKING,
    WAITING,
    ARRIVED,
    REVIEWING,
    COMPLETED,
    REJECTED;

    public String headlineKey() {
        return "client.return.headline." + lowerCamelName();
    }

    String lowerCamelName() {
        String[] words = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            result.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return result.toString();
    }
}
