// The shipment dialogs: the carrier is chosen from the store's carriers or typed after "Other…". The select only fills
// the named text field, so the text field alone is posted (and, without JavaScript, the only one shown). A form that
// async-form.js replaced after a failed save (422) is synced again.
(function () {
    'use strict';

    function syncCarrier(form) {
        var select = form.querySelector('select[data-cl-carrier-select]');
        var input = form.querySelector('input[data-cl-carrier-input]');
        if (!select) {
            return;
        }
        select.hidden = false;
        var other = select.value === '__other__';
        input.hidden = !other;
        if (!other) {
            input.value = select.value;
        }
    }

    document.addEventListener('change', function (event) {
        if (!event.target.matches('form[data-cl-shipment-form] select[data-cl-carrier-select]')) {
            return;
        }
        var form = event.target.closest('form');
        syncCarrier(form);
        if (event.target.value === '__other__') {
            var input = form.querySelector('input[data-cl-carrier-input]');
            input.value = '';
            input.focus();
        }
    });

    document.addEventListener('cl:form-replaced', function (event) {
        var form = event.target.matches && event.target.matches('form[data-cl-shipment-form]')
            ? event.target : event.target.querySelector && event.target.querySelector('form[data-cl-shipment-form]');
        if (form) {
            syncCarrier(form);
        }
    });

    document.querySelectorAll('form[data-cl-shipment-form]').forEach(syncCarrier);
})();
