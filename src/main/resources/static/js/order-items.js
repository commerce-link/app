// Bulk actions of the order's items. table-select.js keeps the checkboxes, the count and the bar; this script labels
// an action with how many of the checked items it applies to when not all of them fit ("To allocation (2 of 3)" — a
// row's data-<scope> flag is the server's own predicate), disables an action none of them fits, asks in the page's
// dialog#cl-confirm-dialog (through table-select.js's shared window.CL_confirmBulk) and posts form#order-items-form to
// the action's address. Without JavaScript the <noscript> buttons open a confirmation page instead.
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

    function refresh() {
        var rows = checkedRows();
        form.querySelectorAll('button[data-cl-bulk-scope]').forEach(function (button) {
            if (!button.hasAttribute('data-server-disabled')) {
                button.setAttribute('data-server-disabled', String(button.disabled));
            }
            var fits = fitting(rows, button);
            button.disabled = button.getAttribute('data-server-disabled') === 'true' || (rows.length > 0 && fits === 0);
            var label = button.getAttribute('data-label');
            // the "(k of n)" suffix only when some checked items do not fit; all of them fitting needs no count
            button.textContent = rows.length === 0 || fits === rows.length ? label
                : template.replace('{label}', label).replace('{n}', String(fits)).replace('{m}', String(rows.length));
        });
    }

    function confirmThen(button) {
        var rows = checkedRows();
        var fits = fitting(rows, button);
        // the dialog names how many items the action will touch: "k of n" when some are left out
        var count = fits < rows.length
            ? countTemplate.replace('{k}', String(fits)).replace('{n}', String(rows.length)) : String(rows.length);
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
        if (button && !button.disabled && checkedRows().length) {
            event.preventDefault();
            confirmThen(button);
            return;
        }
        if (event.target.closest('[data-cl-select-clear]')) {
            // table-select.js unchecks the rows in its own handler of the same click
            window.setTimeout(refresh, 0);
        }
    });
    table.addEventListener('change', refresh);
    refresh();
})();
