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
}
