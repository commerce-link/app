// Action menu (disclosure pattern, not an ARIA menu): .cl-menu > button[aria-controls][aria-expanded] toggles the
// ul.cl-menu-list[hidden] it controls. Click, Enter and Space open it; Escape and a click outside close it and hand the
// focus back to the button; the arrow keys move between the items that can be used. An item marked aria-disabled="true"
// stays readable (its reason sits in .cl-menu-reason) but does nothing. Near the bottom of the window the list opens
// upwards (.is-up) instead of changing the overflow of the table around it.
(function () {
    'use strict';

    var open = null;

    function items(list) {
        return Array.prototype.slice.call(list.querySelectorAll('.cl-menu-item:not([aria-disabled="true"])'));
    }

    function close(focusButton) {
        if (!open) {
            return;
        }
        var current = open;
        open = null;
        current.list.hidden = true;
        current.list.classList.remove('is-up');
        current.button.setAttribute('aria-expanded', 'false');
        if (focusButton) {
            current.button.focus();
        }
    }

    function show(button) {
        var list = document.getElementById(button.getAttribute('aria-controls'));
        if (!list) {
            return;
        }
        close(false);
        list.hidden = false;
        button.setAttribute('aria-expanded', 'true');
        var box = list.getBoundingClientRect();
        list.classList.toggle('is-up', box.bottom > window.innerHeight && button.getBoundingClientRect().top > box.height);
        open = { button: button, list: list };
        var first = items(list)[0];
        if (first) {
            first.focus();
        }
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest && event.target.closest('.cl-menu > button[aria-controls]');
        if (button) {
            event.preventDefault();
            if (open && open.button === button) {
                close(true);
            } else {
                show(button);
            }
            return;
        }
        if (!open) {
            return;
        }
        var item = event.target.closest && event.target.closest('.cl-menu-item');
        if (item && open.list.contains(item)) {
            if (item.getAttribute('aria-disabled') === 'true') {
                event.preventDefault();
                event.stopImmediatePropagation();
                return;
            }
            // the item's own action (link, dialog, form) goes on; the list just closes
            close(false);
            return;
        }
        if (!open.list.contains(event.target)) {
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
        } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            var list = items(open.list);
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
