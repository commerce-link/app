// "Edit payments" on the order page: Delete hides the row and empties what makes a payment complete (amount, fee,
// reference — Payment.isComplete), so the server drops it; the row itself is still posted, because Spring binds
// payments[i] by index and a gap would bind an empty payment. The amount is sent as 0, not blank: a blank text in a
// number field fails to bind. If every row is deleted, the server keeps one placeholder with its method, as before.
(function () {
    'use strict';

    var form = document.querySelector('form[data-cl-payments-form]');
    if (!form) {
        return;
    }
    form.addEventListener('click', function (event) {
        var remove = event.target.closest && event.target.closest('[data-cl-payment-remove]');
        if (!remove) {
            return;
        }
        var row = remove.closest('tr');
        row.querySelector('[name$=".amount"]').value = '0';
        row.querySelector('[name$=".fee"]').value = '0';
        row.querySelector('[name$=".referenceNo"]').value = '';
        row.hidden = true;
        var next = row.nextElementSibling;
        while (next && next.hidden) {
            next = next.nextElementSibling;
        }
        (next ? next.querySelector('select, input:not([type="hidden"])') : form.querySelector('[type="submit"]')).focus();
    });
})();
