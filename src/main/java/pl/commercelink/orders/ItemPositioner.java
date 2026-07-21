package pl.commercelink.orders;

import java.util.List;
import java.util.Map;

public class ItemPositioner {

    public static <T extends Positioned> void add(List<T> items, T newItem) {
        int lastSameCategoryIndex = lastIndexOfCategory(items, newItem.getCategory());
        if (lastSameCategoryIndex >= 0) {
            items.add(lastSameCategoryIndex + 1, newItem);
            reindex(items);
            return;
        }

        int bandStart = newItem.isService() ? PositionGroup.SERVICE_GROUP_START : 0;
        int next = items.stream()
                .filter(i -> i.isService() == newItem.isService())
                .mapToInt(Positioned::getPosition)
                .filter(p -> p >= bandStart)
                .max().orElse(bandStart - 1) + 1;
        newItem.setPosition(next);
        items.add(newItem);
    }

    public static <T extends Positioned> void addInCategoryOrder(List<T> items, T newItem, Map<String, Integer> categorySequenceNumbers) {
        Integer sequenceNumber = newItem.isService() ? null : sequenceNumberOf(newItem, categorySequenceNumbers);
        if (sequenceNumber == null || lastIndexOfCategory(items, newItem.getCategory()) >= 0) {
            add(items, newItem);
            return;
        }

        int index = 0;
        while (index < items.size() && staysBefore(items.get(index), sequenceNumber, categorySequenceNumbers)) {
            index++;
        }
        items.add(index, newItem);
        reindex(items);
    }

    public static void reindex(List<? extends Positioned> items) {
        int productPosition = 0;
        int servicePosition = PositionGroup.SERVICE_GROUP_START;
        for (Positioned item : items) {
            item.setPosition(item.isService() ? servicePosition++ : productPosition++);
        }
    }

    private static boolean staysBefore(Positioned existing, int sequenceNumber, Map<String, Integer> categorySequenceNumbers) {
        if (existing.isService()) {
            return false;
        }
        Integer existingSequenceNumber = sequenceNumberOf(existing, categorySequenceNumbers);
        return existingSequenceNumber != null && existingSequenceNumber <= sequenceNumber;
    }

    private static Integer sequenceNumberOf(Positioned item, Map<String, Integer> categorySequenceNumbers) {
        return item.getCategory() == null ? null : categorySequenceNumbers.get(item.getCategory());
    }

    private static int lastIndexOfCategory(List<? extends Positioned> items, String category) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (category != null && category.equals(items.get(i).getCategory())) {
                return i;
            }
        }
        return -1;
    }
}
