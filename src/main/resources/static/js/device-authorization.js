// A form marked data-cl-device-authorization waits for the operator to confirm a code on another site (OAuth device
// flow). The script asks data-status-url every data-interval seconds and shows the answer in [data-cl-device-status];
// when the answer is final (connected, refused, expired) it goes where the server says. Without JavaScript the form's
// own "Check" button asks once and the page reloads with the answer.
(function () {
    'use strict';

    function watch(form) {
        var statusUrl = form.getAttribute('data-status-url');
        var interval = Math.max(parseInt(form.getAttribute('data-interval'), 10) || 5, 2) * 1000;
        var expiresAt = parseInt(form.getAttribute('data-expires-at'), 10) || 0;
        var status = form.querySelector('[data-cl-device-status]');
        var timer = null;
        var done = false;

        function schedule(delay) {
            timer = window.setTimeout(ask, delay);
        }

        function ask() {
            if (done) { return; }
            if (expiresAt && Date.now() > expiresAt + interval) {
                // One last question lets the server clear the code and answer "expired" in the operator's language
                done = true;
            }
            fetch(statusUrl, { method: 'POST', headers: { 'X-Requested-With': 'fetch' }, redirect: 'manual' })
                .then(function (response) {
                    if (response.type === 'opaqueredirect' || !response.ok) { throw new Error('status ' + response.status); }
                    return response.json();
                })
                .then(function (body) {
                    if (body.redirect) {
                        done = true;
                        window.location.assign(body.redirect);
                        return;
                    }
                    if (status && body.message) { status.textContent = body.message; }
                    if (!done) { schedule(interval); }
                })
                .catch(function () {
                    // A lost request (network, expired session) is not an answer: keep the button, try again later
                    if (!done) { schedule(interval * 2); }
                });
        }

        form.addEventListener('submit', function () {
            done = true;
            if (timer) { window.clearTimeout(timer); }
        });
        schedule(interval);
    }

    document.querySelectorAll('form[data-cl-device-authorization]').forEach(watch);
})();
