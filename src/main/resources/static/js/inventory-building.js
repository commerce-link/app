/*
 * "Indeks się buduje" on the supplier assortment: while the browse index is being built the results block says so, and
 * this script fetches the block again until the list or the tiles are there. Loaded for every role, unlike the add
 * dialog. After the last attempt it shows the "Odśwież stronę" link the block carries hidden; without JavaScript the
 * block's <noscript> hint asks for a reload instead.
 */
(function () {
    'use strict';

    var BUILD_POLL_MS = 3000;
    var BUILD_POLL_LIMIT = 40;
    var polling = false;

    function startPolling() {
        if (!polling) {
            pollWhileBuilding(0);
        }
    }

    function showStalled(results) {
        var hint = results.querySelector('[data-browse-building-stalled]');
        if (hint) {
            hint.hidden = false;
        }
    }

    function pollWhileBuilding(attempt) {
        var results = document.querySelector('[data-cl-list-results]');
        var building = !!results && !!results.querySelector('[data-browse-building]');
        polling = building && attempt < BUILD_POLL_LIMIT && !!window.fetch && !!window.DOMParser;
        if (!polling) {
            if (building) {
                showStalled(results);
            }
            return;
        }
        window.setTimeout(function () {
            var url = new URL(window.location.href);
            url.pathname = results.getAttribute('data-cl-list-fragment');
            fetch(url, { headers: { 'X-Requested-With': 'fetch' }, credentials: 'same-origin' })
                .then(function (response) {
                    if (!response.ok) { throw new Error('HTTP ' + response.status); }
                    return response.text();
                })
                .then(function (html) {
                    var fresh = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-cl-list-results]');
                    var current = document.querySelector('[data-cl-list-results]');
                    if (fresh && current && !fresh.querySelector('[data-browse-building]')) {
                        polling = false;
                        // The swap removes the focused status line; the focus moves to the fresh block's own target
                        // instead of falling to the body. Focus elsewhere on the page stays where it is.
                        var hadFocus = current.contains(document.activeElement);
                        current.innerHTML = fresh.innerHTML;
                        if (hadFocus) {
                            var target = current.querySelector('[data-cl-list-focus]') || current.querySelector('.cl-table-results');
                            if (target) {
                                if (!target.hasAttribute('tabindex')) { target.setAttribute('tabindex', '-1'); }
                                target.focus({ preventScroll: true });
                            }
                        }
                        current.dispatchEvent(new CustomEvent('cl-list:swapped', { bubbles: true }));
                        return;
                    }
                    pollWhileBuilding(attempt + 1);
                })
                .catch(function () { pollWhileBuilding(attempt + 1); });
        }, BUILD_POLL_MS);
    }

    document.addEventListener('DOMContentLoaded', startPolling);
    // list-page.js swaps the block on navigation; the fresh one may be the building state again.
    document.addEventListener('cl-list:swapped', startPolling);
})();
