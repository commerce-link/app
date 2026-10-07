// The single-choice combobox of fragments/combobox: swaps the native select for a trigger with a searchable, grouped
// listbox. The options are drawn by the server; the script only filters, moves the highlight and picks.
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
        var trigger = root.querySelector('[data-combobox-trigger]');
        var label = root.querySelector('[data-combobox-label]');
        var menu = box.querySelector('.cl-picker-menu');
        var search = root.querySelector('[data-combobox-search]');
        var list = root.querySelector('[data-combobox-list]');
        var empty = root.querySelector('[data-combobox-empty]');
        var counter = root.querySelector('[data-combobox-count]');
        var groups = Array.prototype.slice.call(list.querySelectorAll('[role="group"]'));
        var options = Array.prototype.slice.call(list.querySelectorAll('[role="option"]'));
        var haystacks = new Map(options.map(function (option) {
            var group = option.closest('[role="group"]');
            var heading = group ? group.querySelector('.cl-picker-group-label') : null;
            // A group's heading is searchable too: typing a catalog's name lists its categories.
            return [option, normalize(option.dataset.label + ' ' + (heading ? heading.textContent : ''))];
        }));
        var visible = options;
        var active = -1;
        var announceTimer = null;
        var hadFocus = document.activeElement === select;

        // The combobox takes over: the label points at the trigger, the select no longer posts or validates.
        var fieldLabel = document.querySelector('label[for="' + select.id + '"]');
        if (fieldLabel) {
            fieldLabel.htmlFor = trigger.id;
        }
        // An error summary links to the field by its id; the hidden select would take no focus.
        document.querySelectorAll('a[href="#' + select.id + '"]').forEach(function (link) {
            link.setAttribute('href', '#' + trigger.id);
        });
        select.hidden = true;
        select.disabled = true;
        field.disabled = false;
        box.hidden = false;
        if (hadFocus || select.hasAttribute('autofocus')) {
            trigger.focus();
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
                search.removeAttribute('aria-activedescendant');
                return;
            }
            visible[active].classList.add('is-active');
            visible[active].scrollIntoView({ block: 'nearest' });
            search.setAttribute('aria-activedescendant', visible[active].id);
        }

        function filter() {
            var query = normalize(search.value.trim());
            visible = options.filter(function (option) {
                var match = !query || haystacks.get(option).indexOf(query) !== -1;
                option.hidden = !match;
                return match;
            });
            groups.forEach(function (group) {
                group.hidden = !group.querySelector('[role="option"]:not([hidden])');
            });
            empty.hidden = visible.length > 0;
            list.scrollTop = 0;
            highlight(-1);
            announce();
        }

        function open() {
            menu.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            search.setAttribute('aria-expanded', 'true');
            search.value = '';
            filter();
            counter.textContent = '';
            clearTimeout(announceTimer);
            search.focus();
            // A field low on a phone screen would open its list below the fold.
            menu.scrollIntoView({ block: 'nearest' });
            var chosen = visible.findIndex(function (option) {
                return option.dataset.value === field.value;
            });
            if (chosen >= 0) {
                highlight(chosen);
            }
        }

        function close(focusTrigger) {
            var focusInside = menu.contains(document.activeElement);
            menu.hidden = true;
            trigger.setAttribute('aria-expanded', 'false');
            search.setAttribute('aria-expanded', 'false');
            search.removeAttribute('aria-activedescendant');
            clearTimeout(announceTimer);
            // Hiding the menu with the focus in it would drop the focus onto <body>.
            if (focusTrigger || focusInside) {
                trigger.focus();
            }
        }

        function pick(option) {
            var changed = option.dataset.value !== field.value;
            field.value = option.dataset.value;
            label.textContent = option.dataset.label;
            options.forEach(function (candidate) {
                var isSelected = candidate === option;
                candidate.classList.toggle('is-selected', isSelected);
                candidate.setAttribute('aria-selected', String(isSelected));
            });
            trigger.classList.remove('is-invalid');
            close(true);
            if (changed) {
                root.dispatchEvent(new CustomEvent('cl:combobox-change', {
                    bubbles: true,
                    detail: { value: option.dataset.value, label: option.dataset.label }
                }));
            }
        }

        trigger.addEventListener('click', function () {
            if (isOpen()) {
                close(true);
            } else {
                open();
            }
        });
        trigger.addEventListener('keydown', function (event) {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                open();
            } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey && event.key !== ' ') {
                // Typing on the closed field starts the search with that letter.
                event.preventDefault();
                open();
                search.value = event.key;
                filter();
            }
        });
        search.addEventListener('input', filter);
        search.addEventListener('keydown', function (event) {
            if (event.key === 'ArrowDown') {
                event.preventDefault();
                highlight(Math.min(active + 1, visible.length - 1));
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                highlight(Math.max(active - 1, 0));
            } else if ((event.key === 'Home' || event.key === 'End') && active >= 0) {
                // While an option is highlighted Home and End move through the list; otherwise they move the caret.
                event.preventDefault();
                highlight(event.key === 'Home' ? 0 : visible.length - 1);
            } else if (event.key === 'Enter') {
                event.preventDefault();
                if (active >= 0) {
                    pick(visible[active]);
                } else if (visible.length === 1) {
                    pick(visible[0]);
                }
            }
        });
        list.addEventListener('mousedown', function (event) {
            // The focus stays in the search, so a click on an option does not count as leaving the field.
            event.preventDefault();
        });
        list.addEventListener('click', function (event) {
            var option = event.target.closest('[role="option"]');
            if (option && !option.hidden) {
                pick(option);
            }
        });
        trigger.addEventListener('mousedown', function (event) {
            // Safari does not focus a pressed button: the menu would close on the press and open again on the click.
            if (isOpen()) {
                event.preventDefault();
            }
        });
        root.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && isOpen()) {
                event.preventDefault();
                close(true);
            }
        });
        root.addEventListener('focusout', function (event) {
            if (isOpen() && !root.contains(event.relatedTarget)) {
                close(false);
            }
        });
        document.addEventListener('click', function (event) {
            if (isOpen() && !root.contains(event.target)) {
                close(false);
            }
        });

        if (field.form && select.required) {
            field.form.addEventListener('submit', function (event) {
                if (!field.value && !(event.submitter && event.submitter.formNoValidate)) {
                    event.preventDefault();
                    trigger.classList.add('is-invalid');
                    open();
                }
            });
        }
    }

    document.querySelectorAll('[data-cl-combobox]').forEach(setUp);
})();
