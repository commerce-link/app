package pl.commercelink.web.settings;

public record StoreAlert(boolean warning, String titleKey, String message, String actionHref, String actionKey) {

    public String cssClass() {
        return warning ? "is-warn" : "is-info";
    }

    public String icon() {
        return warning ? "fa-exclamation-triangle" : "fa-info-circle";
    }
}
