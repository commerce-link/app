// Filter and search over the rows of a data table. A container [data-cl-table-filter] holds any number of filter
// groups: buttons [data-cl-filter-group="g"][data-cl-filter="value"] (one pressed per group; a button without a group
// belongs to "status") and selects [data-cl-filter-group="g"][data-cl-filter-select] (option value "all" = no filter).
// A row carries one attribute per group: [data-cl-filter-<g>="value"] (space-separated list for groups that may match
// several values); the legacy [data-cl-filter-value] is read as the "status" group. Optional [data-cl-table-search]
// matches [data-cl-search] (lower-cased). [data-cl-filter-empty] shows when nothing matches, [data-cl-filter-clear]
// resets every group. Counts in span.cl-segment-count / option[data-count] are recomputed against the other groups and
// the search, so a segment tells how many rows it would show. The state of the groups named in
// [data-cl-filter-default="status:active feature:all"] lives in the URL (history.replaceState) and starts from there;
// a bare default ("rejected") is the value of the "status" group and stays out of the URL. Each change dispatches
// "cl:table-filtered" on the container so table-select.js can drop hidden rows from the selection.
(function () {
    'use strict';

    function groupOf(control) {
        return control.getAttribute('data-cl-filter-group') || 'status';
    }

    function rowValues(row, group) {
        var attribute = row.getAttribute('data-cl-filter-' + group);
        if (attribute === null && group === 'status') {
            attribute = row.getAttribute('data-cl-filter-value');
        }
        if (attribute === null) {
            return [];
        }
        // The whole attribute counts as one value, so a group whose values are free text matches a label with a space
        // in it ("RTX 5060"); a group whose row may match several values lists them separated by spaces.
        var values = attribute.split(/\s+/).filter(Boolean);
        var whole = attribute.trim();
        if (whole !== '' && values.indexOf(whole) === -1) {
            values.push(whole);
        }
        return values;
    }

    function selected(container) {
        var state = {};
        container.querySelectorAll('[data-cl-filter][aria-pressed="true"]').forEach(function (button) {
            state[groupOf(button)] = button.getAttribute('data-cl-filter');
        });
        container.querySelectorAll('select[data-cl-filter-select]').forEach(function (select) {
            state[groupOf(select)] = select.value || 'all';
        });
        return state;
    }

    function matches(row, state, needle, skipGroup) {
        for (var group in state) {
            if (group === skipGroup || state[group] === 'all') {
                continue;
            }
            if (rowValues(row, group).indexOf(state[group]) === -1) {
                return false;
            }
        }
        return needle === '' || (row.getAttribute('data-cl-search') || '').indexOf(needle) !== -1;
    }

    function rows(container) {
        return Array.prototype.filter.call(container.querySelectorAll('tbody tr'), function (row) {
            return Array.prototype.some.call(row.attributes, function (a) {
                return a.name.indexOf('data-cl-filter-') === 0;
            });
        });
    }

    function recount(container, all, state, needle) {
        container.querySelectorAll('[data-cl-filter]').forEach(function (button) {
            var group = groupOf(button);
            var value = button.getAttribute('data-cl-filter');
            var count = all.filter(function (row) {
                return matches(row, state, needle, group) && (value === 'all' || rowValues(row, group).indexOf(value) !== -1);
            }).length;
            button.setAttribute('data-count', String(count));
            var badge = button.querySelector('.cl-segment-count');
            if (badge) {
                badge.textContent = String(count);
            }
        });
        container.querySelectorAll('select[data-cl-filter-select]').forEach(function (select) {
            var group = groupOf(select);
            Array.prototype.forEach.call(select.options, function (option) {
                if (!option.hasAttribute('data-count')) {
                    return;
                }
                var count = all.filter(function (row) {
                    return matches(row, state, needle, group)
                        && (option.value === 'all' || rowValues(row, group).indexOf(option.value) !== -1);
                }).length;
                option.setAttribute('data-count', String(count));
                var label = option.getAttribute('data-label') || option.textContent.replace(/\s*\(\d+\)$/, '');
                option.setAttribute('data-label', label);
                option.textContent = label + ' (' + count + ')';
            });
        });
    }

    // Only the groups written as "group:value" in the default; a page on the legacy single-group markup keeps the
    // address it was opened with.
    function declared(container) {
        var groups = [];
        (container.getAttribute('data-cl-filter-default') || '').split(/\s+/).forEach(function (pair) {
            var parts = pair.split(':');
            if (parts.length === 2) {
                groups.push(parts[0]);
            }
        });
        return groups;
    }

    function remember(container, state) {
        var groups = declared(container);
        if (groups.length === 0 || !window.history || !window.history.replaceState) {
            return;
        }
        var url = new URL(window.location.href);
        groups.forEach(function (group) {
            if (!(group in state) || state[group] === 'all') {
                url.searchParams.delete(group);
            } else {
                url.searchParams.set(group, state[group]);
            }
        });
        window.history.replaceState(null, '', url.toString());
    }

    function apply(container) {
        var state = selected(container);
        var search = container.querySelector('[data-cl-table-search]');
        var needle = search ? search.value.trim().toLowerCase() : '';
        var all = rows(container);
        var shown = 0;
        all.forEach(function (row) {
            var visible = matches(row, state, needle, null);
            row.hidden = !visible;
            if (visible) {
                shown++;
            }
        });
        var empty = container.querySelector('[data-cl-filter-empty]');
        if (empty) {
            empty.hidden = shown > 0;
        }
        recount(container, all, state, needle);
        remember(container, state);
        container.dispatchEvent(new CustomEvent('cl:table-filtered', { bubbles: true, detail: { shown: shown, state: state } }));
    }

    function buttonsOf(container, group) {
        return Array.prototype.filter.call(container.querySelectorAll('[data-cl-filter]'), function (button) {
            return groupOf(button) === group;
        });
    }

    function press(container, group, value) {
        var buttons = buttonsOf(container, group);
        var found = false;
        buttons.forEach(function (button) {
            var on = button.getAttribute('data-cl-filter') === value;
            button.setAttribute('aria-pressed', String(on));
            found = found || on;
        });
        container.querySelectorAll('select[data-cl-filter-select]').forEach(function (select) {
            if (groupOf(select) === group) {
                select.value = value;
                // A value the server no longer offers (a stale address) leaves the select empty; fall back to "all".
                if (select.value !== value) {
                    select.value = 'all';
                }
            }
        });
        if (!found && buttons.length > 0) {
            buttons[0].setAttribute('aria-pressed', 'true');
        }
    }

    function defaults(container) {
        var state = {};
        (container.getAttribute('data-cl-filter-default') || 'status:all').split(/\s+/).forEach(function (pair) {
            var parts = pair.split(':');
            if (parts.length === 2) {
                state[parts[0]] = parts[1];
            } else if (parts[0] !== '') {
                state.status = parts[0];
            }
        });
        var url = new URL(window.location.href);
        declared(container).forEach(function (group) {
            if (url.searchParams.has(group)) {
                state[group] = url.searchParams.get(group);
            }
        });
        return state;
    }

    function init(container) {
        container.querySelectorAll('[data-cl-filter-controls]').forEach(function (controls) {
            controls.hidden = false;
        });
        container.addEventListener('click', function (event) {
            var button = event.target.closest && event.target.closest('[data-cl-filter]');
            if (button) {
                press(container, groupOf(button), button.getAttribute('data-cl-filter'));
                apply(container);
                return;
            }
            if (event.target.closest && event.target.closest('[data-cl-filter-clear]')) {
                Object.keys(defaults(container)).forEach(function (group) {
                    press(container, group, 'all');
                });
                var search = container.querySelector('[data-cl-table-search]');
                if (search) {
                    search.value = '';
                }
                apply(container);
            }
        });
        container.addEventListener('input', function (event) {
            if (event.target.matches('[data-cl-table-search]')) {
                apply(container);
            }
        });
        container.addEventListener('change', function (event) {
            if (event.target.matches('select[data-cl-filter-select]')) {
                apply(container);
            }
        });
        var state = defaults(container);
        Object.keys(state).forEach(function (group) {
            press(container, group, state[group]);
        });
        apply(container);
    }

    function initAll() {
        document.querySelectorAll('[data-cl-table-filter]').forEach(init);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
})();
