package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationTest {

    @Test
    void clampsThePageIntoRangeAndBuildsNeighbourLinks() {
        Pagination p = Pagination.of(9, 120, 50, n -> "/x?page=" + n);
        assertThat(p.page()).isEqualTo(3);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(p.previousHref()).isEqualTo("/x?page=2");
        assertThat(p.nextHref()).isNull();
        assertThat(p.fromIndex()).isEqualTo(100);
        assertThat(p.toIndex()).isEqualTo(120);
        assertThat(p.isNeeded()).isTrue();
    }

    @Test
    void singlePageIsNotNeededAndEmptyListHasOnePage() {
        Pagination one = Pagination.of(1, 12, 50, n -> "/x?page=" + n);
        assertThat(one.isNeeded()).isFalse();
        assertThat(one.previousHref()).isNull();
        assertThat(one.nextHref()).isNull();
        Pagination empty = Pagination.of(4, 0, 50, n -> "/x?page=" + n);
        assertThat(empty.page()).isEqualTo(1);
        assertThat(empty.totalPages()).isEqualTo(1);
        assertThat(empty.fromIndex()).isZero();
        assertThat(empty.toIndex()).isZero();
    }

    @Test
    void openEndedSecondPageShowsItsRangeAndBothNeighbours() {
        // when
        Pagination p = Pagination.openEnded(2, 25, 25, true, n -> "/list?page=" + n);

        // then
        assertThat(p.isNeeded()).isTrue();
        assertThat(p.fromIndex()).isEqualTo(25);
        assertThat(p.toIndex()).isEqualTo(50);
        assertThat(p.previousHref()).isEqualTo("/list?page=1");
        assertThat(p.nextHref()).isEqualTo("/list?page=3");
        assertThat(p.openEnded()).isTrue();
    }

    @Test
    void openEndedSinglePageIsNotNeeded() {
        // when
        Pagination p = Pagination.openEnded(1, 25, 7, false, n -> "/list?page=" + n);

        // then
        assertThat(p.isNeeded()).isFalse();
        assertThat(p.nextHref()).isNull();
    }

    @Test
    void ofKeepsItsTotal() {
        // when
        Pagination p = Pagination.of(2, 60, 25, n -> "/x?page=" + n);

        // then
        assertThat(p.openEnded()).isFalse();
        assertThat(p.totalItems()).isEqualTo(60);
    }
}
