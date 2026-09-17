package pl.commercelink.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static pl.commercelink.stores.StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED;
import static pl.commercelink.stores.StoreNotificationType.UNAUTHENTICATED;
import static pl.commercelink.stores.StoreNotificationType.WELCOME;

@ExtendWith(MockitoExtension.class)
class StoreNotificationServiceTest {

    private static final String STORE_ID = "store-1";
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 15, 30);

    @Mock
    private StoreNotificationsRepository repository;

    private StoreNotificationService service;

    @BeforeEach
    void setUp() {
        service = new StoreNotificationService(repository, Clock.fixed(NOW.atZone(WARSAW).toInstant(), WARSAW));
    }

    private static StoreNotification expiredAllegroConnection() {
        return new StoreNotification(StoreNotificationSeverity.WARNING, UNAUTHENTICATED, "allegro_marketplace",
                "Your connection to Allegro marketplace has expired");
    }

    private static StoreNotificationRecord record(String id, StoreNotificationType type, LocalDateTime createdAt,
                                                  LocalDateTime readAt) {
        StoreNotificationRecord record = new StoreNotificationRecord();
        record.setStoreId(STORE_ID);
        record.setNotificationId(id);
        record.setType(type);
        record.setSeverity(StoreNotificationSeverity.WARNING);
        record.setMessage("Message of " + id);
        record.setCreatedAt(createdAt);
        record.setReadAt(readAt);
        record.setUnreadStoreId(readAt == null ? STORE_ID : null);
        return record;
    }

    @Test
    void publishStoresANewNotificationAsUnreadUnderItsDeterministicId() {
        // given
        when(repository.putIfAbsent(any())).thenReturn(true);
        ArgumentCaptor<StoreNotificationRecord> saved = ArgumentCaptor.forClass(StoreNotificationRecord.class);

        // when
        service.publish(STORE_ID, expiredAllegroConnection());

        // then
        verify(repository).putIfAbsent(saved.capture());
        StoreNotificationRecord record = saved.getValue();
        assertThat(record.getStoreId()).isEqualTo(STORE_ID);
        assertThat(record.getNotificationId()).isEqualTo("UNAUTHENTICATED:allegro_marketplace");
        assertThat(record.getSeverity()).isEqualTo(StoreNotificationSeverity.WARNING);
        assertThat(record.getMessage()).isEqualTo("Your connection to Allegro marketplace has expired");
        assertThat(record.getCreatedAt()).isEqualTo(NOW);
        assertThat(record.getReadAt()).isNull();
        assertThat(record.getUnreadStoreId()).isEqualTo(STORE_ID);
    }

    @Test
    void publishLeavesAKnownNotificationAsItIsEvenWhenItWasAlreadyRead() {
        // given
        when(repository.putIfAbsent(any())).thenReturn(false);

        // when
        service.publish(STORE_ID, expiredAllegroConnection());

        // then
        verify(repository).putIfAbsent(any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void resolveDeletesTheNotificationAboutTheObject() {
        // when
        service.resolve(STORE_ID, UNAUTHENTICATED, "allegro_marketplace");

        // then
        verify(repository).delete(STORE_ID, "UNAUTHENTICATED:allegro_marketplace");
    }

    @Test
    void countsUnreadNotificationsFromTheIndex() {
        // given
        when(repository.countUnread(STORE_ID)).thenReturn(7L);

        // when / then
        assertThat(service.unreadCount(STORE_ID)).isEqualTo(7L);
    }

    @Test
    void latestListsTheNewestNotificationsFirstUpToTheLimit() {
        // given
        when(repository.findAll(STORE_ID)).thenReturn(List.of(
                record("OLD:1", WELCOME, NOW.minusDays(3), null),
                record("NEW:1", UNAUTHENTICATED, NOW.minusHours(1), NOW.minusMinutes(5)),
                record("MID:1", MARKETPLACE_RETURN_UNMATCHED, NOW.minusDays(1), null)));

        // when
        List<StoreNotificationRecord> latest = service.latest(STORE_ID, 2);

        // then
        assertThat(latest).extracting(StoreNotificationRecord::getNotificationId).containsExactly("NEW:1", "MID:1");
        verify(repository, never()).delete(anyString(), anyString());
    }

    @Test
    void purgesNotificationsReadMoreThanNinetyDaysAgoButNeverUnreadOnes() {
        // given
        when(repository.findAll(STORE_ID)).thenReturn(List.of(
                record("READ:91", UNAUTHENTICATED, NOW.minusDays(200), NOW.minusDays(91)),
                record("READ:89", UNAUTHENTICATED, NOW.minusDays(200), NOW.minusDays(89)),
                record("UNREAD:400", UNAUTHENTICATED, NOW.minusDays(400), null)));

        // when
        List<StoreNotificationRecord> latest = service.latest(STORE_ID, 10);

        // then
        verify(repository).delete(STORE_ID, "READ:91");
        verify(repository, never()).delete(STORE_ID, "READ:89");
        verify(repository, never()).delete(STORE_ID, "UNREAD:400");
        assertThat(latest).extracting(StoreNotificationRecord::getNotificationId).containsExactly("READ:89", "UNREAD:400");
    }

    @Test
    void listFiltersByStateAndTypeAndCountsUnreadAcrossTheStore() {
        // given
        when(repository.findAll(STORE_ID)).thenReturn(List.of(
                record("U:1", UNAUTHENTICATED, NOW.minusHours(1), null),
                record("R:1", MARKETPLACE_RETURN_UNMATCHED, NOW.minusHours(2), NOW.minusHours(1)),
                record("U:2", MARKETPLACE_RETURN_UNMATCHED, NOW.minusHours(3), null)));

        // when
        NotificationPage unread = service.list(STORE_ID, new NotificationFilter(true, null), 1, 50);
        NotificationPage unmatched = service.list(STORE_ID, new NotificationFilter(false, MARKETPLACE_RETURN_UNMATCHED), 1, 50);

        // then
        assertThat(unread.items()).extracting(StoreNotificationRecord::getNotificationId).containsExactly("U:1", "U:2");
        assertThat(unread.unreadCount()).isEqualTo(2);
        assertThat(unmatched.items()).extracting(StoreNotificationRecord::getNotificationId).containsExactly("R:1", "U:2");
        assertThat(unmatched.totalItems()).isEqualTo(2);
        assertThat(unmatched.unreadCount()).isEqualTo(2);
    }

    @Test
    void listOffersOnlyTheTypesTheStoreHasInDeclarationOrderWhateverTheFilter() {
        // given
        when(repository.findAll(STORE_ID)).thenReturn(List.of(
                record("R:1", MARKETPLACE_RETURN_UNMATCHED, NOW.minusHours(1), NOW.minusHours(1)),
                record("U:1", UNAUTHENTICATED, NOW.minusHours(2), null),
                record("U:2", MARKETPLACE_RETURN_UNMATCHED, NOW.minusHours(3), null),
                record("EXPIRED:1", WELCOME, NOW.minusDays(120), NOW.minusDays(91))));

        // when
        NotificationPage unreadUnauthenticated = service.list(STORE_ID, new NotificationFilter(true, UNAUTHENTICATED), 1, 50);

        // then
        assertThat(unreadUnauthenticated.types()).containsExactly(UNAUTHENTICATED, MARKETPLACE_RETURN_UNMATCHED);
    }

    @Test
    void listCutsTheResultIntoPagesAndKeepsTheRequestedPageWithinRange() {
        // given
        when(repository.findAll(STORE_ID)).thenReturn(IntStream.range(0, 5)
                .mapToObj(i -> record("N:" + i, WELCOME, NOW.minusMinutes(i), null))
                .toList());
        NotificationFilter all = new NotificationFilter(false, null);

        // when
        NotificationPage second = service.list(STORE_ID, all, 2, 2);
        NotificationPage beyondTheLast = service.list(STORE_ID, all, 9, 2);
        NotificationPage beforeTheFirst = service.list(STORE_ID, all, 0, 2);

        // then
        assertThat(second.items()).extracting(StoreNotificationRecord::getNotificationId).containsExactly("N:2", "N:3");
        assertThat(second.page()).isEqualTo(2);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(second.totalItems()).isEqualTo(5);
        assertThat(beyondTheLast.page()).isEqualTo(3);
        assertThat(beyondTheLast.items()).extracting(StoreNotificationRecord::getNotificationId).containsExactly("N:4");
        assertThat(beforeTheFirst.page()).isEqualTo(1);
        assertThat(beforeTheFirst.items()).extracting(StoreNotificationRecord::getNotificationId).containsExactly("N:0", "N:1");
    }

    @Test
    void listOfAStoreWithoutNotificationsIsOneEmptyPage() {
        // given
        when(repository.findAll(STORE_ID)).thenReturn(List.of());

        // when
        NotificationPage page = service.list(STORE_ID, new NotificationFilter(true, null), 1, 50);

        // then
        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.totalItems()).isZero();
        assertThat(page.types()).isEmpty();
    }

    @Test
    void markReadCountsOnlyNotificationsThatStillExist() {
        // given
        when(repository.markRead(STORE_ID, "A:1", NOW)).thenReturn(true);
        when(repository.markRead(STORE_ID, "A:2", NOW)).thenReturn(false);

        // when
        int marked = service.markRead(STORE_ID, List.of("A:1", "A:2", "A:1"));

        // then
        assertThat(marked).isEqualTo(1);
        verify(repository, times(1)).markRead(STORE_ID, "A:1", NOW);
    }

    @Test
    void markUnreadCountsOnlyNotificationsThatStillExist() {
        // given
        when(repository.markUnread(STORE_ID, "A:1")).thenReturn(true);
        when(repository.markUnread(STORE_ID, "A:2")).thenReturn(false);

        // when
        int marked = service.markUnread(STORE_ID, List.of("A:1", "A:2"));

        // then
        assertThat(marked).isEqualTo(1);
    }

    @Test
    void markAllReadMarksEveryNotificationStillInTheUnreadIndex() {
        // given
        when(repository.findUnreadIds(STORE_ID)).thenReturn(List.of("A:1", "A:2"));
        when(repository.markRead(eq(STORE_ID), anyString(), eq(NOW))).thenReturn(true);

        // when
        int marked = service.markAllRead(STORE_ID);

        // then
        assertThat(marked).isEqualTo(2);
        verify(repository).markRead(STORE_ID, "A:1", NOW);
        verify(repository).markRead(STORE_ID, "A:2", NOW);
    }
}
