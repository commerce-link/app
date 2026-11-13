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

    function pickedNames(root, name) {
        var items = root.querySelectorAll('[data-discount-picked="' + name + '"] [data-discount-picked-list] > li');
        return Array.prototype.map.call(items, function (item) {
            return item.getAttribute('data-name');
        });
    }

    function minimum(root) {
        var value = root.querySelector('#minOrderValue').value.trim();
        return value && Number(value.replace(/\s/g, '').replace(',', '.')) > 0 ? money(value) : null;
    }

    function picked(root, name, quantity, some, none) {
        var names = pickedNames(root, name);
        return names.length ? format(some, [quantity, names.join(', ')]) : format(none, [quantity]);
    }

    function conditionPart(root, texts) {
        var condition = checkedValue(root, 'conditionTarget');
        var threshold = minimum(root);
        if (!condition) {
            return threshold ? format(texts.minimumOnly, [threshold]) : texts.always;
        }
        var quantity = root.querySelector('#conditionQuantity').value.trim() || '…';
        var part;
        if (condition === 'Product') {
            var products = pickedNames(root, 'conditionProducts').length;
            part = products ? format(texts.products, [quantity, products]) : format(texts.noProducts, [quantity]);
        } else if (condition === 'Brand') {
            part = picked(root, 'conditionBrands', quantity, texts.brands, texts.noBrands);
        } else {
            part = picked(root, 'conditionCategories', quantity, texts.categories, texts.noCategories);
        }
        return threshold ? part + ' ' + format(texts.andMinimum, [threshold]) : part;
    }

    function discountPart(root, texts) {
        var value = root.querySelector('#value').value.trim() || '…';
        return checkedValue(root, 'type') === 'Amount'
            ? format(texts.amount, [money(value)])
            : format(texts.percentage, [value]);
    }

    function targetPart(root, texts) {
        var target = checkedValue(root, 'target');
        if (target === 'Product') {
            var products = pickedNames(root, 'products').length;
            return products ? format(texts.targetProducts, [products]) : texts.targetNoProducts;
        }
        if (target === 'Brand') {
            var brands = pickedNames(root, 'brands');
            return brands.length ? format(texts.targetBrands, [brands.join(', ')]) : texts.targetNoBrands;
        }
        if (target !== 'Category') {
            return texts.basket;
        }
        var categories = pickedNames(root, 'categories');
        return categories.length ? format(texts.targetCategories, [categories.join(', ')]) : texts.targetNoCategories;
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
