// The "register payment" dialog (fragments/add-payment-modal.html) on the order details, Payments and the delivery
// details. Payments opens it from a[data-cl-payment-open] links (delegated click below); the order and delivery details
// open it with data-cl-dialog-open
// (dialog.js fires cl:dialog-open). The mode picks the fee checkboxes: "order" (autofill the difference, the amount
// includes the fee) or "delivery" (the surplus is the fee) and the default direction. The hint under the amounts says
// what will be booked and whether it is a full, short or over payment; its words come from the dialog's data-hint-*.
// Every amount goes through window.CL_formatMoney (money.js), which takes the unit from general.currency.amount, so
// neither this script nor the order.payment.hint.* keys spell the currency. The amount fields are text (the server
// reads them with AmountParser), so amountOf reads "149,99" and "1 499,99" here the same way. An order's refund is
// typed without a sign and the server stores it negative: the hint, which speaks of money that came in, stays empty
// for it.
(function () {
    'use strict';

    var dialog = document.getElementById('addPaymentModal');
    if (!dialog) {
        return;
    }
    var form = dialog.querySelector('form');
    var expectedEl = document.getElementById('addPaymentExpected');
    var bankEl = document.getElementById('addPaymentBankAmount');
    var feeEl = document.getElementById('addPaymentProcessingFee');
    var autofillEl = document.getElementById('addPaymentFeeAutofill');
    var includedEl = document.getElementById('addPaymentFeeIncluded');
    var surplusEl = document.getElementById('addPaymentFeeFromSurplus');
    var directionEl = document.getElementById('addPaymentDirection');
    var sourceEl = document.getElementById('addPaymentSource');
    var hintEl = document.getElementById('addPaymentComputed');
    var directionHelpEl = document.getElementById('addPaymentDirectionHelp');
    var TEXT_FIELDS = ['name', 'referenceNo', 'bankTransactionNo', 'bankTransactionDate'];

    function words(name, values) {
        var text = dialog.getAttribute('data-hint' + name) || '';
        values.forEach(function (value, index) {
            text = text.replace('{' + index + '}', value);
        });
        return text;
    }

    function amountOf(field) {
        var typed = (field.value || '').replace(/[\s\u00a0\u202f]/g, '').replace(',', '.');
        var value = Number(typed);
        return typed === '' || isNaN(value) ? 0 : value;
    }

    function toggleAddPaymentModal(show) {
        if (show && !dialog.open) {
            dialog.showModal();
            bankEl.focus();
        } else if (!show && dialog.open) {
            dialog.close();
        }
    }

    function setPaymentModalMode(mode) {
        dialog.setAttribute('data-mode', mode);
        dialog.querySelectorAll('[data-payment-mode]').forEach(function (field) {
            field.hidden = field.getAttribute('data-payment-mode') !== mode;
        });
        if (surplusEl) {
            surplusEl.checked = false;
        }
        directionEl.value = mode === 'delivery' ? 'Outgoing' : 'Incoming';
        if (directionHelpEl) {
            directionHelpEl.textContent = directionHelpEl.getAttribute('data-help-' + mode) || directionHelpEl.textContent;
        }
    }

    function isDeliveryMode() {
        return dialog.getAttribute('data-mode') === 'delivery';
    }

    function expected() {
        return parseFloat(expectedEl.getAttribute('data-value') || '0') || 0;
    }

    function applyFeeAutofill() {
        feeEl.value = Math.max(0, expected() - amountOf(bankEl)).toFixed(2);
    }

    function applyFeeFromSurplus() {
        feeEl.value = Math.max(0, amountOf(bankEl) - expected()).toFixed(2);
    }

    function renderHint() {
        var bank = amountOf(bankEl);
        var fee = amountOf(feeEl);
        if (bank <= 0 || (!isDeliveryMode() && directionEl.value === 'Outgoing')) {
            hintEl.textContent = '';
            return;
        }
        var booked;
        var applied;
        if (isDeliveryMode()) {
            booked = bank;
            applied = booked - fee;
        } else {
            booked = includedEl.checked ? bank : bank + fee;
            applied = booked;
        }
        var diff = expected() - applied;
        var state = Math.abs(diff) < 0.005 ? words('-full', [])
            : diff > 0 ? words('-under', [window.CL_formatMoney(diff)]) : words('-over', [window.CL_formatMoney(-diff)]);
        hintEl.textContent = words('', [window.CL_formatMoney(booked), window.CL_formatMoney(fee), state]);
    }

    function reset(action, expectedAmount, pending) {
        if (action) {
            form.action = action;
        }
        var value = parseFloat(expectedAmount) || 0;
        expectedEl.setAttribute('data-value', value);
        expectedEl.value = value.toFixed(2);
        bankEl.value = '';
        feeEl.value = '0';
        autofillEl.checked = false;
        includedEl.checked = false;
        if (surplusEl) {
            surplusEl.checked = false;
        }
        TEXT_FIELDS.forEach(function (name) {
            form.querySelector('input[name="' + name + '"]').value = '';
        });
        sourceEl.selectedIndex = 0;
        if (pending) {
            if (pending.source) {
                sourceEl.value = pending.source;
            }
            TEXT_FIELDS.forEach(function (name) {
                if (pending[name]) {
                    form.querySelector('input[name="' + name + '"]').value = pending[name];
                }
            });
            var pendingFee = parseFloat(pending.fee) || 0;
            if (pendingFee > 0) {
                feeEl.value = pendingFee.toFixed(2);
            }
        }
        renderHint();
    }

    function pendingFrom(btn) {
        return {
            source: btn.dataset.pendingSource || '',
            name: btn.dataset.pendingName || '',
            referenceNo: btn.dataset.pendingRef || '',
            fee: btn.dataset.pendingFee || 0,
            bankTransactionNo: btn.dataset.pendingBankTrxNo || '',
            bankTransactionDate: btn.dataset.pendingBankTrxDate || ''
        };
    }

    function openPaymentModalForOrder(orderId, expectedAmount, pending) {
        setPaymentModalMode('order');
        reset('/dashboard/orders/' + encodeURIComponent(orderId) + '/addPayment', expectedAmount, pending);
        toggleAddPaymentModal(true);
    }

    // The Payments page sends the operator back to itself, with its filters, after the dialog is saved.
    function applyTriggerOptions(btn) {
        var field = form.querySelector('input[name="returnTo"]');
        if (btn.dataset.returnTo) {
            if (!field) {
                field = document.createElement('input');
                field.type = 'hidden';
                field.name = 'returnTo';
                form.appendChild(field);
            }
            field.value = btn.dataset.returnTo;
        } else if (field) {
            field.remove();
        }
        if (btn.dataset.direction) {
            directionEl.value = btn.dataset.direction;
            renderHint();
        }
    }

    function openPaymentModalForOrderFromButton(btn) {
        openPaymentModalForOrder(btn.dataset.orderId, btn.dataset.unpaid, pendingFrom(btn));
        applyTriggerOptions(btn);
    }

    function openPaymentModalForDelivery(deliveryId, expectedAmount, pending) {
        setPaymentModalMode('delivery');
        reset('/dashboard/deliveries/' + encodeURIComponent(deliveryId) + '/addPayment', expectedAmount, pending);
        toggleAddPaymentModal(true);
    }

    function openPaymentModalForDeliveryFromButton(btn) {
        openPaymentModalForDelivery(btn.dataset.deliveryId, btn.dataset.unpaid, pendingFrom(btn));
        applyTriggerOptions(btn);
    }

    // Delegated, so it also works in the results block list-page.js swaps; without JS the link opens the record.
    document.addEventListener('click', function (event) {
        var trigger = event.target.closest('[data-cl-payment-open]');
        if (!trigger) {
            return;
        }
        event.preventDefault();
        if (trigger.dataset.orderId) {
            openPaymentModalForOrderFromButton(trigger);
        } else {
            openPaymentModalForDeliveryFromButton(trigger);
        }
    });

    bankEl.addEventListener('input', function () {
        if (autofillEl.checked) {
            applyFeeAutofill();
        }
        if (surplusEl && surplusEl.checked) {
            applyFeeFromSurplus();
        }
        renderHint();
    });
    feeEl.addEventListener('input', function () {
        autofillEl.checked = false;
        if (surplusEl) {
            surplusEl.checked = false;
        }
        renderHint();
    });
    autofillEl.addEventListener('change', function () {
        if (autofillEl.checked) {
            if (surplusEl) {
                surplusEl.checked = false;
            }
            applyFeeAutofill();
        }
        renderHint();
    });
    includedEl.addEventListener('change', renderHint);
    directionEl.addEventListener('change', renderHint);
    if (surplusEl) {
        surplusEl.addEventListener('change', function () {
            if (surplusEl.checked) {
                autofillEl.checked = false;
                applyFeeFromSurplus();
            }
            renderHint();
        });
    }

    // the order and delivery details: the server rendered the action, the unpaid amount and the pending payment already
    dialog.addEventListener('cl:dialog-open', function (event) {
        var trigger = event.detail && event.detail.trigger;
        setPaymentModalMode(trigger && trigger.dataset.mode ? trigger.dataset.mode : 'order');
        renderHint();
    });
    // Payments does not load dialog.js: Cancel and a click on the backdrop close the dialog here
    dialog.querySelector('[data-cl-payment-close]').addEventListener('click', function () {
        dialog.close();
    });
    dialog.addEventListener('click', function (event) {
        if (event.target === dialog) {
            dialog.close();
        }
    });

    setPaymentModalMode(dialog.getAttribute('data-mode') || 'order');

    window.toggleAddPaymentModal = toggleAddPaymentModal;
    window.openPaymentModalForOrder = openPaymentModalForOrder;
    window.openPaymentModalForOrderFromButton = openPaymentModalForOrderFromButton;
    window.openPaymentModalForDelivery = openPaymentModalForDelivery;
    window.openPaymentModalForDeliveryFromButton = openPaymentModalForDeliveryFromButton;
})();
