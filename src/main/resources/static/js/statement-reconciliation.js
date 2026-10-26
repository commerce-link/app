// The reconciliation of an uploaded bank statement (payments-statement.html). The page holds everything: the proposals
// per operation in the hidden field "allocations" (JSON: operation number -> [{orderId, amount, fee}], amounts in
// grosze), the orders that still owe money (#statement-receivables) and the payout channels (#statement-channels).
// Ticking a row books its proposal; the assignment dialog and the payout dialog change a proposal and tick the row.
// Without JavaScript the ticked rows are booked with the proposals the page came with.
(function () {
    'use strict';

    var form = document.querySelector('[data-cl-statement]');
    if (!form) {
        return;
    }
    var allocationsField = document.getElementById('statement-allocations');
    var allocations = parse(allocationsField.value, {});
    var initial = parse(allocationsField.value, {});
    var receivables = parse(textOf('statement-receivables'), []);
    var channels = parse(textOf('statement-channels'), []);
    var byId = {};
    receivables.forEach(function (order) {
        byId[order.id] = order;
    });
    var currency = form.getAttribute('data-currency') || '{0}';

    function textOf(id) {
        var element = document.getElementById(id);
        return element ? element.textContent : '';
    }

    function parse(text, fallback) {
        try {
            return text ? JSON.parse(text) : fallback;
        } catch (e) {
            return fallback;
        }
    }

    function format(template, values) {
        return template.replace(/\{(\d+)\}/g, function (match, index) {
            return values[index] !== undefined ? values[index] : match;
        });
    }

    function plain(grosze) {
        var negative = grosze < 0;
        var value = Math.abs(Math.round(grosze));
        var whole = String(Math.floor(value / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0');
        var cents = String(value % 100);
        return (negative ? '\u2212' : '') + whole + ',' + (cents.length < 2 ? '0' + cents : cents);
    }

    function money(grosze) {
        return format(currency, [plain(grosze)]).replace(/ /g, '\u00a0');
    }

    // "159,00", "159.00", "1 249,00": one decimal mark, at most two decimals; null for anything else
    function grosze(text) {
        var typed = String(text || '').replace(/[\s\u00a0\u202f]/g, '').replace(',', '.');
        if (!/^\d+(\.\d{1,2})?$/.test(typed)) {
            return null;
        }
        return Math.round(parseFloat(typed) * 100);
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    }

    function rows() {
        return Array.prototype.slice.call(form.querySelectorAll('tr[data-number]'));
    }

    function checkbox(row) {
        return row.querySelector('[data-cl-statement-row]');
    }

    function save() {
        allocationsField.value = JSON.stringify(allocations);
    }

    function updateBar() {
        var count = 0;
        var orders = 0;
        var total = 0;
        var selectable = 0;
        rows().forEach(function (row) {
            var box = checkbox(row);
            if (!box || box.disabled) {
                return;
            }
            selectable++;
            if (box.checked) {
                count++;
                total += Number(row.getAttribute('data-amount'));
                orders += (allocations[row.getAttribute('data-number')] || []).length;
            }
        });
        var bar = form.querySelector('[data-cl-statement-total]');
        bar.textContent = format(form.getAttribute('data-text-selected'), [count, orders, money(total)]);
        form.querySelector('[data-cl-statement-submit]').disabled = count === 0;
        var all = form.querySelector('[data-cl-statement-all]');
        if (all) {
            all.checked = selectable > 0 && count === selectable;
            all.indeterminate = count > 0 && count < selectable;
        }
    }

    form.addEventListener('change', function (event) {
        if (event.target.hasAttribute('data-cl-statement-all')) {
            rows().forEach(function (row) {
                var box = checkbox(row);
                if (box && !box.disabled) {
                    box.checked = event.target.checked;
                }
            });
        }
        updateBar();
    });

    form.addEventListener('submit', save);

    // the operation's date, sender and full title from its row (and what the caller adds); a line without a value,
    // e.g. the sender of a statement without that column, is hidden
    function facts(dialog, row, extra) {
        var values = {
            date: row.getAttribute('data-date'),
            sender: row.getAttribute('data-sender'),
            title: row.getAttribute('data-title')
        };
        Object.keys(extra || {}).forEach(function (key) {
            values[key] = extra[key];
        });
        Array.prototype.forEach.call(dialog.querySelectorAll('[data-cl-operation] [data-field]'), function (field) {
            var value = values[field.getAttribute('data-field')] || '';
            field.textContent = value;
            field.parentNode.hidden = !value;
        });
    }

    // as fragments/statement-orders.html: each order links to its page in a new tab (leaving would lose the
    // reconciliation), and a payout of many orders shows the first three and folds the rest
    function orderLine(ref) {
        var order = byId[ref.orderId] || {shortId: ref.orderId.substring(0, 8), client: ''};
        var line = element('div', 'cl-statement-order');
        var link = element('a', 'cl-table-code', order.shortId);
        link.href = '/dashboard/orders/' + encodeURIComponent(ref.orderId);
        link.target = '_blank';
        link.rel = 'noopener';
        link.title = format(form.getAttribute('data-text-open-order'), [order.shortId]);
        line.appendChild(link);
        if (order.client) {
            line.appendChild(document.createTextNode(' '));
            line.appendChild(element('span', null, order.client));
        }
        if (ref.detail) {
            line.appendChild(element('span', 'cl-table-sub', ref.detail));
        }
        return line;
    }

    function orderCell(row, refs, note) {
        var cell = row.querySelector('[data-cl-statement-orders]');
        cell.textContent = '';
        var folded = refs.length > 4;
        refs.slice(0, folded ? 3 : refs.length).forEach(function (ref) {
            cell.appendChild(orderLine(ref));
        });
        if (folded) {
            var more = element('details', 'cl-row-disclosure');
            more.appendChild(element('summary', null, format(form.getAttribute('data-text-orders-more'), [refs.length - 3])));
            refs.slice(3).forEach(function (ref) {
                more.appendChild(orderLine(ref));
            });
            cell.appendChild(more);
        }
        if (note) {
            cell.appendChild(element('span', 'cl-table-sub', note));
        }
    }

    function accept(row, chosen, refs, note) {
        var number = row.getAttribute('data-number');
        allocations[number] = chosen;
        save();
        orderCell(row, refs, note);
        var box = checkbox(row);
        if (box) {
            // an operation whose assignment was removed has nothing to book
            box.disabled = !chosen.length;
            box.checked = chosen.length > 0;
        }
        updateBar();
    }

    // the operations in the statement's order, which the rows keep within a group
    var positions = {};
    parse((form.querySelector('input[name="operations"]') || {}).value, []).forEach(function (operation, index) {
        positions[operation.number] = index;
    });

    // Where a row stands follows the operator's choice as on the server (StatementPageFactory.groupOf): left without
    // orders it waits among the unrecognised ones, assigned by hand from there it is checked like a proposal.
    function regroup(row, chosen) {
        var from = row.closest('tbody').getAttribute('data-cl-group');
        var to = !chosen.length ? 'unmatched' : from === 'unmatched' ? 'check' : from;
        var target = form.querySelector('tbody[data-cl-group="' + to + '"]');
        if (to === from || !target) {
            return;
        }
        var position = positions[row.getAttribute('data-number')];
        var next = Array.prototype.filter.call(target.querySelectorAll('tr[data-number]'), function (other) {
            return positions[other.getAttribute('data-number')] > position;
        })[0];
        target.insertBefore(row, next || null);
        countGroups();
    }

    function countGroups() {
        Array.prototype.forEach.call(form.querySelectorAll('tbody[data-cl-group]'), function (group) {
            var id = group.getAttribute('data-cl-group');
            var members = group.querySelectorAll('tr[data-number]');
            var total = Array.prototype.reduce.call(members, function (sum, member) {
                return sum + Number(member.getAttribute('data-amount'));
            }, 0);
            group.hidden = !members.length;
            group.querySelector('[data-cl-group-count]').textContent = members.length;
            var tile = document.querySelector('[data-cl-tile="' + id + '"]');
            if (tile) {
                tile.querySelector('[data-cl-tile-count]').textContent = members.length;
                tile.querySelector('[data-cl-tile-amount]').textContent = money(total);
            }
        });
    }

    // the payment the assigned orders fill, as the page shows it: the first order's, with the count of the orders
    function paymentCell(row, chosen) {
        var cell = row.querySelector('[data-cl-statement-payment]');
        var first = chosen.length ? byId[chosen[0].orderId] : null;
        cell.textContent = '';
        cell.appendChild(element('strong', null, first ? first.type : form.getAttribute('data-text-no-payment')));
        if (first) {
            cell.appendChild(element('span', 'cl-table-sub', first.reference + (chosen.length > 1
                ? ' · ' + format(form.getAttribute('data-text-payment-orders'), [chosen.length]) : '')));
        }
    }

    // ---- assignment: one transfer on one or more orders ----

    var assign = document.getElementById('statement-assign-dialog');
    var assignState = null;

    function openAssign(row) {
        var amount = Number(row.getAttribute('data-amount'));
        var number = row.getAttribute('data-number');
        var current = allocations[number] || [];
        assignState = {
            row: row,
            amount: amount,
            assigned: current.length > 0,
            order: current.map(function (allocation) { return allocation.orderId; }),
            amounts: {},
            candidates: parse(row.getAttribute('data-candidates'), [])
        };
        current.forEach(function (allocation) {
            assignState.amounts[allocation.orderId] = allocation.amount;
        });
        assign.querySelector('[data-cl-dialog-title]').textContent = format(assign.getAttribute('data-text-title'), [money(amount)]);
        facts(assign, row);
        var search = assign.querySelector('[data-cl-assign-search]');
        search.value = '';
        renderCandidates();
        renderAllocations();
        assign.showModal();
        search.focus();
    }

    function matchesQuery(order, query) {
        if (!query) {
            return true;
        }
        // the amount as shown ("3 400,00") and as typed without spaces ("3400,00")
        var amount = plain(order.unpaid);
        var text = (order.shortId + ' ' + order.id + ' ' + order.client + ' ' + order.email + ' ' + amount + ' '
            + amount.split(String.fromCharCode(160)).join('')).toLowerCase();
        return query.toLowerCase().split(/\s+/).every(function (word) {
            return text.indexOf(word) >= 0;
        });
    }

    var CANDIDATES_SHOWN = 30;

    // The orders the matcher offered come first; then, without a search, every order that still owes money with the
    // amounts closest to what is left of the transfer first, or with a search the orders that match it.
    function renderCandidates() {
        var list = assign.querySelector('[data-cl-assign-list]');
        var note = assign.querySelector('[data-cl-assign-note]');
        var query = assign.querySelector('[data-cl-assign-search]').value.trim();
        list.textContent = '';
        var ids = [];
        // the ticked orders stay in view whatever is searched, so they can be unticked
        assignState.order.forEach(function (id) {
            if (ids.indexOf(id) < 0 && byId[id]) {
                ids.push(id);
            }
        });
        assignState.candidates.forEach(function (id) {
            if (ids.indexOf(id) < 0 && byId[id] && matchesQuery(byId[id], query)) {
                ids.push(id);
            }
        });
        var left = assignState.amount - assigned(null);
        var others = receivables.filter(function (order) {
            return ids.indexOf(order.id) < 0 && matchesQuery(order, query);
        });
        if (!query) {
            others.sort(function (a, b) {
                return Math.abs(a.unpaid - left) - Math.abs(b.unpaid - left);
            });
        }
        var total = ids.length + others.length;
        ids = ids.concat(others.slice(0, Math.max(0, CANDIDATES_SHOWN - ids.length)).map(function (order) { return order.id; }));
        list.hidden = !ids.length;
        note.hidden = ids.length > 0 && total <= ids.length;
        note.textContent = ids.length ? format(assign.getAttribute('data-text-more'), [ids.length, total])
            : assign.getAttribute(query ? 'data-text-empty' : 'data-text-none');
        ids.forEach(function (id) {
            var order = byId[id];
            var label = element('label', 'cl-choice');
            var box = element('input');
            box.type = 'checkbox';
            box.checked = assignState.order.indexOf(id) >= 0;
            box.addEventListener('change', function () {
                toggle(id, box.checked);
            });
            label.appendChild(box);
            var text = element('span', 'cl-choice-text');
            text.appendChild(element('span', 'cl-choice-title',
                order.shortId + ' ' + order.client + (order.ordered ? ' · ' + order.ordered : '')));
            text.appendChild(element('span', 'cl-choice-description', [order.type, order.reference,
                format(assign.getAttribute('data-text-owed'), [money(order.unpaid)])].filter(Boolean).join(' · ')));
            label.appendChild(text);
            list.appendChild(label);
        });
    }

    function assigned(exceptId) {
        return assignState.order.reduce(function (sum, id) {
            return id === exceptId ? sum : sum + (assignState.amounts[id] || 0);
        }, 0);
    }

    function toggle(id, on) {
        var at = assignState.order.indexOf(id);
        if (on && at < 0) {
            // a lone order held the whole transfer; once the transfer is shared it holds at most what it owes
            if (assignState.order.length === 1) {
                var first = assignState.order[0];
                assignState.amounts[first] = byId[first] ? Math.min(byId[first].unpaid, assignState.amount) : assignState.amount;
            }
            assignState.order.push(id);
            // up to what the order owes: a surplus stays unassigned until the operator types it in as an overpayment
            assignState.amounts[id] = Math.max(0, Math.min(byId[id].unpaid, assignState.amount - assigned(id)));
        } else if (!on && at >= 0) {
            assignState.order.splice(at, 1);
            delete assignState.amounts[id];
        }
        renderAllocations();
    }

    // One order takes the whole transfer, short of what it owes or over it, so there is nothing to type; only a transfer
    // shared by several orders needs its parts, which must add up to it.
    function renderAllocations() {
        var body = assign.querySelector('[data-cl-assign-allocations]');
        var single = assignState.order.length === 1;
        body.textContent = '';
        assign.querySelector('[data-cl-assign-table]').hidden = !assignState.order.length;
        assign.querySelector('[data-cl-assign-summary]').hidden = single;
        if (single) {
            assignState.amounts[assignState.order[0]] = assignState.amount;
        }
        assignState.order.forEach(function (id) {
            var order = byId[id];
            var name = order ? order.shortId + ' ' + order.client : id;
            var line = element('tr');
            var who = element('td');
            var amount = element('td', 'is-numeric');
            var state = element('div');
            if (single) {
                who.appendChild(element('span', null, name));
                amount.className = 'is-numeric cl-nowrap';
                amount.textContent = money(assignState.amount);
            } else {
                var input = element('input', 'cl-input is-qty');
                input.type = 'text';
                input.inputMode = 'decimal';
                input.id = 'statement-assign-' + id;
                input.value = fieldValue(assignState.amounts[id] || 0);
                input.addEventListener('input', function () {
                    var value = grosze(input.value);
                    input.classList.toggle('is-invalid', value === null || value <= 0);
                    assignState.amounts[id] = value === null ? 0 : value;
                    showState(state, order, assignState.amounts[id]);
                    updateRemaining();
                });
                var label = element('label', null, name);
                label.htmlFor = input.id;
                who.appendChild(label);
                amount.appendChild(input);
            }
            who.appendChild(state);
            showState(state, order, assignState.amounts[id] || 0);
            line.appendChild(who);
            line.appendChild(amount);
            body.appendChild(line);
        });
        updateRemaining();
    }

    // What the order will owe once its part is booked, worded as the warnings on the statement's rows: more than it owes
    // is an overpayment, less an underpayment.
    function showState(state, order, paid) {
        state.textContent = '';
        if (!order || paid <= 0) {
            return;
        }
        var left = order.unpaid - paid;
        var text = left === 0 ? assign.getAttribute('data-text-paid')
            : format(assign.getAttribute(left > 0 ? 'data-text-underpaid' : 'data-text-overpaid'), [money(Math.abs(left))]);
        state.appendChild(element('span', 'cl-status ' + (left === 0 ? 'is-ok' : 'is-warn'), text));
    }

    function updateRemaining() {
        var left = assignState.amount - assigned(null);
        var status = assign.querySelector('[data-cl-assign-remaining]');
        status.className = 'cl-status ' + (left === 0 ? 'is-ok' : left > 0 ? 'is-warn' : 'is-bad');
        status.textContent = left >= 0 ? (left === 0 ? money(0) : format(assign.getAttribute('data-text-remaining'), [money(left)]))
            : format(assign.getAttribute('data-text-over'), [money(-left)]);
        var valid = assignState.order.length > 0 && left === 0 && assignState.order.every(function (id) {
            return assignState.amounts[id] > 0;
        });
        // unticking every order of an operation that had them removes its assignment
        var clearing = assignState.assigned && !assignState.order.length;
        var apply = assign.querySelector('[data-cl-assign-apply]');
        apply.textContent = assign.getAttribute(clearing ? 'data-text-clear' : 'data-text-apply');
        apply.disabled = !valid && !clearing;
    }

    assign.querySelector('[data-cl-assign-search]').addEventListener('input', renderCandidates);
    assign.querySelector('[data-cl-assign-apply]').addEventListener('click', function () {
        var chosen = assignState.order.map(function (id) {
            return {orderId: id, amount: assignState.amounts[id], fee: 0};
        });
        var refs = chosen.map(function (allocation) {
            return {orderId: allocation.orderId, detail: format(form.getAttribute('data-text-assigned'), [money(allocation.amount)])};
        });
        accept(assignState.row, chosen, refs, chosen.length ? null : form.getAttribute('data-text-unassigned'));
        paymentCell(assignState.row, chosen);
        assignState.row.querySelector('[data-cl-statement-open]').textContent =
            form.getAttribute(chosen.length ? 'data-text-change' : 'data-text-assign');
        regroup(assignState.row, chosen);
        assign.close();
    });

    // ---- payout: one transfer for many orders of a channel ----

    var payout = document.getElementById('statement-payout-dialog');
    var payoutState = null;

    // the payer's rows (indexes in #statement-channels): an order of any of them may be in the payout, and its fee
    // counts as its first row of the payer says, as on the server
    function payerRow(order) {
        var rows = payoutState.rows.filter(function (index) {
            return (order.channels || []).indexOf(index) >= 0;
        });
        return rows.length ? Math.min.apply(null, rows) : -1;
    }

    function openPayout(row) {
        var number = row.getAttribute('data-number');
        var current = allocations[number] || [];
        payoutState = {
            row: row,
            number: number,
            amount: Number(row.getAttribute('data-amount')),
            rows: parse(row.getAttribute('data-channels'), []),
            picked: current.map(function (allocation) { return allocation.orderId; }),
            // grosze the payer kept from each order: as proposed, typed, or worked out from the difference
            fees: {},
            inputs: {},
            netCells: {}
        };
        current.forEach(function (allocation) {
            payoutState.fees[allocation.orderId] = allocation.fee || 0;
        });
        var ids = [];
        receivables.forEach(function (order) {
            if (payerRow(order) >= 0) {
                ids.push(order.id);
            }
        });
        payoutState.picked.forEach(function (id) {
            if (ids.indexOf(id) < 0 && byId[id]) {
                ids.push(id);
            }
        });
        payoutState.ids = ids;
        var labels = [];
        payoutState.rows.forEach(function (index) {
            var label = channels[index] ? channels[index].label : '';
            if (label && labels.indexOf(label) < 0) {
                labels.push(label);
            }
        });
        payout.querySelector('[data-cl-dialog-title]').textContent = format(payout.getAttribute('data-text-title'), [money(payoutState.amount)]);
        payout.querySelector('[data-cl-payout-transfer]').textContent = money(payoutState.amount);
        payout.querySelector('[data-cl-payout-search]').value = '';
        autoFee().checked = false;
        facts(payout, row, {payer: labels.join(', ')});
        renderPayoutRows();
        payout.showModal();
    }

    function autoFee() {
        return payout.querySelector('[data-cl-payout-auto-fee]');
    }

    // what a payer that deducts its fee keeps from the order, as recorded on the order's pending payment
    function recordedFee(order) {
        var index = payerRow(order);
        return index >= 0 && channels[index] && channels[index].net ? (order.fee || 0) : 0;
    }

    function feeOf(id) {
        return payoutState.fees[id] !== undefined ? payoutState.fees[id] : recordedFee(byId[id]);
    }

    function pickedIds() {
        return payoutState.ids.filter(function (id) { return payoutState.picked.indexOf(id) >= 0; });
    }

    // An amount for a text field: "1249,00", no grouping spaces.
    function fieldValue(grosze) {
        return plain(grosze).split(String.fromCharCode(160)).join('');
    }

    // "Auto-uzupełnij prowizję jako różnicę", as when a payment is added by hand: what the ticked orders owe less the
    // transfer is the fee, shared out in proportion to what each order owes; the grosze left by the rounding go to the
    // largest order, so the fees add up to the difference exactly.
    function spreadFee() {
        var picked = pickedIds();
        var owed = picked.reduce(function (sum, id) { return sum + byId[id].unpaid; }, 0);
        var kept = owed - payoutState.amount;
        if (!picked.length || kept < 0 || owed === 0) {
            return;
        }
        var given = 0;
        var largest = picked[0];
        picked.forEach(function (id) {
            var share = Math.floor(kept * byId[id].unpaid / owed);
            payoutState.fees[id] = share;
            given += share;
            if (byId[id].unpaid > byId[largest].unpaid) {
                largest = id;
            }
        });
        payoutState.fees[largest] += kept - given;
    }

    function refreshFees() {
        Object.keys(payoutState.inputs).forEach(function (id) {
            var ticked = payoutState.picked.indexOf(id) >= 0;
            var input = payoutState.inputs[id];
            input.disabled = !ticked;
            if (document.activeElement !== input) {
                input.value = fieldValue(ticked ? feeOf(id) : recordedFee(byId[id]));
                input.classList.remove('is-invalid');
            }
            payoutState.netCells[id].textContent = money(byId[id].unpaid - (ticked ? feeOf(id) : recordedFee(byId[id])));
        });
    }

    function renderPayoutRows() {
        var body = payout.querySelector('[data-cl-payout-rows]');
        var query = payout.querySelector('[data-cl-payout-search]').value.trim();
        body.textContent = '';
        payoutState.inputs = {};
        payoutState.netCells = {};
        var shown = payoutState.ids.filter(function (id) {
            return payoutState.picked.indexOf(id) >= 0 || matchesQuery(byId[id], query);
        });
        payout.querySelector('[data-cl-payout-empty]').hidden = shown.length > 0;
        shown.forEach(function (id) {
            var order = byId[id];
            var line = element('tr');
            var pick = element('td', 'cl-table-check');
            var target = element('label', 'cl-check-target');
            var box = element('input', 'cl-check-input');
            box.type = 'checkbox';
            box.checked = payoutState.picked.indexOf(id) >= 0;
            box.setAttribute('aria-label', order.shortId + ' ' + order.client);
            box.addEventListener('change', function () {
                var at = payoutState.picked.indexOf(id);
                if (box.checked && at < 0) {
                    payoutState.picked.push(id);
                } else if (!box.checked && at >= 0) {
                    payoutState.picked.splice(at, 1);
                    delete payoutState.fees[id];
                }
                line.classList.toggle('is-selected', box.checked);
                if (autoFee().checked) {
                    spreadFee();
                }
                refreshFees();
                updatePayout();
            });
            target.appendChild(box);
            pick.appendChild(target);
            line.appendChild(pick);
            var who = element('td');
            who.appendChild(element('span', 'cl-table-code', order.shortId));
            who.appendChild(element('span', 'cl-table-sub', order.client));
            line.appendChild(who);
            line.appendChild(element('td', 'cl-nowrap', order.ordered));
            var payment = element('td');
            payment.appendChild(element('span', null, order.type));
            payment.appendChild(element('span', 'cl-table-sub', order.reference));
            line.appendChild(payment);
            line.appendChild(element('td', 'is-numeric cl-nowrap', money(order.unpaid)));
            var feeCell = element('td', 'is-numeric');
            var input = element('input', 'cl-input is-qty');
            input.type = 'text';
            input.inputMode = 'decimal';
            input.setAttribute('aria-label', format(payout.getAttribute('data-text-fee'), [order.shortId]));
            input.addEventListener('input', function () {
                var value = grosze(input.value);
                var valid = value !== null && value <= order.unpaid;
                input.classList.toggle('is-invalid', !valid);
                payoutState.fees[id] = valid ? value : NaN;
                // the operator took over: the difference is no longer shared out by itself
                autoFee().checked = false;
                payoutState.netCells[id].textContent = valid ? money(order.unpaid - value) : '';
                updatePayout();
            });
            feeCell.appendChild(input);
            line.appendChild(feeCell);
            var netCell = element('td', 'is-numeric cl-nowrap');
            line.appendChild(netCell);
            payoutState.inputs[id] = input;
            payoutState.netCells[id] = netCell;
            line.classList.toggle('is-selected', box.checked);
            body.appendChild(line);
        });
        refreshFees();
        updatePayout();
    }

    function payoutSums() {
        var gross = 0;
        var fee = 0;
        var valid = true;
        pickedIds().forEach(function (id) {
            gross += byId[id].unpaid;
            var kept = feeOf(id);
            if (isNaN(kept)) {
                valid = false;
            } else {
                fee += kept;
            }
        });
        return {gross: gross, fee: fee, net: gross - fee, difference: gross - fee - payoutState.amount, valid: valid};
    }

    function updatePayout() {
        var sums = payoutSums();
        payout.querySelector('[data-cl-payout-gross]').textContent = money(sums.gross);
        payout.querySelector('[data-cl-payout-fee]').textContent = money(sums.fee);
        payout.querySelector('[data-cl-payout-net]').textContent = money(sums.net);
        var diff = payout.querySelector('[data-cl-payout-diff]');
        diff.className = 'cl-status ' + (sums.difference === 0 ? 'is-ok' : sums.difference > 0 ? 'is-warn' : 'is-bad');
        diff.textContent = sums.difference === 0 ? money(0)
            : format(payout.getAttribute(sums.difference > 0 ? 'data-text-lower' : 'data-text-higher'), [money(Math.abs(sums.difference))]);
        var missing = payout.querySelector('[data-cl-payout-missing]');
        missing.hidden = sums.difference >= 0;
        missing.textContent = sums.difference < 0 ? format(payout.getAttribute('data-text-missing'), [money(-sums.difference)]) : '';
        payout.querySelector('[data-cl-payout-count]').textContent = format(payout.getAttribute('data-text-count'), [payoutState.picked.length]);
        payout.querySelector('[data-cl-payout-apply]').disabled = payoutState.picked.length === 0 || !sums.valid
            || sums.difference !== 0;
    }

    payout.querySelector('[data-cl-payout-search]').addEventListener('input', renderPayoutRows);
    autoFee().addEventListener('change', function () {
        if (autoFee().checked) {
            spreadFee();
        } else {
            pickedIds().forEach(function (id) { delete payoutState.fees[id]; });
        }
        refreshFees();
        updatePayout();
    });
    payout.querySelector('[data-cl-payout-auto]').addEventListener('click', function () {
        var proposal = initial[payoutState.number] || [];
        payoutState.picked = proposal.map(function (allocation) { return allocation.orderId; });
        payoutState.fees = {};
        proposal.forEach(function (allocation) {
            payoutState.fees[allocation.orderId] = allocation.fee || 0;
        });
        autoFee().checked = false;
        renderPayoutRows();
    });
    payout.querySelector('[data-cl-payout-apply]').addEventListener('click', function () {
        var sums = payoutSums();
        var chosen = pickedIds().map(function (id) {
            return {orderId: id, amount: byId[id].unpaid, fee: feeOf(id)};
        });
        var refs = chosen.map(function (allocation) {
            return {orderId: allocation.orderId, detail: allocation.fee > 0
                ? format(form.getAttribute('data-text-payout-fee'), [money(allocation.amount), money(allocation.fee)])
                : format(form.getAttribute('data-text-owed'), [money(allocation.amount)])};
        });
        var note = chosen.length > 1 ? format(form.getAttribute('data-text-payout'), [chosen.length, money(sums.gross)]) : null;
        accept(payoutState.row, chosen, refs, note);
        payout.close();
    });

    form.addEventListener('click', function (event) {
        var opener = event.target.closest('[data-cl-statement-open]');
        if (!opener) {
            return;
        }
        var row = opener.closest('tr[data-number]');
        if (row.getAttribute('data-payout') === 'true') {
            openPayout(row);
        } else {
            openAssign(row);
        }
    });

    updateBar();
})();
