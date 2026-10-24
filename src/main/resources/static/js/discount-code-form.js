(function () {
    'use strict';

    var CODE_CHARACTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    var GENERATED_LENGTH = 8;

    function form() {
        return document.getElementById('discount-code-form');
    }

    function format(template, values) {
        return template.replace(/\{(\d)\}/g, function (match, index) {
            return values[Number(index)];
        });
    }

    function randomCode() {
        var bytes = new Uint8Array(GENERATED_LENGTH);
        window.crypto.getRandomValues(bytes);
        return Array.prototype.map.call(bytes, function (byte) {
            return CODE_CHARACTERS.charAt(byte % CODE_CHARACTERS.length);
        }).join('');
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

    function discountPart(root, texts) {
        var value = root.querySelector('#value').value.trim() || '…';
        return checkedValue(root, 'type') === 'Amount'
            ? format(texts.amount, [money(value)])
            : format(texts.percentage, [value]);
    }

    function targetPart(root, texts) {
        if (checkedValue(root, 'target') !== 'Category') {
            return texts.basket;
        }
        var names = Array.prototype.map.call(root.querySelectorAll('input[name="categories"]:checked'), function (box) {
            return box.value;
        });
        return names.length ? format(texts.categories, [names.join(', ')]) : texts.noCategories;
    }

    function gatewayPart(root, texts) {
        var select = root.querySelector('#paymentProvider');
        if (!select || !select.value) {
            return texts.anyGateway;
        }
        return format(texts.gateway, [select.options[select.selectedIndex].text]);
    }

    function limitPart(root, texts) {
        var limit = root.querySelector('#maxUses').value.trim();
        if (limit === '' || limit === '0') {
            return texts.unlimited;
        }
        return limit === '1' ? texts.once : format(texts.limit, [limit]);
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
        var summary = root && root.querySelector('[data-discount-summary]');
        if (!summary) {
            return;
        }
        var texts = summary.dataset;
        var minimum = root.querySelector('#minOrderValue').value.trim();
        var first = discountPart(root, texts) + ' ' + targetPart(root, texts)
            + (minimum && Number(minimum.replace(',', '.')) > 0 ? ' ' + format(texts.minimum, [money(minimum)]) : '');
        var second = [gatewayPart(root, texts), limitPart(root, texts), validityPart(root, texts)].join(', ');
        var sentence = first + '. ' + second + '.';
        if (!root.querySelector('#active').checked) {
            sentence += ' ' + texts.inactive;
        }
        summary.querySelector('[data-discount-summary-text]').textContent = sentence;
    }

    function markUsageChoice() {
        var root = form();
        if (!root) {
            return;
        }
        var limit = root.querySelector('#maxUses').value.trim();
        root.querySelectorAll('[data-discount-uses]').forEach(function (chip) {
            var value = chip.getAttribute('data-discount-uses');
            var pressed = value === '' ? limit === '' || limit === '0' : limit === value;
            chip.setAttribute('aria-pressed', pressed ? 'true' : 'false');
        });
    }

    function prepare() {
        var root = form();
        if (!root) {
            return;
        }
        root.querySelectorAll('[data-discount-generate], [data-discount-uses-choices], [data-discount-summary]')
            .forEach(function (element) {
                element.hidden = false;
            });
        markUsageChoice();
        summarize();
    }

    document.addEventListener('click', function (event) {
        var generate = event.target.closest && event.target.closest('[data-discount-generate]');
        if (generate) {
            var code = form().querySelector('#code');
            code.value = randomCode();
            code.focus();
            summarize();
            return;
        }
        var chip = event.target.closest && event.target.closest('[data-discount-uses]');
        if (chip) {
            form().querySelector('#maxUses').value = chip.getAttribute('data-discount-uses');
            markUsageChoice();
            summarize();
        }
    });

    document.addEventListener('input', function (event) {
        if (form() && form().contains(event.target)) {
            markUsageChoice();
            summarize();
        }
    });

    document.addEventListener('change', function (event) {
        if (form() && form().contains(event.target)) {
            summarize();
        }
    });

    document.addEventListener('cl:form-replaced', prepare);
    prepare();
})();
