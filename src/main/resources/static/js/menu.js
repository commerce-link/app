// Action menu: details.cl-menu > summary + ul.cl-menu-list. The browser opens and closes it (and tells assistive
// technology whether it is expanded), so without JavaScript every entry is still reachable. This script adds the rest:
// one menu open at a time, the first usable item focused on opening, Escape and a click outside close it (Escape hands
// the focus back to the summary), the arrow keys move between the items that can be used and Home/End jump to the
// first/last of them. An item marked aria-disabled="true" stays readable (its reason sits in .cl-menu-reason) but does
// nothing; an item inside a hidden element (an entry a script has not revealed) is skipped. Near the bottom of the
// window the list opens upwards (.is-up) instead of changing the overflow of the table around it, and a list that
// would leave the window on the left (right-aligned to a toggle near the left edge, e.g. "Wystaw" at 320 px) opens
// from the toggle's left edge instead (.is-start).
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
        return Array.prototype.slice.call(menu.querySelectorAll('.cl-menu-item:not([aria-disabled="true"])'))
            .filter(function (item) { return !item.closest('[hidden]'); });
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
                list.classList.remove('is-up', 'is-end', 'is-start');
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
            if (!isNote(menu) && box.left < MARGIN) {
                list.classList.add('is-start');
                // opened from the toggle's left edge it may leave the window on the right instead (a toggle in the
                // middle of a narrow screen): keep whichever placement shows more of the list
                var overRight = list.getBoundingClientRect().right - (document.documentElement.clientWidth - MARGIN);
                if (overRight > 0 && overRight > MARGIN - box.left) {
                    list.classList.remove('is-start');
                }
            }
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
        } else if ((event.key === 'Home' || event.key === 'End') && !isNote(open)) {
            var usable = items(open);
            if (!usable.length) {
                return;
            }
            event.preventDefault();
            usable[event.key === 'Home' ? 0 : usable.length - 1].focus();
        } else if (event.key === 'Tab') {
            close(false);
        }
    });
})();
