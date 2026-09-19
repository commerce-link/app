package pl.commercelink.stores;

/** The store a settings hub page is about; the page reads only its name and id. */
public class StoreForm {

    private Store store;

    public StoreForm() {
    }

    public StoreForm(Store store) {
        this.store = store;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }
}
