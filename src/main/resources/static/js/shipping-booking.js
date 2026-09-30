// Courier booking page (shipping.html): when the record has several possible recipients (the service centres of a
// distributor), choosing one copies its details into the hidden fields posted with "Load parcels" and into the address
// shown above them. Only the first form is updated, as before: the later steps carry the recipient they were
// rendered with, so the operator loads the parcels again after changing the recipient.
// "Zamów kuriera" is disabled once its form is sent, so a double click books (and pays for) one label, not two; a page
// restored from the back/forward cache gets it back. The server refuses a second booking as well.
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

    var createForm = document.getElementById('shipping-create-form');
    if (createForm) {
        createForm.addEventListener('submit', function (event) {
            if (event.submitter && event.submitter.id === 'shipping-create-submit') {
                event.submitter.disabled = true;
            }
        });
        window.addEventListener('pageshow', function (event) {
            var submit = document.getElementById('shipping-create-submit');
            if (event.persisted && submit) {
                submit.disabled = false;
            }
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
