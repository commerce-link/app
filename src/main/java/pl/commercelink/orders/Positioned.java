package pl.commercelink.orders;

public interface Positioned {

    String getCategory();

    boolean isService();

    int getPosition();

    void setPosition(int position);
}
