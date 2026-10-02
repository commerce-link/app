package pl.commercelink.web.deliveries.details;

/** Who looks at the delivery: the roles are disjoint, so a super admin is never "admin" here. back: the list to return to. */
public record DeliveryViewer(boolean superAdmin, boolean admin, String back) {

    /** ADMIN or SUPER_ADMIN: may change the delivery (header, items, removal). */
    public boolean manages() {
        return superAdmin || admin;
    }

    /** A store's own admin: documents, payments and the store's own purchases. */
    public boolean storeAdmin() {
        return admin && !superAdmin;
    }
}
