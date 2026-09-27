// Action menu: details.cl-menu > summary + ul.cl-menu-list. The browser opens and closes it (and tells assistive
// technology whether it is expanded), so without JavaScript every entry is still reachable. This script adds the rest:
// one menu open at a time, the first usable item focused on opening, Escape and a click outside close it (Escape hands
// the focus back to the summary), the arrow keys move between the items that can be used. An item marked
// aria-disabled="true" stays readable (its reason sits in .cl-menu-reason) but does nothing. Near the bottom of the
// window the list opens upwards (.is-up) instead of changing the overflow of the table around it.
// A marker with a popover (details.cl-note > summary + .cl-note-panel, e.g. an item's comment) follows the same rules:
// it counts as a menu for "one open at a time", Escape and the outside click, has no items to focus or to move between
// with the arrows, and its panel also flips to the left edge of its toggle (.is-end) when it would leave the window.
(function () {
    'use strict';

    var POPUPS = 'details.cl-menu, details.cl-note';
    var MARGIN = 8;
    var open = null;

    function isNote(popup) {
        return popup.matches('details.cl-note');
    }

    function panelOf(popup) {
        return popup.querySelector(':scope > .cl-menu-list, :scope > .cl-note-panel');
    }

    function items(menu) {
        return Array.prototype.slice.call(menu.querySelectorAll('.cl-menu-item:not([aria-disabled="true"])'));
    }

    function summaryOf(menu) {
        return menu.querySelector(':scope > summary');
    }

    function close(focusSummary) {
        if (!open) {
            return;
        }
        var current = open;
        open = null;
        current.open = false;
        if (focusSummary) {
            summaryOf(current).focus();
        }
    }

    // toggle does not bubble; a capturing listener on the document still sees it for every menu
    document.addEventListener('toggle', function (event) {
        var menu = event.target;
        if (!menu.matches || !menu.matches(POPUPS)) {
            return;
        }
        var list = panelOf(menu);
        if (!menu.open) {
            if (list) {
                list.classList.remove('is-up', 'is-end');
            }
            if (open === menu) {
                open = null;
            }
            return;
        }
        if (open && open !== menu) {
            open.open = false;
        }
        open = menu;
        if (list) {
            var box = list.getBoundingClientRect();
            list.classList.toggle('is-up', box.bottom > window.innerHeight
                && summaryOf(menu).getBoundingClientRect().top > box.height);
            if (isNote(menu) && box.right > document.documentElement.clientWidth - MARGIN) {
                list.classList.add('is-end');
                // aligned to the right of its toggle it would leave the window on the left: keep the first placement
                if (list.getBoundingClientRect().left < MARGIN) {
                    list.classList.remove('is-end');
                }
            }
        }
        if (isNote(menu)) {
            // nothing inside to focus: the focus stays on the toggle, which Escape and Enter keep using
            return;
        }
        var first = items(menu)[0];
        if (first) {
            first.focus();
        }
    }, true);

    document.addEventListener('click', function (event) {
        if (!open || !event.target.closest) {
            return;
        }
        if (summaryOf(open).contains(event.target)) {
            // the browser toggles the details itself
            return;
        }
        var item = event.target.closest('.cl-menu-item');
        if (item && open.contains(item)) {
            if (item.getAttribute('aria-disabled') === 'true') {
                event.preventDefault();
                event.stopImmediatePropagation();
                return;
            }
            // the item's own action (link, dialog, form) goes on; the list just closes
            close(false);
            return;
        }
        if (!open.contains(event.target)) {
            close(false);
        }
    }, true);

    document.addEventListener('keydown', function (event) {
        if (!open) {
            return;
        }
        if (event.key === 'Escape') {
            event.preventDefault();
            close(true);
        } else if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && !isNote(open)) {
            event.preventDefault();
            var list = items(open);
            if (!list.length) {
                return;
            }
            var index = list.indexOf(document.activeElement);
            var next = event.key === 'ArrowDown' ? (index + 1) % list.length : (index - 1 + list.length) % list.length;
            list[next].focus();
        } else if (event.key === 'Tab') {
            close(false);
        }
    });
})();
