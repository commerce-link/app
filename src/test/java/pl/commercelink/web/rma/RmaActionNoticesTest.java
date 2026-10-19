package pl.commercelink.web.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RmaActionNoticesTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private RmaActionNotices notices;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        notices = new RmaActionNotices(messages);
    }

    private static RMAItem item(String id, String name, RMAItemStatus status) {
        RMAItem item = new RMAItem();
        item.setRmaItemId(id);
        item.setName(name);
        item.setStatus(status);
        return item;
    }

    @Test
    void listsTheSkippedItemsWithTheirReason() {
        // given
        List<RMAItem> selected = List.of(item("a", "A", RMAItemStatus.Received), item("b", "B", RMAItemStatus.MovedToWarehouse));

        // when
        RmaNotice notice = notices.done("rma.detail.done.sentToDistributor", selected, List.of("a"), PL);

        // then
        assertThat(notice.text()).contains("1");
        assertThat(notice.lines()).containsExactly("Pominięto 1: B — jest już w magazynie.");
        assertThat(notice.tone()).isEqualTo("is-ok");
        assertThat(notice.scope()).isEqualTo(RmaNotice.Scope.ITEMS);
    }

    @Test
    void anItemStoredWithoutAStatusIsSkippedAsNew() {
        // given
        List<RMAItem> selected = List.of(item("a", "A", RMAItemStatus.Received), item("b", "B", null));

        // when
        RmaNotice notice = notices.done("rma.detail.done.received", selected, List.of("a"), PL);

        // then
        assertThat(notice.lines()).containsExactly("Pominięto 1: B — jeszcze nie odebrana.");
    }

    @Test
    void nothingProcessedIsAWarning() {
        // given
        List<RMAItem> selected = List.of(item("b", "B", RMAItemStatus.New));

        // when
        RmaNotice notice = notices.done("rma.detail.done.received", selected, List.of(), PL);

        // then
        assertThat(notice.tone()).isEqualTo("is-warn");
        assertThat(notice.lines()).containsExactly("Pominięto 1: B — jeszcze nie odebrana.");
    }

    @Test
    void skippedItemsAreJoinedIntoOneLine() {
        // given
        List<RMAItem> selected = List.of(item("a", "A", RMAItemStatus.New), item("b", "B", RMAItemStatus.SentForRepair));

        // when
        RmaNotice notice = notices.done("rma.detail.done.accepted", selected, List.of(), PL);

        // then
        assertThat(notice.lines()).containsExactly("Pominięto 2: A — jeszcze nie odebrana; B — w naprawie.");
    }

    @Test
    void everyItemStatusAndDoneKeyHasAnEnglishText() {
        // given
        List<RMAItem> selected = java.util.Arrays.stream(RMAItemStatus.values()).map(s -> item(s.name(), s.name(), s)).toList();

        // when
        RmaNotice notice = notices.done("rma.detail.done.deleted", selected, List.of(), Locale.ENGLISH);

        // then
        assertThat(notice.lines()).hasSize(5)
                .allSatisfy(line -> assertThat(line).startsWith("Skipped: ").doesNotContain("rma.detail"));
    }

    @Test
    void moreThanTwoSkippedItemsAreListedOnePerLine() {
        // given
        List<RMAItem> selected = List.of(item("a", "A", RMAItemStatus.New), item("b", "B", RMAItemStatus.SentForRepair),
                item("c", "C", RMAItemStatus.MovedToWarehouse), item("d", "D", RMAItemStatus.Received));

        // when
        RmaNotice notice = notices.done("rma.detail.done.sentToDistributor", selected, List.of("d"), PL);

        // then
        assertThat(notice.lines()).containsExactly(
                "Pominięto: A — jeszcze nie odebrana.",
                "Pominięto: B — w naprawie.",
                "Pominięto: C — jest już w magazynie.");
    }
}
