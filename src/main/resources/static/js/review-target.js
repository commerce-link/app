// "Kategoria katalogu" on "Uzupełnij dane" opened from the inventory: a pick in the combobox redraws the rows below
// in place. The form goes, typed values and all, to the address "Zmień kategorię" posts to, with X-Requested-With: fetch,
// and the server answers with the redrawn part alone. Without JavaScript that button (or "Dalej") does the same with a
// full page. The save still refuses a category other than the one the rows were drawn for, so a failed redraw cannot
// save into the wrong one.
(function () {
    'use strict';

    var card = document.querySelector('[data-review-target]');
    if (!card) {
        return;
    }
    var form = card.closest('form');
    var combobox = card.querySelector('[data-cl-combobox]');
    var change = card.querySelector('[data-review-change]');
    var next = card.querySelector('[data-review-next]');
    var footer = card.querySelector('[data-review-target-footer]');
    var failed = card.querySelector('[data-review-failed]');
    var status = card.querySelector('[data-review-status]');
    var action = (change || next).getAttribute('formaction');
    var pending = null;

    // A pick draws the rows at once, so the buttons that would do it are only for a page without the script.
    [change, next].forEach(function (button) {
        if (button) {
            button.hidden = true;
        }
    });

    function showFallback() {
        failed.hidden = false;
        [change, next].forEach(function (button) {
            if (button) {
                button.hidden = false;
            }
        });
    }

    function redraw() {
        if (pending) {
            pending.abort();
        }
        var controller = new AbortController();
        pending = controller;
        var area = document.getElementById('review-area');
        area.setAttribute('aria-busy', 'true');
        failed.hidden = true;

        fetch(action, {
            method: 'POST',
            body: new FormData(form),
            headers: { 'X-Requested-With': 'fetch' },
            credentials: 'same-origin',
            signal: controller.signal
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.text();
        }).then(function (html) {
            if (pending !== controller) {
                return;
            }
            pending = null;
            var redrawn = new DOMParser().parseFromString(html, 'text/html').getElementById('review-area');
            // An expired session answers with the login page (after its redirects), not with the rows.
            if (!redrawn) {
                window.location.assign(window.location.href);
                return;
            }
            var current = document.getElementById('review-area');
            current.replaceWith(redrawn);
            if (footer && redrawn.querySelector('#products')) {
                footer.hidden = true;
            }
            if (status && redrawn.dataset.reviewStatus) {
                status.textContent = redrawn.dataset.reviewStatus;
            }
        }).catch(function (error) {
            if (error.name === 'AbortError') {
                return;
            }
            pending = null;
            document.getElementById('review-area').removeAttribute('aria-busy');
            showFallback();
        });
    }

    combobox.addEventListener('cl:combobox-change', redraw);
})();
