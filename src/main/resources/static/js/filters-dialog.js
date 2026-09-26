// Custom filters of the orders list. The "save this view" dialog posts its form with fetch (X-Requested-With: fetch)
// and follows the answer: a 200 carrying data-cl-redirect sends the page to the list with the new filter, a 422
// swaps the dialog body with the rejection and the user's input. Without JavaScript the same form posts and the page
// reloads. Managing filters is a page of its own (/dashboard/orders/filters), not a dialog. On the filter's
// new/edit subpage the postal-code field gets its "00-000" dash as the user types.
(function () {
    'use strict';

    function formatPostalCode(input, appendSeparator) {
        var digits = input.value.replace(/\D/g, '').slice(0, 5);
        input.value = digits.length > 2 ? digits.slice(0, 2) + '-' + digits.slice(2) : (digits.length === 2 && appendSeparator ? digits + '-' : digits);
    }

    document.addEventListener('input', function (event) {
        if (event.target.matches && event.target.matches('[data-cl-postal-code]')) {
            formatPostalCode(event.target, !(event.inputType || '').startsWith('delete'));
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest && event.target.closest('form[data-cl-filters-form]');
        if (!form || !window.fetch) { return; }
        var body = form.closest('[data-cl-dialog-body]');
        if (!body) { return; }
        event.preventDefault();
        fetch(form.action, {
            method: 'POST',
            headers: { 'X-Requested-With': 'fetch' },
            body: new URLSearchParams(new FormData(form)),
            credentials: 'same-origin',
            redirect: 'manual'
        }).then(function (response) {
            if (response.type === 'opaqueredirect' || (response.status >= 300 && response.status < 400)) {
                form.submit(); // session expired: the plain post shows the login page
                return null;
            }
            if (response.status !== 200 && response.status !== 422) {
                throw new Error('HTTP ' + response.status);
            }
            return response.text().then(function (html) { return { status: response.status, html: html }; });
        }).then(function (result) {
            if (!result) { return; }
            var fresh = new DOMParser().parseFromString(result.html, 'text/html').body.firstElementChild;
            var redirect = fresh && fresh.getAttribute('data-cl-redirect');
            if (result.status === 200 && redirect) {
                window.location.assign(redirect);
                return;
            }
            body.replaceChildren(fresh);
            var error = body.querySelector('[data-cl-error-summary]');
            if (error) { error.focus(); }
        }).catch(function () {
            if (typeof showToast === 'function') { showToast(document.body.getAttribute('data-cl-server-error'), 'danger'); }
        });
    });
})();
