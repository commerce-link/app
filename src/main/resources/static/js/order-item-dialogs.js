// Dialogs of the order's items (orders details), opened by dialog.js from the row menu: the menu entry's data
// attributes (item id, name, sku, category, qty, price, tax) fill the dialog on cl:dialog-open. Assign SKU picks codes
// from a catalog's pricelist (fetched once per catalog) or typed by hand and joins them with ":"; Assign supplier offers
// the item's own codes and turns a gross price net with the item's VAT for the hint (the server converts it); Assign
// from warehouse lists the stock of the item's category; Split bundle keeps the rows numbered rows[0..n] and the total
// balanced; Serial numbers never submit on Enter; Move previews the target order.
(function () {
    'use strict';

    // Shared with item-add-dialog.js: money.js formats the number and adds the unit from the pattern the
    // layout renders once per page, so no script here hard-codes a currency literal.
    function format(value) {
        return window.CL_formatMoney(value);
    }

    function cell(row, text, className) {
        var td = document.createElement('td');
        if (className) {
            td.className = className;
        }
        td.textContent = text === undefined || text === null ? '' : String(text);
        row.appendChild(td);
        return td;
    }

    function fillContext(dialog, trigger) {
        var qty = parseInt(trigger.getAttribute('data-qty'), 10) || 0;
        var price = parseFloat(trigger.getAttribute('data-price')) || 0;
        var values = {
            name: trigger.getAttribute('data-name') || '',
            category: trigger.getAttribute('data-category') || '',
            qty: String(qty),
            price: format(price),
            total: format(price * qty)
        };
        dialog.querySelectorAll('[data-cl-item-context] [data-field]').forEach(function (dd) {
            dd.textContent = values[dd.getAttribute('data-field')];
        });
        dialog.querySelectorAll('[data-cl-item-id]').forEach(function (input) {
            input.value = trigger.getAttribute('data-item-id') || '';
        });
    }

    function codesOf(trigger) {
        return (trigger.getAttribute('data-sku') || '').split(':').map(function (code) {
            return code.trim();
        }).filter(function (code) {
            return code.length > 0;
        });
    }

    // --- Assign SKU ---------------------------------------------------------------------------------------------
    (function () {
        var dialog = document.getElementById('assign-sku-dialog');
        if (!dialog) {
            return;
        }
        var form = dialog.querySelector('form');
        var catalog = dialog.querySelector('[data-cl-sku-catalog]');
        var category = dialog.querySelector('[data-cl-sku-category]');
        var label = dialog.querySelector('[data-cl-sku-label]');
        var search = dialog.querySelector('[data-cl-sku-search]');
        var manual = dialog.querySelector('[data-cl-sku-manual]');
        var chips = dialog.querySelector('[data-cl-sku-chips]');
        var rows = dialog.querySelector('[data-cl-sku-rows]');
        var cache = {};
        var data = [];
        var selected = [];
        var itemCategory = '';

        function load(catalogId, done) {
            if (!catalogId) {
                data = [];
                done();
                return;
            }
            if (cache[catalogId]) {
                data = cache[catalogId];
                done();
                return;
            }
            fetch(catalog.getAttribute('data-url') + '?catalogId=' + encodeURIComponent(catalogId))
                .then(function (response) {
                    return response.ok ? response.json() : [];
                })
                .catch(function () {
                    return [];
                })
                .then(function (loaded) {
                    cache[catalogId] = loaded;
                    data = loaded;
                    done();
                });
        }

        function options(select, values, chosen) {
            select.replaceChildren(new Option(select.getAttribute('data-placeholder'), ''));
            values.forEach(function (value) {
                select.appendChild(new Option(value, value));
            });
            select.value = values.indexOf(chosen) >= 0 ? chosen : '';
        }

        function unique(values) {
            return values.filter(function (value, index) {
                return value && values.indexOf(value) === index;
            });
        }

        function refreshLabels() {
            options(label, unique(data.filter(function (p) {
                return p.category === category.value;
            }).map(function (p) {
                return p.label;
            })), '');
            renderTable();
        }

        function refresh() {
            selected.forEach(function (s) {
                s.foreign = !data.some(function (p) {
                    return p.mfn === s.code;
                });
            });
            options(category, unique(data.map(function (p) {
                return p.category;
            })), itemCategory);
            refreshLabels();
            renderChips();
        }

        function renderTable() {
            var term = search.value.trim().toLowerCase();
            var shown = data.filter(function (p) {
                return (!category.value || p.category === category.value) && (!label.value || p.label === label.value)
                    && (!term || (p.name || '').toLowerCase().indexOf(term) >= 0 || (p.mfn || '').toLowerCase().indexOf(term) >= 0);
            });
            rows.replaceChildren();
            shown.forEach(function (p) {
                var picked = selected.some(function (s) {
                    return s.code === p.mfn;
                });
                var row = document.createElement('tr');
                row.classList.toggle('is-selected', picked);
                var check = document.createElement('td');
                check.className = 'cl-table-check';
                var box = document.createElement('input');
                box.type = 'checkbox';
                box.className = 'cl-check-input';
                box.checked = picked;
                box.setAttribute('aria-label', p.name || p.mfn);
                check.appendChild(box);
                row.appendChild(check);
                cell(row, p.name);
                cell(row, p.label);
                cell(row, p.mfn, 'cl-table-code');
                cell(row, format(p.price), 'is-numeric');
                cell(row, p.qty, 'is-numeric');
                row.addEventListener('click', function (event) {
                    if (event.target !== box) {
                        box.checked = !box.checked;
                    }
                    toggle(p.mfn);
                });
                rows.appendChild(row);
            });
            dialog.querySelector('[data-cl-sku-none]').hidden = shown.length > 0;
        }

        function toggle(code) {
            var index = selected.findIndex(function (s) {
                return s.code === code;
            });
            if (index >= 0) {
                selected.splice(index, 1);
            } else {
                selected.push({ code: code, foreign: false });
            }
            renderChips();
            renderTable();
        }

        function renderChips() {
            chips.replaceChildren();
            selected.forEach(function (s) {
                var chip = document.createElement('li');
                chip.className = 'cl-chip-tag' + (s.foreign ? ' is-warn' : '');
                if (s.foreign) {
                    chip.title = chips.getAttribute('data-foreign');
                }
                chip.appendChild(document.createTextNode(s.code));
                var remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'cl-chip-tag-remove';
                remove.setAttribute('aria-label', chips.getAttribute('data-remove') + ' ' + s.code);
                remove.textContent = '×';
                remove.addEventListener('click', function () {
                    selected = selected.filter(function (x) {
                        return x.code !== s.code;
                    });
                    renderChips();
                    renderTable();
                });
                chip.appendChild(remove);
                chips.appendChild(chip);
            });
            var joined = selected.map(function (s) {
                return s.code;
            }).join(':');
            dialog.querySelector('[data-cl-sku-preview]').textContent = joined || '—';
            dialog.querySelector('[data-cl-sku-empty]').hidden = selected.length > 0;
            dialog.querySelector('[data-cl-sku-hint]').hidden = selected.length > 0;
            dialog.querySelector('[data-cl-sku-submit]').disabled = selected.length === 0;
            dialog.querySelector('[data-cl-sku-value]').value = joined;
        }

        function addManual() {
            var code = manual.value.trim();
            if (!code || code.indexOf(':') >= 0 || selected.some(function (s) {
                return s.code === code;
            })) {
                return;
            }
            selected.push({ code: code, foreign: !data.some(function (p) {
                return p.mfn === code;
            }) });
            manual.value = '';
            renderChips();
            renderTable();
        }

        dialog.addEventListener('cl:dialog-open', function (event) {
            var trigger = event.detail.trigger;
            fillContext(dialog, trigger);
            itemCategory = trigger.getAttribute('data-category') || '';
            search.value = '';
            manual.value = '';
            selected = codesOf(trigger).map(function (code) {
                return { code: code, foreign: true };
            });
            if (!catalog.value && catalog.options.length === 2) {
                catalog.selectedIndex = 1;
            }
            renderChips();
            load(catalog.value, refresh);
        });
        catalog.addEventListener('change', function () {
            load(catalog.value, refresh);
        });
        category.addEventListener('change', refreshLabels);
        label.addEventListener('change', renderTable);
        search.addEventListener('input', renderTable);
        // Enter already filters live via 'input'; without this it also submits the form (the item's own codes
        // are preselected, so the submit button is enabled) and saves instead of filtering.
        search.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
            }
        });
        dialog.querySelector('[data-cl-sku-add]').addEventListener('click', addManual);
        manual.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                addManual();
            }
        });
        form.addEventListener('submit', function (event) {
            if (selected.length === 0) {
                event.preventDefault();
            }
        });
    })();

    // --- Assign supplier ------------------------------------------------------------------------------------------
    (function () {
        var dialog = document.getElementById('assign-supplier-dialog');
        if (!dialog) {
            return;
        }
        var tax = 1;

        function form() {
            // async-form.js replaces the form after a refused save; look it up every time
            return dialog.querySelector('form');
        }

        function hint() {
            var f = form();
            var input = f.querySelector('input[name="cost"]');
            var type = f.querySelector('input[name="priceType"]');
            var target = f.querySelector('[data-cl-cost-hint]');
            var amount = Number(input.value.replace(/\s/g, '').replace(',', '.'));
            if (input.value.trim() === '' || isNaN(amount) || tax < 1) {
                target.textContent = '';
                return;
            }
            var gross = type.value === 'gross';
            var other = gross ? amount / tax : amount * tax;
            var vat = Math.round((tax - 1) * 10000) / 100;
            target.textContent = target.getAttribute(gross ? 'data-template-net' : 'data-template-gross')
                .replace('{0}', format(other)).replace('{1}', String(vat));
        }

        function mfnOptions(codes) {
            var list = form().querySelector('[data-cl-mfn-options]');
            var input = form().querySelector('input[name="manufacturerCode"]');
            list.replaceChildren();
            codes.forEach(function (code) {
                var item = document.createElement('li');
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'cl-chip';
                button.textContent = code;
                button.setAttribute('aria-pressed', String(input.value === code));
                button.addEventListener('click', function () {
                    input.value = code;
                    list.querySelectorAll('button').forEach(function (other) {
                        other.setAttribute('aria-pressed', String(other === button));
                    });
                });
                item.appendChild(button);
                list.appendChild(item);
            });
        }

        dialog.addEventListener('cl:dialog-open', function (event) {
            var trigger = event.detail.trigger;
            var f = form();
            tax = parseFloat(trigger.getAttribute('data-tax')) || 1;
            f.querySelectorAll('[data-cl-item-id]').forEach(function (input) {
                input.value = trigger.getAttribute('data-item-id') || '';
            });
            f.querySelector('input[name="manufacturerCode"]').value = '';
            f.querySelector('input[name="ean"]').value = '';
            f.querySelector('input[name="cost"]').value = '';
            f.querySelector('input[name="priceType"]').value = 'net';
            f.querySelectorAll('[data-cl-cost-type] button').forEach(function (button) {
                button.setAttribute('aria-pressed', String(button.getAttribute('data-value') === 'net'));
            });
            var select = f.querySelector('select[name="supplier"]');
            select.selectedIndex = 0;
            select.dispatchEvent(new Event('change', { bubbles: true }));
            var summary = f.querySelector('[data-cl-error-summary]');
            if (summary) {
                summary.remove();
            }
            mfnOptions(codesOf(trigger));
            hint();
        });
        dialog.addEventListener('click', function (event) {
            var button = event.target.closest && event.target.closest('[data-cl-cost-type] button[data-value]');
            if (!button) {
                return;
            }
            form().querySelector('input[name="priceType"]').value = button.getAttribute('data-value');
            form().querySelectorAll('[data-cl-cost-type] button').forEach(function (other) {
                other.setAttribute('aria-pressed', String(other === button));
            });
            hint();
        });
        dialog.addEventListener('input', function (event) {
            if (event.target.name === 'cost') {
                hint();
            }
        });
    })();

    // --- Assign from warehouse ------------------------------------------------------------------------------------
    (function () {
        var dialog = document.getElementById('assign-warehouse-dialog');
        if (!dialog) {
            return;
        }
        var form = dialog.querySelector('form');
        var table = dialog.querySelector('table');
        var rows = dialog.querySelector('[data-cl-warehouse-rows]');
        var search = dialog.querySelector('[data-cl-warehouse-search]');
        var cache = {};
        var data = [];
        var chosen = '';

        function load(category, done) {
            if (cache[category]) {
                data = cache[category];
                done();
                return;
            }
            fetch(form.getAttribute('data-url') + '?category=' + encodeURIComponent(category))
                .then(function (response) {
                    return response.ok ? response.json() : [];
                })
                .catch(function () {
                    return [];
                })
                .then(function (loaded) {
                    cache[category] = loaded;
                    data = loaded;
                    done();
                });
        }

        function render() {
            var term = search.value.trim().toLowerCase();
            var shown = data.filter(function (i) {
                return !term || [i.name, i.mfn, i.serialNo].some(function (value) {
                    return (value || '').toLowerCase().indexOf(term) >= 0;
                });
            });
            rows.replaceChildren();
            shown.forEach(function (i) {
                var row = document.createElement('tr');
                row.classList.toggle('is-selected', i.itemId === chosen);
                var pick = document.createElement('td');
                pick.className = 'cl-table-check';
                var radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = 'assign-warehouse-pick';
                radio.checked = i.itemId === chosen;
                radio.setAttribute('aria-label', i.name || i.mfn);
                pick.appendChild(radio);
                row.appendChild(pick);
                cell(row, i.name);
                cell(row, i.mfn, 'cl-table-code');
                cell(row, i.serialNo, 'cl-table-code');
                cell(row, i.qty, 'is-numeric');
                var state = cell(row, '');
                var delivered = i.status === 'Delivered';
                var pill = document.createElement('span');
                pill.className = 'cl-status ' + (delivered ? 'is-ok' : 'is-info');
                pill.textContent = table.getAttribute(delivered ? 'data-status-delivered' : 'data-status-ordered');
                state.appendChild(pill);
                if (i.condition && i.condition !== 'Sealed') {
                    var condition = document.createElement('span');
                    condition.className = 'cl-status ' + (i.condition === 'Damaged' ? 'is-bad' : 'is-warn');
                    condition.textContent = table.getAttribute('data-condition-' + i.condition.toLowerCase()) || i.condition;
                    state.appendChild(document.createTextNode(' '));
                    state.appendChild(condition);
                }
                row.addEventListener('click', function () {
                    chosen = i.itemId;
                    form.querySelector('[data-cl-warehouse-item]').value = chosen;
                    render();
                });
                rows.appendChild(row);
            });
            dialog.querySelector('[data-cl-warehouse-none]').hidden = shown.length > 0;
            dialog.querySelector('[data-cl-warehouse-submit]').disabled = !chosen;
            dialog.querySelector('[data-cl-warehouse-hint]').hidden = !!chosen;
        }

        dialog.addEventListener('cl:dialog-open', function (event) {
            var trigger = event.detail.trigger;
            fillContext(dialog, trigger);
            search.value = '';
            chosen = '';
            form.querySelector('[data-cl-warehouse-item]').value = '';
            load(trigger.getAttribute('data-category') || '', render);
        });
        search.addEventListener('input', render);
    })();

    // --- Split bundle ---------------------------------------------------------------------------------------------
    (function () {
        var dialog = document.getElementById('split-group-dialog');
        if (!dialog) {
            return;
        }
        var body = dialog.querySelector('[data-cl-split-rows]');
        var state = { qty: 1, total: 0 };

        function input(name, value, attributes) {
            var field = document.createElement('input');
            field.className = 'cl-input';
            field.name = name;
            field.value = value;
            Object.keys(attributes).forEach(function (key) {
                field.setAttribute(key, attributes[key]);
            });
            return field;
        }

        // Spring binds rows[i] by index; a removed row must not leave a gap
        function renumber() {
            Array.prototype.forEach.call(body.rows, function (row, index) {
                row.querySelectorAll('[name^="rows["]').forEach(function (field) {
                    field.name = field.name.replace(/rows\[\d+]/, 'rows[' + index + ']');
                });
                row.querySelector('[data-cl-split-remove]').hidden = body.rows.length <= 2;
            });
        }

        function addRow(qty, sku, name) {
            var row = document.createElement('tr');
            var qtyCell = document.createElement('td');
            qtyCell.appendChild(input('rows[0].qty', qty || state.qty, { type: 'number', min: '1', required: '', 'data-split-qty': '' }));
            var qtyHint = document.createElement('p');
            qtyHint.className = 'cl-help';
            qtyHint.setAttribute('data-split-qty-hint', '');
            qtyCell.appendChild(qtyHint);
            row.appendChild(qtyCell);
            var skuCell = document.createElement('td');
            skuCell.appendChild(input('rows[0].sku', sku || '', { type: 'text', required: '' }));
            row.appendChild(skuCell);
            var nameCell = document.createElement('td');
            nameCell.appendChild(input('rows[0].name', name || '', { type: 'text', required: '' }));
            row.appendChild(nameCell);
            var priceCell = document.createElement('td');
            priceCell.appendChild(input('rows[0].price', '0.00', { type: 'number', min: '0', step: '0.01', required: '', 'data-split-price': '' }));
            row.appendChild(priceCell);
            cell(row, '', 'is-numeric').setAttribute('data-split-total', '');
            var actions = document.createElement('td');
            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'cl-link-button is-danger';
            remove.setAttribute('data-cl-split-remove', '');
            remove.setAttribute('aria-label', body.getAttribute('data-remove-label'));
            remove.textContent = body.getAttribute('data-remove');
            remove.addEventListener('click', function () {
                row.remove();
                renumber();
                recalc();
            });
            actions.appendChild(remove);
            row.appendChild(actions);
            row.addEventListener('input', recalc);
            body.appendChild(row);
            renumber();
        }

        function recalc() {
            var allocated = 0;
            Array.prototype.forEach.call(body.rows, function (row) {
                var qty = parseInt(row.querySelector('[data-split-qty]').value, 10) || 0;
                var price = parseFloat(row.querySelector('[data-split-price]').value) || 0;
                allocated += qty * price;
                row.querySelector('[data-split-total]').textContent = format(qty * price);
                var hint = row.querySelector('[data-split-qty-hint]');
                var divisible = qty % state.qty === 0;
                hint.textContent = divisible
                    ? body.getAttribute('data-qty-hint').replace('{0}', String(qty / state.qty)).replace('{1}', String(state.qty))
                    : body.getAttribute('data-qty-warning').replace('{0}', String(state.qty));
                hint.classList.toggle('is-warn', !divisible);
            });
            var difference = state.total - allocated;
            var balanced = Math.abs(difference) < 0.005;
            dialog.querySelector('[data-cl-split-allocated]').textContent = format(allocated);
            dialog.querySelector('[data-cl-split-difference]').textContent = format(difference);
            var bar = dialog.querySelector('[data-cl-split-bar]');
            bar.classList.toggle('is-ok', balanced);
            bar.classList.toggle('is-bad', !balanced);
            dialog.querySelector('[data-cl-split-submit]').disabled = !balanced || body.rows.length < 2;
        }

        function distribute() {
            var current = Array.prototype.map.call(body.rows, function (row) {
                return {
                    row: row,
                    qty: parseInt(row.querySelector('[data-split-qty]').value, 10) || 0,
                    price: parseFloat(row.querySelector('[data-split-price]').value) || 0
                };
            });
            var weights = current.map(function (c) {
                return c.qty * c.price;
            });
            if (weights.every(function (w) {
                return w === 0;
            })) {
                weights = current.map(function (c) {
                    return c.qty;
                });
            }
            var sum = weights.reduce(function (a, b) {
                return a + b;
            }, 0);
            if (sum <= 0) {
                return;
            }
            current.forEach(function (c, i) {
                var price = c.qty > 0 ? (state.total * weights[i] / sum) / c.qty : 0;
                c.row.querySelector('[data-split-price]').value = (Math.round(price * 100) / 100).toFixed(2);
            });
            recalc();
        }

        dialog.addEventListener('cl:dialog-open', function (event) {
            var trigger = event.detail.trigger;
            var preview = (window.CL_SPLIT_PREVIEWS || {})[trigger.getAttribute('data-item-id')];
            fillContext(dialog, trigger);
            body.replaceChildren();
            if (!preview) {
                return;
            }
            state.qty = preview.qty;
            state.total = preview.totalPrice;
            dialog.querySelector('[data-cl-split-total]').textContent = format(preview.totalPrice);
            preview.components.forEach(function (component) {
                addRow(component.qty, component.sku, component.name);
            });
            recalc();
        });
        dialog.querySelector('[data-cl-split-add]').addEventListener('click', function () {
            addRow();
            recalc();
        });
        dialog.querySelector('[data-cl-split-distribute]').addEventListener('click', distribute);
    })();

    // --- Serial numbers: Enter moves on instead of submitting half-filled numbers --------------------------------------
    (function () {
        var form = document.querySelector('form[data-cl-serials]');
        if (form) {
            form.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' && event.target.tagName === 'INPUT') {
                    event.preventDefault();
                }
            });
        }
    })();

    // --- Move to an existing order: preview of the target ----------------------------------------------------------
    (function () {
        var dialog = document.getElementById('move-dialog');
        if (!dialog) {
            return;
        }
        var field = dialog.querySelector('[data-cl-move-target]');
        var preview = dialog.querySelector('[data-cl-move-preview]');
        var submit = dialog.querySelector('[data-cl-move-submit]');
        var timer = null;
        var sequence = 0;

        // The field starts disabled in the markup (see item-dialogs.html); this script running at all proves
        // scripting is enabled, so items.html's <noscript> fallback field is not part of the DOM and only this
        // one reaches the server.
        field.disabled = false;

        function checkedCount() {
            return document.querySelectorAll('#order-items-form input[data-cl-select-row]:checked').length;
        }

        function message(text, tone) {
            var alert = document.createElement('p');
            alert.className = 'cl-help' + (tone ? ' ' + tone : '');
            alert.textContent = text;
            preview.replaceChildren(alert);
        }

        function show(target) {
            var card = document.createElement('div');
            card.className = 'cl-card is-flat is-status';
            var head = document.createElement('div');
            head.className = 'cl-card-head';
            var title = document.createElement('h3');
            title.className = 'cl-card-title';
            title.textContent = dialog.getAttribute('data-title').replace('{0}', target.shortId);
            var pill = document.createElement('span');
            pill.className = 'cl-status ' + (target.statusTone || 'is-neutral');
            pill.textContent = target.statusLabel;
            head.appendChild(title);
            head.appendChild(pill);
            card.appendChild(head);
            var list = document.createElement('dl');
            list.className = 'cl-kv is-column';
            // target.amount already carries the currency (general.currency.amount, e.g. "5 629,99 PLN")
            [['data-client', target.clientName], ['data-items', String(target.items)], ['data-amount', target.amount]]
                .forEach(function (pair) {
                    var row = document.createElement('div');
                    var dt = document.createElement('dt');
                    dt.textContent = dialog.getAttribute(pair[0]);
                    var dd = document.createElement('dd');
                    dd.textContent = pair[1] || '—';
                    row.appendChild(dt);
                    row.appendChild(dd);
                    list.appendChild(row);
                });
            card.appendChild(list);
            var effect = document.createElement('p');
            effect.className = target.canReceiveItems ? 'cl-dialog-effect' : 'cl-help is-warn';
            effect.textContent = target.canReceiveItems
                ? dialog.getAttribute('data-effect').replace('{0}', String(checkedCount()))
                : target.reason;
            preview.replaceChildren(card, effect);
            submit.disabled = !target.canReceiveItems;
        }

        function lookup() {
            var value = field.value.trim();
            var mine = ++sequence;
            submit.disabled = true;
            if (value.length < 4) {
                preview.replaceChildren();
                return;
            }
            fetch(dialog.getAttribute('data-url') + '?q=' + encodeURIComponent(value), { headers: { Accept: 'application/json' } })
                .then(function (response) {
                    if (mine !== sequence) {
                        return;
                    }
                    if (response.status === 404) {
                        message(dialog.getAttribute('data-not-found'), 'is-warn');
                        return;
                    }
                    return response.json().then(function (target) {
                        if (response.ok) {
                            show(target);
                        } else {
                            message(target.reason, 'is-warn');
                        }
                    });
                })
                .catch(function () {
                    message(dialog.getAttribute('data-not-found'), 'is-warn');
                });
        }

        field.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(lookup, 300);
        });
        dialog.addEventListener('cl:dialog-open', function () {
            field.value = '';
            preview.replaceChildren();
            submit.disabled = true;
        });
    })();
})();
