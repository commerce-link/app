// Bulk actions of the order's items, in the selection row (the "Route to" and "Move" menus and "Remove"). table-select.js
// keeps the checkboxes, the count and the row; this script labels an action with how many of the checked items it
// applies to when some but not all of them fit ("To allocation (2 of 3)" — a row's data-<scope> flag is the server's own
// predicate), greys an action none of them fits (aria-disabled, with its data-skipped text as the visible reason, as the
// row menu names its reasons), asks in the page's dialog#cl-confirm-dialog (through table-select.js's shared
// window.CL_confirmBulk) and posts form#order-items-form to the action's address. An action the server marked
// unavailable (data-cl-bulk-unavailable) stays greyed with the server's reason. Without JavaScript the <noscript> buttons
// open a confirmation page instead.
(function () {
    'use strict';

    var form = document.getElementById('order-items-form');
    var table = form && form.querySelector('table[data-cl-select-table]');
    if (!table) {
        return;
    }
    var actions = form.querySelector('[data-cl-scope-template]');
    var template = actions.getAttribute('data-cl-scope-template');
    var countTemplate = actions.getAttribute('data-cl-scope-count-template');

    function checkedRows() {
        return Array.prototype.slice.call(table.querySelectorAll('input[data-cl-select-row]:checked'))
            .map(function (box) {
                return box.closest('tr');
            });
    }

    function fitting(rows, button) {
        var scope = button.getAttribute('data-cl-bulk-scope');
        return rows.filter(function (row) {
            return row.getAttribute('data-' + scope) === 'true';
        }).length;
    }

    function off(button) {
        return button.getAttribute('aria-disabled') === 'true';
    }

    // the reason sits inside a menu entry, or (for "Remove", a link-style button) in the element its id names
    function reasonOf(button) {
        return button.querySelector('[data-cl-bulk-reason]')
            || document.getElementById(button.getAttribute('data-cl-bulk-reason-id') || '');
    }

    function refresh() {
        var rows = checkedRows();
        form.querySelectorAll('[data-cl-bulk-scope]').forEach(function (button) {
            var fits = fitting(rows, button);
            var unavailable = button.hasAttribute('data-cl-bulk-unavailable');
            var none = rows.length > 0 && fits === 0;
            if (unavailable || none) {
                button.setAttribute('aria-disabled', 'true');
            } else {
                button.removeAttribute('aria-disabled');
            }
            var reason = reasonOf(button);
            if (reason && !unavailable) {
                reason.textContent = none ? button.getAttribute('data-skipped') : '';
                reason.hidden = !none;
                // a reason outside the button ("Remove") describes it only while it shows
                if (reason.id && !button.contains(reason)) {
                    if (none) {
                        button.setAttribute('aria-describedby', reason.id);
                    } else {
                        button.removeAttribute('aria-describedby');
                    }
                }
            }
            var label = button.getAttribute('data-label');
            var text = button.querySelector('[data-cl-bulk-label]') || button;
            // the "(k of n)" suffix only when some but not all checked items fit: all of them fitting needs no count,
            // none fitting greys the action and its reason says why
            text.textContent = rows.length === 0 || fits === 0 || fits === rows.length ? label
                : template.replace('{label}', label).replace('{n}', String(fits)).replace('{m}', String(rows.length));
        });
    }

    function confirmThen(button) {
        var rows = checkedRows();
        var fits = fitting(rows, button);
        // the dialog names how many items the action will touch: "k of n" when some are left out
        var count = fits < rows.length
            ? countTemplate.replace('{k}', String(fits)).replace('{n}', String(rows.length)) : String(rows.length);
        // the entry sits in a menu that closes now: the dialog hands the focus back to the menu's button, not the body
        var menu = button.closest('details.cl-menu');
        if (menu) {
            menu.querySelector(':scope > summary').focus();
        }
        window.CL_confirmBulk(button, count, function () {
            form.action = button.getAttribute('data-cl-bulk-action');
            form.submit();
        }, 'data-cl-bulk-confirm-title', 'data-cl-bulk-confirm-message', 'data-cl-bulk-confirm-action');
    }

    form.addEventListener('click', function (event) {
        if (!event.target.closest) {
            return;
        }
        var button = event.target.closest('button[data-cl-bulk-action]');
        if (button && !off(button) && checkedRows().length) {
            event.preventDefault();
            confirmThen(button);
            return;
        }
        if (event.target.closest('[data-cl-select-clear]')) {
            // table-select.js unchecks the rows in its own handler of the same click
            window.setTimeout(refresh, 0);
        }
    });
    // the checkboxes of the table and the select-all box of the selection row, which sits outside the table
    form.addEventListener('change', refresh);
    refresh();
})();
