// Row selection for a cl-table with bulk actions. table[data-cl-select-table] has a header checkbox [data-cl-select-all]
// (checks the visible rows) and row checkboxes [data-cl-select-row][value]. The selection row [data-cl-selection-bar]
// shows while anything is checked and covers the table header: the table gets .has-selection, whose CSS hides the
// header's contents while the header keeps its place, and the row, placed right before the table, takes the header's
// measured height (--cl-selection-head) and pulls the table up under itself by the same amount, so nothing below moves.
// The selection row carries a select-all box of its own [data-cl-select-all] in the header's place; the focus moves
// between the two boxes as one of them disappears. [data-cl-selection-count][data-template="... {k} ... {n}"] says how
// many of the visible rows are checked, buttons [data-cl-select-action=x] submit form[data-cl-select-form] with hidden
// inputs name=action / name=productIds.
// A select-all box clears the whole selection whenever anything is checked (also from the indeterminate state, which a
// browser would turn into "all checked") and checks the visible rows only when nothing is; its accessible name follows,
// from data-cl-label-select / data-cl-label-clear. Clearing hides the selection row, so the focus goes to the header's box.
// A button with [data-cl-select-confirm-title] first opens the page's dialog#cl-confirm-dialog (fragments/confirm-dialog,
// whose confirm-dialog.js closes it on Cancel) and submits on confirm; "{n}" in the title and the message becomes the
// number of checked rows. Such a button does nothing at all when the page has no usable dialog -- an action worth
// confirming is not worth doing unconfirmed. Rows hidden by table-filter.js (event cl:table-filtered) are dropped from
// the selection so a bulk action never touches what the operator cannot see. Without JavaScript nothing here is shown.
//
// The count is announced from [data-cl-selection-status] (a visually hidden role=status that is always in the page,
// outside the selection row -- a live region revealed in the same frame as its text is often not read); it speaks only
// when the count changes, never on load. The bulk form takes the page's query string along (the filter as the operator
// left it, kept in the address by table-filter.js), so the redirect after the action can come back to it.
//
// Instead of submitting, a button [data-cl-select-print="<address>"] prints the checked rows: the address with one
// ids=<value> per checked row, in the rows' order, goes to print.js (window.CL_printInFrame), which loads it into its
// hidden frame and opens one print dialog. Above data-cl-select-confirm-above rows the page's dialog asks first, with
// the title and the accept label of the count's Polish plural form (data-cl-select-confirm-title-few/-many,
// -action-few/-many; English pages give both the same text) and data-cl-select-confirm-message. A button
// [data-cl-select-clear] unticks every row. [data-cl-selection-text][data-template] in the selection row follows the
// count like the count itself ({k} checked, {n} visible). A set-up table gets .is-selectable (a page may hide its
// checkbox column without the script) and is set up once; tables swapped in by list-page.js (cl-list:swapped) are set
// up as they arrive.
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

    // the header's select-all box and the selection row's own
    function allBoxes(table) {
        var bar = barOf(table);
        var boxes = Array.prototype.slice.call(table.querySelectorAll('[data-cl-select-all]'));
        return bar ? boxes.concat(Array.prototype.slice.call(bar.querySelectorAll('[data-cl-select-all]'))) : boxes;
    }

    // The header's height (it keeps its place while hidden) becomes the selection row's height and the overlap the table
    // is pulled up by. Below 720 px the header is visually hidden (card mode) and measures next to nothing: the row then
    // stands above the cards at its own height.
    function measureHead(table, bar) {
        var head = table.tHead;
        if (!head || !bar) {
            return;
        }
        var height = head.getBoundingClientRect().height;
        if (height > 8) {
            bar.style.setProperty('--cl-selection-head', height + 'px');
        } else {
            bar.style.removeProperty('--cl-selection-head');
        }
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
        var shown = rowsOf(table).filter(visible);
        allBoxes(table).forEach(function (all) {
            all.checked = shown.length > 0 && shown.every(function (box) { return box.checked; });
            all.indeterminate = selected.length > 0 && !all.checked;
            var label = all.getAttribute(selected.length > 0 ? 'data-cl-label-clear' : 'data-cl-label-select');
            if (label) {
                all.setAttribute('aria-label', label);
            }
        });
        if (!bar) {
            return;
        }
        if (selected.length > 0 && !table.classList.contains('has-selection')) {
            measureHead(table, bar);
        }
        bar.hidden = selected.length === 0;
        table.classList.toggle('has-selection', selected.length > 0);
        var count = bar.querySelector('[data-cl-selection-count]');
        if (count) {
            var text = (count.getAttribute('data-template') || '{k}')
                .replace('{k}', String(selected.length)).replace('{n}', String(shown.length));
            count.textContent = text;
            announce(table, text, selected.length);
        }
        bar.querySelectorAll('[data-cl-selection-text]').forEach(function (node) {
            node.textContent = (node.getAttribute('data-template') || '')
                .replace('{k}', String(selected.length)).replace('{n}', String(shown.length));
        });
    }

    // The select-all box that was just used may have disappeared with the header or the selection row; the focus
    // moves to the one that shows now.
    function keepFocus(table) {
        var active = document.activeElement;
        if (!active || !active.matches || !active.matches('[data-cl-select-all]')) {
            return;
        }
        var bar = barOf(table);
        var inBar = bar && bar.contains(active);
        var target = inBar && bar.hidden ? table.querySelector('[data-cl-select-all]')
            : !inBar && bar && !bar.hidden ? bar.querySelector('[data-cl-select-all]') : null;
        if (target) {
            target.focus();
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

    // Shared with order-items.js: opens #cl-confirm-dialog, fills it from the button's title/message/
    // action attributes (named by the caller, with "{n}" replaced by count) and wires the accept button to onConfirm.
    // Fails closed: without the dialog the operator never gets asked, and a bulk action is not something to do on a
    // page whose confirmation markup is missing. Returns whether the dialog was shown.
    window.CL_confirmBulk = function (button, count, onConfirm, titleAttr, messageAttr, actionAttr) {
        var dialog = document.getElementById('cl-confirm-dialog');
        var accept = dialog && dialog.querySelector('[data-cl-confirm-submit]');
        if (!dialog || !accept || typeof dialog.showModal !== 'function') {
            return false;
        }
        var title = dialog.querySelector('#cl-confirm-title');
        var message = dialog.querySelector('#cl-confirm-message');
        if (title) {
            title.textContent = (button.getAttribute(titleAttr) || '').replace('{n}', count);
        }
        if (message) {
            message.textContent = (button.getAttribute(messageAttr) || '').replace('{n}', count);
        }
        accept.textContent = (button.getAttribute(actionAttr) || accept.textContent).replace('{n}', count);
        accept.classList.toggle('is-danger', button.classList.contains('is-danger'));
        accept.classList.toggle('is-primary', !button.classList.contains('is-danger'));
        accept.disabled = false;
        var handler = function (event) {
            event.preventDefault();
            dialog.close();
            onConfirm();
        };
        accept.addEventListener('click', handler);
        dialog.addEventListener('close', function () {
            accept.removeEventListener('click', handler);
        }, { once: true });
        dialog.showModal();
        var cancel = dialog.querySelector('[data-cl-confirm-cancel]');
        if (cancel) {
            cancel.focus();
        }
        return true;
    };

    function confirmThen(button, table) {
        var action = button.getAttribute('data-cl-select-action');
        // The dialog's own form posts to the address of the link that last opened it; this action goes through the
        // bulk form instead, so the confirm button must not submit that form.
        window.CL_confirmBulk(button, String(checked(table).length), function () {
            submit(table, action);
        }, 'data-cl-select-confirm-title', 'data-cl-select-confirm-message', 'data-cl-select-confirm-action');
    }

    // Unticks every row; the selection row hides, so the focus goes to the header's select-all box.
    function clearSelection(table) {
        rowsOf(table).forEach(function (box) {
            box.checked = false;
        });
        refresh(table);
        var header = table.querySelector('[data-cl-select-all]');
        if (header) {
            header.focus();
        }
    }

    // The Polish plural form of a count above the confirmation limit (never 1), as PluralForm.java picks it: "few" for
    // 2-4, 22-24, 32-34... but not 12-14, "many" for the rest.
    function pluralForm(count) {
        var ones = count % 10;
        var tens = count % 100;
        return ones >= 2 && ones <= 4 && (tens < 12 || tens > 14) ? 'few' : 'many';
    }

    // Prints the checked rows through print.js; without it (or, above the limit, without the dialog) nothing happens.
    function printSelection(button, table) {
        var print = window.CL_printInFrame;
        var rows = checked(table);
        if (typeof print !== 'function' || rows.length === 0) {
            return;
        }
        var params = new URLSearchParams();
        rows.forEach(function (box) {
            params.append('ids', box.value);
        });
        var address = button.getAttribute('data-cl-select-print');
        var href = address + (address.indexOf('?') < 0 ? '?' : '&') + params.toString();
        var limit = parseInt(button.getAttribute('data-cl-select-confirm-above'), 10);
        if (isNaN(limit) || rows.length <= limit) {
            print(href, button);
            return;
        }
        var form = pluralForm(rows.length);
        window.CL_confirmBulk(button, String(rows.length), function () {
            print(href, button);
        }, 'data-cl-select-confirm-title-' + form, 'data-cl-select-confirm-message', 'data-cl-select-confirm-action-' + form);
    }

    function init(table) {
        if (table.hasAttribute('data-cl-select-ready')) {
            return;
        }
        table.setAttribute('data-cl-select-ready', '');
        table.classList.add('is-selectable');
        table.querySelectorAll('[data-cl-select-all], [data-cl-select-row]').forEach(function (box) {
            box.hidden = false;
        });
        var bar = barOf(table);
        var onChange = function (event) {
            if (event.target.matches('[data-cl-select-all]') && checked(table).length > 0) {
                // The rows still hold the state from before the click: anything checked means "clear". The selection
                // row is hidden now and a mouse click may not have focused the box at all, so the focus is set here.
                rowsOf(table).forEach(function (box) {
                    box.checked = false;
                });
                refresh(table);
                var header = table.querySelector('[data-cl-select-all]');
                if (header) {
                    header.focus();
                }
                return;
            }
            if (event.target.matches('[data-cl-select-all]')) {
                rowsOf(table).filter(visible).forEach(function (box) {
                    box.checked = true;
                });
            }
            refresh(table);
            keepFocus(table);
        };
        table.addEventListener('change', onChange);
        if (bar) {
            bar.addEventListener('change', onChange);
            window.addEventListener('resize', function () {
                if (!bar.hidden) {
                    measureHead(table, bar);
                }
            });
            bar.addEventListener('click', function (event) {
                if (!event.target.closest) {
                    return;
                }
                if (event.target.closest('[data-cl-select-clear]')) {
                    clearSelection(table);
                    return;
                }
                var printButton = event.target.closest('[data-cl-select-print]');
                if (printButton) {
                    printSelection(printButton, table);
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
    // list-page.js replaced the results block (sort, filter, page): its fresh table is set up like the first one
    document.addEventListener('cl-list:swapped', initAll);
})();
