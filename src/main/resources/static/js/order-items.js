// Bulk actions of the order's items. table-select.js keeps the checkboxes, the count and the bar; this script labels
// each action with how many of the checked items it applies to ("To allocation (2 of 3)" — a row's data-<scope> flag is
// the server's own predicate), disables an action none of them fits, asks in the page's dialog#cl-confirm-dialog
// (through table-select.js's shared window.CL_confirmBulk, D-12) and posts form#order-items-form to the action's
// address. Without JavaScript the <noscript> buttons post the same form.
(function () {
    'use strict';

    var form = document.getElementById('order-items-form');
    var table = form && form.querySelector('table[data-cl-select-table]');
    if (!table) {
        return;
    }
    var actions = form.querySelector('[data-cl-scope-template]');
    var template = actions ? actions.getAttribute('data-cl-scope-template') : '{label} ({n}/{m})';

    function checkedRows() {
        return Array.prototype.slice.call(table.querySelectorAll('input[data-cl-select-row]:checked'))
            .map(function (box) {
                return box.closest('tr');
            });
    }

    function refresh() {
        var rows = checkedRows();
        form.querySelectorAll('button[data-cl-bulk-scope]').forEach(function (button) {
            if (!button.hasAttribute('data-server-disabled')) {
                button.setAttribute('data-server-disabled', String(button.disabled));
            }
            var scope = button.getAttribute('data-cl-bulk-scope');
            var fits = rows.filter(function (row) {
                return row.getAttribute('data-' + scope) === 'true';
            }).length;
            button.disabled = button.getAttribute('data-server-disabled') === 'true' || (rows.length > 0 && fits === 0);
            var label = button.getAttribute('data-label');
            button.textContent = rows.length === 0 ? label
                : template.replace('{label}', label).replace('{n}', String(fits)).replace('{m}', String(rows.length));
        });
    }

    function confirmThen(button) {
        window.CL_confirmBulk(button, String(checkedRows().length), function () {
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
