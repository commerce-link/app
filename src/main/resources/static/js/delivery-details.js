// The delivery details page (templates/deliveries/details.html). Everything works without JavaScript: an opener is a
// link to the page with its dialog open, the selection row's noscript buttons post to …/confirm/…. This script adds:
// the destination rows' toggles; the selection row docked to the bottom on phones while the items card is on screen
// (P2); a "…-all" opener (data-cl-select-pending) checking every waiting destination before its dialog opens and
// restoring the selection when the dialog closes unsent; the list of what a selection dialog acts on, rebuilt from
// the checked rows; Enter inside a dialog field running that dialog's own action, never the form's first button; the
// dialogs' own checks (field errors at the field instead of alert()), the shipment form's pickup point, the quantity
// dialog filled from its opener, the invoice id choosing its way, and "Usuń wpłatę" asking first.
(function () {
    'use strict';

    var form = document.getElementById('allocationsForm');
    var TEXT_INPUT = 'input:not([type="checkbox"]):not([type="radio"]):not([type="hidden"])';

    function rowBoxes() {
        return form ? Array.prototype.slice.call(form.querySelectorAll('input[data-cl-select-row]')) : [];
    }

    function checkedBoxes() {
        return rowBoxes().filter(function (box) {
            return box.checked;
        });
    }

    // table-select.js refreshes the count and the selection row on a change event from its table
    function refreshSelection() {
        var table = form && form.querySelector('table[data-cl-select-table]');
        if (table) {
            table.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    // Collapsing a product unchecks its destinations: table-select.js counts and selects only visible rows, and a
    // hidden checked box would still post, so the action would touch what the operator cannot see.
    function setOpen(button, open) {
        var unchecked = false;
        button.setAttribute('aria-expanded', String(open));
        (button.getAttribute('aria-controls') || '').split(' ').forEach(function (id) {
            var row = id && document.getElementById(id);
            if (!row) {
                return;
            }
            row.hidden = !open;
            if (!open) {
                row.querySelectorAll('input[data-cl-select-row]').forEach(function (box) {
                    unchecked = unchecked || box.checked;
                    box.checked = false;
                });
            }
        });
        var main = button.closest('tr');
        if (main) {
            main.classList.toggle('is-open', open);
        }
        if (unchecked) {
            refreshSelection();
        }
    }

    function expandAll() {
        document.querySelectorAll('[data-cl-alloc-toggle][aria-expanded="false"]').forEach(function (button) {
            setOpen(button, true);
        });
    }

    function initToggles() {
        document.querySelectorAll('[data-cl-alloc-toggle]').forEach(function (button) {
            button.hidden = false;
            button.addEventListener('click', function () {
                setOpen(button, button.getAttribute('aria-expanded') !== 'true');
            });
        });
    }

    function initDockedBar() {
        var bar = form && form.querySelector('.cl-selection-row.is-docked');
        var card = form && form.closest('.cl-card');
        if (!bar || !card || typeof IntersectionObserver !== 'function') {
            return;
        }
        // A hidden bar measures 0; writing that would override the stylesheet fallback and let the bar, once shown,
        // cover the last destination row, so the height is taken whenever the selection (and so the bar) changes.
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

    function fillList(list) {
        while (list.firstChild) {
            list.removeChild(list.firstChild);
        }
        checkedBoxes().forEach(function (box) {
            var item = document.createElement('li');
            var name = document.createElement('span');
            name.textContent = box.getAttribute('data-name');
            var qty = document.createElement('span');
            qty.className = 'is-numeric';
            qty.textContent = box.getAttribute('data-qty-label');
            var destination = document.createElement('span');
            destination.className = 'cl-dialog-list-sub';
            destination.textContent = '→ ' + box.getAttribute('data-dest');
            item.appendChild(name);
            item.appendChild(qty);
            item.appendChild(destination);
            list.appendChild(item);
        });
    }

    function initDialogs() {
        document.querySelectorAll('dialog.cl-dialog').forEach(function (dialog) {
            var before = null;
            dialog.addEventListener('cl:dialog-open', function (event) {
                var trigger = event.detail && event.detail.trigger;
                dialog.removeAttribute('data-cl-submitted');
                before = null;
                if (trigger && trigger.hasAttribute('data-cl-select-pending')) {
                    before = checkedBoxes();
                    // collapsed rows would be checked but not counted, so every product opens first
                    expandAll();
                    rowBoxes().forEach(function (box) {
                        box.checked = true;
                    });
                    refreshSelection();
                }
                dialog.querySelectorAll('ul[data-cl-selection-list]').forEach(fillList);
                dialog.querySelectorAll('[data-cl-field-error]').forEach(function (error) {
                    error.hidden = true;
                });
            });
            dialog.addEventListener('close', function () {
                if (before !== null && !dialog.hasAttribute('data-cl-submitted')) {
                    var kept = before;
                    rowBoxes().forEach(function (box) {
                        box.checked = kept.indexOf(box) >= 0;
                    });
                    refreshSelection();
                }
                before = null;
            });
            dialog.querySelectorAll('[data-cl-dialog-submit]').forEach(function (button) {
                button.addEventListener('click', function (event) {
                    if (button.closest('#allocationsForm') && checkedBoxes().length === 0) {
                        event.preventDefault();
                        return;
                    }
                    if (!event.defaultPrevented) {
                        dialog.setAttribute('data-cl-submitted', '');
                    }
                });
            });
        });
    }

    function initEnter() {
        if (!form) {
            return;
        }
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

    function filled(fieldId, errorId) {
        var field = document.getElementById(fieldId);
        var ok = !!field && field.value.trim() !== '';
        var error = document.getElementById(errorId);
        if (error) {
            error.hidden = ok;
        }
        if (field) {
            field.setAttribute('aria-invalid', ok ? 'false' : 'true');
            if (!ok) {
                field.focus();
            }
        }
        return ok;
    }

    function shipmentFields() {
        return {
            type: form ? form.querySelector('input[name="shipmentType"]:checked') : null,
            carrier: document.getElementById('shipmentCarrier'),
            tracking: document.getElementById('shipmentTrackingNo'),
            point: document.getElementById('shipmentCollectionPointCode'),
            shippedAt: document.getElementById('shipmentShippedAt')
        };
    }

    // the server's own rule (DropshipShipment.validationError): type, carrier, tracking number, date, a point for a pickup point
    function shipmentComplete() {
        var f = shipmentFields();
        var pickup = !!f.type && f.type.value === 'PickupPoint';
        return !!f.type && !!f.carrier && f.carrier.value.trim() !== '' && !!f.tracking && f.tracking.value.trim() !== ''
            && !!f.shippedAt && f.shippedAt.value !== '' && (!pickup || (!!f.point && f.point.value.trim() !== ''));
    }

    function invoiceReady() {
        var byId = document.querySelector('input[name="linkMode"][value="byId"]');
        return !byId || !byId.checked || filled('invoiceId', 'invoiceId-error');
    }

    // registered before initDialogs, so a refused submit never marks its dialog as sent
    function initValidation() {
        document.querySelectorAll('[data-cl-validate]').forEach(function (button) {
            button.addEventListener('click', function (event) {
                var kind = button.getAttribute('data-cl-validate');
                var ok = kind === 'merge' ? filled('targetDeliveryId', 'targetDeliveryId-error')
                    : kind === 'split' ? filled('targetExternalDeliveryId', 'targetExternalDeliveryId-error')
                    : kind === 'ship' ? shipmentComplete()
                    : kind === 'invoice' ? invoiceReady() : true;
                var shipError = kind === 'ship' && document.getElementById('ship-error');
                if (shipError) {
                    shipError.hidden = ok;
                }
                if (!ok) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
            });
        });
    }

    // the pickup point field only means something for a pickup point; the button waits for complete data (M7)
    function initShipment() {
        var dialog = document.getElementById('ship-dialog');
        var f = shipmentFields();
        if (!dialog || !f.point) {
            return;
        }
        var submit = dialog.querySelector('[data-cl-dialog-submit]');
        var update = function () {
            var current = form.querySelector('input[name="shipmentType"]:checked');
            f.point.disabled = !current || current.value !== 'PickupPoint';
            if (submit) {
                submit.disabled = !shipmentComplete();
            }
        };
        dialog.addEventListener('input', update);
        dialog.addEventListener('change', update);
        dialog.addEventListener('cl:dialog-open', update);
        update();
    }

    function initQuantity() {
        var dialog = document.getElementById('qty-dialog');
        var input = document.getElementById('qty');
        var delta = document.getElementById('qty-delta');
        var help = document.getElementById('qty-help');
        if (!dialog || !input || !delta || !help) {
            return;
        }
        var original = parseInt(input.value, 10) || 0;
        var render = function () {
            var diff = (parseInt(input.value, 10) || 0) - original;
            delta.hidden = diff === 0;
            delta.textContent = (diff > 0 ? delta.getAttribute('data-up') : delta.getAttribute('data-down'))
                .replace('{n}', String(Math.abs(diff)));
            delta.classList.toggle('is-ok', diff > 0);
            delta.classList.toggle('is-warn', diff < 0);
        };
        dialog.addEventListener('cl:dialog-open', function (event) {
            var trigger = event.detail && event.detail.trigger;
            if (!trigger || !trigger.hasAttribute('data-mfn')) {
                return;
            }
            var min = trigger.getAttribute('data-min-qty');
            var ean = trigger.getAttribute('data-ean');
            original = parseInt(trigger.getAttribute('data-qty'), 10) || 0;
            document.getElementById('qty-mfn').value = trigger.getAttribute('data-mfn');
            document.getElementById('qty-name').textContent = trigger.getAttribute('data-name');
            document.getElementById('qty-codes').textContent = (ean ? ean + ' · ' : '') + trigger.getAttribute('data-mfn');
            input.value = String(original);
            input.min = min;
            help.textContent = help.getAttribute('data-template').replace('{min}', min);
            render();
        });
        input.addEventListener('input', render);
    }

    // typing an id means the operator chose that way: the field is visible next to both options (D7)
    function initInvoice() {
        var field = document.getElementById('invoiceId');
        var byId = document.querySelector('input[name="linkMode"][value="byId"]');
        if (!field || !byId) {
            return;
        }
        field.addEventListener('input', function () {
            if (field.value.trim() !== '') {
                byId.checked = true;
            }
        });
    }

    // "Usuń wpłatę" posts its own form (the other payments); with JavaScript it asks first, in the page's confirm dialog
    function initPaymentRemoval() {
        document.querySelectorAll('[data-cl-remove-payment]').forEach(function (button) {
            button.addEventListener('click', function (event) {
                event.preventDefault();
                var target = document.getElementById(button.getAttribute('form'));
                if (!target || typeof window.CL_confirmBulk !== 'function') {
                    return;
                }
                window.CL_confirmBulk(button, '1', function () {
                    target.submit();
                }, 'data-cl-select-confirm-title', 'data-cl-select-confirm-message', 'data-cl-select-confirm-action');
            });
        });
    }

    function init() {
        initToggles();
        initDockedBar();
        initValidation();
        initDialogs();
        initEnter();
        initShipment();
        initQuantity();
        initInvoice();
        initPaymentRemoval();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
