// The shipments dialog: no blank row is added on its own (P25); "Add shipment" clones the <template> row, "Delete" removes
// a row (the last one stays, the server keeps at least one shipment), and the rows are renumbered shipments[0..n] so
// Spring binds them without gaps. The carrier is chosen from the store's carriers or typed after "Other…": the select
// only fills the named text field, so without JavaScript the text field alone is posted.
(function () {
    'use strict';

    var form = document.querySelector('form[data-cl-shipments-form]');
    if (!form) {
        return;
    }
    var body = form.querySelector('[data-cl-shipment-rows]');
    var template = form.querySelector('template[data-cl-shipment-template]');

    function renumber() {
        Array.prototype.forEach.call(body.rows, function (row, index) {
            row.querySelectorAll('[name^="shipments["]').forEach(function (field) {
                field.name = field.name.replace(/shipments\[\d+]/, 'shipments[' + index + ']');
            });
            row.querySelector('[data-cl-shipment-remove]').hidden = body.rows.length <= 1;
        });
    }

    function syncCarrier(row) {
        var select = row.querySelector('select[data-cl-carrier-select]');
        var input = row.querySelector('input[data-cl-carrier-input]');
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

    body.addEventListener('change', function (event) {
        if (event.target.matches('select[data-cl-carrier-select]')) {
            var row = event.target.closest('tr');
            syncCarrier(row);
            if (event.target.value === '__other__') {
                var input = row.querySelector('input[data-cl-carrier-input]');
                input.value = '';
                input.focus();
            }
        }
    });

    body.addEventListener('click', function (event) {
        var remove = event.target.closest && event.target.closest('[data-cl-shipment-remove]');
        if (remove && body.rows.length > 1) {
            var row = remove.closest('tr');
            var next = row.nextElementSibling || row.previousElementSibling;
            row.remove();
            renumber();
            next.querySelector('select, input').focus();
        }
    });

    form.querySelector('[data-cl-shipment-add]').addEventListener('click', function () {
        var row = template.content.firstElementChild.cloneNode(true);
        body.appendChild(row);
        renumber();
        syncCarrier(row);
        row.querySelector('select').focus();
    });

    Array.prototype.forEach.call(body.rows, syncCarrier);
    renumber();
})();
