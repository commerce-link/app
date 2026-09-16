// Saves a form marked data-cl-async without reloading the page. The server answers the same POST with the form
// re-rendered: 422 with field errors, or 200 with data-success-message once saved. The form is swapped in place,
// so validation markup and saved values come from one template. Without JavaScript the form submits normally.
(function () {
    'use strict';

    function swap(form, html) {
        var temp = document.createElement('div');
        temp.innerHTML = html;
        var next = temp.querySelector('form[data-cl-async]');
        if (!next || next.id !== form.id) {
            return null;
        }
        form.replaceWith(next);
        return next;
    }

    function unlock(form, button) {
        form.removeAttribute('aria-busy');
        if (button) {
            button.disabled = false;
        }
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-cl-async')) {
            return;
        }
        event.preventDefault();
        if (form.getAttribute('aria-busy') === 'true') {
            return;
        }
        var button = form.querySelector('[type="submit"]');
        form.setAttribute('aria-busy', 'true');
        if (button) {
            button.disabled = true;
        }

        fetch(form.action, {
            method: 'POST',
            body: new URLSearchParams(new FormData(form)),
            headers: { 'X-Requested-With': 'fetch' },
            // An expired session answers with a redirect to the login page on another origin; following it would
            // fail as a CORS error indistinguishable from a network outage.
            redirect: 'manual'
        })
            .then(function (response) {
                return response.text().then(function (html) {
                    return { status: response.status, html: html };
                });
            })
            .then(function (result) {
                var next = result.status === 200 || result.status === 422 ? swap(form, result.html) : null;
                if (!next) {
                    // Anything but the form coming back (an expired session redirected to the login page, a server
                    // error page) is left to a regular submit, which shows the user what the server has to say.
                    unlock(form, button);
                    HTMLFormElement.prototype.submit.call(form);
                    return;
                }
                var summary = next.querySelector('[data-cl-error-summary]');
                if (summary) {
                    summary.focus();
                    return;
                }
                var message = next.getAttribute('data-success-message');
                if (message) {
                    showToast(message, 'success');
                }
                var nextButton = next.querySelector('[type="submit"]');
                if (nextButton) {
                    nextButton.focus({ preventScroll: true });
                }
            })
            .catch(function () {
                unlock(form, button);
                showToast(form.getAttribute('data-error-message'), 'danger');
            });
    });
})();
