// The items table of the offer details page (offers/details/items.html), inside form#offer-items.
// - Order and variant groups: a group is its head row, its items and an end row added here (the drop zone that keeps an
//   item dragged above it inside the group). Rows are dragged by their handle (Sortable, a group as a whole and never
//   into another group) or moved one step with "Przenieś wyżej/niżej" in their menu or the arrow keys on the handle.
//   An item's variantGroupId is the name of the head above it; a group name must be filled in and unique (ignoring
//   case), else the error shows at the name and the save stops. Ungrouping keeps the items.
// - The chosen variant of a group is its first item marked variantSelected, else its first item (Basket.chosenVariant);
//   the others are outside the total.
// - Totals: each row and the summary are recounted as the fields change, with the server's formulas
//   (OfferDetailsPageFactory.summary, ItemMargin): total = chosen items (qty × price) + delivery, cost = chosen items
//   (qty × cost), gross profit = total − cost, net = gross / VAT rate; amounts rounded half up to grosze as Money.format.
// - "Edytuj pozycję" fills its dialog from the row's hidden fields; "Zastosuj" writes them back and refreshes the row.
// - Before the form is sent the rows are renumbered basketItems[0..n] in their order on screen, and an emptied
//   quantity or price becomes 0, as it does when the field is left.
// window.CL_offerLines gives offer-details.js the same preparation for a save it sends itself, the recount after a
// new delivery price and the position an item will have once the server has saved the table.
(function () {
    'use strict';

    var form = document.getElementById('offer-items');
    var body = form && form.querySelector('tbody[data-cl-lines]');
    if (!body) {
        return;
    }
    var table = body.closest('table');
    var summary = document.getElementById('summary');
    var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    var addedGroups = 0;

    function fill(text, values) {
        return (text || '').replace(/\{(\w+)\}/g, function (whole, key) {
            return values && values[key] !== undefined ? values[key] : whole;
        });
    }

    function message(name, values) {
        return fill(table.getAttribute('data-msg-' + name), values);
    }

    function rows() {
        return Array.prototype.slice.call(body.children);
    }

    function isItem(row) {
        return row.hasAttribute('data-cl-line');
    }

    function isHead(row) {
        return row.hasAttribute('data-cl-line-group');
    }

    function isFoot(row) {
        return row.hasAttribute('data-cl-group-foot');
    }

    function field(row, name) {
        return row.querySelector('input[data-cl-field="' + name + '"]');
    }

    function qtyInput(row) {
        return row.querySelector('input[name$=".qty"]');
    }

    function priceInput(row) {
        return row.querySelector('input[name$=".unitPrice"]');
    }

    function nameInput(head) {
        return head.querySelector('[data-cl-group-name]');
    }

    function groupOf(head) {
        var items = [];
        var row = head.nextElementSibling;
        while (row && !isFoot(row)) {
            items.push(row);
            row = row.nextElementSibling;
        }
        return { items: items, foot: row };
    }

    function headOf(foot) {
        var row = foot.previousElementSibling;
        while (row && !isHead(row)) {
            row = row.previousElementSibling;
        }
        return row;
    }

    function createFoot() {
        return form.querySelector('template[data-cl-group-foot-template]').content.firstElementChild.cloneNode(true);
    }

    function changed() {
        form.dispatchEvent(new CustomEvent('cl:lines-changed', { bubbles: true }));
    }

    // --- amounts, as Money.format and ItemMargin round them

    // Half up on the decimal the number reads as (BigDecimal.valueOf), so 2.675 becomes 2.68 as on the server.
    function roundHalfUp(value, digits) {
        var places = digits === undefined ? 2 : digits;
        var abs = Math.abs(value);
        var text = String(abs);
        var rounded = text.indexOf('e') >= 0
            ? Math.round(abs * Math.pow(10, places)) / Math.pow(10, places)
            : Number(Math.round(Number(text + 'e' + places)) + 'e-' + places);
        return value < 0 ? -rounded : rounded;
    }

    function divideHalfUp(numerator, denominator) {
        var quotient = Math.floor((2 * Math.abs(numerator) + denominator) / (2 * denominator));
        return numerator < 0 ? -quotient : quotient;
    }

    function number(input) {
        var value = input ? parseFloat(input.value) : NaN;
        return isFinite(value) ? value : 0;
    }

    function qtyOf(row) {
        var value = parseInt(qtyInput(row).value, 10);
        return isFinite(value) ? value : 0;
    }

    function vatRate() {
        var rate = summary ? parseFloat(summary.getAttribute('data-cl-vat-rate')) : NaN;
        return isFinite(rate) && rate > 0 ? rate : 1.23;
    }

    function amount(value) {
        return window.CL_formatAmount(roundHalfUp(value));
    }

    function money(value) {
        return window.CL_formatMoney(roundHalfUp(value));
    }

    function percent(tenths) {
        var text = (Math.abs(tenths) / 10).toFixed(1).replace('.', ',');
        return tenths < 0 ? '−' + text : text;
    }

    // ItemMargin.of: price and cost rounded to grosze, the margin to a tenth of a percent, the net profit to grosze
    function marginOf(price, cost) {
        if (cost <= 0) {
            return { tone: 'unknown', percent: null };
        }
        var priceCents = Math.round(roundHalfUp(price) * 100);
        var costCents = Math.round(roundHalfUp(cost) * 100);
        var profit = priceCents - costCents;
        return {
            tone: profit < 0 ? 'loss' : 'ok',
            percent: priceCents > 0 ? percent(divideHalfUp(profit * 1000, priceCents)) : null,
            profit: profit / 100,
            net: divideHalfUp(profit * 100, Math.round(vatRate() * 100)) / 100,
            cost: costCents / 100
        };
    }

    // what the table save drops (OfferDetailsPageFactory.droppedOnSave)
    function dropped(row) {
        return !field(row, 'name').value.trim() || !field(row, 'mfn').value.trim() || qtyOf(row) <= 0
            || number(priceInput(row)) < 0;
    }

    function recountRow(row) {
        var qty = qtyOf(row);
        var price = number(priceInput(row));
        var cost = number(field(row, 'unitCost'));
        row.querySelector('[data-cl-line-total]').textContent = amount(qty * price);
        row.querySelector('[data-cl-line-warn]').hidden = !dropped(row);
        if (field(row, 'service').value === 'true') {
            return;
        }
        var hint = row.querySelector('[data-cl-line-cost]');
        hint.textContent = cost > 0 ? message('line-cost', [window.CL_formatAmount(roundHalfUp(cost))]) : message('line-no-cost');
        hint.classList.toggle('is-warn', !(cost > 0));

        var margin = marginOf(price, cost);
        var label = row.querySelector('[data-cl-line-margin]');
        label.textContent = margin.percent === null ? message('line-margin-unknown') : message('line-margin', [margin.percent]);
        label.classList.toggle('is-bad', margin.percent !== null && margin.tone === 'loss');
        label.classList.toggle('is-low', margin.percent === null
            || (margin.tone !== 'loss' && (price - cost) * 10 < price));

        var mark = row.querySelector('.cl-margin');
        if (!mark) {
            return;
        }
        var text = (margin.tone === 'unknown' ? message('margin-unknown')
            : message('margin-' + margin.tone, [margin.percent === null ? '—' : margin.percent,
                window.CL_formatMoney(margin.profit), window.CL_formatMoney(margin.cost), window.CL_formatMoney(margin.net)]))
            + '\n' + (mark.getAttribute('data-cl-market-text') || '');
        mark.setAttribute('data-tooltip', text);
        mark.setAttribute('aria-label', text);
        var loss = margin.tone === 'loss';
        if (mark.classList.contains('is-loss') !== loss) {
            // Font Awesome has drawn the icon as an <svg> already: a new <i> is drawn again by its observer
            var icon = document.createElement('i');
            icon.setAttribute('aria-hidden', 'true');
            icon.className = 'fas ' + (loss ? 'fa-exclamation-circle' : 'fa-info-circle');
            mark.replaceChildren(icon);
        }
        ['ok', 'loss', 'unknown'].forEach(function (tone) {
            mark.classList.toggle('is-' + tone, tone === margin.tone);
        });
    }

    function recount() {
        if (!summary) {
            return;
        }
        var items = 0;
        var cost = 0;
        rows().filter(isItem).forEach(function (row) {
            if (row.classList.contains('is-unpicked')) {
                return;
            }
            var qty = qtyOf(row);
            items += qty * number(priceInput(row));
            cost += qty * number(field(row, 'unitCost'));
        });
        var deliveryHolder = document.querySelector('[data-cl-delivery-price]');
        var delivery = deliveryHolder ? parseFloat(deliveryHolder.getAttribute('data-cl-delivery-price')) || 0 : 0;
        var total = items + delivery;
        var profitGross = total - cost;
        var values = {
            items: money(items),
            total: money(total),
            net: money(total / vatRate()),
            cost: money(cost),
            // OfferDetailsPageFactory rounds both profits with Math.round before formatting them
            profitGross: window.CL_formatMoney(Math.round(profitGross * 100) / 100),
            profitNet: window.CL_formatMoney(Math.round(profitGross / vatRate() * 100) / 100)
        };
        Object.keys(values).forEach(function (key) {
            document.querySelectorAll('output[data-cl-sum="' + key + '"]').forEach(function (output) {
                output.textContent = values[key];
            });
        });
    }

    // --- groups and order

    function setDisabled(button, disabled) {
        if (!button) {
            return;
        }
        if (disabled) {
            button.setAttribute('aria-disabled', 'true');
        } else {
            button.removeAttribute('aria-disabled');
        }
    }

    function updateMoves() {
        rows().forEach(function (row) {
            if (isItem(row)) {
                setDisabled(row.querySelector('[data-cl-line-move="up"]'), !row.previousElementSibling);
                setDisabled(row.querySelector('[data-cl-line-move="down"]'), !row.nextElementSibling);
            } else if (isHead(row)) {
                setDisabled(row.querySelector('[data-cl-group-move="up"]'), !row.previousElementSibling);
                setDisabled(row.querySelector('[data-cl-group-move="down"]'), !groupOf(row).foot.nextElementSibling);
            }
        });
    }

    function showGroupError(head, key) {
        var error = head.querySelector('[data-cl-group-error]');
        var input = nameInput(head);
        input.classList.toggle('is-invalid', !!key);
        if (key) {
            input.setAttribute('aria-invalid', 'true');
            var icon = document.createElement('i');
            icon.className = 'fas fa-exclamation-circle';
            icon.setAttribute('aria-hidden', 'true');
            var text = document.createElement('span');
            text.textContent = message(key);
            error.replaceChildren(icon, ' ', text);
        } else {
            input.removeAttribute('aria-invalid');
            error.replaceChildren();
        }
        error.hidden = !key;
    }

    // the first group name in error, or null; errors show at each name
    function validateGroups() {
        var heads = rows().filter(isHead);
        var counts = {};
        heads.forEach(function (head) {
            var key = nameInput(head).value.trim().toLowerCase();
            counts[key] = (counts[key] || 0) + 1;
        });
        var first = null;
        heads.forEach(function (head) {
            var key = nameInput(head).value.trim().toLowerCase();
            var problem = key === '' ? 'group-required' : counts[key] > 1 ? 'group-duplicate' : null;
            if (head.getAttribute('data-cl-group-problem') !== (problem || '')) {
                showGroupError(head, problem);
                head.setAttribute('data-cl-group-problem', problem || '');
            }
            if (problem && !first) {
                first = nameInput(head);
            }
        });
        return first;
    }

    function labelRow(row) {
        var name = field(row, 'name').value;
        row.querySelector('[data-cl-line-name]').textContent = name;
        row.querySelector('[data-cl-drag]').setAttribute('aria-label', message('line-drag', [name]));
        row.querySelector('.cl-table-edit').setAttribute('aria-label', message('line-edit', [name]));
        qtyInput(row).setAttribute('aria-label', message('line-qty', [name]));
        priceInput(row).setAttribute('aria-label', message('line-price', [name]));
        row.querySelector('.cl-menu > summary').setAttribute('aria-label', message('line-menu', [name]));
    }

    function labelGroup(head) {
        var name = nameInput(head).value.trim();
        head.querySelector('[data-cl-drag]').setAttribute('aria-label', message('group-drag', [name]));
        head.querySelector('.cl-menu > summary').setAttribute('aria-label', message('group-menu', [name]));
    }

    // Writes each item's group from the head above it and shows which variant counts; recount = the totals as well.
    function refresh(withTotals) {
        var group = null;
        var groups = [];
        rows().forEach(function (row) {
            if (isHead(row)) {
                group = { head: row, name: nameInput(row).value.trim(), items: [] };
                row.setAttribute('data-group', group.name);
                groups.push(group);
                return;
            }
            if (isFoot(row)) {
                row.classList.toggle('is-empty', !!group && !group.items.length);
                group = null;
                return;
            }
            field(row, 'variantGroupId').value = group ? group.name : '';
            if (group) {
                row.setAttribute('data-group', group.name);
                group.items.push(row);
            } else {
                row.removeAttribute('data-group');
                row.classList.remove('is-unpicked');
                row.querySelector('[data-cl-mark-selected]').hidden = true;
            }
            row.classList.toggle('is-variant', !!group);
            row.querySelector('[data-cl-line-ungroup-item]').hidden = !group;
        });
        groups.forEach(function (each) {
            var chosen = each.items.filter(function (row) {
                return field(row, 'variantSelected').value === 'true';
            })[0] || each.items[0];
            each.items.forEach(function (row) {
                row.classList.toggle('is-unpicked', row !== chosen);
                row.querySelector('[data-cl-mark-selected]').hidden = row !== chosen;
            });
            each.head.querySelector('[data-cl-group-count]').textContent = message('group-picks', { n: each.items.length });
        });
        validateGroups();
        updateMoves();
        if (withTotals) {
            recount();
        }
    }

    function afterMove(focusTarget) {
        refresh(true);
        changed();
        if (focusTarget) {
            focusTarget.focus();
            focusTarget.scrollIntoView({ block: 'nearest' });
        }
    }

    function moveItem(row, direction) {
        // one step over whatever is next: an item swaps, a group's head or end lets the item out of or into the group
        if (direction === 'up' && row.previousElementSibling) {
            row.previousElementSibling.before(row);
            return true;
        }
        if (direction === 'down' && row.nextElementSibling) {
            row.nextElementSibling.after(row);
            return true;
        }
        return false;
    }

    function moveGroup(head, direction) {
        var group = groupOf(head);
        var block = [head].concat(group.items, [group.foot]);
        if (direction === 'up') {
            var before = head.previousElementSibling;
            if (!before) {
                return false;
            }
            var target = isFoot(before) ? headOf(before) : before;
            block.forEach(function (row) {
                target.before(row);
            });
            return true;
        }
        var after = group.foot.nextElementSibling;
        if (!after) {
            return false;
        }
        var anchor = isHead(after) ? groupOf(after).foot : after;
        block.forEach(function (row) {
            anchor.after(row);
            anchor = row;
        });
        return true;
    }

    function menuSummary(row) {
        return row.querySelector('.cl-menu > summary');
    }

    function uniqueGroupName(base) {
        var taken = rows().filter(isHead).map(function (head) {
            return nameInput(head).value.trim().toLowerCase();
        });
        if (taken.indexOf(base.toLowerCase()) < 0) {
            return base;
        }
        var n = 2;
        while (taken.indexOf((base + ' ' + n).toLowerCase()) >= 0) {
            n++;
        }
        return base + ' ' + n;
    }

    function addGroup() {
        var head = form.querySelector('template[data-cl-group-template]').content.firstElementChild.cloneNode(true);
        var key = 'offer-group-added-' + (++addedGroups);
        head.querySelectorAll('[id], [for], [aria-describedby]').forEach(function (element) {
            ['id', 'for', 'aria-describedby'].forEach(function (attribute) {
                if (element.hasAttribute(attribute)) {
                    element.setAttribute(attribute, element.getAttribute(attribute).replace(/offer-group-new/g, key));
                }
            });
        });
        head.querySelectorAll('[data-cl-lines-reveal][hidden]').forEach(function (control) {
            control.hidden = false;
        });
        var input = nameInput(head);
        input.value = uniqueGroupName(message('group-new'));
        body.append(head, createFoot());
        labelGroup(head);
        refresh(false);
        head.scrollIntoView({ block: 'nearest' });
        input.focus();
        input.select();
    }

    function ungroup(head) {
        var group = groupOf(head);
        head.remove();
        group.foot.remove();
        var focus = group.items.length ? menuSummary(group.items[0]) : form.querySelector('[data-cl-group-add]');
        afterMove(focus);
    }

    function takeOutOfGroup(row) {
        var foot = row.nextElementSibling;
        while (foot && !isFoot(foot)) {
            foot = foot.nextElementSibling;
        }
        if (foot) {
            foot.after(row);
            afterMove(menuSummary(row));
        }
    }

    function initSortable() {
        if (typeof window.Sortable !== 'function') {
            return;
        }
        // an item dropped next to a group's rows lands in the group: only an item may, a group never goes into another
        function insideGroup(related, willInsertAfter) {
            if (isItem(related)) {
                return related.classList.contains('is-variant');
            }
            if (isHead(related)) {
                return willInsertAfter;
            }
            if (isFoot(related)) {
                return !willInsertAfter;
            }
            return false;
        }
        new Sortable(body, {
            handle: '[data-cl-drag]',
            draggable: 'tr',
            filter: '[data-cl-group-foot]',
            animation: reducedMotion.matches ? 0 : 150,
            onStart: function (event) {
                table.classList.add('is-dragging');
                if (!isHead(event.item)) {
                    return;
                }
                var group = groupOf(event.item);
                group.items.concat([group.foot]).forEach(function (row) {
                    row.hidden = true;
                    row.setAttribute('data-cl-drag-hidden', '');
                });
            },
            onMove: function (event) {
                return isHead(event.dragged) ? !insideGroup(event.related, event.willInsertAfter) : true;
            },
            onEnd: function (event) {
                table.classList.remove('is-dragging');
                if (isHead(event.item)) {
                    var anchor = event.item;
                    body.querySelectorAll('[data-cl-drag-hidden]').forEach(function (row) {
                        row.hidden = false;
                        row.removeAttribute('data-cl-drag-hidden');
                        anchor.after(row);
                        anchor = row;
                    });
                }
                afterMove(null);
            }
        });
    }

    // --- the "Edytuj pozycję" dialog

    function initEditDialog() {
        var dialog = document.getElementById('item-edit-dialog');
        var editForm = dialog && dialog.querySelector('form[data-cl-item-edit-form]');
        if (!editForm) {
            return;
        }
        var editing = null;

        function input(name) {
            return editForm.querySelector('[data-cl-item-field="' + name + '"]');
        }

        function clearError(control) {
            control.classList.remove('is-invalid');
            control.removeAttribute('aria-invalid');
            var error = document.getElementById(control.id + '-error');
            if (error) {
                error.remove();
            }
        }

        function showError(control, text) {
            control.classList.add('is-invalid');
            control.setAttribute('aria-invalid', 'true');
            var error = document.createElement('p');
            error.className = 'cl-field-error';
            error.id = control.id + '-error';
            var icon = document.createElement('i');
            icon.className = 'fas fa-exclamation-circle';
            icon.setAttribute('aria-hidden', 'true');
            var span = document.createElement('span');
            span.textContent = text;
            error.append(icon, ' ', span);
            control.after(error);
        }

        function selectCategory(value) {
            var select = input('category');
            select.querySelectorAll('option[data-cl-item-custom]').forEach(function (option) {
                option.remove();
            });
            var known = Array.prototype.some.call(select.options, function (option) {
                return option.value === value;
            });
            if (value && !known) {
                // a category from outside the catalogs (an old item, an import) stays as it is, as in the old row
                var custom = new Option(value, value);
                custom.setAttribute('data-cl-item-custom', '');
                select.options[0].after(custom);
            }
            select.value = value;
        }

        dialog.addEventListener('cl:dialog-open', function (event) {
            editing = event.detail.trigger.closest('tr[data-cl-line]');
            if (!editing) {
                return;
            }
            ['name', 'mfn', 'unitCost'].forEach(function (name) {
                clearError(input(name));
                input(name).value = field(editing, name).value;
            });
            selectCategory(field(editing, 'category').value);
            input('consolidated').checked = field(editing, 'consolidated').value === 'true';
            dialog.querySelector('[data-cl-item-market]').textContent = editing.getAttribute('data-market') || '—';
            dialog.querySelector('[data-cl-item-market-stock]').textContent = editing.getAttribute('data-market-stock') || '';
        });

        // "Zastosuj" only changes the row; the item is saved with the table. Prevented here, so dialog.js does not lock
        // the button for a page load that never comes.
        editForm.addEventListener('submit', function (event) {
            event.preventDefault();
            if (!editing) {
                dialog.close();
                return;
            }
            var missing = [['name', 'name-required'], ['mfn', 'mfn-required']].filter(function (rule) {
                clearError(input(rule[0]));
                return !input(rule[0]).value.trim();
            });
            if (missing.length) {
                missing.forEach(function (rule) {
                    showError(input(rule[0]), editForm.getAttribute('data-msg-' + rule[1]));
                });
                input(missing[0][0]).focus();
                return;
            }
            var row = editing;
            field(row, 'name').value = input('name').value;
            field(row, 'mfn').value = input('mfn').value;
            field(row, 'category').value = input('category').value;
            field(row, 'unitCost').value = input('unitCost').value === '' ? '0' : input('unitCost').value;
            field(row, 'consolidated').value = input('consolidated').checked ? 'true' : 'false';
            renderSub(row);
            row.querySelector('[data-cl-mark-consolidated]').hidden = !input('consolidated').checked;
            labelRow(row);
            recountRow(row);
            recount();
            dialog.close();
            changed();
        });
    }

    function renderSub(row) {
        var category = field(row, 'category').value;
        var mfn = field(row, 'mfn').value;
        var sub = row.querySelector('[data-cl-line-sub]');
        sub.replaceChildren();
        if (category) {
            var categoryText = document.createElement('span');
            categoryText.setAttribute('data-cl-line-category', '');
            categoryText.textContent = category;
            sub.append(categoryText);
        }
        if (mfn) {
            if (category) {
                var separator = document.createElement('span');
                separator.className = 'cl-table-sep';
                separator.textContent = '·';
                sub.append(' ', separator, ' ');
            }
            var code = document.createElement('span');
            code.className = 'cl-code-nowrap';
            code.setAttribute('data-cl-line-mfn', '');
            code.textContent = mfn;
            var wrap = document.createElement('span');
            wrap.append(message('mfn-prefix') + ' ', code);
            sub.append(wrap);
        }
    }

    // --- before a save

    function zeroEmptyFields() {
        rows().filter(isItem).forEach(function (row) {
            [qtyInput(row), priceInput(row)].forEach(function (input) {
                if (input.value === '') {
                    input.value = '0';
                }
            });
        });
    }

    function renumber() {
        rows().filter(isItem).forEach(function (row, index) {
            row.querySelectorAll('[name]').forEach(function (element) {
                element.name = element.name.replace(/basketItems\[\d+\]/, 'basketItems[' + index + ']');
            });
        });
    }

    // null when the table can be sent (renumbered), else the group name to fix
    function prepare() {
        zeroEmptyFields();
        var invalid = validateGroups();
        if (invalid) {
            return invalid;
        }
        renumber();
        return null;
    }

    // --- wiring

    body.addEventListener('input', function (event) {
        var row = event.target.closest('tr[data-cl-line]');
        if (row && (event.target === qtyInput(row) || event.target === priceInput(row))) {
            recountRow(row);
            recount();
            return;
        }
        var head = event.target.closest('tr[data-cl-line-group]');
        if (head && event.target === nameInput(head)) {
            labelGroup(head);
            refresh(false);
        }
    });

    // an emptied quantity or price is 0, as before
    body.addEventListener('focusout', function (event) {
        var row = event.target.closest('tr[data-cl-line]');
        if (row && (event.target === qtyInput(row) || event.target === priceInput(row)) && event.target.value === '') {
            event.target.value = '0';
            event.target.dispatchEvent(new Event('input', { bubbles: true }));
        }
    });

    body.addEventListener('click', function (event) {
        var target = event.target.closest && event.target.closest('button');
        if (!target || target.getAttribute('aria-disabled') === 'true') {
            return;
        }
        var row = target.closest('tr[data-cl-line]');
        var head = target.closest('tr[data-cl-line-group]');
        if (row && target.hasAttribute('data-cl-line-move')) {
            if (moveItem(row, target.getAttribute('data-cl-line-move'))) {
                afterMove(menuSummary(row));
            }
        } else if (row && target.hasAttribute('data-cl-line-ungroup')) {
            takeOutOfGroup(row);
        } else if (head && target.hasAttribute('data-cl-group-move')) {
            if (moveGroup(head, target.getAttribute('data-cl-group-move'))) {
                afterMove(menuSummary(head));
            }
        } else if (head && target.hasAttribute('data-cl-ungroup')) {
            ungroup(head);
        }
    });

    // the handle moves its row (or group) with the arrow keys too, and keeps the focus
    body.addEventListener('keydown', function (event) {
        var handle = event.target.closest && event.target.closest('[data-cl-drag]');
        if (!handle || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) {
            return;
        }
        event.preventDefault();
        var direction = event.key === 'ArrowUp' ? 'up' : 'down';
        var row = handle.closest('tr');
        if (isHead(row) ? moveGroup(row, direction) : moveItem(row, direction)) {
            afterMove(handle);
        }
    });

    form.querySelectorAll('[data-cl-group-add]').forEach(function (button) {
        button.addEventListener('click', addGroup);
    });

    form.addEventListener('submit', function (event) {
        var invalid = prepare();
        if (invalid) {
            event.preventDefault();
            invalid.focus();
        }
    });

    form.querySelectorAll('[data-cl-lines-reveal][hidden]').forEach(function (control) {
        control.hidden = false;
    });
    rows().filter(isHead).forEach(function (head) {
        var name = head.getAttribute('data-group');
        var last = head;
        while (last.nextElementSibling && isItem(last.nextElementSibling)
            && last.nextElementSibling.getAttribute('data-group') === name) {
            last = last.nextElementSibling;
        }
        last.after(createFoot());
    });
    // the server rendered the totals from the stored amounts: they are recounted once something changes
    refresh(false);
    initSortable();
    initEditDialog();

    window.CL_offerLines = {
        prepare: prepare,
        recount: recount,
        // the index the item will have in the saved offer (the save drops incomplete items), or -1 when it is dropped
        savedIndex: function (row) {
            if (dropped(row)) {
                return -1;
            }
            var index = 0;
            for (var before = body.firstElementChild; before && before !== row; before = before.nextElementSibling) {
                if (isItem(before) && !dropped(before)) {
                    index++;
                }
            }
            return index;
        }
    };
})();
