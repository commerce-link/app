// The RMA details page (templates/rma-detail.html, rma/detail/items.html and dialogs.html). Everything works without
// JavaScript: the items card then posts each bulk action straight from its noscript row and the server skips the items
// an action does not apply to. This script adds, on top of table-select.js (selection row, counts, "Wyczyść"):
// - the selection row showing only the actions that would process at least one checked item (decision D4). An action
//   [data-cl-rma-action] names the item statuses it processes (data-accepts, the server's own rules) or needs exactly
//   one checked item of a minimum quantity (data-single-min-qty). One that processes none is hidden in the row; in a
//   menu it is greyed with its data-reason (aria-disabled + cl-menu-reason), or hidden when it has none, and a menu
//   none of whose entries applies is hidden as a whole;
// - each action's dialog filled from the checked rows: the count in the title (data-template-one/-few/-many), the list
//   of the items it processes and of those it skips with the reason (the form's data-skip-<status>, the same texts as
//   the outcome notice), the sum of the deleted items' values, the split's quantities and its address;
// - the dialogs' own checks (a missing condition, a split quantity out of range) shown at the field, never in a browser alert;
// - Enter inside a dialog field running that dialog's action, never the form's first button;
// - the selection row docked to the bottom on phones while the items card is on screen.
(function () {
    'use strict';

    var form = document.getElementById('rmaItemsForm');
    if (!form) {
        return;
    }
    var TEXT_INPUT = 'input:not([type="checkbox"]):not([type="radio"]):not([type="hidden"])';

    function rowBoxes() {
        return Array.prototype.slice.call(form.querySelectorAll('input[data-cl-select-row]'));
    }

    function checkedBoxes() {
        return rowBoxes().filter(function (box) {
            return box.checked;
        });
    }

    function qty(box) {
        return parseInt(box.getAttribute('data-qty'), 10) || 0;
    }

    // the checked rows the action would process
    function processed(control) {
        var boxes = checkedBoxes();
        var minQty = control.getAttribute('data-single-min-qty');
        if (minQty) {
            return boxes.length === 1 && qty(boxes[0]) >= parseInt(minQty, 10) ? boxes : [];
        }
        var accepts = control.getAttribute('data-accepts');
        if (!accepts) {
            return boxes;
        }
        var statuses = accepts.split(',');
        return boxes.filter(function (box) {
            return statuses.indexOf(box.getAttribute('data-status')) >= 0;
        });
    }

    function setOpener(control, enabled) {
        var dialogId = control.getAttribute('data-cl-rma-dialog');
        if (!dialogId) {
            return;
        }
        if (enabled) {
            control.setAttribute('data-cl-dialog-open', dialogId);
        } else {
            control.removeAttribute('data-cl-dialog-open');
        }
    }

    function refreshActions() {
        var bar = form.querySelector('[data-cl-selection-bar]');
        if (!bar) {
            return;
        }
        bar.querySelectorAll('[data-cl-rma-action]').forEach(function (control) {
            var applies = processed(control).length > 0;
            var entry = control.closest('.cl-menu-list > li');
            if (!entry) {
                control.hidden = !applies;
                setOpener(control, applies);
                return;
            }
            var reasonText = control.getAttribute('data-reason');
            var reason = control.querySelector('.cl-menu-reason');
            entry.hidden = !applies && !reasonText;
            if (reason) {
                reason.textContent = reasonText || '';
                reason.hidden = applies;
            }
            if (applies) {
                control.removeAttribute('aria-disabled');
            } else {
                control.setAttribute('aria-disabled', 'true');
            }
            setOpener(control, applies);
        });
        bar.querySelectorAll('details.cl-menu').forEach(function (menu) {
            var any = Array.prototype.some.call(menu.querySelectorAll('[data-cl-rma-action]'), function (control) {
                return processed(control).length > 0;
            });
            menu.hidden = !any;
            if (!any) {
                menu.open = false;
            }
        });
    }

    // A greyed entry does nothing: it has no opener, and a greyed submit (none today) must not post either.
    function initGreyedEntries() {
        form.addEventListener('click', function (event) {
            var greyed = event.target.closest && event.target.closest('[data-cl-rma-action][aria-disabled="true"]');
            if (greyed) {
                event.preventDefault();
            }
        });
    }

    // The Polish plural form, as PluralForm.java picks it: "one" for 1, "few" for 2-4, 22-24... but not 12-14, "many"
    // for the rest. English pages give "few" and "many" the same text.
    function pluralForm(count) {
        if (count === 1) {
            return 'one';
        }
        var ones = count % 10;
        var tens = count % 100;
        return ones >= 2 && ones <= 4 && (tens < 12 || tens > 14) ? 'few' : 'many';
    }

    // a function replacement: a name or reason containing "$&" or "$'" must come out as typed
    function fill(template, placeholder, value) {
        return template.replace(placeholder, function () {
            return value;
        });
    }

    function formatMoney(value) {
        return typeof window.CL_formatMoney === 'function' ? window.CL_formatMoney(value) : value.toFixed(2);
    }

    function listItem(name, detail, detailClass) {
        var item = document.createElement('li');
        var label = document.createElement('span');
        label.textContent = name;
        var extra = document.createElement('span');
        extra.className = detailClass;
        extra.textContent = detail;
        item.appendChild(label);
        item.appendChild(extra);
        return item;
    }

    function fillList(list, done) {
        while (list.firstChild) {
            list.removeChild(list.firstChild);
        }
        var qtyTemplate = form.getAttribute('data-qty-template') || '{n}';
        var skippedTemplate = form.getAttribute('data-skipped-template') || '{reason}';
        checkedBoxes().forEach(function (box) {
            if (done.indexOf(box) >= 0) {
                list.appendChild(listItem(box.getAttribute('data-name'),
                    fill(qtyTemplate, '{n}', String(qty(box))), 'is-numeric'));
                return;
            }
            var reason = form.getAttribute('data-skip-' + box.getAttribute('data-status')) || '';
            list.appendChild(listItem(box.getAttribute('data-name'),
                fill(skippedTemplate, '{reason}', reason), 'cl-dialog-list-sub'));
        });
    }

    function fillTitle(dialog, count) {
        var title = dialog.querySelector('[data-cl-rma-title]');
        if (!title) {
            return;
        }
        var attribute = { one: 'data-template-one', few: 'data-template-few', many: 'data-template-many' }[pluralForm(count)];
        var template = title.getAttribute(attribute) || title.getAttribute('data-template-many') || '';
        title.textContent = fill(template, '{n}', String(count));
    }

    function fillSum(dialog, done) {
        var sum = dialog.querySelector('[data-cl-rma-sum]');
        if (!sum) {
            return;
        }
        var total = done.reduce(function (acc, box) {
            return acc + (parseFloat(box.getAttribute('data-total')) || 0);
        }, 0);
        sum.textContent = fill(sum.getAttribute('data-template') || '', '{sum}', formatMoney(total));
    }

    function splitFields(dialog) {
        return {
            name: dialog.querySelector('[data-cl-rma-split-name]'),
            qty1: dialog.querySelector('#split-qty1'),
            qty2Text: dialog.querySelector('[data-cl-rma-qty2]'),
            qty2: dialog.querySelector('input[name="qty2"]'),
            submit: dialog.querySelector('[data-cl-dialog-submit]')
        };
    }

    function renderQty2(dialog) {
        var f = splitFields(dialog);
        var total = parseInt(dialog.getAttribute('data-cl-rma-split-qty'), 10) || 0;
        var first = parseInt(f.qty1.value, 10);
        var second = isNaN(first) ? 0 : total - first;
        var qtyTemplate = form.getAttribute('data-qty-template') || '{n}';
        f.qty2.value = String(second);
        f.qty2Text.textContent = fill(qtyTemplate, '{n}', String(Math.max(second, 0)));
    }

    // the split's address names the item: /dashboard/rma/{rmaId}/items/{id}/split
    function fillSplit(dialog, done) {
        var f = splitFields(dialog);
        if (!f.qty1 || !f.qty2 || !f.submit) {
            return;
        }
        var box = done[0];
        if (!box) {
            f.submit.removeAttribute('formaction');
            dialog.removeAttribute('data-cl-rma-split-qty');
            return;
        }
        var total = qty(box);
        var qtyTemplate = form.getAttribute('data-qty-template') || '{n}';
        dialog.setAttribute('data-cl-rma-split-qty', String(total));
        f.submit.setAttribute('formaction', '/dashboard/rma/' + encodeURIComponent(form.getAttribute('data-rma-id'))
            + '/items/' + encodeURIComponent(box.getAttribute('data-id')) + '/split');
        if (f.name) {
            f.name.textContent = box.getAttribute('data-name') + ' · ' + fill(qtyTemplate, '{n}', String(total));
        }
        f.qty1.min = '1';
        f.qty1.max = String(total - 1);
        f.qty1.value = '1';
        renderQty2(dialog);
    }

    function hideErrors(dialog) {
        dialog.querySelectorAll('[data-cl-field-error]').forEach(function (error) {
            error.hidden = true;
        });
        dialog.querySelectorAll('[aria-invalid]').forEach(function (field) {
            field.removeAttribute('aria-invalid');
        });
    }

    function initDialogs() {
        form.querySelectorAll('dialog.cl-dialog').forEach(function (dialog) {
            dialog.addEventListener('cl:dialog-open', function (event) {
                var trigger = event.detail && event.detail.trigger;
                var control = trigger && trigger.closest('[data-cl-rma-action]');
                var done = control ? processed(control) : checkedBoxes();
                hideErrors(dialog);
                // the three dialogs asking for the condition share one radio group: each starts with nothing chosen
                dialog.querySelectorAll('fieldset[data-cl-rma-condition] input[type="radio"]').forEach(function (radio) {
                    radio.checked = false;
                });
                dialog.querySelectorAll('ul[data-cl-selection-list]').forEach(function (list) {
                    fillList(list, done);
                });
                fillTitle(dialog, done.length);
                fillSum(dialog, done);
                if (dialog.querySelector('#split-qty1')) {
                    fillSplit(dialog, done);
                }
            });
            var qty1 = dialog.querySelector('#split-qty1');
            if (qty1) {
                qty1.addEventListener('input', function () {
                    renderQty2(dialog);
                });
            }
        });
    }

    function showError(error, field) {
        if (error) {
            error.hidden = false;
        }
        if (field) {
            field.setAttribute('aria-invalid', 'true');
            field.focus();
        }
    }

    function conditionChosen(dialog) {
        var fieldset = dialog.querySelector('fieldset[data-cl-rma-condition]');
        if (!fieldset || fieldset.querySelector('input[type="radio"]:checked')) {
            return true;
        }
        showError(fieldset.querySelector('[data-cl-field-error]'), fieldset.querySelector('input[type="radio"]'));
        return false;
    }

    function splitValid(dialog) {
        var qty1 = dialog.querySelector('#split-qty1');
        if (!qty1) {
            return true;
        }
        var total = parseInt(dialog.getAttribute('data-cl-rma-split-qty'), 10) || 0;
        var first = Number(qty1.value);
        var submit = dialog.querySelector('[data-cl-dialog-submit]');
        if (submit && submit.hasAttribute('formaction') && Number.isInteger(first) && first >= 1 && first <= total - 1) {
            return true;
        }
        var error = qty1.parentElement.querySelector('[data-cl-field-error]');
        var text = error && error.querySelector('[data-template]');
        if (text) {
            text.textContent = fill(text.getAttribute('data-template'), '{max}', String(Math.max(total - 1, 1)));
        }
        showError(error, qty1);
        return false;
    }

    function initValidation() {
        form.querySelectorAll('dialog [data-cl-dialog-submit]').forEach(function (button) {
            button.addEventListener('click', function (event) {
                var dialog = button.closest('dialog');
                hideErrors(dialog);
                if (checkedBoxes().length === 0 || !conditionChosen(dialog) || !splitValid(dialog)) {
                    event.preventDefault();
                }
            });
        });
    }

    function initEnter() {
        form.addEventListener('keydown', function (event) {
            // a radio or checkbox keeps its own keys; Enter in a text field runs the dialog's action
            if (event.key !== 'Enter' || !event.target.matches(TEXT_INPUT)) {
                return;
            }
            var dialog = event.target.closest('dialog');
            if (!dialog) {
                return;
            }
            event.preventDefault();
            var submit = dialog.querySelector('[data-cl-dialog-submit]');
            if (submit && !submit.disabled) {
                submit.click();
            }
        });
    }

    // A second click must not send the action again while the first request is on its way; a page restored from the
    // back/forward cache gets its button back.
    function initSubmitOnce() {
        form.addEventListener('submit', function (event) {
            var button = event.submitter;
            if (event.defaultPrevented || !button || button.hasAttribute('data-cl-enter-guard')) {
                return;
            }
            button.disabled = true;
            window.addEventListener('pageshow', function (shown) {
                if (shown.persisted) {
                    button.disabled = false;
                }
            }, { once: true });
        });
    }

    // Fields that would post a value of their own on a page without JavaScript (where the dialogs never open) are
    // rendered disabled; with the dialogs working they take part again.
    function enableDialogFields() {
        form.querySelectorAll('[data-cl-rma-enable]').forEach(function (field) {
            field.disabled = false;
        });
    }

    function initDockedBar() {
        var bar = form.querySelector('.cl-selection-row.is-docked');
        var card = form.closest('.cl-card');
        if (!bar || !card || typeof IntersectionObserver !== 'function') {
            return;
        }
        // A hidden bar measures 0; writing that would override the stylesheet fallback and let the bar, once shown,
        // cover the last row, so the height is taken whenever the selection (and so the bar) changes.
        var measure = function () {
            var height = bar.offsetHeight;
            if (height === 0) {
                return;
            }
            form.style.setProperty('--cl-docked-bar', (height + 8) + 'px');
        };
        new IntersectionObserver(function (entries) {
            bar.classList.toggle('is-in-view', entries[entries.length - 1].isIntersecting);
            measure();
        }).observe(card);
        form.addEventListener('change', measure);
        window.addEventListener('resize', measure);
    }

    function init() {
        enableDialogFields();
        initGreyedEntries();
        initValidation();
        initDialogs();
        initEnter();
        initSubmitOnce();
        initDockedBar();
        // table-select.js ends every refresh with cl:selection-changed on the table; it may have run its first one
        // before this script, so the actions are worked out once here too
        form.addEventListener('cl:selection-changed', refreshActions);
        refreshActions();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

// The single dialogs outside #rmaItemsForm (rma/detail/dialogs :: single), each with its own form:
// - "Odrzuć RMA": the reason is required on every RMA; a blank one shows the field error instead of posting, and on a
//   marketplace RMA the characters typed are counted against the limit (the counter's data-template);
// - "Dodaj pozycję": the quantity is limited to the chosen order item (data-max-qty) and the effect names how much the
//   insurance grows (data-unit-price × quantity, the server's own sum);
// - the split without the items form (?open=split): the second quantity follows the first;
// - the shipments card's POST-only menu entries (rma/detail/shipments :: card): the links that go through the
//   confirmation dialog are shown ([data-cl-rma-js-only]); without JavaScript a noscript form stands in for each.
// Every text comes from the templates' data attributes.
(function () {
    'use strict';

    function fill(template, placeholder, value) {
        return template.replace(placeholder, function () {
            return value;
        });
    }

    function initReject() {
        document.querySelectorAll('textarea[data-cl-rma-required]').forEach(function (field) {
            var dialog = field.closest('dialog');
            var error = field.closest('.cl-field').querySelector('[data-cl-field-error]');
            var counter = document.getElementById(field.getAttribute('data-cl-rma-counter') || '');
            var hideError = function () {
                if (error) {
                    error.hidden = true;
                }
                field.removeAttribute('aria-invalid');
            };
            var count = function () {
                if (counter) {
                    counter.textContent = fill(counter.getAttribute('data-template') || '', '{n}', String(field.value.length));
                }
            };
            field.addEventListener('input', function () {
                count();
                if (field.value.trim()) {
                    hideError();
                }
            });
            if (dialog) {
                dialog.addEventListener('cl:dialog-open', hideError);
            }
            var submit = dialog && dialog.querySelector('[data-cl-dialog-submit]');
            if (submit) {
                submit.addEventListener('click', function (event) {
                    if (field.value.trim()) {
                        return;
                    }
                    event.preventDefault();
                    if (error) {
                        error.hidden = false;
                    }
                    field.setAttribute('aria-invalid', 'true');
                    field.focus();
                });
            }
            count();
        });
    }

    function initAdd() {
        var select = document.querySelector('select[data-cl-rma-add]');
        if (!select) {
            return;
        }
        var form = select.form;
        var quantity = form.querySelector('input[name="quantity"]');
        var max = form.querySelector('[data-cl-rma-add-max]');
        var effect = form.querySelector('[data-cl-rma-add-effect]');
        var initialEffect = effect ? effect.textContent : '';
        var formatMoney = function (value) {
            return typeof window.CL_formatMoney === 'function' ? window.CL_formatMoney(value) : value.toFixed(2);
        };
        var refresh = function () {
            var option = select.options[select.selectedIndex];
            var maxQty = option && option.value ? parseInt(option.getAttribute('data-max-qty'), 10) : NaN;
            if (isNaN(maxQty)) {
                quantity.removeAttribute('max');
                if (max) {
                    max.hidden = true;
                }
                if (effect) {
                    effect.textContent = initialEffect;
                }
                return;
            }
            quantity.max = String(maxQty);
            var qty = parseInt(quantity.value, 10);
            if (!isNaN(qty) && qty > maxQty) {
                quantity.value = String(maxQty);
                qty = maxQty;
            }
            if (max) {
                max.textContent = fill(max.getAttribute('data-template') || '', '{n}', String(maxQty));
                max.hidden = false;
            }
            if (effect) {
                var unitPrice = parseFloat(option.getAttribute('data-unit-price')) || 0;
                effect.textContent = isNaN(qty) || qty < 1 ? initialEffect
                    : fill(effect.getAttribute('data-template') || '', '{sum}', formatMoney(unitPrice * qty));
            }
        };
        select.addEventListener('change', refresh);
        quantity.addEventListener('input', refresh);
        refresh();
    }

    function initSplitForm() {
        document.querySelectorAll('form[data-cl-rma-split-form]').forEach(function (form) {
            var total = parseInt(form.getAttribute('data-qty'), 10) || 0;
            var first = form.querySelector('input[name="qty1"]');
            var second = form.querySelector('input[name="qty2"]');
            if (!first || !second) {
                return;
            }
            first.addEventListener('input', function () {
                var qty1 = parseInt(first.value, 10);
                if (!isNaN(qty1)) {
                    second.value = String(total - qty1);
                }
            });
        });
    }

    function showScriptedControls() {
        // confirm-dialog.js gives up without dialog support; the link would then GET an address that only takes a POST
        if (typeof HTMLDialogElement === 'undefined' || typeof HTMLDialogElement.prototype.showModal !== 'function') {
            return;
        }
        document.querySelectorAll('[data-cl-rma-js-only]').forEach(function (control) {
            control.hidden = false;
        });
    }

    function init() {
        initReject();
        initAdd();
        initSplitForm();
        showScriptedControls();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
