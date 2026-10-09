package pl.commercelink.shipping;

import org.apache.commons.lang3.StringUtils;

/**
 * The name the operator knows an Allegro courier by. Allegro gives courier ids (DHL, DPD, ALLEGRO_ONE_KURIER, ...);
 * every id of Allegro's own network (ALLEGRO...) is shown as "One by Allegro", the others as they come.
 */
public final class AllegroCarrierNames {

    static final String ONE_BY_ALLEGRO = "One by Allegro";

    private AllegroCarrierNames() {
    }

    public static String displayName(String carrierId) {
        if (StringUtils.isBlank(carrierId)) {
            return null;
        }
        return carrierId.startsWith("ALLEGRO") ? ONE_BY_ALLEGRO : carrierId;
    }
}
