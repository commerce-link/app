package pl.commercelink.orders;

import pl.commercelink.taxonomy.Categorized;

public interface Positioned extends Categorized {

    int getPosition();

    void setPosition(int position);
}
