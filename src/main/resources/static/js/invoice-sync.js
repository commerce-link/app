// Invoice sync preview (invoiceSyncPreview.html): after every change of an invoice line it redraws the match state of
// the row (the rule of InvoiceSyncPreview.MatchState), the counts above the table, the warnings (one invoice line
// chosen for several rows, another quantity on the invoice), the invoice lines nobody chose, the assigned and the
// unassigned totals and the effects of the save. The server draws the same state for the first view, so the page
// reads the same without this script.
(function () {
    'use strict';

    var EPS = 0.005;
    var CLOSE_DELTA = 0.01;
    var TONES = {EXACT: 'is-ok', CLOSE: 'is-info', DIFFERENT: 'is-warn', UNASSIGNED: 'is-bad', NO_COST: 'is-neutral'};
    var LABELS = {EXACT: 'labelExact', CLOSE: 'labelClose', DIFFERENT: 'labelDifferent', UNASSIGNED: 'labelUnassigned', NO_COST: 'labelNoCost'};

    function fill(template, values) {
        return values.reduce(function (text, value, i) { return text.split('{' + i + '}').join(value); }, template || '');
    }

    // Money.format (NBSP thousands, comma decimals, U+2212 minus) followed by the invoice's currency code: money.js
    // renders the store's "PLN" pattern, while an invoice may be in another currency.
    function money(value, currency) {
        var amount = Math.round(Number(value || 0) * 100) / 100;
        var parts = Math.abs(amount).toFixed(2).split('.');
        var number = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ',' + parts[1];
        return (amount < 0 ? '−' : '') + number + ' ' + currency;
    }

    function chosen(row) {
        var select = row.querySelector('[data-cl-sync-select]');
        var option = select && select.options[select.selectedIndex];
        if (!option || !option.value) {
            return null;
        }
        return {
            id: option.value,
            priceNet: parseFloat(option.getAttribute('data-price-net')),
            totalNet: parseFloat(option.getAttribute('data-total-net')),
            qty: parseInt(option.getAttribute('data-qty'), 10)
        };
    }

    function compare(invoiceAmount, deliveryAmount) {
        var delta = Math.abs(invoiceAmount - deliveryAmount);
        if (delta < EPS) {
            return 'EXACT';
        }
        return Math.abs(delta - CLOSE_DELTA) < EPS ? 'CLOSE' : 'DIFFERENT';
    }

    function evaluate(row) {
        var item = row.getAttribute('data-cl-sync-row') === 'item';
        var cost = parseFloat(row.getAttribute('data-cost')) || 0;
        var choice = chosen(row);
        if (!choice) {
            return {row: row, item: item, choice: null, state: !item && Math.abs(cost) < EPS ? 'NO_COST' : 'UNASSIGNED'};
        }
        var invoiceAmount = item ? choice.priceNet : choice.totalNet;
        return {row: row, item: item, choice: choice, state: compare(invoiceAmount, cost), delta: invoiceAmount - cost};
    }

    function drawState(form, result) {
        var cell = result.row.querySelector('[data-cl-sync-state]');
        cell.textContent = '';
        var pill = document.createElement('span');
        pill.className = 'cl-status ' + TONES[result.state];
        pill.textContent = form.dataset[LABELS[result.state]];
        cell.appendChild(pill);
        if (result.state === 'DIFFERENT') {
            var diff = document.createElement('span');
            diff.className = 'cl-diff';
            diff.textContent = fill(form.dataset.templateDiff, [(result.delta > 0 ? '+' : '') + money(result.delta, form.dataset.currency)]);
            cell.appendChild(diff);
        }
    }

    function drawWarnings(form, result, results) {
        var box = result.row.querySelector('[data-cl-sync-warnings]');
        box.textContent = '';
        if (!result.choice) {
            return false;
        }
        var messages = [];
        var others = results.filter(function (other) {
            return other !== result && other.choice && other.choice.id === result.choice.id;
        }).map(function (other) { return other.row.getAttribute('data-name'); });
        if (others.length) {
            messages.push(fill(form.dataset.templateDuplicate, [others.join(', ')]));
        }
        var qty = parseInt(result.row.getAttribute('data-qty'), 10);
        if (result.item && result.choice.qty !== qty) {
            messages.push(fill(form.dataset.templateQty, [result.choice.qty, qty]));
        }
        messages.forEach(function (message) {
            var p = document.createElement('p');
            p.className = 'cl-help is-warn';
            p.textContent = message;
            box.appendChild(p);
        });
        return others.length > 0;
    }

    function drawCounts(form, items) {
        form.querySelectorAll('[data-cl-sync-counts] [data-state]').forEach(function (li) {
            var state = li.getAttribute('data-state');
            var n = items.filter(function (r) { return r.state === state; }).length;
            li.hidden = n === 0;
            li.querySelector('.cl-status').textContent = fill(form.dataset.templateCount, [form.dataset[LABELS[state]], n]);
        });
        var allExact = form.querySelector('[data-cl-sync-all-exact]');
        if (allExact) {
            allExact.hidden = !(items.length > 0 && items.every(function (r) { return r.state === 'EXACT'; }));
        }
    }

    function drawUnassigned(form, results) {
        var ids = {};
        results.forEach(function (r) { if (r.choice) { ids[r.choice.id] = r.choice.totalNet; } });
        var card = form.querySelector('[data-cl-sync-unassigned]');
        var left = 0;
        var leftCount = 0;
        var totals = {};
        form.querySelectorAll('[data-cl-sync-select] option[value]:not([value=""])').forEach(function (option) {
            totals[option.value] = parseFloat(option.getAttribute('data-total-net'));
        });
        card.querySelectorAll('[data-position-id]').forEach(function (li) {
            var id = li.getAttribute('data-position-id');
            li.hidden = Object.prototype.hasOwnProperty.call(ids, id);
            if (!li.hidden) {
                left += totals[id] || 0;
                leftCount++;
            }
        });
        card.hidden = leftCount === 0;
        var title = card.querySelector('[data-template]');
        title.textContent = fill(title.getAttribute('data-template'), [leftCount]);

        var assigned = Object.keys(ids).reduce(function (sum, id) { return sum + ids[id]; }, 0);
        form.querySelector('[data-cl-sync-assigned]').textContent = money(assigned, form.dataset.currency);
        var leftCell = form.querySelector('[data-cl-sync-left]');
        leftCell.querySelector('output').textContent = money(left, form.dataset.currency);
        leftCell.classList.toggle('is-bad', left >= EPS);
    }

    function drawEffects(form, results) {
        var items = results.filter(function (r) { return r.item; });
        var assigned = items.filter(function (r) { return r.choice; }).length;
        var line = form.querySelector('[data-cl-effect-items]');
        line.querySelector('[data-cl-effect-text]').textContent = assigned === 0
            ? line.getAttribute('data-template-none')
            : fill(line.getAttribute('data-template'), [assigned, items.length]);
        var rest = line.querySelector('[data-cl-effect-sub]');
        rest.hidden = assigned === 0 || assigned === items.length;
        rest.textContent = fill(line.getAttribute('data-template-rest'), [items.length - assigned]);

        results.filter(function (r) { return !r.item; }).forEach(function (r) {
            var effect = form.querySelector('[data-cl-effect-extra="' + r.row.getAttribute('data-cl-sync-row') + '"]');
            if (!effect) {
                return;
            }
            effect.hidden = !r.choice;
            if (r.choice) {
                effect.querySelector('[data-cl-effect-text]').textContent =
                    fill(effect.getAttribute('data-template'), [money(r.choice.totalNet, form.dataset.currency)]);
                var unchanged = Math.abs(r.choice.totalNet - parseFloat(effect.getAttribute('data-cost'))) < EPS;
                effect.querySelector('[data-cl-effect-sub]').textContent =
                    effect.getAttribute(unchanged ? 'data-unchanged' : 'data-now');
            }
        });
    }

    function refresh(form) {
        var results = Array.prototype.map.call(form.querySelectorAll('[data-cl-sync-row]'), evaluate);
        var duplicates = false;
        results.forEach(function (result) {
            drawState(form, result);
            duplicates = drawWarnings(form, result, results) || duplicates;
        });
        var duplicateAlert = form.querySelector('[data-cl-sync-duplicates]');
        if (duplicateAlert) {
            duplicateAlert.hidden = !duplicates;
        }
        drawCounts(form, results.filter(function (r) { return r.item; }));
        drawUnassigned(form, results);
        drawEffects(form, results);
    }

    document.addEventListener('DOMContentLoaded', function () {
        var form = document.querySelector('[data-cl-invoice-sync]');
        if (!form) {
            return;
        }
        form.addEventListener('change', function (event) {
            if (event.target.matches('[data-cl-sync-select]')) {
                refresh(form);
            }
        });
        refresh(form);
    });
})();
