// Add-items dialog shared by the order details and the offer details (fragments/item-add-modal.html): pricelist tab
// (catalog, category, label, search, sortable name and price), inventory tab (the product of a typed EAN, from
// data-inventory-url), the list of picked entries with quantities, and a submit that posts items[i].* to
// data-add-items-url (or hands them to the page: cl:item-add-submit). Both pages open it with data-cl-dialog-open
// (dialog.js), which fires cl:dialog-open.
(function () {
    'use strict';

    // picked entries: {source: 'pricelist', catalogId, product, qty} or {source: 'inventory', product, qty}
    const itemAdd = {
        data: [],
        cache: {},
        sortKey: 'price',
        sortDir: 1,
        picked: [],
        tab: 'pricelist',
        loading: false,
        pending: false,
        inventoryRows: [],
        inventoryLoading: false,
        inventorySearched: false
    };

    function itemAddModalRoot() {
        return document.getElementById('item-add-dialog');
    }

    function toggleAddItemModal(show) {
        var dialog = itemAddModalRoot();
        if (!dialog) {
            return;
        }
        if (show) {
            if (!dialog.open) {
                dialog.showModal();
            }
            initItemAddModal();
        } else if (dialog.open) {
            dialog.close();
        }
    }

    function initItemAddModal() {
        const catalogSelect = document.getElementById('itemAddCatalog');
        if (!catalogSelect.value && catalogSelect.options.length === 2) {
            catalogSelect.selectedIndex = 1;
        }
        if (catalogSelect.value && !itemAdd.data.length) {
            loadItemAddPricelist(catalogSelect.value);
        } else {
            renderItemAddBasket();
        }
    }

    function switchAddItemTab(tab) {
        itemAdd.tab = tab;
        document.getElementById('itemAddTabPricelist').setAttribute('aria-pressed', String(tab === 'pricelist'));
        document.getElementById('itemAddTabInventory').setAttribute('aria-pressed', String(tab === 'inventory'));
        document.getElementById('itemAddPricelistPane').hidden = tab !== 'pricelist';
        document.getElementById('itemAddInventoryPane').hidden = tab !== 'inventory';
        if (tab === 'inventory') {
            document.getElementById('itemAddEan').focus();
        }
    }

    function onItemAddCatalogChange(select) {
        loadItemAddPricelist(select.value);
    }

    function loadItemAddPricelist(catalogId) {
        if (!catalogId) {
            itemAdd.data = [];
            refreshItemAddFilters();
            return;
        }
        if (itemAdd.cache[catalogId]) {
            itemAdd.data = itemAdd.cache[catalogId];
            refreshItemAddFilters();
            return;
        }
        itemAdd.loading = true;
        renderItemAddTable();
        const url = itemAddModalRoot().getAttribute('data-pricelist-url')
            + '?catalogId=' + encodeURIComponent(catalogId);
        fetch(url)
            .then(response => response.ok ? response.json() : [])
            .catch(() => [])
            .then(data => {
                itemAdd.cache[catalogId] = data;
                itemAdd.data = data;
                itemAdd.loading = false;
                refreshItemAddFilters();
            });
    }

    function refreshItemAddFilters() {
        const categorySelect = document.getElementById('itemAddCategory');
        const previous = categorySelect.value;
        categorySelect.replaceChildren();
        categorySelect.appendChild(new Option(categorySelect.getAttribute('data-placeholder'), ''));
        [...new Set(itemAdd.data.map(p => p.category))]
            .sort((a, b) => (a || '').localeCompare(b || '', 'pl'))
            .forEach(category => categorySelect.appendChild(new Option(category, category)));
        categorySelect.value = [...categorySelect.options].some(o => o.value === previous) ? previous : '';
        onItemAddCategoryChange();
    }

    function onItemAddCategoryChange() {
        const category = document.getElementById('itemAddCategory').value;
        const labelSelect = document.getElementById('itemAddLabel');
        labelSelect.replaceChildren();
        labelSelect.appendChild(new Option(labelSelect.getAttribute('data-placeholder'), ''));
        [...new Set(itemAdd.data.filter(p => !category || p.category === category).map(p => p.label))]
            .sort((a, b) => (a || '').localeCompare(b || '', 'pl'))
            .forEach(label => labelSelect.appendChild(new Option(label, label)));
        renderItemAddTable();
    }

    function setItemAddSort(key) {
        if (itemAdd.sortKey === key) {
            itemAdd.sortDir = -itemAdd.sortDir;
        } else {
            itemAdd.sortKey = key;
            itemAdd.sortDir = 1;
        }
        renderItemAddTable();
    }

    // Shared with order-item-dialogs.js: money.js formats the number and adds the unit from the pattern the
    // layout renders once per page, so no script here hard-codes a currency literal.
    function formatItemAddPrice(value) {
        return window.CL_formatMoney(value);
    }

    function renderItemAddTable() {
        renderItemAddPricelistTable();
        renderItemAddInventoryTable();
        renderItemAddBasket();
    }

    function renderItemAddPricelistTable() {
        const category = document.getElementById('itemAddCategory').value;
        const label = document.getElementById('itemAddLabel').value;
        const search = document.getElementById('itemAddSearch').value.trim().toLowerCase();
        const catalogSelected = !!document.getElementById('itemAddCatalog').value;

        let rows = itemAdd.data.filter(p => !category || p.category === category);
        if (label) rows = rows.filter(p => p.label === label);
        if (search) rows = rows.filter(p =>
            [p.name, p.label, p.ean, p.mfn].some(v => (v || '').toLowerCase().includes(search)));

        rows = [...rows].sort((a, b) => itemAdd.sortDir * (itemAdd.sortKey === 'price'
            ? a.price - b.price
            : (a.name || '').localeCompare(b.name || '', 'pl')));

        document.getElementById('itemAddArrowName').textContent =
            itemAdd.sortKey === 'name' ? (itemAdd.sortDir === 1 ? '▲' : '▼') : '';
        document.getElementById('itemAddArrowPrice').textContent =
            itemAdd.sortKey === 'price' ? (itemAdd.sortDir === 1 ? '▲' : '▼') : '';

        const catalogId = document.getElementById('itemAddCatalog').value;
        const tbody = document.getElementById('itemAddRows');
        const labels = columnLabels(tbody);
        tbody.replaceChildren();
        rows.forEach(p => tbody.appendChild(itemAddProductRow(p, findItemAddPicked(catalogId, p),
            [p.label, p.ean, p.mfn], labels,
            entry => togglePicked(entry, () => ({source: 'pricelist', catalogId: catalogId, product: p, qty: 1})))));

        document.getElementById('itemAddLoading').hidden = !itemAdd.loading;
        document.getElementById('itemAddNoCatalog').hidden = itemAdd.loading || catalogSelected;
        document.getElementById('itemAddNoResults').hidden = itemAdd.loading || !catalogSelected || !!rows.length;

        const count = document.getElementById('itemAddCount');
        count.textContent = catalogSelected && !itemAdd.loading
            ? count.getAttribute('data-template').replace('{0}', rows.length).replace('{1}', itemAdd.data.length)
            : '';
    }

    function renderItemAddInventoryTable() {
        const tbody = document.getElementById('itemAddInventoryRows');
        const labels = columnLabels(tbody);
        tbody.replaceChildren();
        itemAdd.inventoryRows.forEach(p => tbody.appendChild(itemAddProductRow(p, findItemAddInventoryPicked(p),
            [p.brand, p.ean, p.mfn], labels,
            entry => togglePicked(entry, () => ({source: 'inventory', product: p, qty: 1})))));

        document.getElementById('itemAddInventoryLoading').hidden = !itemAdd.inventoryLoading;
        document.getElementById('itemAddInventoryNoResults').hidden =
            itemAdd.inventoryLoading || !itemAdd.inventorySearched || !!itemAdd.inventoryRows.length;
    }

    // below 720 px the table shows as cards and every cell names its column from data-label
    function columnLabels(tbody) {
        return Array.from(tbody.closest('table').querySelectorAll('thead th'))
            .map(th => (th.querySelector('span') || th).textContent.trim());
    }

    function itemAddProductRow(p, entry, codes, labels, toggle) {
        const row = document.createElement('tr');
        row.classList.toggle('is-picked', !!entry);

        const checkCell = document.createElement('td');
        checkCell.className = 'cl-table-check';
        const check = document.createElement('input');
        check.type = 'checkbox';
        check.className = 'cl-check-input';
        check.checked = !!entry;
        check.tabIndex = -1;
        check.setAttribute('aria-hidden', 'true');
        checkCell.appendChild(check);

        const nameCell = document.createElement('td');
        nameCell.className = 'cl-item-add-name';
        const nameLine = document.createElement('div');
        nameLine.textContent = p.name;
        const subLine = document.createElement('div');
        subLine.className = 'cl-table-sub';
        subLine.textContent = codes.filter(v => v).join(' · ');
        nameCell.append(nameLine, subLine);

        const categoryCell = document.createElement('td');
        categoryCell.textContent = p.category;

        const priceCell = document.createElement('td');
        priceCell.className = 'is-numeric cl-item-add-strong';
        priceCell.textContent = formatItemAddPrice(p.price);

        const qtyCell = document.createElement('td');
        qtyCell.className = 'is-numeric';
        const qtyTag = document.createElement('span');
        qtyTag.className = 'cl-status ' + (p.qty === 0 ? 'is-bad' : (p.qty <= 5 ? 'is-warn' : 'is-ok'));
        qtyTag.textContent = p.qty;
        qtyCell.appendChild(qtyTag);

        const deliveryCell = document.createElement('td');
        deliveryCell.className = 'is-numeric';
        deliveryCell.textContent = p.estimatedDeliveryDays + ' ' + itemAddModalRoot().getAttribute('data-days-suffix');

        const pickCell = document.createElement('td');
        pickCell.className = 'is-numeric';
        if (entry) {
            pickCell.appendChild(itemAddStepper(entry));
        } else {
            const add = document.createElement('button');
            add.type = 'button';
            add.className = 'cl-button cl-item-add-row-add';
            add.textContent = '+ ' + itemAddModalRoot().getAttribute('data-add-label');
            pickCell.appendChild(add);
        }

        row.append(checkCell, nameCell, categoryCell, priceCell, qtyCell, deliveryCell, pickCell);
        Array.from(row.children).forEach((cell, i) => { cell.dataset.label = i === 0 ? '' : (labels[i] || ''); });
        row.addEventListener('click', event => {
            if (event.target.closest('.cl-item-add-stepper')) return;
            toggle(entry);
        });
        return row;
    }

    function findItemAddPicked(catalogId, product) {
        return itemAdd.picked.find(e => e.source === 'pricelist' && e.catalogId === catalogId && e.product.pimId === product.pimId);
    }

    function findItemAddInventoryPicked(product) {
        return itemAdd.picked.find(e => e.source === 'inventory' && e.product.ean === product.ean);
    }

    function togglePicked(entry, create) {
        if (entry) {
            itemAdd.picked.splice(itemAdd.picked.indexOf(entry), 1);
        } else {
            itemAdd.picked.push(create());
            document.getElementById('itemAddBasket').classList.remove('is-collapsed');
            document.querySelector('[data-item-add-basket-toggle]').setAttribute('aria-expanded', 'true');
        }
        renderItemAddTable();
    }

    function itemAddStepper(entry) {
        const wrap = document.createElement('span');
        wrap.className = 'cl-item-add-stepper';
        const minus = document.createElement('button');
        minus.type = 'button';
        minus.textContent = '−';
        const input = document.createElement('input');
        input.type = 'text';
        input.inputMode = 'numeric';
        input.value = entry.qty;
        input.setAttribute('aria-label', itemAddModalRoot().getAttribute('data-qty-label'));
        const plus = document.createElement('button');
        plus.type = 'button';
        plus.textContent = '+';
        minus.addEventListener('click', () => { entry.qty = Math.max(1, entry.qty - 1); renderItemAddTable(); });
        plus.addEventListener('click', () => { entry.qty += 1; renderItemAddTable(); });
        input.addEventListener('change', () => { entry.qty = Math.max(1, parseInt(input.value, 10) || 1); renderItemAddTable(); });
        input.addEventListener('click', event => event.stopPropagation());
        wrap.append(minus, input, plus);
        return wrap;
    }

    function renderItemAddSearchButton() {
        document.getElementById('itemAddInventorySearch').disabled = !document.getElementById('itemAddEan').value.trim();
    }

    function searchItemAddInventory() {
        const ean = document.getElementById('itemAddEan').value.trim();
        if (!ean) return;
        itemAdd.inventoryRows = [];
        itemAdd.inventoryLoading = true;
        renderItemAddInventoryTable();
        const url = itemAddModalRoot().getAttribute('data-inventory-url') + '?ean=' + encodeURIComponent(ean);
        fetch(url)
            .then(response => response.ok ? response.json() : [])
            .catch(() => [])
            .then(rows => {
                itemAdd.inventoryRows = rows;
                itemAdd.inventoryLoading = false;
                itemAdd.inventorySearched = true;
                renderItemAddInventoryTable();
            });
    }

    function removeItemAddEntry(entry) {
        itemAdd.picked.splice(itemAdd.picked.indexOf(entry), 1);
        renderItemAddTable();
    }

    function clearItemAddPicked() {
        itemAdd.picked = [];
        renderItemAddTable();
    }

    function toggleItemAddBasket() {
        const basket = document.getElementById('itemAddBasket');
        const collapsed = basket.classList.toggle('is-collapsed');
        document.querySelector('[data-item-add-basket-toggle]').setAttribute('aria-expanded', String(!collapsed));
    }

    function itemAddMessage(attribute, ...args) {
        return args.reduce((text, value, index) => text.replace('{' + index + '}', value),
            itemAddModalRoot().getAttribute(attribute));
    }

    function renderItemAddBasket() {
        const modal = itemAddModalRoot();
        const tbody = document.getElementById('itemAddBasketRows');
        tbody.replaceChildren();
        itemAdd.picked.forEach(entry => {
            const row = document.createElement('tr');

            const nameCell = document.createElement('td');
            const nameLine = document.createElement('div');
            nameLine.textContent = entry.product.name;
            const subLine = document.createElement('div');
            subLine.className = 'cl-table-sub';
            subLine.textContent = [entry.product.category, entry.product.mfn].filter(v => v).join(' · ');
            nameCell.append(nameLine, subLine);

            const qtyCell = document.createElement('td');
            qtyCell.className = 'is-numeric';
            qtyCell.appendChild(itemAddStepper(entry));

            const priceCell = document.createElement('td');
            priceCell.className = 'is-numeric cl-item-add-muted';
            priceCell.textContent = formatItemAddPrice(entry.product.price);

            const totalCell = document.createElement('td');
            totalCell.className = 'is-numeric cl-item-add-strong';
            totalCell.textContent = formatItemAddPrice(entry.product.price * entry.qty);

            const removeCell = document.createElement('td');
            removeCell.className = 'cl-table-actions';
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'cl-link-button is-danger';
            remove.setAttribute('aria-label', modal.getAttribute('data-remove-label'));
            remove.textContent = '×';
            remove.addEventListener('click', () => removeItemAddEntry(entry));
            removeCell.appendChild(remove);

            row.append(nameCell, qtyCell, priceCell, totalCell, removeCell);
            tbody.appendChild(row);
        });

        const count = itemAdd.picked.length;
        document.getElementById('itemAddBasketEmpty').hidden = !!count;
        document.getElementById('itemAddBasketCount').textContent = count;

        const submit = document.getElementById('itemAddSubmit');
        const hint = document.getElementById('itemAddHint');
        if (!count) {
            submit.textContent = modal.getAttribute('data-submit-label');
            submit.disabled = true;
            hint.textContent = hint.getAttribute('data-select-hint');
            return;
        }
        const pieces = itemAdd.picked.reduce((sum, e) => sum + e.qty, 0);
        const total = itemAdd.picked.reduce((sum, e) => sum + e.product.price * e.qty, 0);

        hint.replaceChildren();
        const summary = document.createElement('strong');
        summary.textContent = itemAddMessage('data-summary', count, pieces, formatItemAddPrice(total));
        hint.appendChild(summary);
        submit.textContent = itemAddMessage('data-submit-count', count);
        submit.disabled = itemAdd.pending;
    }

    function submitAddItems() {
        if (itemAdd.pending || !itemAdd.picked.length) return;

        const params = {};
        itemAdd.picked.forEach((entry, index) => {
            const prefix = 'items[' + index + '].';
            params[prefix + 'source'] = entry.source;
            params[prefix + 'qty'] = entry.qty;
            if (entry.source === 'pricelist') {
                params[prefix + 'catalogId'] = entry.catalogId;
                params[prefix + 'pimId'] = entry.product.pimId;
            } else {
                params[prefix + 'ean'] = entry.product.ean;
            }
        });

        itemAdd.pending = true;
        document.getElementById('itemAddSubmit').disabled = true;

        const form = document.createElement('form');
        form.method = 'post';
        form.action = itemAddModalRoot().getAttribute('data-add-items-url');
        window.clCsrf.appendTo(form);
        Object.entries(params).forEach(([name, value]) => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value;
            form.appendChild(input);
        });
        document.body.appendChild(form);
        // A page that adds the items itself (the offer's draft table) cancels this and sends params on its own: done
        // empties the dialog once they are added, abort hands it back as it was when they are not, and proceed posts
        // as without a listener. Without a listener the post goes on as before (the order page).
        const submitting = new CustomEvent('cl:item-add-submit', {
            bubbles: true,
            cancelable: true,
            detail: {
                params: params,
                proceed: () => form.submit(),
                done: () => {
                    form.remove();
                    itemAdd.pending = false;
                    itemAdd.picked = [];
                    renderItemAddTable();
                },
                abort: () => {
                    form.remove();
                    itemAdd.pending = false;
                    renderItemAddBasket();
                }
            }
        });
        if (itemAddModalRoot().dispatchEvent(submitting)) {
            form.submit();
        }
    }

    function bind() {
        var dialog = itemAddModalRoot();
        if (!dialog) {
            return;
        }
        dialog.addEventListener('cl:dialog-open', initItemAddModal);
        // A click is sent to the nearest element holding both ends of the press, so pressing inside the dialog (e.g. to
        // select text) and letting go over the backdrop — or the other way round — clicks the dialog element itself, just
        // like a click on the backdrop does. The backdrop closes the dialog only when the press both started and ended on it.
        var pressStart = null;
        var pressEnd = null;
        dialog.addEventListener('pointerdown', function (event) {
            pressStart = event.target;
        });
        dialog.addEventListener('pointerup', function (event) {
            pressEnd = event.target;
        });
        dialog.addEventListener('click', function (event) {
            var target = event.target;
            var onBackdrop = target === dialog && pressStart === dialog && pressEnd === dialog;
            pressStart = null;
            pressEnd = null;
            if (onBackdrop || (target.closest && target.closest('[data-cl-item-add-close]'))) {
                toggleAddItemModal(false);
                return;
            }
            if (target === dialog) {
                return;
            }
            var tab = target.closest && target.closest('[data-item-add-tab]');
            if (tab) {
                switchAddItemTab(tab.getAttribute('data-item-add-tab'));
            }
            var sort = target.closest && target.closest('[data-item-add-sort]');
            if (sort) {
                setItemAddSort(sort.getAttribute('data-item-add-sort'));
            }
            if (target.closest && target.closest('[data-item-add-basket-toggle]')) {
                toggleItemAddBasket();
            }
            if (target.closest && target.closest('[data-item-add-clear]')) {
                clearItemAddPicked();
            }
        });
        document.getElementById('itemAddCatalog').addEventListener('change', function (event) {
            onItemAddCatalogChange(event.target);
        });
        document.getElementById('itemAddCategory').addEventListener('change', onItemAddCategoryChange);
        document.getElementById('itemAddLabel').addEventListener('change', renderItemAddTable);
        document.getElementById('itemAddSearch').addEventListener('input', renderItemAddTable);
        var ean = document.getElementById('itemAddEan');
        ean.addEventListener('input', renderItemAddSearchButton);
        ean.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                searchItemAddInventory();
            }
        });
        document.getElementById('itemAddInventorySearch').addEventListener('click', searchItemAddInventory);
        document.getElementById('itemAddSubmit').addEventListener('click', submitAddItems);
    }

    bind();
})();
