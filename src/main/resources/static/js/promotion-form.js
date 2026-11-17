(function () {
    'use strict';

    function form() {
        return document.getElementById('promotion-form');
    }

    function format(template, values) {
        return template.replace(/\{(\d)\}/g, function (match, index) {
            return values[Number(index)];
        });
    }

    function checkedValue(root, name) {
        var checked = root.querySelector('input[name="' + name + '"]:checked');
        return checked ? checked.value : '';
    }

    function money(text) {
        var number = Number(String(text).replace(/\s/g, '').replace(',', '.'));
        return isFinite(number) && text.trim() !== '' ? number.toFixed(2).replace('.', ',') : text.trim();
    }

    function dateTime(value) {
        var match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
        return match ? match[3] + '.' + match[2] + '.' + match[1] + ', ' + match[4] + ':' + match[5] : value;
    }

    function minimum(root) {
        var value = root.querySelector('#minOrderValue').value.trim();
        return value && Number(value.replace(/\s/g, '').replace(',', '.')) > 0 ? money(value) : null;
    }

    function conditionScope(root, texts) {
        return window.CL_discountScopePhrase(root, 'condition', {
            everything: null, anyProducts: texts.conditionAnyProducts, products: texts.conditionProducts,
            categories: texts.categories, brands: texts.brands, and: texts.and
        });
    }

    function conditionPart(root, texts) {
        var scope = conditionScope(root, texts);
        var threshold = minimum(root);
        if (scope === null) {
            return threshold ? format(texts.minimumOnly, [threshold]) : texts.always;
        }
        var quantity = root.querySelector('#conditionQuantity').value.trim() || '…';
        var part = format(texts.condition, [quantity, scope]);
        return threshold ? part + ' ' + format(texts.andMinimum, [threshold]) : part;
    }

    function discountPart(root, texts) {
        var value = root.querySelector('#value').value.trim() || '…';
        return checkedValue(root, 'type') === 'Amount'
            ? format(texts.amount, [money(value)])
            : format(texts.percentage, [value]);
    }

    function targetPart(root, texts) {
        return window.CL_discountScopePhrase(root, '', {
            everything: texts.basket, anyProducts: texts.anyProducts, products: texts.products,
            categories: texts.categories, brands: texts.brands, and: texts.and
        });
    }

    function validityPart(root, texts) {
        var from = root.querySelector('#validFrom').value;
        var to = root.querySelector('#validTo').value;
        if (from && to) {
            return format(texts.range, [dateTime(from), dateTime(to)]);
        }
        if (from) {
            return format(texts.from, [dateTime(from)]);
        }
        return to ? format(texts.to, [dateTime(to)]) : texts.noDates;
    }

    function summarize() {
        var root = form();
        var summary = root && root.querySelector('[data-promotion-summary]');
        if (!summary) {
            return;
        }
        var texts = summary.dataset;
        root.querySelector('[data-promotion-condition-quantity]').hidden = conditionScope(root, texts) === null;
        var sentence = conditionPart(root, texts) + ': ' + discountPart(root, texts) + ' ' + targetPart(root, texts)
            + '. ' + validityPart(root, texts);
        if (!root.querySelector('#active').checked) {
            sentence += ' ' + texts.inactive;
        }
        summary.querySelector('[data-promotion-summary-text]').textContent = sentence;
    }

    function summarizeInForm(event) {
        if (form() && form().contains(event.target)) {
            summarize();
        }
    }

    function prepare() {
        var root = form();
        if (!root) {
            return;
        }
        root.querySelector('[data-promotion-summary]').hidden = false;
        summarize();
    }

    document.addEventListener('input', summarizeInForm);
    document.addEventListener('change', summarizeInForm);
    document.addEventListener('cl:discount-picked', summarizeInForm);
    document.addEventListener('cl:form-replaced', prepare);
    prepare();
})();
