package pl.commercelink.baskets;

import java.time.LocalDate;

public class BasketFilter {
    private final String namePrefix;
    private final String basketId;
    private final BasketType type;
    private final LocalDate createdAtStart;
    private final LocalDate createdAtEnd;

    public BasketFilter(String namePrefix, String basketId, BasketType type, LocalDate createdAtStart, LocalDate createdAtEnd) {
        this.namePrefix = namePrefix;
        this.basketId = basketId;
        this.type = type;
        this.createdAtStart = createdAtStart;
        this.createdAtEnd = createdAtEnd;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public String getBasketId() {
        return basketId;
    }

    public BasketType getType() {
        return type;
    }

    public LocalDate getCreatedAtStart() {
        return createdAtStart;
    }

    public LocalDate getCreatedAtEnd() {
        return createdAtEnd;
    }

}
