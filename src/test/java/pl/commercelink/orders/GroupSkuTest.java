package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupSkuTest {

    @Test
    void recognizesGroupSkuByPrefix() {
        // when / then
        assertTrue(GroupSku.isGroup("#1x12RSLIN3W|1xUF-SLIN120-3W"));
        assertFalse(GroupSku.isGroup("12RSLIN3W"));
        assertFalse(GroupSku.isGroup("1X12RSLIN3W+1XUF-SLIN120-3W"));
        assertFalse(GroupSku.isGroup(null));
        assertFalse(GroupSku.isGroup(""));
    }

    @Test
    void parsesComponentsWithQuantities() {
        // given
        String sku = "#1x12RSLIN3W|2xUF-SLIN120-3W|1xUF-SLIN120-1W";

        // when
        List<GroupSku.Component> components = GroupSku.parse(sku);

        // then
        assertEquals(3, components.size());
        assertEquals(new GroupSku.Component(1, "12RSLIN3W"), components.get(0));
        assertEquals(new GroupSku.Component(2, "UF-SLIN120-3W"), components.get(1));
        assertEquals(new GroupSku.Component(1, "UF-SLIN120-1W"), components.get(2));
    }

    @Test
    void quantityEndsAtFirstXEvenWhenSkuStartsWithDigits() {
        // when
        List<GroupSku.Component> components = GroupSku.parse("#3x12X4TRUCK");

        // then
        assertEquals(List.of(new GroupSku.Component(3, "12X4TRUCK")), components);
    }

    @Test
    void segmentWithoutQuantityDefaultsToOne() {
        // when
        List<GroupSku.Component> components = GroupSku.parse("#UF-SLIN120-3W|2xUF-SLIN120-1W");

        // then
        assertEquals(new GroupSku.Component(1, "UF-SLIN120-3W"), components.get(0));
        assertEquals(new GroupSku.Component(2, "UF-SLIN120-1W"), components.get(1));
    }

    @Test
    void ignoresBlankSegmentsAndTrimsWhitespace() {
        // when
        List<GroupSku.Component> components = GroupSku.parse("# 1x12RSLIN3W | | 2x UF-SLIN120-3W ");

        // then
        assertEquals(List.of(
                new GroupSku.Component(1, "12RSLIN3W"),
                new GroupSku.Component(2, "UF-SLIN120-3W")
        ), components);
    }

    @Test
    void nonGroupSkuParsesToEmptyList() {
        // when / then
        assertTrue(GroupSku.parse("12RSLIN3W").isEmpty());
        assertTrue(GroupSku.parse(null).isEmpty());
    }

}
