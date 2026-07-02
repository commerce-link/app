package pl.commercelink.taxonomy;

import java.util.List;
import java.util.Objects;

public interface Positioned {

    Integer getPosition();

    void setPosition(Integer position);

    static void reindex(List<? extends Positioned> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
    }

    static boolean fillMissing(List<? extends Positioned> items) {
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getPosition() == null) {
                items.get(i).setPosition(i);
                changed = true;
            }
        }
        return changed;
    }

    static int next(List<? extends Positioned> items) {
        return items.stream()
                .map(Positioned::getPosition)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(items.size() - 1) + 1;
    }
}
