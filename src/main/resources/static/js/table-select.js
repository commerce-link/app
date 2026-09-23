// Row selection for a cl-table with bulk actions. table[data-cl-select-table] has a header checkbox [data-cl-select-all]
// (checks the visible rows) and row checkboxes [data-cl-select-row][value]. The bar [data-cl-selection-bar] shows while
// anything is checked: [data-cl-selection-count][data-template="... {n}"] says how many, buttons [data-cl-select-action=x]
// submit form[data-cl-select-form] with hidden inputs name=action / name=productIds, [data-cl-select-clear] unchecks all.
// A button with [data-cl-select-confirm-title] first opens the page's dialog#cl-confirm-dialog (fragments/confirm-dialog,
// whose confirm-dialog.js closes it on Cancel) and submits on confirm; "{n}" in the title and the message becomes the
// number of checked rows. Such a button does nothing at all when the page has no usable dialog -- an action worth
// confirming is not worth doing unconfirmed. Rows hidden by table-filter.js (event cl:table-filtered) are dropped from
// the selection so a bulk action never touches what the operator cannot see. Without JavaScript nothing here is shown.
//
// The count is announced from [data-cl-selection-status] (a visually hidden role=status that is always in the page,
// outside the bar -- a live region revealed in the same frame as its text is often not read); it speaks only when the
// count changes, never on load. The bulk form takes the page's query string along (the filter as the operator left it,
// kept in the address by table-filter.js), so the redirect after the action can come back to it. "Clear" moves the
// focus to the header checkbox, as the bar it was pressed in disappears.
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

    function statusOf(table) {
        var card = table.closest('.cl-card') || document;
        return card.querySelector('[data-cl-selection-status]');
    }

    // The first call only notes the count: a page that loads with nothing selected has nothing to announce.
    function announce(table, text, count) {
        var status = statusOf(table);
        if (!status || status.getAttribute('data-count') === String(count)) {
            return;
        }
        if (status.hasAttribute('data-count')) {
            status.textContent = text;
        }
        status.setAttribute('data-count', String(count));
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
            var text = (count.getAttribute('data-template') || '{n}').replace('{n}', String(selected.length));
            count.textContent = text;
            announce(table, text, selected.length);
        }
    }

    // The page's query string, as hidden inputs: the server echoes back the filter it understands and nothing else.
    // The names the form itself posts are never taken from the address.
    function carryAddress(form) {
        form.querySelectorAll('input[data-cl-select-carried]').forEach(function (old) {
            old.remove();
        });
        new URLSearchParams(window.location.search).forEach(function (value, name) {
            if (name === 'action' || name === 'productIds') {
                return;
            }
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value;
            input.setAttribute('data-cl-select-carried', '');
            form.appendChild(input);
        });
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
        carryAddress(form);
        form.submit();
    }

    function confirmThen(button, table) {
        var dialog = document.getElementById('cl-confirm-dialog');
        var action = button.getAttribute('data-cl-select-action');
        var accept = dialog && dialog.querySelector('[data-cl-confirm-submit]');
        // Fails closed: without the dialog the operator never gets asked, and a bulk delete is not something to do on
        // a page whose confirmation markup is missing.
        if (!dialog || !accept || typeof dialog.showModal !== 'function') {
            return;
        }
        var count = String(checked(table).length);
        var title = dialog.querySelector('#cl-confirm-title');
        var message = dialog.querySelector('#cl-confirm-message');
        if (title) {
            title.textContent = (button.getAttribute('data-cl-select-confirm-title') || '').replace('{n}', count);
        }
        if (message) {
            message.textContent = (button.getAttribute('data-cl-select-confirm-message') || '').replace('{n}', count);
        }
        accept.textContent = button.getAttribute('data-cl-select-confirm-action') || accept.textContent;
        accept.classList.toggle('is-danger', button.classList.contains('is-danger'));
        accept.classList.toggle('is-primary', !button.classList.contains('is-danger'));
        // The dialog's own form posts to the address of the link that last opened it; this action goes through the
        // bulk form instead, so the confirm button must not submit that form.
        accept.disabled = false;
        var onConfirm = function (event) {
            event.preventDefault();
            dialog.close();
            submit(table, action);
        };
        accept.addEventListener('click', onConfirm);
        dialog.addEventListener('close', function () {
            accept.removeEventListener('click', onConfirm);
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
                    // The bar, and the button in it, is hidden now; the focus would fall to the body.
                    var all = table.querySelector('[data-cl-select-all]');
                    if (all) {
                        all.focus();
                    }
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
