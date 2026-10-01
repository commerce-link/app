// The dropship delivery form (dropshipCreate.html): ticking an order line sets the product's requested quantity (the
// hidden field the server reads and the number shown beside it), the net value of the products follows the quantities,
// unit costs and currency, and "Zamów u dostawcy" is off while nothing is requested or the server blocked it.
(function () {
    'use strict';

    // Same number format as money.js (NBSP thousands, comma decimals); the currency is the form's own, not always PLN.
    function formatAmount(value) {
        var parts = Math.abs(value).toFixed(2).split('.');
        parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        return (value < 0 ? '−' : '') + parts.join(',');
    }

    function syncRequestedQty(row) {
        var qty = 0;
        row.querySelectorAll('input[type="checkbox"][data-cl-allocation-qty]:checked').forEach(function (checkbox) {
            qty += parseInt(checkbox.getAttribute('data-cl-allocation-qty'), 10) || 0;
        });
        row.querySelector('input[data-cl-requested-qty]').value = qty;
        row.querySelector('[data-cl-requested-qty-label]').textContent = qty;
    }

    function syncTotals(form) {
        var total = 0;
        var totalQty = 0;
        form.querySelectorAll('[data-cl-dropship-item]').forEach(function (row) {
            var qty = parseInt(row.querySelector('input[data-cl-requested-qty]').value, 10) || 0;
            if (qty <= 0) {
                return;
            }
            var cost = row.querySelector('input[name$=".unitCost"]:not([type="hidden"])');
            totalQty += qty;
            total += qty * (cost ? parseFloat(cost.value) || 0 : 0);
        });
        document.getElementById('deliveryNetValue').textContent = formatAmount(total);
        var currency = form.querySelector('select[data-cl-dropship-currency]');
        document.getElementById('deliveryNetCurrency').textContent = currency.value;
        var purchase = document.getElementById('purchase-order-button');
        if (purchase) {
            purchase.disabled = totalQty === 0 || purchase.getAttribute('data-blocked') === 'true';
        }
    }

    function init() {
        var form = document.querySelector('form[data-cl-dropship-form]');
        if (!form) {
            return;
        }
        form.addEventListener('change', function (event) {
            var row = event.target.closest('[data-cl-dropship-item]');
            if (row && event.target.matches('input[type="checkbox"][data-cl-allocation-qty]')) {
                syncRequestedQty(row);
            }
            syncTotals(form);
        });
        form.addEventListener('input', function () {
            syncTotals(form);
        });
        syncTotals(form);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
