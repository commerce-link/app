// "Podziel" of a pending payment (orders/details/payments.html :: splitDialog). "Dodaj część" copies the first part with
// its fields emptied, "Usuń" takes a part away while another is left, and the last part's amount is what the parts leave
// of the payment (data-expected), counted as the amounts are typed; with nothing left for it the save is held back and
// data-text-over says why. Amounts are read like add-payment-dialog.js reads them, so "1 499,99" counts.
(function () {
    'use strict';

    function amountOf(input) {
        var typed = (input.value || '').replace(/[\s  ]/g, '').replace(',', '.');
        var value = Number(typed);
        return typed === '' || isNaN(value) ? 0 : value;
    }

    function money(value) {
        return window.CL_formatMoney ? window.CL_formatMoney(value) : value.toFixed(2);
    }

    function setUp(dialog) {
        var parts = dialog.querySelector('[data-cl-split-parts]');
        var rest = dialog.querySelector('[data-cl-split-rest]');
        var left = dialog.querySelector('[data-cl-split-left]');
        var message = dialog.querySelector('[data-cl-split-message]');
        var add = dialog.querySelector('[data-cl-split-add]');
        var save = dialog.querySelector('button[type="submit"]');
        var help = message.textContent;
        var expected = parseFloat(dialog.getAttribute('data-expected')) || 0;

        function rows() {
            return Array.prototype.slice.call(parts.querySelectorAll('[data-cl-split-part]'));
        }

        // each part's fields keep ids of their own, so every label still names its field
        function renumber() {
            var all = rows();
            all.forEach(function (row, index) {
                row.querySelectorAll('select, input').forEach(function (field) {
                    var id = field.id.replace(/-\d+$/, '-' + (index + 1));
                    var label = row.querySelector('label[for="' + field.id + '"]');
                    field.id = id;
                    if (label) {
                        label.setAttribute('for', id);
                    }
                });
                row.querySelector('[data-cl-split-remove]').hidden = all.length < 2;
            });
        }

        function count() {
            var sum = rows().reduce(function (total, row) {
                return total + amountOf(row.querySelector('input[name="amounts"]'));
            }, 0);
            var remainder = Math.round((expected - sum) * 100) / 100;
            left.textContent = remainder > 0 ? money(remainder) : '—';
            message.textContent = remainder > 0 ? help : dialog.getAttribute('data-text-over');
            message.classList.toggle('is-bad', remainder <= 0);
            save.disabled = remainder <= 0;
        }

        add.hidden = false;
        add.addEventListener('click', function () {
            var copy = rows()[0].cloneNode(true);
            copy.querySelector('input[name="amounts"]').value = '';
            copy.querySelector('select[name="methods"]').value = 'BankTransfer';
            parts.insertBefore(copy, rest);
            renumber();
            count();
            copy.querySelector('input[name="amounts"]').focus();
        });
        parts.addEventListener('click', function (event) {
            var remove = event.target.closest('[data-cl-split-remove]');
            if (remove && rows().length > 1) {
                remove.closest('[data-cl-split-part]').remove();
                renumber();
                count();
            }
        });
        parts.addEventListener('input', count);
        renumber();
        count();
    }

    document.querySelectorAll('[data-cl-payment-split]').forEach(setUp);
})();
