// The custom filter's new/edit subpage (/dashboard/orders/filters/add, …/{id}/edit): the postal-code field gets its
// "00-000" dash as the user types. The form itself posts plainly, with or without JavaScript.
(function () {
    'use strict';

    function formatPostalCode(input, appendSeparator) {
        var digits = input.value.replace(/\D/g, '').slice(0, 5);
        input.value = digits.length > 2 ? digits.slice(0, 2) + '-' + digits.slice(2) : (digits.length === 2 && appendSeparator ? digits + '-' : digits);
    }

    document.addEventListener('input', function (event) {
        if (event.target.matches && event.target.matches('[data-cl-postal-code]')) {
            formatPostalCode(event.target, !(event.inputType || '').startsWith('delete'));
        }
    });
})();
