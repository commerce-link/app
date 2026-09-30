// The shipments card carries data-cl-cancellation-poll while a courier cancellation waits for Furgonetka's answer. The
// script asks that address every 5 seconds for about 2 minutes and reloads the page once nothing is in progress any
// more, so the card shows the cleared shipment or why the cancellation failed. A reload waits while a dialog or an
// action menu is open or table rows are selected, so nothing typed, opened or selected is lost. Without JavaScript
// the pill says the cancellation is in progress and the operator refreshes the page.
(function () {
    'use strict';

    var INTERVAL = 5000;
    var LIMIT = 120000;
    var BUSY = 'dialog[open], details.cl-menu[open], [data-cl-select-row]:checked';

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
