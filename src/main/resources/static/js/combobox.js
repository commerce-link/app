// The single-choice combobox of fragments/combobox: swaps the native select for an editable combobox -- one text field
// that shows the chosen label and filters the server-drawn options as the operator types (APG "editable combobox with
// list autocomplete"). The script only filters, moves the highlight and picks.
// A pick (Enter, a click) fires "cl:combobox-change" on the .cl-combobox with {value, label}; the arrows only move the
// highlight, so nothing that listens for a pick runs while the operator is still looking (WCAG 3.2.2).
(function () {
    'use strict';

    var ANNOUNCE_DELAY = 600;

    function normalize(text) {
        return text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/ł/g, 'l');
    }

    function format(template, values) {
        return values.reduce(function (text, value, index) {
            return text.replace('{' + index + '}', value);
        }, template);
    }

    function setUp(root) {
        if (root.dataset.comboboxReady) {
            return;
        }
        root.dataset.comboboxReady = 'true';

        var select = root.querySelector('[data-combobox-select]');
        var field = root.querySelector('[data-combobox-value]');
        var box = root.querySelector('[data-combobox]');
        var input = root.querySelector('[data-combobox-input]');
        var toggle = root.querySelector('[data-combobox-toggle]');
        var menu = box.querySelector('.cl-picker-menu');
        var list = root.querySelector('[data-combobox-list]');
        var empty = root.querySelector('[data-combobox-empty]');
        var counter = root.querySelector('[data-combobox-count]');
        var options = Array.prototype.slice.call(list.querySelectorAll('[role="option"]'));
        var haystacks = new Map(options.map(function (option) {
            // The grey line is searchable too: typing a catalog's name lists its categories.
            return [option, normalize(option.textContent.replace(/\s+/g, ' '))];
        }));
        var chosenLabel = input.value;
        var visible = options;
        var active = -1;
        var announceTimer = null;
        var quiet = false;
        var selectOnMouseUp = false;
        var hadFocus = document.activeElement === select;

        // The combobox takes over: the label points at the text field, the select no longer posts or validates.
        var fieldLabel = document.querySelector('label[for="' + select.id + '"]');
        if (fieldLabel) {
            fieldLabel.htmlFor = input.id;
        }
        // An error summary links to the field by its id; the hidden select would take no focus.
        document.querySelectorAll('a[href="#' + select.id + '"]').forEach(function (link) {
            link.setAttribute('href', '#' + input.id);
        });
        select.hidden = true;
        select.disabled = true;
        field.disabled = false;
        box.hidden = false;

        // Focus that the operator did not ask for (the page's autofocus, a pick, Escape) must not open the list.
        function focusQuietly() {
            quiet = true;
            input.focus();
            quiet = false;
        }

        if (hadFocus || select.hasAttribute('autofocus')) {
            focusQuietly();
        }

        function isOpen() {
            return !menu.hidden;
        }

        function announce() {
            clearTimeout(announceTimer);
            var text = format(counter.dataset.comboboxCountTemplate || '', [visible.length, options.length]);
            announceTimer = setTimeout(function () {
                counter.textContent = text;
            }, ANNOUNCE_DELAY);
        }

        function highlight(index) {
            active = index;
            options.forEach(function (option) {
                option.classList.remove('is-active');
            });
            if (active < 0 || !visible[active]) {
                input.removeAttribute('aria-activedescendant');
                return;
            }
            visible[active].classList.add('is-active');
            visible[active].scrollIntoView({ block: 'nearest' });
            input.setAttribute('aria-activedescendant', visible[active].id);
        }

        function filter(text) {
            var query = normalize(text.trim());
            visible = options.filter(function (option) {
                var match = !query || haystacks.get(option).indexOf(query) !== -1;
                option.hidden = !match;
                return match;
            });
            empty.hidden = visible.length > 0;
            list.scrollTop = 0;
            highlight(-1);
        }

        function setExpanded(expanded) {
            menu.hidden = !expanded;
            input.setAttribute('aria-expanded', String(expanded));
            toggle.setAttribute('aria-expanded', String(expanded));
        }

        // Opening shows the whole list with the chosen option highlighted. Opened by a focus or a click, the text is
        // selected, so typing replaces it; opened by typing, the caret stays after what was typed.
        function open(selectText) {
            if (isOpen()) {
                return;
            }
            setExpanded(true);
            filter('');
            counter.textContent = '';
            clearTimeout(announceTimer);
            // A field low on a phone screen would open its list below the fold.
            menu.scrollIntoView({ block: 'nearest' });
            var chosen = visible.findIndex(function (option) {
                return option.dataset.value === field.value;
            });
            if (chosen >= 0) {
                highlight(chosen);
            }
            if (selectText !== false) {
                input.select();
            }
        }

        // Closing without a pick puts the chosen label back: a half-typed text is never the field's value.
        function close() {
            setExpanded(false);
            input.removeAttribute('aria-activedescendant');
            clearTimeout(announceTimer);
            input.value = chosenLabel;
        }

        function pick(option) {
            var changed = option.dataset.value !== field.value;
            field.value = option.dataset.value;
            chosenLabel = option.dataset.label;
            options.forEach(function (candidate) {
                var isSelected = candidate === option;
                candidate.classList.toggle('is-selected', isSelected);
                candidate.setAttribute('aria-selected', String(isSelected));
            });
            input.classList.remove('is-invalid');
            input.removeAttribute('aria-invalid');
            close();
            if (document.activeElement !== input) {
                focusQuietly();
            }
            if (changed) {
                root.dispatchEvent(new CustomEvent('cl:combobox-change', {
                    bubbles: true,
                    detail: { value: option.dataset.value, label: option.dataset.label }
                }));
            }
        }

        input.addEventListener('focus', function () {
            if (!quiet) {
                open();
            }
        });
        input.addEventListener('mousedown', function () {
            // A click that focuses the field would put the caret where it landed and undo the selected text.
            selectOnMouseUp = document.activeElement !== input;
        });
        input.addEventListener('mouseup', function (event) {
            if (selectOnMouseUp) {
                event.preventDefault();
                selectOnMouseUp = false;
            }
        });
        input.addEventListener('click', function () {
            open();
        });
        input.addEventListener('input', function () {
            open(false);
            filter(input.value);
            announce();
        });
        input.addEventListener('keydown', function (event) {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                if (!isOpen() || event.altKey) {
                    open();
                } else if (event.key === 'ArrowDown') {
                    highlight(Math.min(active + 1, visible.length - 1));
                } else {
                    highlight(Math.max(active - 1, 0));
                }
            } else if ((event.key === 'Home' || event.key === 'End') && isOpen() && active >= 0) {
                // While an option is highlighted Home and End move through the list; otherwise they move the caret.
                event.preventDefault();
                highlight(event.key === 'Home' ? 0 : visible.length - 1);
            } else if (event.key === 'Enter') {
                // Never the form's implicit submit: its first button is "Zmień kategorię", the no-script reload.
                event.preventDefault();
                if (!isOpen()) {
                    return;
                }
                if (active >= 0) {
                    pick(visible[active]);
                } else if (visible.length === 1) {
                    pick(visible[0]);
                }
            } else if (event.key === 'Escape') {
                if (isOpen() || input.value !== chosenLabel) {
                    event.preventDefault();
                    close();
                }
            }
        });
        toggle.addEventListener('mousedown', function (event) {
            // The focus stays in the text field, so pressing the chevron does not count as leaving it.
            event.preventDefault();
        });
        toggle.addEventListener('click', function () {
            if (isOpen()) {
                close();
                focusQuietly();
            } else {
                focusQuietly();
                open();
            }
        });
        list.addEventListener('mousedown', function (event) {
            // The focus stays in the text field, so a click on an option does not count as leaving the field.
            event.preventDefault();
        });
        list.addEventListener('click', function (event) {
            var option = event.target.closest('[role="option"]');
            if (option && !option.hidden) {
                pick(option);
            }
        });
        root.addEventListener('focusout', function (event) {
            if (!root.contains(event.relatedTarget) && (isOpen() || input.value !== chosenLabel)) {
                close();
            }
        });
        document.addEventListener('click', function (event) {
            if (isOpen() && !root.contains(event.target)) {
                close();
            }
        });
        input.addEventListener('invalid', function () {
            input.classList.add('is-invalid');
            input.setAttribute('aria-invalid', 'true');
            // The browser focuses the field to show its message; the list would cover it.
            quiet = true;
            setTimeout(function () {
                quiet = false;
            });
        });

        // A form with novalidate (the server's error summary) skips the browser's check, so an empty required field is
        // stopped here; reportValidity still shows the browser's own message on the text field and focuses it.
        if (field.form && select.required) {
            field.form.addEventListener('submit', function (event) {
                if (!field.value && !(event.submitter && event.submitter.formNoValidate)) {
                    event.preventDefault();
                    input.classList.add('is-invalid');
                    input.setAttribute('aria-invalid', 'true');
                    if (input.reportValidity()) {
                        input.focus();
                    }
                }
            });
        }
    }

    document.querySelectorAll('[data-cl-combobox]').forEach(setUp);
})();
