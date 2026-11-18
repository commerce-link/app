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
// for it. An order's dialog first asks which pending payment the money is for: the opening button's data-pendings
// (PendingPaymentsJson) lists them, the chosen one's amount becomes the expected amount and its fields are filled in,
// and "Inna wpłata" (target "new") is money the order does not wait for.
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
    var targetsEl = document.getElementById('addPaymentTargets');
    var targetListEl = document.getElementById('addPaymentTargetList');
    var targetState = { pendings: null, chosen: null };
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

    function textOf(name, values) {
        var text = dialog.getAttribute('data-text-' + name) || '';
        (values || []).forEach(function (value, index) {
            text = text.replace('{' + index + '}', value);
        });
        return text;
    }

    function methodLabel(source) {
        var option = source ? sourceEl.querySelector('option[value="' + source + '"]') : null;
        return option ? option.textContent : '';
    }

    function setExpected(value) {
        expectedEl.setAttribute('data-value', value === null ? '' : value);
        expectedEl.value = value === null ? '—' : value.toFixed(2);
    }

    function choiceOf(value, title, status, description) {
        var label = document.createElement('label');
        label.className = 'cl-choice';
        var input = document.createElement('input');
        input.type = 'radio';
        input.name = 'target';
        input.value = value;
        var body = document.createElement('span');
        body.className = 'cl-choice-text';
        var head = document.createElement('span');
        head.className = 'cl-choice-title';
        head.textContent = title;
        if (status) {
            var pill = document.createElement('span');
            pill.className = 'cl-status is-neutral';
            pill.textContent = status;
            head.appendChild(pill);
        }
        var sub = document.createElement('span');
        sub.className = 'cl-choice-description';
        sub.textContent = description;
        body.appendChild(head);
        body.appendChild(sub);
        label.appendChild(input);
        label.appendChild(body);
        return label;
    }

    // the payment the money is for: its amount is what is expected, its method and fields are filled in
    function choose(target) {
        targetState.chosen = target;
        var value = target === 'new' ? 'new' : String(target.index);
        targetListEl.querySelectorAll('input[name="target"]').forEach(function (input) {
            input.checked = input.value === value;
        });
        if (target === 'new') {
            setExpected(null);
        } else {
            setExpected(parseFloat(target.expected) || 0);
            if (target.source) {
                sourceEl.value = target.source;
            }
            form.querySelector('input[name="name"]').value = target.name || '';
            form.querySelector('input[name="referenceNo"]').value = target.referenceNo || '';
            var fee = parseFloat(target.fee) || 0;
            feeEl.value = fee > 0 ? fee.toFixed(2) : '0';
        }
        if (autofillEl.checked) {
            applyFeeAutofill();
        }
        renderHint();
    }

    // data-pendings of the opening button; without it (a delivery, an older page) the dialog asks nothing
    function buildTargets(json) {
        targetListEl.textContent = '';
        var pendings = null;
        try {
            pendings = json ? JSON.parse(json) : null;
        } catch (e) {
            pendings = null;
        }
        targetState.pendings = isDeliveryMode() ? null : pendings;
        targetState.chosen = null;
        if (!targetState.pendings) {
            targetsEl.hidden = true;
            return;
        }
        targetsEl.hidden = false;
        targetState.pendings.forEach(function (pending) {
            targetListEl.appendChild(choiceOf(String(pending.index), methodLabel(pending.source) || '—',
                pending.rest ? textOf('rest') : null,
                textOf('expected', [window.CL_formatMoney(parseFloat(pending.expected) || 0)])));
        });
        targetListEl.appendChild(choiceOf('new', textOf('new'), null, textOf('new-description')));
        choose(targetState.pendings.length ? targetState.pendings[0] : 'new');
    }

    function waitingText() {
        var chosen = targetState.chosen;
        return !isDeliveryMode() && chosen && chosen !== 'new' ? words('-waiting', [methodLabel(chosen.source)]) : '';
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
        var state = !isDeliveryMode() && targetState.chosen === 'new' ? words('-new', [])
            : Math.abs(diff) < 0.005 ? words('-full', [])
            : diff > 0 ? words('-under', [window.CL_formatMoney(diff)]) + waitingText()
            : words('-over', [window.CL_formatMoney(-diff)]);
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
        buildTargets(btn.dataset.direction === 'Outgoing' ? null : btn.dataset.pendings);
    }

    function openPaymentModalForDelivery(deliveryId, expectedAmount, pending) {
        setPaymentModalMode('delivery');
        reset('/dashboard/deliveries/' + encodeURIComponent(deliveryId) + '/addPayment', expectedAmount, pending);
        toggleAddPaymentModal(true);
    }

    function openPaymentModalForDeliveryFromButton(btn) {
        openPaymentModalForDelivery(btn.dataset.deliveryId, btn.dataset.unpaid, pendingFrom(btn));
        applyTriggerOptions(btn);
        buildTargets(null);
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
    targetListEl.addEventListener('change', function (event) {
        var value = event.target.value;
        var pending = (targetState.pendings || []).filter(function (candidate) {
            return String(candidate.index) === value;
        })[0];
        choose(pending || 'new');
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
        buildTargets(trigger ? trigger.dataset.pendings : null);
        renderHint();
    });
    // Payments does not load dialog.js: Cancel and a click on the backdrop close the dialog here
    dialog.querySelector('[data-cl-payment-close]').addEventListener('click', function () {
        dialog.close();
    });
    // A click is sent to the nearest element holding both ends of the press, so pressing inside the dialog (e.g. to
    // select text) and letting go over the backdrop — or the other way round — clicks the dialog element itself, just
    // like a click on the backdrop does. The backdrop closes the dialog only when the press both started and ended on it.
    var pressStart = null;
    var pressEnd = null;
    dialog.addEventListener('pointerdown', function (event) {
        pressStart = event.target;
    });
    dialog.addEventListener('pointerup', function (event) {
        pressEnd = event.target;
    });
    dialog.addEventListener('click', function (event) {
        var onBackdrop = event.target === dialog && pressStart === dialog && pressEnd === dialog;
        pressStart = null;
        pressEnd = null;
        if (onBackdrop) {
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
