// Expandable table rows: a button[data-cl-row-toggle][aria-controls] in a row opens and closes the detail row it
// names. Without JavaScript the detail rows stay open and the button hidden (the plain count shows instead); this
// script shows the buttons, hides the plain counts and closes the rows, at start and after list-page.js swaps the
// results (cl-list:swapped).
(function () {
    'use strict';

    function setUp(scope) {
        scope.querySelectorAll('[data-cl-row-toggle]').forEach(function (button) {
            var detail = document.getElementById(button.getAttribute('aria-controls'));
            if (!detail) { return; }
            var open = button.getAttribute('aria-expanded') === 'true';
            button.hidden = false;
            var fallback = button.parentNode.querySelector('[data-cl-row-toggle-fallback]');
            if (fallback) { fallback.hidden = true; }
            detail.hidden = !open;
        });
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest && event.target.closest('[data-cl-row-toggle]');
        if (!button) { return; }
        var detail = document.getElementById(button.getAttribute('aria-controls'));
        if (!detail) { return; }
        var open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        detail.hidden = !open;
        var row = button.closest('tr');
        if (row) { row.classList.toggle('is-open', open); }
    });

    document.addEventListener('cl-list:swapped', function (event) { setUp(event.target); });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { setUp(document); });
    } else {
        setUp(document);
    }
})();
