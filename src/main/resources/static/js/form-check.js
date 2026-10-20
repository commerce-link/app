// Checks a form ([data-cl-form-check]) in the browser before it is sent, for forms whose server answers a refusal with a
// redirect that empties them. Without this script the browser's own required checks apply (the form has no novalidate).
// With it the form gets noValidate and the messages come from the page, in the design system's look:
//   - [required] fields: data-cl-required-message; an e-mail field with a wrong format: data-cl-type-message;
//   - a file over data-cl-max-bytes: data-cl-size-message (the server's upload limit, so the file is not sent for nothing);
//   - "one of these" rules: p.cl-field-error[data-cl-any-of="id1 id2"][hidden] inside the group, shown when every field
//     it names is empty; the group note ([data-cl-any-of-note]) gives way to it.
// Errors are listed in the summary ([data-cl-form-check-summary]) with links to the fields, which gets the focus.
// Typing into a field takes its message away. A form that passed is not sent a second time by another click. A
// data-cl-async form (async-form.js) is checked the same way, again after each swap, and stays sendable after a failed send.
(function () {
    'use strict';

    function isBlank(control) {
        if (control.type === 'file') {
            return !control.files || control.files.length === 0;
        }
        return control.value.trim() === '';
    }

    function frameOf(control) {
        return control.closest('[data-cl-file-input]') || control;
    }

    function markInvalid(control, invalid) {
        frameOf(control).classList.toggle('is-invalid', invalid);
        if (invalid) {
            control.setAttribute('aria-invalid', 'true');
        } else {
            control.removeAttribute('aria-invalid');
        }
    }

    function describedBy(control, id, add) {
        var ids = (control.getAttribute('aria-describedby') || '').split(' ').filter(function (value) {
            return value && value !== id;
        });
        if (add) {
            ids.push(id);
        }
        if (ids.length) {
            control.setAttribute('aria-describedby', ids.join(' '));
        } else {
            control.removeAttribute('aria-describedby');
        }
    }

    function fieldErrorId(control) {
        return control.id + '-check-error';
    }

    function removeFieldError(control) {
        var error = document.getElementById(fieldErrorId(control));
        if (error) {
            error.remove();
            describedBy(control, error.id, false);
        }
    }

    function addFieldError(control, message) {
        var error = document.createElement('p');
        error.className = 'cl-field-error';
        error.id = fieldErrorId(control);
        var icon = document.createElement('i');
        icon.className = 'fas fa-exclamation-circle';
        icon.setAttribute('aria-hidden', 'true');
        var text = document.createElement('span');
        text.textContent = message;
        error.append(icon, text);
        var frame = frameOf(control);
        var help = frame.parentElement.querySelector(':scope > .cl-help');
        (help || frame).after(error);
        describedBy(control, error.id, true);
    }

    function ruleControls(form, rule) {
        return rule.getAttribute('data-cl-any-of').split(' ').map(function (id) {
            return form.querySelector('#' + CSS.escape(id));
        }).filter(Boolean);
    }

    function hasOwnError(control) {
        return !!document.getElementById(fieldErrorId(control));
    }

    // A hidden element still counts in a description, so a field names the rule's error, or else the group note, only
    // while it shows.
    function showRule(form, rule, shown) {
        rule.hidden = !shown;
        var group = rule.parentElement;
        var note = group.querySelector(':scope > [data-cl-any-of-note]');
        if (note) {
            note.hidden = !!group.querySelector(':scope > [data-cl-any-of]:not([hidden])');
        }
        ruleControls(form, rule).forEach(function (control) {
            markInvalid(control, shown || hasOwnError(control));
            if (rule.id) {
                describedBy(control, rule.id, shown);
            }
            if (note && note.id) {
                describedBy(control, note.id, !note.hidden && !shown);
            }
        });
    }

    function fieldProblem(control) {
        if (control.required && isBlank(control)) {
            return control.getAttribute('data-cl-required-message');
        }
        if (control.type === 'email' && !isBlank(control) && control.validity.typeMismatch) {
            return control.getAttribute('data-cl-type-message');
        }
        var maxBytes = Number(control.getAttribute('data-cl-max-bytes'));
        if (control.type === 'file' && maxBytes > 0 && control.files && control.files.length && control.files[0].size > maxBytes) {
            return control.getAttribute('data-cl-size-message');
        }
        return null;
    }

    function controlsOf(form) {
        return Array.prototype.filter.call(form.elements, function (control) {
            return control.id && !control.disabled
                && (control.required || control.type === 'email' || control.hasAttribute('data-cl-max-bytes'));
        });
    }

    function fillSummary(form, problems) {
        var summary = form.querySelector('[data-cl-form-check-summary]');
        if (!summary) {
            return null;
        }
        var list = summary.querySelector('ul');
        list.replaceChildren();
        problems.forEach(function (problem) {
            var link = document.createElement('a');
            link.href = '#' + problem.id;
            link.textContent = problem.message;
            link.addEventListener('click', function (event) {
                var target = document.getElementById(problem.id);
                if (target) {
                    event.preventDefault();
                    target.focus();
                }
            });
            var item = document.createElement('li');
            item.append(link);
            list.append(item);
        });
        summary.hidden = problems.length === 0;
        return summary;
    }

    function check(form) {
        var problems = [];
        controlsOf(form).forEach(function (control) {
            removeFieldError(control);
            var message = fieldProblem(control);
            if (message) {
                addFieldError(control, message);
                problems.push({ id: control.id, message: message });
            }
            markInvalid(control, !!message);
        });
        form.querySelectorAll('[data-cl-any-of]').forEach(function (rule) {
            var controls = ruleControls(form, rule);
            var missing = controls.length > 0 && controls.every(isBlank);
            showRule(form, rule, missing);
            if (missing) {
                problems.push({ id: controls[0].id, message: rule.textContent.trim() });
            }
        });
        return problems;
    }

    function recheck(form, control) {
        if (hasOwnError(control) && !fieldProblem(control)) {
            removeFieldError(control);
            markInvalid(control, false);
        }
        form.querySelectorAll('[data-cl-any-of]:not([hidden])').forEach(function (rule) {
            var controls = ruleControls(form, rule);
            if (controls.indexOf(control) >= 0 && !controls.every(isBlank)) {
                showRule(form, rule, false);
            }
        });
        var summary = form.querySelector('[data-cl-form-check-summary]');
        var anyLeft = form.querySelector('[id$="-check-error"], [data-cl-any-of]:not([hidden])');
        if (summary && !anyLeft) {
            summary.hidden = true;
        }
    }

    function enhance(form) {
        var sending = false;
        form.noValidate = true;
        form.addEventListener('submit', function (event) {
            if (sending) {
                event.preventDefault();
                return;
            }
            var problems = check(form);
            if (problems.length) {
                event.preventDefault();
                // async-form.js listens on the document and sends a data-cl-async form whatever happened before
                event.stopPropagation();
                var summary = fillSummary(form, problems);
                (summary || document.getElementById(problems[0].id)).focus();
            } else {
                fillSummary(form, []);
                // an async form stays on the page when its send fails, and must be sendable again
                sending = !form.hasAttribute('data-cl-async');
            }
        });
        // Back to a page kept by the browser (bfcache): the form can be sent again.
        window.addEventListener('pageshow', function () {
            sending = false;
        });
        ['input', 'change'].forEach(function (type) {
            form.addEventListener(type, function (event) {
                if (event.target.id) {
                    recheck(form, event.target);
                }
            });
        });
    }

    function init() {
        document.querySelectorAll('form[data-cl-form-check]').forEach(enhance);
        // a form async-form.js swapped for the server's answer is a new element
        document.addEventListener('cl:form-replaced', function (event) {
            if (event.target instanceof HTMLFormElement && event.target.hasAttribute('data-cl-form-check')) {
                enhance(event.target);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
