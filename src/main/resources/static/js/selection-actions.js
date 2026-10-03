// Bulk actions on the rows checked in a cl-table (warehouse list). table-select.js keeps the checkboxes, the count and the
// selection row; this script greys an action the selection does not fit (its reason as visible text: a status the action
// is not for, or rows from different sources for an action that needs one), shows the checked units, and runs the action:
// a quantity dialog (#cl-quantity-dialog, full quantity by default, inline validation), the page's confirmation dialog
// (window.CL_confirmBulk) or a plain post. Every post goes to the action's own address with selectedItemIds (and
// quantities, reason, note). Rows come and go with list-page.js swaps (cl-list:swapped), so all lookups are fresh.
(function () {
    'use strict';

    // A second click while the first post is on its way would run the action twice (two RW documents for one
    // partial destroy); the flag drops when the dialog opens again or the page comes back from the history cache.
    var submitting = false;

    function bar() {
        return document.querySelector('[data-cl-selection-actions]');
    }

    function checkedBoxes() {
        return Array.prototype.slice.call(document.querySelectorAll('table[data-cl-select-table] input[data-cl-select-row]:checked'));
    }

    // Spec §4.5: an action runs only when every checked row fits it (order-items.js skips the rows that do not; §14.8).
    function fits(button, boxes) {
        var allowed = (button.getAttribute('data-cl-action-for') || '').split(' ');
        var status = boxes.every(function (box) {
            return allowed.indexOf(box.getAttribute('data-status')) >= 0;
        });
        if (!status) {
            return { fits: false, reason: button.getAttribute('data-reason-status') || '' };
        }
        if (button.hasAttribute('data-cl-action-same-source')) {
            var first = boxes.length ? boxes[0].getAttribute('data-source') : null;
            var same = boxes.every(function (box) {
                return box.getAttribute('data-source') === first;
            });
            if (!same) {
                return { fits: false, reason: bar().getAttribute('data-reason-source') || '' };
            }
        }
        return { fits: true, reason: '' };
    }

    function reasonOf(button) {
        return button.querySelector('[data-cl-action-reason]')
            || document.getElementById(button.getAttribute('aria-describedby') || '');
    }

    function refresh() {
        var root = bar();
        if (!root) {
            return;
        }
        var boxes = checkedBoxes();
        root.querySelectorAll('[data-cl-action-path]').forEach(function (button) {
            var verdict = boxes.length ? fits(button, boxes) : { fits: true, reason: '' };
            var reason = verdict.reason;
            if (!verdict.fits) {
                button.setAttribute('aria-disabled', 'true');
            } else {
                button.removeAttribute('aria-disabled');
            }
            var target = reasonOf(button);
            if (target) {
                target.textContent = reason || '';
                target.hidden = !reason;
            }
        });
        var units = document.querySelector('[data-cl-selection-units]');
        if (units) {
            var sum = boxes.reduce(function (total, box) {
                return total + Number(box.getAttribute('data-qty') || 0);
            }, 0);
            units.textContent = unitsText(sum);
        }
    }

    // "{m} szt." from the selection row's server-rendered template, so the script carries no unit word of its own
    function unitsText(count) {
        var units = document.querySelector('[data-cl-selection-units]');
        return ((units && units.getAttribute('data-template')) || '{m}').replace('{m}', String(count));
    }

    function post(path, boxes, extra) {
        if (submitting) {
            return;
        }
        var form = document.getElementById('warehouse-bulk-form');
        // the CSRF field Thymeleaf renders into the form must survive the rebuild
        Array.prototype.slice.call(form.children).forEach(function (child) {
            if (child.getAttribute('name') !== '_csrf') {
                child.remove();
            }
        });
        form.action = path;
        boxes.forEach(function (box) {
            add(form, 'selectedItemIds', box.value);
        });
        (extra || []).forEach(function (pair) {
            add(form, pair[0], pair[1]);
        });
        submitting = true;
        form.submit();
    }

    function add(form, name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
    }

    // Spec §4.6: the focus goes back to what opened the dialog, however it was closed (Cancel, Escape, a backdrop click).
    function returnFocusOnClose(dialog, opener) {
        dialog.addEventListener('close', function () {
            if (opener && document.contains(opener)) {
                opener.focus();
            }
        }, { once: true });
    }

    // The note's error text is server-rendered, so it is only hidden; the quantity errors are rebuilt with the rows.
    function resetDialog(dialog) {
        dialog.querySelectorAll('.cl-field-error').forEach(function (error) {
            error.hidden = true;
        });
        dialog.querySelectorAll('[aria-invalid]').forEach(function (field) {
            field.removeAttribute('aria-invalid');
        });
        var note = dialog.querySelector('textarea[name="note"]');
        if (note) {
            note.value = '';
        }
    }

    function openQuantities(button, boxes, opener) {
        var dialog = document.getElementById('cl-quantity-dialog');
        var form = dialog.querySelector('[data-cl-quantity-form]');
        var rows = dialog.querySelector('[data-cl-quantity-rows]');
        var template = dialog.querySelector('[data-cl-quantity-row]');
        var destroy = button.hasAttribute('data-cl-action-destroy');
        var title = dialog.querySelector('[data-cl-quantity-title]');
        title.textContent = title.getAttribute('data-template')
            .replace('{label}', button.getAttribute('data-cl-action-label')).replace('{k}', String(boxes.length));
        submitting = false;
        resetDialog(dialog);
        rows.replaceChildren();
        boxes.forEach(function (box) {
            var row = template.content.firstElementChild.cloneNode(true);
            row.querySelector('[data-name]').textContent = box.getAttribute('data-name');
            row.querySelector('[data-meta]').textContent = box.getAttribute('data-status-label') + ' · ' + unitsText(box.getAttribute('data-qty'));
            row.querySelector('input[name="selectedItemIds"]').value = box.value;
            var qty = row.querySelector('input[name="quantities"]');
            qty.max = box.getAttribute('data-qty');
            qty.value = box.getAttribute('data-qty');
            qty.setAttribute('aria-label', box.getAttribute('data-name'));
            rows.appendChild(row);
        });
        var extra = dialog.querySelector('[data-cl-quantity-destroy]');
        extra.hidden = !destroy;
        extra.querySelectorAll('select, textarea').forEach(function (field) {
            field.disabled = !destroy;
        });
        var effect = dialog.querySelector('[data-cl-quantity-effect]');
        effect.textContent = effect.getAttribute(destroy ? 'data-effect-destroy' : 'data-effect-split');
        var submit = dialog.querySelector('[data-cl-quantity-submit]');
        submit.textContent = button.getAttribute('data-cl-action-label');
        submit.classList.toggle('is-danger', destroy);
        submit.classList.toggle('is-primary', !destroy);
        form.action = button.getAttribute('data-cl-action-path');
        returnFocusOnClose(dialog, opener);
        dialog.showModal();
        var first = rows.querySelector('input[name="quantities"]');
        if (first) {
            first.focus();
            first.select();
        }
    }

    function validate(dialog) {
        var effect = dialog.querySelector('[data-cl-quantity-effect]');
        var firstBad = null;
        dialog.querySelectorAll('input[name="quantities"]').forEach(function (input) {
            var error = input.parentElement.querySelector('.cl-field-error');
            var value = Number(input.value);
            var max = Number(input.max);
            var message = !Number.isInteger(value) || value < 1 ? effect.getAttribute('data-error-min')
                : value > max ? effect.getAttribute('data-error-max').replace('{0}', String(max)) : '';
            error.textContent = message;
            error.hidden = !message;
            if (message) {
                input.setAttribute('aria-invalid', 'true');
                firstBad = firstBad || input;
            } else {
                input.removeAttribute('aria-invalid');
            }
        });
        var note = dialog.querySelector('textarea[name="note"]');
        if (note && !note.disabled) {
            var noteError = note.parentElement.querySelector('.cl-field-error');
            var empty = note.value.trim() === '';
            noteError.hidden = !empty;
            if (empty) {
                note.setAttribute('aria-invalid', 'true');
                firstBad = firstBad || note;
            } else {
                note.removeAttribute('aria-invalid');
            }
        }
        if (firstBad) {
            firstBad.focus();
        }
        return !firstBad;
    }

    document.addEventListener('click', function (event) {
        if (!event.target.closest) {
            return;
        }
        var button = event.target.closest('[data-cl-selection-actions] [data-cl-action-path]');
        if (!button || button.getAttribute('aria-disabled') === 'true') {
            return;
        }
        var boxes = checkedBoxes();
        if (!boxes.length) {
            return;
        }
        event.preventDefault();
        var menu = button.closest('details.cl-menu');
        var opener = button;
        if (menu) {
            menu.open = false;
            opener = menu.querySelector(':scope > summary');
            opener.focus();
        }
        if (button.hasAttribute('data-cl-action-quantity')) {
            openQuantities(button, boxes, opener);
        } else if (button.hasAttribute('data-cl-select-confirm-title')) {
            var shown = window.CL_confirmBulk(button, String(boxes.length), function () {
                post(button.getAttribute('data-cl-action-path'), boxes);
            }, 'data-cl-select-confirm-title', 'data-cl-select-confirm-message', 'data-cl-select-confirm-action');
            if (shown) {
                returnFocusOnClose(document.getElementById('cl-confirm-dialog'), opener);
            }
        } else {
            post(button.getAttribute('data-cl-action-path'), boxes);
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.matches || !form.matches('[data-cl-quantity-form]')) {
            return;
        }
        if (submitting) {
            event.preventDefault();
            return;
        }
        if (!validate(form.closest('dialog'))) {
            event.preventDefault();
            return;
        }
        submitting = true;
    });

    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            submitting = false;
        }
    });

    // table-select.js announces every selection change (rows, select-all, clearing, filtering) after it has updated the boxes
    document.addEventListener('cl:selection-changed', refresh);
    document.addEventListener('cl-list:swapped', refresh);
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', refresh);
    } else {
        refresh();
    }
})();
