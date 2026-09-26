// The "register payment" dialog (fragments/add-payment-modal.html) on the order details, Payments and Deliveries.
// Those pages call the public functions below from their buttons; the order page opens it with data-cl-dialog-open
// (dialog.js fires cl:dialog-open). The mode picks the fee checkboxes: "order" (autofill the difference, the amount
// includes the fee) or "delivery" (the surplus is the fee) and the default direction. The hint under the amounts says
// what will be booked and whether it is a full, short or over payment; its words come from the dialog's data-hint-*.
// Every amount goes through window.CL_formatMoney (money.js), which already carries the currency unit, so neither
// this script nor the message keys hard-code "PLN"/"zł".
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
    var TEXT_FIELDS = ['name', 'referenceNo', 'bankTransactionNo', 'bankTransactionDate'];

    function words(name, values) {
        var text = dialog.getAttribute('data-hint' + name) || '';
        values.forEach(function (value, index) {
            text = text.replace('{' + index + '}', value);
        });
        return text;
    }

    function toggleAddPaymentModal(show) {
        if (show && !dialog.open) {
            dialog.showModal();
            bankEl.focus();
        } else if (!show && dialog.open) {
            dialog.close();
        }
    }

    // Deliveries keep their Bulma edit modal (fragments/payments-section.html), opened through this public function.
    function togglePaymentsEditModal(show) {
        var modal = document.getElementById('paymentsEditModal');
        if (modal) {
            modal.classList.toggle('is-active', show);
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
    }

    function isDeliveryMode() {
        return dialog.getAttribute('data-mode') === 'delivery';
    }

    function expected() {
        return parseFloat(expectedEl.getAttribute('data-value') || '0') || 0;
    }

    function applyFeeAutofill() {
        feeEl.value = Math.max(0, expected() - (parseFloat(bankEl.value) || 0)).toFixed(2);
    }

    function applyFeeFromSurplus() {
        feeEl.value = Math.max(0, (parseFloat(bankEl.value) || 0) - expected()).toFixed(2);
    }

    function renderHint() {
        var bank = parseFloat(bankEl.value) || 0;
        var fee = parseFloat(feeEl.value) || 0;
        if (bank <= 0) {
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

    function openAddPaymentModalFromButton(btn) {
        setPaymentModalMode(btn.dataset.mode || 'order');
        renderHint();
        toggleAddPaymentModal(true);
    }

    function openPaymentModalForOrder(orderId, expectedAmount, pending) {
        setPaymentModalMode('order');
        reset('/dashboard/orders/' + encodeURIComponent(orderId) + '/addPayment', expectedAmount, pending);
        toggleAddPaymentModal(true);
    }

    function openPaymentModalForOrderFromButton(btn) {
        openPaymentModalForOrder(btn.dataset.orderId, btn.dataset.unpaid, pendingFrom(btn));
    }

    function openPaymentModalForDelivery(deliveryId, expectedAmount, pending) {
        setPaymentModalMode('delivery');
        reset('/dashboard/deliveries/' + encodeURIComponent(deliveryId) + '/addPayment?redirectToPayments=true',
            expectedAmount, pending);
        toggleAddPaymentModal(true);
    }

    function openPaymentModalForDeliveryFromButton(btn) {
        openPaymentModalForDelivery(btn.dataset.deliveryId, btn.dataset.unpaid, pendingFrom(btn));
    }

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
    if (surplusEl) {
        surplusEl.addEventListener('change', function () {
            if (surplusEl.checked) {
                autofillEl.checked = false;
                applyFeeFromSurplus();
            }
            renderHint();
        });
    }

    // the order page: the server rendered the action, the unpaid amount and the pending payment already
    dialog.addEventListener('cl:dialog-open', function (event) {
        var trigger = event.detail && event.detail.trigger;
        setPaymentModalMode(trigger && trigger.dataset.mode ? trigger.dataset.mode : 'order');
        renderHint();
    });
    // Payments and Deliveries do not load dialog.js: Cancel and a click on the backdrop close the dialog here
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
    window.togglePaymentsEditModal = togglePaymentsEditModal;
    window.openAddPaymentModalFromButton = openAddPaymentModalFromButton;
    window.openPaymentModalForOrder = openPaymentModalForOrder;
    window.openPaymentModalForOrderFromButton = openPaymentModalForOrderFromButton;
    window.openPaymentModalForDelivery = openPaymentModalForDelivery;
    window.openPaymentModalForDeliveryFromButton = openPaymentModalForDeliveryFromButton;
})();
