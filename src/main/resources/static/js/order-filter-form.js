// The custom filter's new/edit subpage (/dashboard/orders/filters/add, …/{id}/edit): the postal-code field gets its
// "00-000" dash as the user types. The form itself posts plainly, with or without JavaScript.
(function () {
    'use strict';

    function formatPostalCode(input, appendSeparator) {
        var digits = input.value.replace(/\D/g, '').slice(0, 5);
        input.value = digits.length > 2 ? digits.slice(0, 2) + '-' + digits.slice(2) : (digits.length === 2 && appendSeparator ? digits + '-' : digits);
    }

    // Multi-value fields (details.cl-filter-menu.is-field): the closed menu names what is ticked, and an open menu
    // closes on a click elsewhere or on Escape, as a select's list would.
    function summarize(menu) {
        var ticked = menu.querySelectorAll('input[type="checkbox"]:checked');
        var value = menu.querySelector('[data-cl-filter-field-value]');
        if (ticked.length === 0) {
            value.textContent = menu.getAttribute('data-cl-any');
        } else if (ticked.length === 1) {
            value.textContent = ticked[0].parentNode.querySelector('.cl-filter-menu-text').textContent;
        } else {
            value.textContent = menu.getAttribute('data-cl-selected').replace('{0}', ticked.length);
        }
    }

    document.addEventListener('change', function (event) {
        var menu = event.target.closest && event.target.closest('[data-cl-filter-field]');
        if (menu) {
            summarize(menu);
        }
    });

    document.addEventListener('click', function (event) {
        var open = document.querySelectorAll('details[data-cl-filter-field][open]');
        for (var i = 0; i < open.length; i++) {
            if (!open[i].contains(event.target)) {
                open[i].removeAttribute('open');
            }
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') {
            return;
        }
        var open = document.querySelector('details[data-cl-filter-field][open]');
        if (open) {
            open.removeAttribute('open');
            open.querySelector('summary').focus();
        }
    });

    document.addEventListener('input', function (event) {
        if (event.target.matches && event.target.matches('[data-cl-postal-code]')) {
            formatPostalCode(event.target, !(event.inputType || '').startsWith('delete'));
        }
    });
})();
