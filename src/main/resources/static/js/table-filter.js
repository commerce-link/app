// Filter and search over the rows of a data table. A container [data-cl-table-filter] holds any number of filter
// groups: buttons [data-cl-filter-group="g"][data-cl-filter="value"] (one pressed per group; a button without a group
// belongs to "status") and selects [data-cl-filter-group="g"][data-cl-filter-select] (option value "all" = no filter).
// A row carries one attribute per group: [data-cl-filter-<g>="value"]; the legacy [data-cl-filter-value] is read as
// the "status" group.
//
// The container says which groups may match several values at once: [data-cl-filter-multi="feature tag"], a
// space-separated list of group names. For a group named there the row attribute is a space-separated list and the row
// matches any one of its values; for every other group the whole attribute is the value, so a free-text label with a
// space in it ("RTX 5060") matches only itself and never the word "RTX". A group cannot be both.
//
// Optional [data-cl-table-search] matches [data-cl-search] (lower-cased). [data-cl-filter-empty] shows when nothing
// matches, [data-cl-filter-clear] resets every group. Counts are recomputed against the other groups and the search,
// so a segment tells how many rows it would show: span.cl-segment-count inside a button, and option[data-count] on a
// select -- but only an option that also carries [data-label] has the count written into its text ("Promocja (3)"),
// because reading the label back out of the text would eat a legitimate trailing "(n)" in the label itself.
//
// The state of the groups named in [data-cl-filter-default="status:active feature:all"] lives in the URL
// (history.replaceState) and starts from there; a group leaves the URL only when it is back at the value its default
// names, so "all" is kept whenever the default is something else. A bare default ("rejected") is the value of the
// "status" group and stays out of the URL. A search field that names a parameter ([data-cl-table-search="q"]) keeps
// its text in the URL under that name as well. Links marked [data-cl-filter-carry] inside the container follow the
// URL's query, so a page opened from the list knows the filter to come back to. Each change dispatches
// "cl:table-filtered" on the container so table-select.js can drop hidden rows from the selection.
(function () {
    'use strict';

    function groupOf(control) {
        return control.getAttribute('data-cl-filter-group') || 'status';
    }

    function multiGroups(container) {
        return (container.getAttribute('data-cl-filter-multi') || '').split(/\s+/).filter(Boolean);
    }

    function rowValues(row, group, multi) {
        var attribute = row.getAttribute('data-cl-filter-' + group);
        if (attribute === null && group === 'status') {
            attribute = row.getAttribute('data-cl-filter-value');
        }
        if (attribute === null) {
            return [];
        }
        if (multi.indexOf(group) !== -1) {
            return attribute.split(/\s+/).filter(Boolean);
        }
        var whole = attribute.trim();
        return whole === '' ? [] : [whole];
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

    function matches(row, state, needle, skipGroup, multi) {
        for (var group in state) {
            if (group === skipGroup || state[group] === 'all') {
                continue;
            }
            if (rowValues(row, group, multi).indexOf(state[group]) === -1) {
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

    function recount(container, all, state, needle, multi) {
        container.querySelectorAll('[data-cl-filter]').forEach(function (button) {
            var group = groupOf(button);
            var value = button.getAttribute('data-cl-filter');
            var count = all.filter(function (row) {
                return matches(row, state, needle, group, multi)
                    && (value === 'all' || rowValues(row, group, multi).indexOf(value) !== -1);
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
                    return matches(row, state, needle, group, multi)
                        && (option.value === 'all' || rowValues(row, group, multi).indexOf(option.value) !== -1);
                }).length;
                option.setAttribute('data-count', String(count));
                // Only a label the template states can be rewritten; guessing it back out of the text would eat a
                // legitimate trailing "(n)" the first time the count is written.
                var label = option.getAttribute('data-label');
                if (label !== null) {
                    option.textContent = label + ' (' + count + ')';
                }
            });
        });
    }

    // Only the groups written as "group:value" in the default, with that value; a page on the legacy single-group
    // markup keeps the address it was opened with.
    function declaredDefaults(container) {
        var initial = {};
        (container.getAttribute('data-cl-filter-default') || '').split(/\s+/).forEach(function (pair) {
            var parts = pair.split(':');
            if (parts.length === 2) {
                initial[parts[0]] = parts[1];
            }
        });
        return initial;
    }

    function declared(container) {
        return Object.keys(declaredDefaults(container));
    }

    // The URL parameter the search keeps its text in, or null when the field names none.
    function searchParam(container) {
        var search = container.querySelector('[data-cl-table-search]');
        var name = search ? search.getAttribute('data-cl-table-search') : null;
        return name ? name : null;
    }

    function remember(container, state, needle) {
        var initial = declaredDefaults(container);
        var groups = Object.keys(initial);
        var param = searchParam(container);
        if ((groups.length === 0 && !param) || !window.history || !window.history.replaceState) {
            return;
        }
        var url = new URL(window.location.href);
        groups.forEach(function (group) {
            // A value is dropped only when it is what the page shows anyway: "all" is a filter like any other when
            // the default is "active", and must survive a reload.
            if (!(group in state) || state[group] === initial[group]) {
                url.searchParams.delete(group);
            } else {
                url.searchParams.set(group, state[group]);
            }
        });
        if (param) {
            if (needle === '') {
                url.searchParams.delete(param);
            } else {
                url.searchParams.set(param, needle);
            }
        }
        window.history.replaceState(null, '', url.toString());
    }

    // Links to pages that come back to this list take the list's filter along.
    function carry(container) {
        container.querySelectorAll('a[data-cl-filter-carry]').forEach(function (link) {
            var target = new URL(link.getAttribute('href'), window.location.href);
            link.setAttribute('href', target.pathname + window.location.search);
        });
    }

    function apply(container) {
        var state = selected(container);
        var search = container.querySelector('[data-cl-table-search]');
        var text = search ? search.value.trim() : '';
        var needle = text.toLowerCase();
        var all = rows(container);
        var multi = multiGroups(container);
        var shown = 0;
        all.forEach(function (row) {
            var visible = matches(row, state, needle, null, multi);
            row.hidden = !visible;
            if (visible) {
                shown++;
            }
        });
        var empty = container.querySelector('[data-cl-filter-empty]');
        if (empty) {
            empty.hidden = shown > 0;
        }
        recount(container, all, state, needle, multi);
        remember(container, state, text);
        carry(container);
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
        var param = searchParam(container);
        var search = container.querySelector('[data-cl-table-search]');
        if (param && search) {
            var start = new URL(window.location.href).searchParams.get(param);
            if (start !== null) {
                search.value = start;
            }
        }
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
