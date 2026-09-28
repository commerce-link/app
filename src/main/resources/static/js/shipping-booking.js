// Courier booking page (shipping.html): when the record has several possible recipients (the service centres of a
// distributor), choosing one copies its details into the hidden fields posted with "Load parcels" and into the address
// shown above them. Only the first form is updated, as before: the later steps carry the recipient they were
// rendered with, so the operator loads the parcels again after changing the recipient.
(function () {
    'use strict';

    function apply(form, option) {
        form.querySelectorAll('[data-cl-recipient-field]').forEach(function (input) {
            var key = input.getAttribute('data-cl-recipient-field');
            input.value = option.getAttribute('data-' + key) || '';
        });
        form.querySelectorAll('[data-cl-recipient-view]').forEach(function (view) {
            var key = view.getAttribute('data-cl-recipient-view');
            view.textContent = option.getAttribute('data-' + key) || '';
        });
    }

    document.querySelectorAll('select[data-cl-recipient-select]').forEach(function (select) {
        select.addEventListener('change', function () {
            var option = select.options[select.selectedIndex];
            if (option && select.form) {
                apply(select.form, option);
            }
        });
    });
})();
