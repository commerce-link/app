// A shipments card carries data-cl-cancellation-poll while a command of one of its shipments waits for the provider:
// a courier cancellation, a creation or a pickup order. The script asks that address every 5 seconds for about 3
// minutes (a creation is checked for up to about 2.5 minutes) and reloads the page once nothing is in progress any
// more, so the card shows the result. A reload waits while a dialog, an action menu or a Bulma modal (RMA page) is
// open or table rows are selected, so nothing typed, opened or selected is lost. Without JavaScript the state line says
// what is going on and the operator refreshes the page.
(function () {
    'use strict';

    var INTERVAL = 5000;
    var LIMIT = 180000;
    var BUSY = 'dialog[open], details.cl-menu[open], .modal.is-active, [data-cl-select-row]:checked';

    function reloadWhenIdle() {
        if (document.querySelector(BUSY)) {
            window.setTimeout(reloadWhenIdle, INTERVAL);
            return;
        }
        window.location.reload();
    }

    function watch(card) {
        var url = card.getAttribute('data-cl-cancellation-poll');
        var startedAt = Date.now();

        function schedule() {
            if (Date.now() - startedAt + INTERVAL <= LIMIT) {
                window.setTimeout(ask, INTERVAL);
            }
        }

        function ask() {
            fetch(url, {
                headers: { 'X-Requested-With': 'fetch', 'Accept': 'application/json' },
                redirect: 'manual',
                cache: 'no-store'
            })
                .then(function (response) {
                    if (response.type === 'opaqueredirect' || !response.ok) { throw new Error('status ' + response.status); }
                    return response.json();
                })
                .then(function (body) {
                    if (body.inProgress === false) {
                        reloadWhenIdle();
                        return;
                    }
                    schedule();
                })
                // A lost request (network, expired session) is not an answer: ask again while there is time left
                .catch(schedule);
        }

        schedule();
    }

    document.querySelectorAll('[data-cl-cancellation-poll]').forEach(watch);
})();
