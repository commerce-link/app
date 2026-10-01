// Step 2 outside the system (deliveries/create/manual.html): the net value follows the currency the operator picks
// (the item costs of step 1 are in that currency), the save button cannot be pressed twice, and the header's back
// button submits the form's own "Wróć do pozycji" so Enter keeps saving.
(function () {
    'use strict';

    function formatAmount(value) {
        var parts = Math.abs(value).toFixed(2).split('.');
        parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        return (value < 0 ? '−' : '') + parts.join(',');
    }

    function syncTotal(form) {
        var total = 0;
        form.querySelectorAll('tr[data-cl-readonly-item]').forEach(function (row) {
            total += (parseInt(row.getAttribute('data-qty'), 10) || 0) * (parseFloat(row.getAttribute('data-unit-cost')) || 0);
        });
        form.querySelector('[data-cl-total-net]').textContent = formatAmount(total);
        form.querySelector('[data-cl-total-currency]').textContent = form.querySelector('select[data-cl-currency]').value;
    }

    function init() {
        var form = document.querySelector('form[data-cl-delivery-manual]');
        if (!form) {
            return;
        }
        form.querySelector('select[data-cl-currency]').addEventListener('change', function () {
            syncTotal(form);
        });
        document.addEventListener('click', function (event) {
            var back = event.target.closest && event.target.closest('[data-cl-back-submit]');
            if (back) {
                document.getElementById(back.getAttribute('data-cl-back-submit')).click();
            }
        });
        form.addEventListener('submit', function (event) {
            if (event.submitter && event.submitter.id === 'save-button') {
                event.submitter.disabled = true;
            }
        });
        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                document.getElementById('save-button').disabled = false;
            }
        });
        syncTotal(form);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
