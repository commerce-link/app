// Unsaved changes guard for a form marked data-cl-dirty-form. The form is compared with what it held when the page
// loaded: a changed value or a changed order of its rows ([data-cl-dirty-row]) makes it dirty, and putting everything
// back makes it clean again. Rows are counted once each; a moved row counts, the rows it merely shifted do not (the
// rows outside the longest run that kept its order are the moved ones). Inside the form:
// - [data-cl-dirty-status] shows data-template with "{n}" replaced by the number of changed rows, or data-clean;
//   it takes the class cl-unsaved while dirty;
// - [data-cl-save-items] buttons take is-primary while dirty;
// - [data-cl-discard] (a link reloading the page) shows while dirty and asks data-discard-confirm of the form first.
// A dirty form makes the browser ask before the page is left; a submit nobody prevented lets the page go. A page
// script that saves the form itself calls window.CL_markClean(form). window.CL_isDirty(form) answers the state, and
// the form receives cl:dirty-changed (detail: dirty, count) whenever it changes. A script that changes the form without
// an input or change event (reordering rows) dispatches cl:lines-changed on it. A field marked data-cl-dirty-ignore is
// not compared: a copy of a value saved elsewhere, which a page script keeps up to date.
(function () {
    'use strict';

    var states = new WeakMap();
    var SEPARATOR = '\u0001';

    function valueOf(control) {
        return control.type === 'checkbox' || control.type === 'radio' ? String(control.checked) : control.value;
    }

    function rowsOf(form) {
        return Array.prototype.slice.call(form.querySelectorAll('[data-cl-dirty-row]'));
    }

    // values only, not names: a script may renumber the names of rows it reorders
    function rowValues(row) {
        return Array.prototype.map.call(row.querySelectorAll('input[name], select[name], textarea[name]'), valueOf)
            .join(SEPARATOR);
    }

    function otherValues(form) {
        return Array.prototype.filter.call(form.elements, function (control) {
            return control.name && control.type !== 'submit' && control.type !== 'button'
                && !control.closest('[data-cl-dirty-row]') && !control.hasAttribute('data-cl-dirty-ignore');
        }).map(function (control) {
            return control.name + '=' + valueOf(control);
        }).join(SEPARATOR);
    }

    function snapshot(form) {
        var rows = new Map();
        rowsOf(form).forEach(function (row, index) {
            rows.set(row, { index: index, values: rowValues(row) });
        });
        return { rows: rows, others: otherValues(form), dirty: false, count: 0, leaving: false };
    }

    // The rows of the longest increasing run of original positions kept their order; every other row was moved.
    function keptInOrder(positions) {
        var length = positions.map(function () { return 1; });
        var previous = positions.map(function () { return -1; });
        var best = -1;
        positions.forEach(function (position, i) {
            for (var j = 0; j < i; j++) {
                if (positions[j] < position && length[j] + 1 > length[i]) {
                    length[i] = length[j] + 1;
                    previous[i] = j;
                }
            }
            if (best < 0 || length[i] > length[best]) {
                best = i;
            }
        });
        var kept = new Set();
        for (var k = best; k >= 0; k = previous[k]) {
            kept.add(k);
        }
        return kept;
    }

    function changedRows(form, state) {
        var rows = rowsOf(form);
        var known = [];
        var count = 0;
        rows.forEach(function (row) {
            var before = state.rows.get(row);
            if (!before) {
                count++;
            } else {
                known.push({ row: row, before: before });
            }
        });
        var kept = keptInOrder(known.map(function (entry) { return entry.before.index; }));
        known.forEach(function (entry, i) {
            if (!kept.has(i) || entry.before.values !== rowValues(entry.row)) {
                count++;
            }
        });
        state.rows.forEach(function (before, row) {
            if (rows.indexOf(row) < 0) {
                count++;
            }
        });
        return count;
    }

    function render(form, state) {
        form.toggleAttribute('data-dirty', state.dirty);
        form.querySelectorAll('[data-cl-dirty-status]').forEach(function (status) {
            status.textContent = state.dirty
                ? (status.getAttribute('data-template') || '').replace('{n}', Math.max(state.count, 1))
                : status.getAttribute('data-clean') || '';
            status.classList.toggle('cl-unsaved', state.dirty);
        });
        form.querySelectorAll('[data-cl-save-items]').forEach(function (button) {
            button.classList.toggle('is-primary', state.dirty);
        });
        form.querySelectorAll('[data-cl-discard]').forEach(function (link) {
            link.hidden = !state.dirty;
        });
    }

    function update(form) {
        var state = states.get(form);
        if (!state) {
            return;
        }
        var count = changedRows(form, state);
        var dirty = count > 0 || otherValues(form) !== state.others;
        var changed = dirty !== state.dirty || count !== state.count;
        state.dirty = dirty;
        state.count = count;
        if (changed) {
            render(form, state);
            form.dispatchEvent(new CustomEvent('cl:dirty-changed', { bubbles: true, detail: { dirty: dirty, count: count } }));
        }
    }

    function watch(form) {
        states.set(form, snapshot(form));
        ['input', 'change', 'cl:lines-changed'].forEach(function (type) {
            form.addEventListener(type, function () {
                update(form);
            });
        });
        form.addEventListener('click', function (event) {
            var discard = event.target.closest && event.target.closest('[data-cl-discard]');
            var question = form.getAttribute('data-discard-confirm');
            if (!discard || !question) {
                return;
            }
            if (window.confirm(question)) {
                states.get(form).leaving = true;
            } else {
                event.preventDefault();
            }
        });
    }

    window.CL_isDirty = function (form) {
        var state = form && states.get(form);
        return !!state && state.dirty;
    };

    window.CL_markClean = function (form) {
        var state = form && states.get(form);
        if (!state) {
            return;
        }
        var wasDirty = state.dirty;
        states.set(form, snapshot(form));
        render(form, states.get(form));
        if (wasDirty) {
            form.dispatchEvent(new CustomEvent('cl:dirty-changed', { bubbles: true, detail: { dirty: false, count: 0 } }));
        }
    };

    // On the window, so it runs after every other submit listener: a submit a page script stopped (a field error) keeps
    // the guard, a submit that goes on leaves the page with the form's own values.
    window.addEventListener('submit', function (event) {
        var state = event.target instanceof HTMLFormElement && states.get(event.target);
        if (state && !event.defaultPrevented) {
            state.leaving = true;
        }
    });

    window.addEventListener('beforeunload', function (event) {
        var forms = document.querySelectorAll('form[data-cl-dirty-form]');
        var unsaved = Array.prototype.some.call(forms, function (form) {
            var state = states.get(form);
            return state && state.dirty && !state.leaving;
        });
        if (unsaved) {
            event.preventDefault();
            event.returnValue = '';
        }
    });

    // a page restored from the back/forward cache stays where it was, with its changes still unsaved
    window.addEventListener('pageshow', function (event) {
        if (!event.persisted) {
            return;
        }
        document.querySelectorAll('form[data-cl-dirty-form]').forEach(function (form) {
            var state = states.get(form);
            if (state) {
                state.leaving = false;
            }
        });
    });

    // after the other deferred scripts of the page, which may still arrange the form (reveal controls, add rows)
    function init() {
        document.querySelectorAll('form[data-cl-dirty-form]').forEach(watch);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
