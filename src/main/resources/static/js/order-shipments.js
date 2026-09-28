// The shipments dialog: repeat-fields.js adds, removes and renumbers the shipment groups (no blank group is added on
// its own, it would post an empty shipment; the last group stays, the server keeps at least one shipment). This script
// only handles the carrier: it is chosen from the store's carriers or typed after "Other…", and the select only fills
// the named text field, so the text field alone is what gets posted.
(function () {
    'use strict';

    var form = document.querySelector('form[data-cl-shipments-form]');
    if (!form) {
        return;
    }

    function syncCarrier(group) {
        var select = group.querySelector('select[data-cl-carrier-select]');
        var input = group.querySelector('input[data-cl-carrier-input]');
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

    form.addEventListener('change', function (event) {
        if (event.target.matches('select[data-cl-carrier-select]')) {
            var group = event.target.closest('[data-cl-repeat-item]');
            syncCarrier(group);
            if (event.target.value === '__other__') {
                var input = group.querySelector('input[data-cl-carrier-input]');
                input.value = '';
                input.focus();
            }
        }
    });

    form.addEventListener('cl:repeat-added', function (event) {
        syncCarrier(event.target);
    });

    form.querySelectorAll('[data-cl-repeat-item]').forEach(syncCarrier);
})();
