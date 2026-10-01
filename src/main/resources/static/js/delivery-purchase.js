// Step 2 through the integration (deliveries/create/purchase.html): asks the supplier for live availability with the
// form's values (POST data-validate-url, answered with the validationResult fragment), keeps the address list
// filterable, and enables the order button only when availability is complete, an address is chosen when one is
// required, every required supplier option is set and the server did not block the order; the unmet conditions are
// listed under the button.
(function () {
    'use strict';

    var state = {checking: true, answered: false, available: false};

    function requiredOptionsSet(form) {
        return Array.prototype.every.call(form.querySelectorAll('select[data-order-option][data-required="true"]'),
            function (select) { return select.value !== ''; });
    }

    function addressSet(form) {
        return !document.getElementById('address-required')
            || form.querySelector('input[name="deliveryAddressId"]:checked') !== null;
    }

    function refresh(form) {
        var submit = document.getElementById('purchase-confirm-submit');
        var blocked = submit.getAttribute('data-blocked') === 'true' || document.getElementById('address-blocked') !== null
            || document.getElementById('order-options-blocked') !== null;
        var reasons = {
            checking: state.checking,
            checkFailed: !state.checking && !state.answered,
            availability: !state.checking && state.answered && !state.available,
            address: !addressSet(form),
            options: !requiredOptionsSet(form),
            blocked: blocked
        };
        var any = false;
        Object.keys(reasons).forEach(function (key) {
            var item = document.querySelector('#confirm-reasons [data-reason="' + key + '"]');
            if (item) {
                item.hidden = !reasons[key];
            }
            any = any || reasons[key];
        });
        document.getElementById('confirm-reasons').hidden = !any;
        submit.disabled = any;
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
        var total = document.querySelector('[data-cl-live-total]');
        state.checking = true;
        refresh(form);
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
                // anything but our fragment (an expired session answers with the login page) counts as a failed check
                if (!area.querySelector('[data-fully-available], [data-cl-validation-error]')) {
                    throw new Error('not a validation result');
                }
                var result = area.querySelector('[data-fully-available]');
                state.answered = !!result;
                state.available = !!(result && result.getAttribute('data-fully-available') === 'true');
                total.textContent = result ? result.getAttribute('data-total') : '—';
            })
            .catch(function () {
                showTemplate(area, 'validation-error-template');
                state.answered = false;
                state.available = false;
                total.textContent = '—';
            })
            .then(function () {
                state.checking = false;
                area.setAttribute('aria-busy', 'false');
                refresh(form);
            });
    }

    function filterAddresses(query) {
        var needle = query.trim().toLocaleLowerCase();
        document.querySelectorAll('label[data-cl-address]').forEach(function (label) {
            var checked = label.querySelector('input').checked;
            label.hidden = needle !== '' && !checked && label.textContent.toLocaleLowerCase().indexOf(needle) === -1;
        });
    }

    function init() {
        var form = document.querySelector('form[data-cl-delivery-purchase]');
        if (!form) {
            return;
        }
        form.addEventListener('change', function () {
            refresh(form);
        });
        document.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-cl-validation-retry]')) {
                loadValidation(form);
            }
            var back = event.target.closest && event.target.closest('[data-cl-back-submit]');
            if (back) {
                document.getElementById(back.getAttribute('data-cl-back-submit')).click();
            }
        });
        // Enter in the address filter or an option must not place the order: the order button is the form's first
        // submit, so once every check passes Enter in any field would press it.
        form.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' && event.target.matches('input:not([type="submit"]), select')) {
                event.preventDefault();
            }
        });
        var filter = document.getElementById('address-filter');
        if (filter) {
            filter.addEventListener('input', function () {
                filterAddresses(filter.value);
            });
        }
        // A second click must not place a second order while the first request is on its way. "Wróć do pozycji"
        // leaves the button alone, and a page restored from the back/forward cache gets it back as the checks allow.
        form.addEventListener('submit', function (event) {
            if (event.submitter && event.submitter.id === 'purchase-confirm-submit') {
                event.submitter.disabled = true;
            }
        });
        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                refresh(form);
            }
        });
        // The list can be long: scroll it (not the page) to the preselected address so the choice is visible.
        var checkedAddress = form.querySelector('input[name="deliveryAddressId"]:checked');
        var addressList = checkedAddress && checkedAddress.closest('.cl-choice-group');
        if (addressList) {
            var label = checkedAddress.closest('label') || checkedAddress;
            addressList.scrollTop = label.getBoundingClientRect().top - addressList.getBoundingClientRect().top;
        }
        loadValidation(form);

        // a page answered with errors puts the keyboard and screen reader on their summary
        var summary = document.querySelector('[data-cl-error-summary]');
        if (summary) {
            summary.focus();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
