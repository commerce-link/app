package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.carriers.CarrierMapping;

import java.util.ArrayList;
import java.util.List;

/** The pairs of a carrier dictionary as the editor posts and shows them, shared by the super admin and the store pages. */
final class CarrierDictionaryForm {

    static final String VIEW = "carrier-dictionary";

    private CarrierDictionaryForm() {
    }

    static List<CarrierMapping> pairs(List<String> externalTexts, List<String> carriers) {
        List<String> texts = externalTexts == null ? List.of() : externalTexts;
        List<String> names = carriers == null ? List.of() : carriers;
        List<CarrierMapping> pairs = new ArrayList<>();
        for (int i = 0; i < Math.max(texts.size(), names.size()); i++) {
            pairs.add(new CarrierMapping(i < texts.size() ? texts.get(i) : null, i < names.size() ? names.get(i) : null));
        }
        return pairs;
    }

    // the last empty row is how a pair is added without JavaScript; an empty row is not saved
    static List<CarrierMapping> withEmptyRow(List<CarrierMapping> pairs) {
        List<CarrierMapping> rows = new ArrayList<>(pairs);
        CarrierMapping last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last == null || StringUtils.isNotBlank(last.getExternalText()) || StringUtils.isNotBlank(last.getCarrier())) {
            rows.add(new CarrierMapping());
        }
        return rows;
    }
}
