// The dropship order confirmation (dropshipConfirmation.html): asks the supplier for live availability with the form's
// values (POST .../dropship/validate, answered with the validationResult fragment) and enables the submit button only
// when every line is available, every required supplier option is chosen and the server did not block the order.
(function () {
    'use strict';

    var validationPassed = false;

    function optionsComplete(form) {
        return Array.prototype.every.call(form.querySelectorAll('select[data-order-option][data-required="true"]'),
            function (select) { return select.value !== ''; });
    }

    function refreshSubmitState(form) {
        var submit = document.getElementById('purchase-confirm-submit');
        var optionsBlocked = document.getElementById('order-options-blocked') !== null;
        submit.disabled = !validationPassed || submit.getAttribute('data-blocked') === 'true'
            || optionsBlocked || !optionsComplete(form);
    }

    function showTemplate(area, id) {
        area.replaceChildren(document.getElementById(id).content.cloneNode(true));
    }

    // The answer is our own server-rendered fragment; it is parsed into nodes, never assigned as markup.
    function showFragment(area, html) {
        var parsed = new DOMParser().parseFromString(html, 'text/html');
        area.replaceChildren.apply(area, Array.prototype.slice.call(parsed.body.childNodes));
    }

    function loadValidation(form) {
        var area = document.getElementById('validation-area');
        validationPassed = false;
        refreshSubmitState(form);
        area.setAttribute('aria-busy', 'true');
        showTemplate(area, 'validation-spinner-template');
        fetch(form.getAttribute('data-validate-url'), {method: 'POST', body: new URLSearchParams(new FormData(form))})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                showFragment(area, html);
                var result = area.querySelector('[data-fully-available]');
                validationPassed = !!(result && result.getAttribute('data-fully-available') === 'true');
            })
            .catch(function () {
                showTemplate(area, 'validation-error-template');
                validationPassed = false;
            })
            .then(function () {
                area.setAttribute('aria-busy', 'false');
                refreshSubmitState(form);
            });
    }

    function init() {
        var form = document.getElementById('purchase-confirm-form');
        if (!form) {
            return;
        }
        form.addEventListener('change', function (event) {
            if (event.target.matches('select[data-order-option]')) {
                refreshSubmitState(form);
            }
        });
        form.addEventListener('click', function (event) {
            if (event.target.closest('[data-cl-validation-retry]')) {
                loadValidation(form);
            }
        });
        // A second click must not place a second order while the first request is on its way. "Cofnij" leaves the
        // button alone, and a page restored from the back/forward cache gets it back in the state the check allows.
        form.addEventListener('submit', function (event) {
            if (event.submitter && event.submitter.id === 'purchase-confirm-submit') {
                event.submitter.disabled = true;
            }
        });
        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                refreshSubmitState(form);
            }
        });
        loadValidation(form);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
