// Filter and search over the rows of a data table (the offers of a marketplace export run). A container
// [data-cl-table-filter] holds buttons [data-cl-filter="value"] (one pressed at a time, "all" matches every row), an
// optional input [data-cl-table-search], rows [data-cl-filter-value][data-cl-search] and a message
// [data-cl-filter-empty] shown when nothing matches. It opens on [data-cl-filter-default]. Without JavaScript the
// controls stay hidden and every row is shown.
(function () {
    'use strict';

    function apply(container) {
        var pressed = container.querySelector('[data-cl-filter][aria-pressed="true"]');
        var filter = pressed ? pressed.getAttribute('data-cl-filter') : 'all';
        var search = container.querySelector('[data-cl-table-search]');
        var needle = search ? search.value.trim().toLowerCase() : '';
        var shown = 0;
        container.querySelectorAll('[data-cl-filter-value]').forEach(function (row) {
            var matches = (filter === 'all' || row.getAttribute('data-cl-filter-value') === filter)
                && (needle === '' || row.getAttribute('data-cl-search').indexOf(needle) !== -1);
            row.hidden = !matches;
            if (matches) {
                shown++;
            }
        });
        var empty = container.querySelector('[data-cl-filter-empty]');
        if (empty) {
            empty.hidden = shown > 0;
        }
    }

    function press(container, value) {
        container.querySelectorAll('[data-cl-filter]').forEach(function (button) {
            button.setAttribute('aria-pressed', String(button.getAttribute('data-cl-filter') === value));
        });
        apply(container);
    }

    function init(container) {
        container.querySelectorAll('[data-cl-filter-controls]').forEach(function (controls) {
            controls.hidden = false;
        });
        container.addEventListener('click', function (event) {
            var button = event.target.closest('[data-cl-filter]');
            if (button) {
                press(container, button.getAttribute('data-cl-filter'));
            }
        });
        container.addEventListener('input', function (event) {
            if (event.target.matches('[data-cl-table-search]')) {
                apply(container);
            }
        });
        press(container, container.getAttribute('data-cl-filter-default') || 'all');
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
