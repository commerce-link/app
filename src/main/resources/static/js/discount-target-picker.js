(function () {
    'use strict';

    function format(template, values) {
        return template.replace(/\{(\d)\}/g, function (match, index) {
            return values[Number(index)];
        });
    }

    function closest(target, selector) {
        return target.closest ? target.closest(selector) : null;
    }

    function indexOfId(values, id) {
        for (var i = 0; i < values.length; i++) {
            if (values[i].id === id) {
                return i;
            }
        }
        return -1;
    }

    function byName(a, b) {
        return (a.name || '').localeCompare(b.name || '', 'pl');
    }

    function readPicked(field) {
        return Array.prototype.map.call(field.querySelectorAll('[data-discount-picked-list] > li'), function (item) {
            return {id: item.getAttribute('data-id'), name: item.getAttribute('data-name')};
        });
    }

    function removeButton(className, label) {
        var remove = document.createElement('button');
        remove.type = 'button';
        remove.className = className;
        remove.setAttribute('aria-label', label);
        remove.textContent = '×';
        return remove;
    }

    function hiddenInput(name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        return input;
    }

    function writePicked(field, values) {
        var name = field.getAttribute('data-discount-picked');
        var withNames = field.getAttribute('data-discount-picked-dialog') === 'products';
        var list = field.querySelector('[data-discount-picked-list]');
        list.replaceChildren();
        values.forEach(function (value, index) {
            var item = document.createElement('li');
            item.className = 'cl-chip-tag';
            item.setAttribute('data-id', value.id);
            item.setAttribute('data-name', value.name);
            var label = document.createElement('span');
            label.textContent = value.name;
            item.appendChild(label);
            if (withNames) {
                item.append(hiddenInput(name + '[' + index + '].id', value.id),
                    hiddenInput(name + '[' + index + '].name', value.name));
            } else {
                item.appendChild(hiddenInput(name, value.id));
            }
            var remove = removeButton('cl-chip-tag-remove', format(field.getAttribute('data-remove-label'), [value.name]));
            remove.setAttribute('data-discount-picked-remove', '');
            item.appendChild(remove);
            list.appendChild(item);
        });
        field.querySelector('[data-discount-picked-empty]').hidden = values.length > 0;
        field.dispatchEvent(new CustomEvent('cl:discount-picked', {bubbles: true}));
    }

    var pricelists = {};
    var picking = null;

    function pricelist(url, catalogId) {
        var address = url + '?catalogId=' + encodeURIComponent(catalogId);
        if (!pricelists[address]) {
            pricelists[address] = fetch(address, {headers: {'Accept': 'application/json'}})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('pricelist ' + response.status);
                    }
                    return response.json();
                })
                .catch(function () {
                    delete pricelists[address];
                    return [];
                });
        }
        return pricelists[address];
    }

    var GROUPED_BY = {brands: 'brand', categories: 'category'};

    function groupedBy(rows, field) {
        var groups = new Map();
        rows.forEach(function (row) {
            var name = (row[field] || '').trim();
            if (!name) {
                return;
            }
            if (!groups.has(name)) {
                groups.set(name, {id: name, name: name, products: 0, categories: new Set()});
            }
            var group = groups.get(name);
            group.products += 1;
            group.categories.add(row.category || '');
        });
        return Array.from(groups.values()).map(function (group) {
            return {id: group.id, name: group.name, products: group.products, categories: group.categories.size};
        });
    }

    function control(dialog, name) {
        return dialog.querySelector('[data-discount-pick-' + name + ']');
    }

    function fillOptions(select, values) {
        var previous = select.value;
        var unique = Array.from(new Set(values.filter(function (value) {
            return value;
        }))).sort(function (a, b) {
            return a.localeCompare(b, 'pl');
        });
        select.replaceChildren(new Option(select.getAttribute('data-placeholder'), ''));
        unique.forEach(function (value) {
            select.appendChild(new Option(value, value));
        });
        select.value = unique.indexOf(previous) >= 0 ? previous : '';
    }

    function fillLabels(state) {
        var category = control(state.dialog, 'category').value;
        fillOptions(control(state.dialog, 'label'), state.rows.filter(function (row) {
            return !category || row.category === category;
        }).map(function (row) {
            return row.label;
        }));
    }

    function visibleRows(state) {
        var search = control(state.dialog, 'search').value.trim().toLowerCase();
        if (state.kind !== 'products') {
            return state.rows.filter(function (group) {
                return !search || group.name.toLowerCase().indexOf(search) >= 0;
            }).sort(byName);
        }
        var category = control(state.dialog, 'category').value;
        var label = control(state.dialog, 'label').value;
        return state.rows.filter(function (row) {
            return (!category || row.category === category) && (!label || row.label === label)
                && (!search || [row.name, row.label, row.ean, row.mfn].some(function (text) {
                    return (text || '').toLowerCase().indexOf(search) >= 0;
                }));
        }).sort(byName);
    }

    function cell(className, text) {
        var element = document.createElement('td');
        element.className = className;
        if (text !== undefined) {
            element.textContent = text;
        }
        return element;
    }

    function rowElement(state, row, labels) {
        var dialog = state.dialog;
        var value = {id: state.kind === 'products' ? row.pimId : row.id, name: row.name};
        var picked = indexOfId(state.picked, value.id) >= 0;
        var tr = document.createElement('tr');
        tr.setAttribute('data-id', value.id);
        tr.classList.toggle('is-picked', picked);

        var checkCell = cell('cl-table-check');
        var box = document.createElement('input');
        box.type = 'checkbox';
        box.className = 'cl-check-input';
        box.checked = picked;
        box.setAttribute('aria-label', format(dialog.getAttribute('data-choose-label'), [value.name]));
        box.addEventListener('change', function () {
            toggle(state, value);
        });
        checkCell.appendChild(box);

        var cells = [checkCell];
        if (state.kind === 'brands') {
            cells.push(cell('', row.name), cell('is-numeric', String(row.products)), cell('is-numeric', String(row.categories)));
        } else if (state.kind === 'categories') {
            cells.push(cell('', row.name), cell('is-numeric', String(row.products)));
        } else {
            var nameCell = cell('cl-item-add-name');
            var nameLine = document.createElement('div');
            nameLine.textContent = row.name;
            var codes = document.createElement('div');
            codes.className = 'cl-table-sub';
            codes.textContent = [row.label, row.ean, row.mfn].filter(function (text) {
                return text;
            }).join(' · ');
            nameCell.append(nameLine, codes);
            cells.push(nameCell, cell('', row.category),
                cell('is-numeric cl-item-add-strong', window.CL_formatMoney(row.price)),
                cell(row.qty > 0 ? 'is-numeric' : 'is-numeric cl-item-add-muted', format(dialog.getAttribute('data-qty'), [row.qty])),
                cell('is-numeric', String(row.estimatedDeliveryDays)));
        }
        cells.forEach(function (element, index) {
            element.setAttribute('data-label', index === 0 ? '' : (labels[index] || ''));
            tr.appendChild(element);
        });
        tr.addEventListener('click', function (event) {
            if (event.target !== box) {
                toggle(state, value);
            }
        });
        return tr;
    }

    function showState(dialog, name, visible) {
        dialog.querySelector('[data-discount-pick-state="' + name + '"]').hidden = !visible;
    }

    function render(state) {
        var dialog = state.dialog;
        var catalogChosen = !!control(dialog, 'catalog').value;
        var rows = state.loading ? [] : visibleRows(state);
        var labels = Array.prototype.map.call(dialog.querySelectorAll('thead th'), function (th) {
            return th.textContent.trim();
        });
        var tbody = control(dialog, 'rows');
        tbody.replaceChildren();
        rows.forEach(function (row) {
            tbody.appendChild(rowElement(state, row, labels));
        });
        var loaded = catalogChosen && !state.loading;
        showState(dialog, 'loading', state.loading);
        showState(dialog, 'noCatalog', !state.loading && !catalogChosen);
        showState(dialog, 'empty', loaded && !state.rows.length);
        showState(dialog, 'noResults', loaded && state.rows.length > 0 && !rows.length);
        control(dialog, 'count').textContent = loaded && state.rows.length
            ? format(dialog.getAttribute('data-count'), [rows.length, state.rows.length]) : '';
        renderPicked(state);
    }

    function syncRows(state) {
        control(state.dialog, 'rows').querySelectorAll('tr').forEach(function (tr) {
            var picked = indexOfId(state.picked, tr.getAttribute('data-id')) >= 0;
            tr.classList.toggle('is-picked', picked);
            tr.querySelector('input[type="checkbox"]').checked = picked;
        });
    }

    function renderPicked(state) {
        var dialog = state.dialog;
        var tbody = control(dialog, 'picked');
        tbody.replaceChildren();
        state.picked.forEach(function (value) {
            var row = document.createElement('tr');
            var removeCell = cell('cl-table-actions');
            var remove = removeButton('cl-link-button is-danger', format(dialog.getAttribute('data-remove-label'), [value.name]));
            remove.addEventListener('click', function () {
                toggle(state, value);
            });
            removeCell.appendChild(remove);
            row.append(cell('', value.name), removeCell);
            tbody.appendChild(row);
        });
        var count = state.picked.length;
        control(dialog, 'picked-count').textContent = count;
        control(dialog, 'none').hidden = count > 0;
        var submit = control(dialog, 'submit');
        submit.disabled = count === 0;
        submit.textContent = count
            ? format(dialog.getAttribute('data-submit-count'), [count]) : dialog.getAttribute('data-submit-label');
    }

    function showBasket(dialog, open) {
        control(dialog, 'basket').classList.toggle('is-collapsed', !open);
        control(dialog, 'basket-toggle').setAttribute('aria-expanded', String(open));
    }

    function toggle(state, value) {
        var index = indexOfId(state.picked, value.id);
        if (index >= 0) {
            state.picked.splice(index, 1);
        } else {
            state.picked.push(value);
            showBasket(state.dialog, true);
        }
        syncRows(state);
        renderPicked(state);
    }

    function refresh(state) {
        if (state.kind === 'products') {
            fillOptions(control(state.dialog, 'category'), state.rows.map(function (row) {
                return row.category;
            }));
            fillLabels(state);
        }
        render(state);
    }

    function loadRows(state) {
        var catalogId = control(state.dialog, 'catalog').value;
        if (!catalogId) {
            state.rows = [];
            state.loading = false;
            refresh(state);
            return;
        }
        state.loading = true;
        render(state);
        pricelist(state.dialog.getAttribute('data-pricelist-url'), catalogId).then(function (rows) {
            if (picking !== state || control(state.dialog, 'catalog').value !== catalogId) {
                return;
            }
            state.rows = state.kind === 'products' ? rows : groupedBy(rows, GROUPED_BY[state.kind]);
            state.loading = false;
            refresh(state);
        });
    }

    function openPicker(field) {
        var kind = field.getAttribute('data-discount-picked-dialog');
        var dialog = document.querySelector('[data-discount-dialog="' + kind + '"]');
        if (!dialog) {
            return;
        }
        var catalog = control(dialog, 'catalog');
        if (!catalog.value && catalog.options.length === 2) {
            catalog.selectedIndex = 1;
        }
        picking = {kind: kind, field: field, dialog: dialog, picked: readPicked(field), rows: [], loading: false};
        dialog.showModal();
        loadRows(picking);
    }

    function applyPicked(state) {
        writePicked(state.field, state.picked);
        state.dialog.close();
        state.field.querySelector('[data-discount-pick]').focus();
    }

    function bindDialog(dialog) {
        // A press started inside and released over the backdrop also clicks the dialog element itself.
        var pressStart = null;
        var pressEnd = null;
        dialog.addEventListener('pointerdown', function (event) {
            pressStart = event.target;
        });
        dialog.addEventListener('pointerup', function (event) {
            pressEnd = event.target;
        });
        dialog.addEventListener('click', function (event) {
            var onBackdrop = event.target === dialog && pressStart === dialog && pressEnd === dialog;
            pressStart = null;
            pressEnd = null;
            if (onBackdrop || closest(event.target, '[data-discount-pick-close]')) {
                dialog.close();
                return;
            }
            if (!picking || picking.dialog !== dialog) {
                return;
            }
            if (closest(event.target, '[data-discount-pick-basket-toggle]')) {
                showBasket(dialog, control(dialog, 'basket').classList.contains('is-collapsed'));
            }
            if (closest(event.target, '[data-discount-pick-clear]')) {
                picking.picked = [];
                syncRows(picking);
                renderPicked(picking);
            }
            if (closest(event.target, '[data-discount-pick-submit]')) {
                applyPicked(picking);
            }
        });
        dialog.addEventListener('close', function () {
            if (picking && picking.dialog === dialog) {
                picking = null;
            }
        });
        control(dialog, 'catalog').addEventListener('change', function () {
            if (picking && picking.dialog === dialog) {
                loadRows(picking);
            }
        });
        var category = control(dialog, 'category');
        if (category) {
            category.addEventListener('change', function () {
                if (picking && picking.dialog === dialog) {
                    fillLabels(picking);
                    render(picking);
                }
            });
        }
        var label = control(dialog, 'label');
        if (label) {
            label.addEventListener('change', function () {
                if (picking && picking.dialog === dialog) {
                    render(picking);
                }
            });
        }
        control(dialog, 'search').addEventListener('input', function () {
            if (picking && picking.dialog === dialog) {
                render(picking);
            }
        });
    }

    function prepare() {
        document.querySelectorAll('[data-discount-picked] [data-discount-pick]').forEach(function (button) {
            button.hidden = false;
        });
    }

    document.addEventListener('click', function (event) {
        var pick = closest(event.target, '[data-discount-pick]');
        if (pick) {
            openPicker(pick.closest('[data-discount-picked]'));
            return;
        }
        var remove = closest(event.target, '[data-discount-picked-remove]');
        if (remove) {
            var field = remove.closest('[data-discount-picked]');
            remove.closest('li').remove();
            writePicked(field, readPicked(field));
            field.querySelector('[data-discount-pick]').focus();
        }
    });

    document.addEventListener('cl:form-replaced', prepare);
    document.querySelectorAll('[data-discount-dialog]').forEach(bindDialog);
    prepare();
})();
