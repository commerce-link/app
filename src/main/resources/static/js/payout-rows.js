// The payout rows of a statement format (store-bank-statement.html) as a list edited in a dialog. The rows' fields
// ([data-cl-payout-fields], the editable table without JavaScript) stay in the form, hidden, and are what is posted:
// adding, editing and removing a row only change them, and the rows are saved with the format ("Zapisz format"). A row
// the server refused keeps its error messages in its fields; the list marks it and its dialog shows them, and the links
// of the error summary open it.
(function () {
    'use strict';

    var root = document.querySelector('[data-cl-payout-rows]');
    var dialog = document.getElementById('payout-row-dialog');
    if (!root || !dialog || typeof dialog.showModal !== 'function') {
        return;
    }
    var fieldsBox = root.querySelector('[data-cl-payout-fields]');
    var body = fieldsBox.querySelector('tbody');
    var template = root.querySelector('template[data-cl-payout-template]');
    var list = root.querySelector('[data-cl-payout-list]');
    var listBody = list.querySelector('[data-cl-payout-list-rows]');
    var listTable = list.querySelector('table');
    var empty = list.querySelector('[data-cl-payout-empty]');
    var unsaved = list.querySelector('[data-cl-payout-unsaved]');
    var status = list.querySelector('[data-cl-payout-status]');
    var form = dialog.querySelector('[data-cl-payout-form]');
    var title = dialog.querySelector('.cl-dialog-title');
    var apply = dialog.querySelector('[data-cl-payout-apply]');
    var PROPERTIES = ['match', 'source', 'method', 'netOfFees', 'feeRates', 'maxFeePercent'];
    var MAX_RATES = 4;
    // the row being edited; null while a new one is added
    var editing = null;
    // the error summary's links, each with the row it names
    var summaryRows = new WeakMap();

    function rows() {
        return Array.prototype.slice.call(body.querySelectorAll('tr[data-cl-payout-row]'));
    }

    function rowField(row, property) {
        return row.querySelector('[name$="].' + property + '"]');
    }

    function dialogField(property) {
        return dialog.querySelector('[data-cl-payout-field="' + property + '"]');
    }

    function read(field) {
        return field.type === 'checkbox' ? field.checked : field.value.trim();
    }

    function write(field, value) {
        if (field.type === 'checkbox') {
            field.checked = value === true;
        } else {
            field.value = value || '';
        }
    }

    function valuesOf(fieldOf) {
        var values = {};
        PROPERTIES.forEach(function (property) {
            values[property] = read(fieldOf(property));
        });
        return values;
    }

    function rowValues(row) {
        return valuesOf(function (property) {
            return rowField(row, property);
        });
    }

    // as BankStatementFormatForm.ChannelRow.isBlank: the server ignores such a row
    function isBlank(values) {
        return !values.match && !values.source && !values.method && !values.feeRates && !values.maxFeePercent;
    }

    function format(text, value) {
        return (text || '').split('{0}').join(value);
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function optionText(select) {
        var option = select.options[select.selectedIndex];
        return select.value && option ? option.text : '';
    }

    // the posted names and the ids carry the row's index; after a row is added or removed they stay a gapless list
    function renumber() {
        rows().forEach(function (row, index) {
            row.querySelectorAll('[name]').forEach(function (field) {
                field.name = field.name.replace(/^channels\[[^\]]*\]/, 'channels[' + index + ']');
            });
            row.querySelectorAll('[id]').forEach(function (field) {
                field.id = field.id.replace(/^channel-[^-]+-/, 'channel-' + index + '-');
            });
        });
    }

    function serverErrors(row) {
        var errors = {};
        PROPERTIES.forEach(function (property) {
            var cell = rowField(row, property).closest('td');
            var message = cell && cell.querySelector('.cl-field-error');
            if (message) {
                errors[property] = message.textContent.trim();
            }
        });
        return errors;
    }

    function clearServerErrors(row) {
        row.querySelectorAll('.cl-field-error').forEach(function (message) {
            message.remove();
        });
        row.querySelectorAll('.is-invalid').forEach(function (field) {
            field.classList.remove('is-invalid');
            field.removeAttribute('aria-invalid');
        });
    }

    function feesText(values) {
        if (!values.netOfFees) {
            return root.getAttribute('data-text-gross');
        }
        var parts = [root.getAttribute('data-text-net')];
        if (values.feeRates) {
            parts.push(format(root.getAttribute('data-text-rates'), values.feeRates));
        }
        if (values.maxFeePercent) {
            parts.push(format(root.getAttribute('data-text-limit'), values.maxFeePercent));
        }
        return parts.join(' · ');
    }

    function render() {
        listBody.textContent = '';
        rows().forEach(function (row, index) {
            var values = rowValues(row);
            var line = element('tr');
            var payer = element('th', 'cl-table-key');
            payer.scope = 'row';
            payer.appendChild(element('span', null, values.match));
            if (row.querySelector('.cl-field-error')) {
                payer.appendChild(element('span', 'cl-status is-bad', root.getAttribute('data-text-invalid')));
            }
            line.appendChild(payer);
            line.appendChild(element('td', null, [optionText(rowField(row, 'source')), optionText(rowField(row, 'method'))]
                .filter(Boolean).join(' · ')));
            line.appendChild(element('td', null, feesText(values)));
            var actions = element('td', 'cl-table-actions');
            var edit = element('button', 'cl-link-button', root.getAttribute('data-text-edit'));
            edit.type = 'button';
            edit.setAttribute('data-cl-dialog-open', dialog.id);
            edit.setAttribute('data-cl-payout-edit', String(index));
            edit.setAttribute('aria-label', format(root.getAttribute('data-text-edit-for'), values.match));
            var remove = element('button', 'cl-link-button is-danger', root.getAttribute('data-text-remove'));
            remove.type = 'button';
            remove.setAttribute('data-cl-payout-remove', String(index));
            remove.setAttribute('aria-label', format(root.getAttribute('data-text-remove-for'), values.match));
            actions.appendChild(edit);
            actions.appendChild(document.createTextNode(' '));
            actions.appendChild(remove);
            line.appendChild(actions);
            listBody.appendChild(line);
        });
        var none = listBody.children.length === 0;
        listTable.hidden = none;
        empty.hidden = !none;
    }

    function changed(message) {
        unsaved.hidden = false;
        status.textContent = message || '';
    }

    function percent(text) {
        return /^\d+([.,]\d+)?$/.test(text) ? Number(text.replace(',', '.')) : NaN;
    }

    // the same rules as BankStatementFormatForm.feeRates and feeLimit; the server checks them again on save
    function ratesValid(text) {
        var rates = [];
        var valid = text.split(/[;\s]+/).filter(Boolean).every(function (part) {
            var rate = percent(part);
            if (rate > 0 && rate < 100) {
                if (rates.indexOf(rate) < 0) {
                    rates.push(rate);
                }
                return true;
            }
            return false;
        });
        return valid && rates.length > 0 && rates.length <= MAX_RATES;
    }

    function limitValid(text) {
        var limit = percent(text);
        return limit > 0 && limit <= 100;
    }

    function check(values) {
        var errors = {};
        if (!values.match) {
            errors.match = dialog.getAttribute('data-error-match');
        }
        if (!values.source && !values.method) {
            errors.source = dialog.getAttribute('data-error-orders');
        }
        if (values.feeRates && !ratesValid(values.feeRates)) {
            errors.feeRates = dialog.getAttribute('data-error-rates');
        }
        if (values.maxFeePercent && !limitValid(values.maxFeePercent)) {
            errors.maxFeePercent = dialog.getAttribute('data-error-limit');
        }
        return errors;
    }

    function showErrors(errors) {
        PROPERTIES.forEach(function (property) {
            var message = dialog.querySelector('[data-cl-payout-error="' + property + '"]');
            var field = dialogField(property);
            var text = errors[property];
            if (message) {
                message.hidden = !text;
                message.querySelector('span').textContent = text || '';
            }
            field.classList.toggle('is-invalid', Boolean(text) && field.type !== 'checkbox');
            if (text) {
                field.setAttribute('aria-invalid', 'true');
            } else {
                field.removeAttribute('aria-invalid');
            }
        });
    }

    // the rates and the limit mean something only for a transfer net of fees
    function showFees() {
        var net = dialogField('netOfFees').checked;
        dialog.querySelectorAll('[data-cl-payout-fees]').forEach(function (box) {
            box.hidden = !net;
        });
    }

    dialog.addEventListener('cl:dialog-open', function (event) {
        var trigger = event.detail && event.detail.trigger;
        var index = trigger ? trigger.getAttribute('data-cl-payout-edit') : null;
        editing = index !== null ? rows()[Number(index)] || null : (trigger && summaryRows.get(trigger)) || null;
        if (editing && !body.contains(editing)) {
            editing = null;
        }
        var values = editing ? rowValues(editing) : {};
        PROPERTIES.forEach(function (property) {
            write(dialogField(property), values[property]);
        });
        title.textContent = dialog.getAttribute(editing ? 'data-title-edit' : 'data-title-add');
        apply.textContent = dialog.getAttribute(editing ? 'data-submit-edit' : 'data-submit-add');
        showErrors(editing ? serverErrors(editing) : {});
        showFees();
    });

    dialogField('netOfFees').addEventListener('change', showFees);

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var values = valuesOf(dialogField);
        if (!values.netOfFees) {
            values.feeRates = '';
            values.maxFeePercent = '';
        }
        var errors = check(values);
        showErrors(errors);
        var invalid = PROPERTIES.filter(function (property) {
            return errors[property];
        });
        if (invalid.length) {
            dialogField(invalid[0]).focus();
            return;
        }
        var row = editing;
        if (!row) {
            row = template.content.firstElementChild.cloneNode(true);
            body.appendChild(row);
            renumber();
        }
        PROPERTIES.forEach(function (property) {
            write(rowField(row, property), values[property]);
        });
        clearServerErrors(row);
        render();
        changed();
        dialog.close();
        // the list was drawn again, so the edit button that opened the dialog is gone: the focus goes to the new one
        if (editing) {
            var button = listBody.querySelector('[data-cl-payout-edit="' + rows().indexOf(row) + '"]');
            if (button) {
                button.focus();
            }
        }
    });

    listBody.addEventListener('click', function (event) {
        var button = event.target.closest('[data-cl-payout-remove]');
        if (!button) {
            return;
        }
        var index = Number(button.getAttribute('data-cl-payout-remove'));
        var row = rows()[index];
        if (!row) {
            return;
        }
        var match = rowValues(row).match;
        row.remove();
        renumber();
        render();
        changed(format(root.getAttribute('data-text-removed'), match));
        var next = listBody.querySelectorAll('[data-cl-payout-edit]');
        var target = next[Math.min(index, next.length - 1)] || list.querySelector('[data-cl-dialog-open]');
        target.focus();
    });

    // the links of the error summary point at fields that are hidden now: they open the row's dialog instead
    document.querySelectorAll('[data-cl-error-summary] a[href^="#channel-"]').forEach(function (link) {
        var found = /^#channel-(\d+)-/.exec(link.getAttribute('href'));
        var row = found ? rows()[Number(found[1])] : null;
        if (row) {
            summaryRows.set(link, row);
            link.setAttribute('data-cl-dialog-open', dialog.id);
        }
    });

    rows().forEach(function (row) {
        if (isBlank(rowValues(row))) {
            row.remove();
        }
    });
    renumber();
    fieldsBox.hidden = true;
    list.hidden = false;
    render();
})();
