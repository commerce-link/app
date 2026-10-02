// A POST form with data-cl-submit-once sends once: a second click while the first request is on its way is ignored
// and the button says it is busy (e.g. "Pobierz wpłaty z systemu fakturowego", which asks the invoicing system about
// every invoice before it answers). The button is disabled after the submit event, so its own value is still sent.
(function () {
    'use strict';

    document.addEventListener('submit', function (event) {
        var form = event.target.closest('form[data-cl-submit-once]');
        if (!form) {
            return;
        }
        if (form.getAttribute('aria-busy') === 'true') {
            event.preventDefault();
            return;
        }
        form.setAttribute('aria-busy', 'true');
        var button = event.submitter || form.querySelector('[type="submit"]');
        if (button) {
            setTimeout(function () {
                button.disabled = true;
            }, 0);
        }
    });

    // Back from the next page may restore this one from the cache with the button still disabled.
    window.addEventListener('pageshow', function (event) {
        if (!event.persisted) {
            return;
        }
        document.querySelectorAll('form[data-cl-submit-once][aria-busy="true"]').forEach(function (form) {
            form.removeAttribute('aria-busy');
            form.querySelectorAll('[type="submit"]').forEach(function (button) {
                button.disabled = false;
            });
        });
    });
})();
