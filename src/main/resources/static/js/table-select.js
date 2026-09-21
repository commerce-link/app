// Row selection for a cl-table with bulk actions. table[data-cl-select-table] has a header checkbox [data-cl-select-all]
// (checks the visible rows) and row checkboxes [data-cl-select-row][value]. The bar [data-cl-selection-bar] shows while
// anything is checked: [data-cl-selection-count][data-template="... {n}"] says how many, buttons [data-cl-select-action=x]
// submit form[data-cl-select-form] with hidden inputs name=action / name=productIds, [data-cl-select-clear] unchecks all.
// A button with [data-cl-select-confirm-title] first opens the page's dialog#cl-confirm-dialog (fragments/confirm-dialog,
// whose confirm-dialog.js closes it on Cancel) and submits on confirm. Rows hidden by table-filter.js (event
// cl:table-filtered) are dropped from the selection so a bulk action never touches what the operator cannot see.
// Without JavaScript nothing here is shown.
(function () {
    'use strict';

    function rowsOf(table) {
        return Array.prototype.slice.call(table.querySelectorAll('input[data-cl-select-row]'));
    }

    function visible(box) {
        var row = box.closest('tr');
        return !row || !row.hidden;
    }

    function checked(table) {
        return rowsOf(table).filter(function (box) {
            return box.checked;
        });
    }

    function barOf(table) {
        var card = table.closest('.cl-card') || document;
        return card.querySelector('[data-cl-selection-bar]');
    }

    function refresh(table) {
        var bar = barOf(table);
        var selected = checked(table);
        rowsOf(table).forEach(function (box) {
            var row = box.closest('tr');
            if (row) {
                row.classList.toggle('is-selected', box.checked);
            }
        });
        var all = table.querySelector('[data-cl-select-all]');
        if (all) {
            var shown = rowsOf(table).filter(visible);
            all.checked = shown.length > 0 && shown.every(function (box) { return box.checked; });
            all.indeterminate = selected.length > 0 && !all.checked;
        }
        if (!bar) {
            return;
        }
        bar.hidden = selected.length === 0;
        var count = bar.querySelector('[data-cl-selection-count]');
        if (count) {
            count.textContent = (count.getAttribute('data-template') || '{n}').replace('{n}', String(selected.length));
        }
    }

    function submit(table, action) {
        var form = document.querySelector('form[data-cl-select-form]');
        if (!form) {
            return;
        }
        form.querySelectorAll('input[name="productIds"], input[name="action"]').forEach(function (old) {
            old.remove();
        });
        var actionInput = document.createElement('input');
        actionInput.type = 'hidden';
        actionInput.name = 'action';
        actionInput.value = action;
        form.appendChild(actionInput);
        checked(table).forEach(function (box) {
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'productIds';
            input.value = box.value;
            form.appendChild(input);
        });
        form.submit();
    }

    function confirmThen(button, table) {
        var dialog = document.getElementById('cl-confirm-dialog');
        var action = button.getAttribute('data-cl-select-action');
        var confirm = dialog && dialog.querySelector('[data-cl-confirm-submit]');
        if (!dialog || !confirm || typeof dialog.showModal !== 'function') {
            submit(table, action);
            return;
        }
        var title = dialog.querySelector('#cl-confirm-title');
        var message = dialog.querySelector('#cl-confirm-message');
        if (title) {
            title.textContent = (button.getAttribute('data-cl-select-confirm-title') || '').replace('{n}', String(checked(table).length));
        }
        if (message) {
            message.textContent = button.getAttribute('data-cl-select-confirm-message') || '';
        }
        confirm.textContent = button.getAttribute('data-cl-select-confirm-action') || confirm.textContent;
        confirm.classList.toggle('is-danger', button.classList.contains('is-danger'));
        confirm.classList.toggle('is-primary', !button.classList.contains('is-danger'));
        // The dialog's own form posts to the address of the link that last opened it; this action goes through the
        // bulk form instead, so the confirm button must not submit that form.
        confirm.disabled = false;
        var onConfirm = function (event) {
            event.preventDefault();
            dialog.close();
            submit(table, action);
        };
        confirm.addEventListener('click', onConfirm);
        dialog.addEventListener('close', function () {
            confirm.removeEventListener('click', onConfirm);
        }, { once: true });
        dialog.showModal();
        var cancel = dialog.querySelector('[data-cl-confirm-cancel]');
        if (cancel) {
            cancel.focus();
        }
    }

    function init(table) {
        table.querySelectorAll('[data-cl-select-all], [data-cl-select-row]').forEach(function (box) {
            box.hidden = false;
        });
        var bar = barOf(table);
        table.addEventListener('change', function (event) {
            if (event.target.matches('[data-cl-select-all]')) {
                var on = event.target.checked;
                rowsOf(table).forEach(function (box) {
                    if (visible(box)) {
                        box.checked = on;
                    }
                });
            }
            refresh(table);
        });
        if (bar) {
            bar.addEventListener('click', function (event) {
                if (!event.target.closest) {
                    return;
                }
                if (event.target.closest('[data-cl-select-clear]')) {
                    rowsOf(table).forEach(function (box) { box.checked = false; });
                    refresh(table);
                    return;
                }
                var action = event.target.closest('[data-cl-select-action]');
                if (action && checked(table).length > 0) {
                    if (action.hasAttribute('data-cl-select-confirm-title')) {
                        confirmThen(action, table);
                    } else {
                        submit(table, action.getAttribute('data-cl-select-action'));
                    }
                }
            });
        }
        var container = table.closest('[data-cl-table-filter]');
        if (container) {
            container.addEventListener('cl:table-filtered', function () {
                rowsOf(table).forEach(function (box) {
                    if (!visible(box)) {
                        box.checked = false;
                    }
                });
                refresh(table);
            });
        }
        refresh(table);
    }

    function initAll() {
        document.querySelectorAll('table[data-cl-select-table]').forEach(init);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
})();
