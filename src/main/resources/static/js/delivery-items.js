// Step 1 of a new delivery (deliveries/create/items.html). Ticking a source changes the product's minimum and
// quantity (an order source moves the minimum and the quantity by its pieces, both ways, a warehouse source only the
// quantity),
// the warehouse adjustment line follows, dropship quantities are the ticked lines, totals and the step buttons follow
// everything, Enter inside the table moves to the next field instead of leaving for step 2, and the restock suggestions
// are fetched after the page is shown.
(function () {
    'use strict';

    function formatAmount(value) {
        var parts = Math.abs(value).toFixed(2).split('.');
        parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        return (value < 0 ? '−' : '') + parts.join(',');
    }

    // A grouped number inside a label the server rendered ("min. 1 000", "+1 500 szt."): MessageFormat groups thousands.
    var NUMBER = /\d(?:[\d\s\u00a0\u202f]*\d)?/;

    function group(value) {
        return formatAmount(value).replace(/,00$/, '');
    }

    function intOf(input) {
        return input ? parseInt(input.value, 10) || 0 : 0;
    }

    // The source checkboxes sit in a nested sub-table inside the detail row, so the nearest tbody is the sub-table's;
    // the product's main row is the detail row's previous sibling. Everywhere else the row is the element's own ancestor.
    function rowOf(element) {
        var detail = element.closest('tr.cl-row-detail');
        if (detail) {
            return detail.previousElementSibling;
        }
        return element.closest('[data-cl-delivery-item]');
    }

    function sources(row) {
        return row.closest('tbody').querySelectorAll('input[type="checkbox"][data-cl-source-qty]');
    }

    function adjustWarehouseRow(row, checkbox) {
        var qtyInput = row.querySelector('input[data-cl-requested-qty]');
        var minLabel = row.querySelector('[data-cl-min]');
        var qty = parseInt(checkbox.getAttribute('data-cl-source-qty'), 10) || 0;
        var min = parseInt(minLabel.getAttribute('data-min'), 10) || 0;
        var current = intOf(qtyInput);
        if (checkbox.getAttribute('data-cl-source-type') === 'Order') {
            min = Math.max(0, checkbox.checked ? min + qty : min - qty);
            minLabel.setAttribute('data-min', String(min));
            minLabel.textContent = minLabel.textContent.replace(NUMBER, group(min));
            qtyInput.min = String(min);
            qtyInput.value = String(checkbox.checked ? Math.max(current + qty, min) : Math.max(min, current - qty));
        } else {
            qtyInput.value = String(checkbox.checked ? current + qty : Math.max(min, current - qty));
        }
    }

    function syncAdjustment(row) {
        var foot = row.closest('tbody').querySelector('[data-cl-adjustment]');
        var minLabel = row.querySelector('[data-cl-min]');
        if (!foot || !minLabel) {
            return;
        }
        var warehouseTicked = 0;
        sources(row).forEach(function (checkbox) {
            if (checkbox.checked && checkbox.getAttribute('data-cl-source-type') === 'Warehouse') {
                warehouseTicked += parseInt(checkbox.getAttribute('data-cl-source-qty'), 10) || 0;
            }
        });
        var adjustment = intOf(row.querySelector('input[data-cl-requested-qty]'))
            - (parseInt(minLabel.getAttribute('data-min'), 10) || 0) - warehouseTicked;
        var value = foot.querySelector('[data-cl-adjustment-value]');
        foot.hidden = adjustment === 0;
        var sign = adjustment > 0 ? '+' : adjustment < 0 ? '−' : '';
        value.textContent = value.textContent.replace(/^[−+-]?/, '').replace(NUMBER, sign + group(Math.abs(adjustment)));
        value.classList.toggle('is-ok', adjustment >= 0);
        value.classList.toggle('is-warn', adjustment < 0);
    }

    function syncDropshipRow(row) {
        var qty = 0;
        sources(row).forEach(function (checkbox) {
            if (checkbox.checked) {
                qty += parseInt(checkbox.getAttribute('data-cl-source-qty'), 10) || 0;
            }
        });
        row.querySelector('input[data-cl-requested-qty]').value = String(qty);
        row.querySelector('[data-cl-requested-qty-label]').textContent = String(qty);
    }

    function syncTotals(form) {
        var products = 0;
        var pieces = 0;
        var net = 0;
        form.querySelectorAll('[data-cl-delivery-item]').forEach(function (row) {
            var qty = intOf(row.querySelector('input[data-cl-requested-qty]'));
            var costInput = row.querySelector('input[data-cl-unit-cost]');
            var cost = costInput ? parseFloat(costInput.value) || 0 : 0;
            var line = row.querySelector('[data-cl-line-value]');
            if (line) {
                line.textContent = formatAmount(qty * cost);
            }
            if (qty > 0) {
                products += 1;
                pieces += qty;
                net += qty * cost;
            }
        });
        form.querySelector('[data-cl-total-products]').textContent = String(products);
        form.querySelector('[data-cl-total-pieces]').textContent = String(pieces);
        form.querySelector('[data-cl-total-net]').textContent = formatAmount(net);
        syncButtons(form, pieces);
    }

    // Nothing requested: both ways are off. A dropship operator who unticked everything and asked to release the
    // lines gets the manual button as "Zwolnij odznaczone pozycje" (the server releases instead of recording).
    function syncButtons(form, pieces) {
        var purchase = document.getElementById('purchase-button');
        var manual = document.getElementById('manual-button');
        var manualHelp = document.getElementById('manual-help');
        var emptyHelp = document.getElementById('empty-help');
        var remove = form.querySelector('[data-cl-remove-unselected]');
        var release = pieces === 0 && form.getAttribute('data-dropship') === 'true' && remove && remove.checked
            && manual.getAttribute('data-release-label');
        // a purchase button blocked by the server (pickup point) stays disabled whatever is ticked
        if (purchase && !document.getElementById('purchase-blocked')) {
            purchase.disabled = pieces === 0;
        }
        manual.disabled = pieces === 0 && !release;
        manual.querySelector('[data-cl-button-label]').textContent =
            release ? manual.getAttribute('data-release-label') : manual.getAttribute('data-label');
        manualHelp.textContent = release ? manualHelp.getAttribute('data-release-help') : manualHelp.getAttribute('data-help');
        emptyHelp.hidden = pieces > 0 || !!release;
        // the reason a button is off is read with the button
        [purchase, manual].forEach(function (button) {
            if (!button) {
                return;
            }
            if (!button.hasAttribute('data-describedby')) {
                button.setAttribute('data-describedby', button.getAttribute('aria-describedby') || '');
            }
            var base = button.getAttribute('data-describedby');
            button.setAttribute('aria-describedby', emptyHelp.hidden || !button.disabled ? base : (base + ' empty-help').trim());
        });
    }

    // Only fields the operator can see: the source checkboxes of a folded row and the folded suggestions are skipped
    // (focusing them does nothing, which used to stop Enter at the first product).
    function nextField(field) {
        var fields = Array.prototype.slice.call(field.form.querySelectorAll(
            '.cl-layout-main input:not([type="hidden"]):not([disabled])')).filter(function (input) {
            return input === field || input.getClientRects().length > 0;
        });
        var next = fields[fields.indexOf(field) + 1];
        if (next) {
            next.focus();
        }
    }

    // Restock suggestions come after the page (parts.html :: suggestions): working them out takes seconds. A fetched
    // row takes over the quantity and cost fields of a row the page already had for the same product (chosen before
    // going back from step 2, or marked by an error), and every row is renumbered so the list binds without gaps.
    function loadSuggestions(form) {
        var section = form.querySelector('[data-cl-suggestions]');
        if (!section) {
            return;
        }
        var status = section.querySelector('[data-cl-suggestions-status]');
        var failed = section.querySelector('[data-cl-suggestions-failed]');
        var summary = section.querySelector('summary');
        summary.textContent = section.getAttribute('data-summary-loading');
        status.hidden = false;
        failed.hidden = true;
        section.setAttribute('aria-busy', 'true');
        fetch(section.getAttribute('data-url'), { headers: { 'Accept': 'text/html' }, credentials: 'same-origin' })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                var list = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-cl-suggestion-list]');
                // an expired session answers with the login page, which is not a list
                if (!list) {
                    throw new Error('Not a suggestion list');
                }
                mergeSuggestions(section, Array.prototype.slice.call(list.querySelectorAll('tr[data-cl-suggestion]')));
                status.hidden = true;
                section.removeAttribute('aria-busy');
                syncTotals(form);
            })
            .catch(function () {
                summary.textContent = section.getAttribute('data-summary-failed');
                status.hidden = true;
                failed.hidden = false;
                section.removeAttribute('aria-busy');
            });
    }

    function mergeSuggestions(section, fetched) {
        var tbody = section.querySelector('[data-cl-suggestion-rows]');
        var focused = document.activeElement;
        var shown = {};
        tbody.querySelectorAll('tr[data-cl-suggestion]').forEach(function (row) {
            shown[row.getAttribute('data-cl-suggestion')] = row;
        });
        var taken = {};
        var rows = fetched.map(function (row) {
            var code = row.getAttribute('data-cl-suggestion');
            var mine = shown[code];
            row = document.adoptNode(row);
            if (mine) {
                taken[code] = true;
                ['input[data-cl-requested-qty]', 'input[data-cl-unit-cost]'].forEach(function (selector) {
                    var fresh = row.querySelector(selector);
                    fresh.parentNode.replaceChild(mine.querySelector(selector), fresh);
                });
            }
            return row;
        });
        var kept = Array.prototype.filter.call(tbody.querySelectorAll('tr[data-cl-suggestion]'), function (row) {
            return !taken[row.getAttribute('data-cl-suggestion')];
        });
        tbody.replaceChildren.apply(tbody, kept.concat(rows));
        var all = tbody.querySelectorAll('tr[data-cl-suggestion]');
        all.forEach(function (row, index) {
            row.querySelectorAll('input[name^="suggestedItems["]').forEach(function (input) {
                input.name = input.name.replace(/^suggestedItems\[\d+]/, 'suggestedItems[' + index + ']');
            });
        });
        section.querySelector('summary').textContent = section.getAttribute('data-summary').replace('{0}', String(all.length));
        section.hidden = all.length === 0;
        if (focused && focused !== document.activeElement && document.body.contains(focused)) {
            focused.focus();
        }
    }

    // Money has two decimals: a third one typed into a cost field is dropped as it is typed (the server rounds the rest).
    var CENTS = /^(-?\d*\.\d{2})\d+$/;

    function limitToCents(input) {
        var match = CENTS.exec(input.value);
        if (match) {
            input.value = match[1];
        }
    }

    function init() {
        document.addEventListener('input', function (event) {
            if (event.target.matches && event.target.matches('input[data-cl-unit-cost], #fulfilment-cost')) {
                limitToCents(event.target);
            }
        }, true);
        var form = document.querySelector('form[data-cl-delivery-items]');
        if (!form) {
            return;
        }
        var dropship = form.getAttribute('data-dropship') === 'true';
        form.addEventListener('change', function (event) {
            var target = event.target;
            var row = target.closest && rowOf(target);
            if (target.matches('input[type="checkbox"][data-cl-source-qty]') && row) {
                if (dropship) {
                    syncDropshipRow(row);
                } else {
                    adjustWarehouseRow(row, target);
                }
            }
            if (target.matches('input[data-cl-requested-qty][min]') && intOf(target) < (parseInt(target.min, 10) || 0)) {
                target.value = target.min;
            }
            if (row && !dropship) {
                syncAdjustment(row);
            }
            syncTotals(form);
        });
        form.addEventListener('input', function (event) {
            var row = event.target.closest && rowOf(event.target);
            if (row && !dropship) {
                syncAdjustment(row);
            }
            syncTotals(form);
        });
        form.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' && event.target.matches('.cl-layout-main input')) {
                event.preventDefault();
                nextField(event.target);
            }
        });
        syncTotals(form);
        loadSuggestions(form);
        form.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-cl-suggestions-retry]')) {
                loadSuggestions(form);
            }
        });

        // a page answered with errors puts the keyboard and screen reader on their summary
        var summary = document.querySelector('[data-cl-error-summary]');
        if (summary) {
            summary.focus();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
