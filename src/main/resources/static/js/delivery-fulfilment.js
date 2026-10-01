// The edit-product dialog of step 1 (deliveries/create/parts.html :: fulfilmentDialog). Opening it from a row's pencil
// fills EAN, manufacturer code, cost and the sources it changes; saving posts with fetch (Accept: application/json)
// and updates the row in place, so nothing the operator typed elsewhere on the page is lost. A product whose new code
// equals another row's code would be merged by the server on the next load, so the page reloads in that case.
(function () {
    'use strict';

    function hidden(name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        return input;
    }

    function fill(dialog, trigger) {
        var form = dialog.querySelector('form');
        form.setAttribute('data-row', trigger.getAttribute('data-row'));
        form.elements.ean.value = trigger.getAttribute('data-ean') || '';
        form.elements.mfn.value = trigger.getAttribute('data-mfn') || '';
        form.elements.unitCost.value = trigger.getAttribute('data-unit-cost') || '';
        dialog.querySelector('#fulfilment-title').textContent =
            dialog.getAttribute('data-title').replace('{0}', trigger.getAttribute('data-name'));
        dialog.querySelector('[data-cl-fulfilment-effect]').textContent =
            dialog.getAttribute('data-effect').replace('{0}', trigger.getAttribute('data-source-count'));
        dialog.querySelector('[data-cl-fulfilment-error]').hidden = true;
        var refs = dialog.querySelector('[data-cl-fulfilment-refs]');
        refs.replaceChildren();
        (trigger.getAttribute('data-allocations') || '').split(';').filter(Boolean).forEach(function (pair, index) {
            var at = pair.lastIndexOf('|');
            refs.appendChild(hidden('allocations[' + index + '].orderId', pair.substring(0, at)));
            refs.appendChild(hidden('allocations[' + index + '].itemId', pair.substring(at + 1)));
        });
        (trigger.getAttribute('data-warehouse-items') || '').split(';').filter(Boolean).forEach(function (itemId) {
            refs.appendChild(hidden('warehouseItemIds', itemId));
        });
    }

    function applyToRow(index, answer, dialog) {
        var trigger = document.querySelector('.cl-table-edit[data-row="' + index + '"]');
        var row = trigger.closest('tr');
        var tbody = row.closest('tbody');
        var duplicate = Array.prototype.some.call(document.querySelectorAll('.cl-table-edit'), function (other) {
            return other !== trigger && other.getAttribute('data-mfn') === answer.mfn;
        });
        if (duplicate) {
            window.location.reload();
            return;
        }
        row.setAttribute('data-mfn', answer.mfn);
        row.querySelector('[data-cl-item-ean]').textContent = answer.ean;
        row.querySelector('[data-cl-item-mfn]').textContent = answer.mfn;
        row.querySelector('[data-cl-item-ean-input]').value = answer.ean;
        row.querySelector('[data-cl-item-mfn-input]').value = answer.mfn;
        tbody.querySelectorAll('input[name$=".ean"]').forEach(function (input) { input.value = answer.ean; });
        tbody.querySelectorAll('input[name$=".mfn"]').forEach(function (input) { input.value = answer.mfn; });
        tbody.querySelectorAll('input[type="hidden"][name$=".unitCost"]').forEach(function (input) {
            input.value = answer.unitCost;
        });
        var cost = row.querySelector('input[data-cl-unit-cost]');
        var untouched = cost.value === trigger.getAttribute('data-unit-cost');
        if (untouched) {
            cost.value = answer.unitCost;
            cost.dispatchEvent(new Event('input', {bubbles: true}));
        }
        trigger.setAttribute('data-ean', answer.ean);
        trigger.setAttribute('data-mfn', answer.mfn);
        trigger.setAttribute('data-unit-cost', answer.unitCost);
        if (typeof window.showToast === 'function') {
            window.showToast(untouched ? answer.message
                : dialog.getAttribute('data-cost-kept').replace('{0}', answer.unitCost), 'info');
        }
    }

    function init() {
        var dialog = document.getElementById('fulfilment-dialog');
        if (!dialog) {
            return;
        }
        var form = dialog.querySelector('form');
        dialog.addEventListener('cl:dialog-open', function (event) {
            fill(dialog, event.detail.trigger);
        });
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var error = dialog.querySelector('[data-cl-fulfilment-error]');
            var submit = form.querySelector('button[type="submit"]');
            submit.disabled = true;
            fetch(dialog.getAttribute('data-url'), {
                method: 'POST',
                headers: {'Accept': 'application/json'},
                body: new URLSearchParams(new FormData(form))
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status);
                    }
                    return response.json();
                })
                .then(function (answer) {
                    if (!answer.ok) {
                        error.querySelector('.cl-alert-text').textContent = answer.message;
                        error.hidden = false;
                        return;
                    }
                    dialog.close();
                    applyToRow(form.getAttribute('data-row'), answer, dialog);
                })
                .catch(function () {
                    error.querySelector('.cl-alert-text').textContent = dialog.getAttribute('data-failed');
                    error.hidden = false;
                })
                .then(function () {
                    submit.disabled = false;
                });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
