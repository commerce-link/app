// Fields that depend on a choice. The choice is a select, a group of radios or a hidden input marked
// data-cl-variant-select="group"; a disabled control does not count (its part of the form is switched off itself).
// - fieldset[data-cl-variant-group="group"][data-cl-variant="value"] shows only while the group's value is that value;
// - [data-cl-variant-when="group=value group!=a|b"] shows only while every condition holds (a value list separated by |,
//   an empty item for "nothing chosen"); a condition on a group with no enabled control is ignored.
// Hidden fieldsets are also disabled, so their fields are not sent. Without JavaScript everything is visible and the server
// reads only the fields of the choices made.
(function () {
    'use strict';

    function current(group) {
        var controls = document.querySelectorAll('[data-cl-variant-select="' + group + '"]');
        for (var i = 0; i < controls.length; i++) {
            var control = controls[i];
            // :disabled, not .disabled: a control inside a disabled fieldset keeps disabled === false.
            if (control.matches(':disabled')) {
                continue;
            }
            if (control.type === 'radio') {
                if (control.checked) {
                    return control.value;
                }
                continue;
            }
            return control.value;
        }
        var radios = document.querySelectorAll('input[type="radio"][data-cl-variant-select="' + group + '"]');
        for (var j = 0; j < radios.length; j++) {
            if (!radios[j].matches(':disabled')) {
                return '';
            }
        }
        return null;
    }

    function holds(condition) {
        var negated = condition.indexOf('!=') > 0;
        var parts = condition.split(negated ? '!=' : '=');
        var value = current(parts[0]);
        if (value === null) {
            return true;
        }
        var matches = parts[1].split('|').indexOf(value) >= 0;
        return negated ? !matches : matches;
    }

    function active(element) {
        var group = element.getAttribute('data-cl-variant-group');
        if (group !== null) {
            var value = current(group);
            if (value !== null && element.getAttribute('data-cl-variant') !== value) {
                return false;
            }
        }
        var when = element.getAttribute('data-cl-variant-when');
        return !when || when.split(/\s+/).every(holds);
    }

    function syncOnce() {
        document.querySelectorAll('[data-cl-variant-group], [data-cl-variant-when]').forEach(function (element) {
            var on = active(element);
            element.hidden = !on;
            if (element.tagName === 'FIELDSET') {
                element.disabled = !on;
            }
        });
    }

    // Twice: a condition can read a choice that the first pass has just switched off (the mode of a price list).
    function syncAll() {
        syncOnce();
        syncOnce();
    }

    document.addEventListener('change', function (event) {
        if (event.target.matches && event.target.matches('[data-cl-variant-select]')) {
            syncAll();
        }
    });
    document.addEventListener('cl:form-replaced', syncAll);
    syncAll();
})();
