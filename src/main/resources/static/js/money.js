// Shared amount formatter for the order-item dialogs (order-item-dialogs.js, item-add-dialog.js): mirrors
// Money.format (server-side) for the number itself -- NBSP thousands, comma decimals, U+2212 minus -- then
// substitutes it into the unit pattern the layout renders once per page (data-cl-amount-format on <body>, the
// resolved general.currency.amount message with its "{0}" placeholder left intact because it is requested with
// no arguments). This keeps the currency literal ("PLN") out of every script that shows a price: one place
// renders it, every script reuses it. toLocaleString('pl-PL') is not used for the number because Intl's pl-PL
// data sets minimumGroupingDigits to 2, so 1 000-9 999 would render without a thousands separator while the
// rest of the page (server-formatted amounts) always has one.
(function () {
    'use strict';

    function formatMoney(value) {
        var amount = Number(value || 0);
        var digits = Math.abs(amount).toFixed(2).replace('.', ',');
        var parts = digits.split(',');
        parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        var joined = parts.join(',');
        var number = amount < 0 ? '−' + joined : joined;
        var pattern = document.body.getAttribute('data-cl-amount-format');
        return pattern ? pattern.replace('{0}', number) : number;
    }

    window.CL_formatMoney = formatMoney;
})();
