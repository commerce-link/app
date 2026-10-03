// A price field with a net/gross switch (div[data-cl-cost-field]): the switch sets the hidden priceType and the help line
// says the other value ("= 560,16 netto (VAT 23%)") from data-tax (the VAT rate as a multiplier, 1.23, a fraction, 0.23,
// or a percentage, 23). The server converts; the hint is only a preview.
(function () {
    'use strict';

    function format(value) {
        return value.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function multiplier(rate) {
        if (rate >= 1 && rate < 2) {
            return rate;
        }
        return rate < 1 ? 1 + rate : 1 + rate / 100;
    }

    function hint(field) {
        var form = field.closest('form');
        var input = field.querySelector('input[name="cost"]');
        var type = form.querySelector('input[name="priceType"]');
        var target = form.querySelector('[data-cl-cost-hint]');
        var tax = multiplier(Number(target.getAttribute('data-tax')));
        var amount = Number(input.value.replace(/[\s ]/g, '').replace(',', '.'));
        if (input.value.trim() === '' || isNaN(amount)) {
            target.textContent = target.getAttribute('data-empty') || '';
            return;
        }
        var gross = type.value === 'gross';
        var other = gross ? amount / tax : amount * tax;
        target.textContent = target.getAttribute(gross ? 'data-template-net' : 'data-template-gross')
            .replace('{0}', format(other)).replace('{1}', String(Math.round((tax - 1) * 100)));
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest && event.target.closest('[data-cl-cost-field] .cl-segment[data-value]');
        if (!button) {
            return;
        }
        var field = button.closest('[data-cl-cost-field]');
        field.closest('form').querySelector('input[name="priceType"]').value = button.getAttribute('data-value');
        field.querySelectorAll('.cl-segment[data-value]').forEach(function (other) {
            other.setAttribute('aria-pressed', String(other === button));
        });
        hint(field);
    });

    document.addEventListener('input', function (event) {
        var field = event.target.closest && event.target.closest('[data-cl-cost-field]');
        if (field && event.target.name === 'cost') {
            hint(field);
        }
    });

    document.querySelectorAll('[data-cl-cost-field]').forEach(hint);
})();
