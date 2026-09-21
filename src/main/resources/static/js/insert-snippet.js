// Values inserted into a text field with one click (the parameters of an email template). A list
// [data-cl-insert-into="bodyId otherId"] holds code elements [data-cl-insert="{{orderId}}"]; each becomes a button that
// inserts its value at the caret of the field last used among the listed ones (the first one until then), keeps the
// caret after it and announces the insert in [data-cl-insert-status]. Without JavaScript the list stays text to copy.
(function () {
    'use strict';

    var lastField = {};

    function fieldsOf(list) {
        return list.getAttribute('data-cl-insert-into').split(/\s+/)
            .map(function (id) { return document.getElementById(id); })
            .filter(Boolean);
    }

    function enhance(root) {
        root.querySelectorAll('[data-cl-insert-into]').forEach(function (list) {
            list.querySelectorAll('code[data-cl-insert]').forEach(function (code) {
                if (code.closest('button')) {
                    return;
                }
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'cl-param';
                button.setAttribute('data-cl-insert', code.getAttribute('data-cl-insert'));
                button.setAttribute('aria-label', (list.getAttribute('data-insert-label') || '') + ' ' + code.textContent);
                code.parentNode.insertBefore(button, code);
                button.appendChild(code);
            });
            list.parentNode.querySelectorAll('[data-cl-insert-hint]').forEach(function (hint) {
                hint.hidden = false;
            });
        });
    }

    function insert(field, value) {
        var start = field.selectionStart == null ? field.value.length : field.selectionStart;
        var end = field.selectionEnd == null ? start : field.selectionEnd;
        field.focus();
        field.setRangeText(value, start, end, 'end');
        field.dispatchEvent(new Event('input', { bubbles: true }));
    }

    document.addEventListener('focusin', function (event) {
        document.querySelectorAll('[data-cl-insert-into]').forEach(function (list) {
            if (fieldsOf(list).indexOf(event.target) !== -1) {
                lastField[list.getAttribute('data-cl-insert-into')] = event.target.id;
            }
        });
    });

    document.addEventListener('click', function (event) {
        var button = event.target.closest('button[data-cl-insert]');
        if (!button) {
            return;
        }
        var list = button.closest('[data-cl-insert-into]');
        var fields = fieldsOf(list);
        var field = document.getElementById(lastField[list.getAttribute('data-cl-insert-into')]) || fields[0];
        if (!field) {
            return;
        }
        insert(field, button.getAttribute('data-cl-insert'));
        var status = list.parentNode.querySelector('[data-cl-insert-status]');
        if (status) {
            var label = document.querySelector('label[for="' + field.id + '"]');
            status.textContent = (status.getAttribute('data-template') || '{0} → {1}')
                .replace('{0}', button.getAttribute('data-cl-insert'))
                .replace('{1}', label ? label.textContent.trim() : field.id);
        }
    });

    document.addEventListener('cl:form-replaced', function (event) {
        enhance(event.target);
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            enhance(document);
        });
    } else {
        enhance(document);
    }
})();
