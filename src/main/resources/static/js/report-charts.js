// Reports page: the daily chart shows one panel at a time behind a segmented switch, its columns take the arrow keys
// and a tap, and the period menu closes on a click elsewhere or Escape. A change of period (‹ ›, a shortcut, "Show",
// Back) fetches the page in the background and swaps the parts that depend on the period — the files and the closing
// button in the title line, the period bar and the cards — so the frame, the icons and the chosen chart stay put; a
// wait longer than a moment dims the cards and shows a spinner. Without this script every panel stays visible, the menu
// is a plain <details> and a period is an ordinary page load; anything unexpected falls back to one.
(function () {
    'use strict';

    var PATH = '/dashboard/reports';
    var SWAPPED = ['.cl-page-actions.is-report', '.cl-report-toolbar', '.cl-chart-grid2'];
    var BUSY_AFTER_MS = 150;

    function setUpCharts(chosen) {
        document.querySelectorAll('[data-cl-chart]').forEach(function (card) {
            var panels = card.querySelectorAll('[data-cl-chart-panel]');
            var buttons = card.querySelectorAll('[data-cl-chart-show]');
            var title = card.querySelector('[data-cl-chart-heading]');
            var switcher = card.querySelector('[data-cl-chart-switch]');
            if (!panels.length || !switcher) { return; }
            function show(key) {
                panels.forEach(function (panel) { panel.hidden = panel.getAttribute('data-cl-chart-panel') !== key; });
                buttons.forEach(function (button) {
                    var on = button.getAttribute('data-cl-chart-show') === key;
                    button.setAttribute('aria-pressed', on ? 'true' : 'false');
                    if (on && title) { title.textContent = button.getAttribute('data-cl-chart-title'); }
                });
            }
            switcher.hidden = false;
            card.classList.add('is-switched');
            buttons.forEach(function (button) {
                button.addEventListener('click', function () { show(button.getAttribute('data-cl-chart-show')); });
            });
            var keys = Array.prototype.map.call(buttons, function (button) { return button.getAttribute('data-cl-chart-show'); });
            show(keys.indexOf(chosen) >= 0 ? chosen : keys[0]);
        });

        // Columns: one stop in the Tab order and the arrow keys between them (a month is 30 columns, a year 53); a
        // finger cannot hit a column a few pixels wide, so a tap anywhere on the plot shows the nearest one.
        document.querySelectorAll('.cl-chart-cols').forEach(function (list) {
            var columns = Array.prototype.slice.call(list.querySelectorAll('.cl-chart-col'));
            if (!columns.length) { return; }
            function select(index, focus) {
                columns.forEach(function (column, i) {
                    column.tabIndex = i === index ? 0 : -1;
                    column.classList.toggle('is-active', i === index && !focus);
                });
                if (focus) { columns[index].focus(); }
            }
            columns.forEach(function (column, i) { column.tabIndex = i === 0 ? 0 : -1; });
            list.addEventListener('keydown', function (event) {
                var index = columns.indexOf(document.activeElement);
                if (index < 0) { return; }
                var keys = { ArrowLeft: index - 1, ArrowRight: index + 1, Home: 0, End: columns.length - 1 };
                if (!(event.key in keys)) { return; }
                event.preventDefault();
                select(Math.max(0, Math.min(columns.length - 1, keys[event.key])), true);
            });
            list.addEventListener('pointerup', function (event) {
                if (event.pointerType === 'mouse') { return; }
                var nearest = 0, distance = Infinity;
                columns.forEach(function (column, i) {
                    var box = column.getBoundingClientRect();
                    var d = Math.abs(box.left + box.width / 2 - event.clientX);
                    if (d < distance) { distance = d; nearest = i; }
                });
                select(nearest, false);
            });
        });
    }

    function chosenChart() {
        var pressed = document.querySelector('[data-cl-chart-show][aria-pressed="true"]');
        return pressed && pressed.getAttribute('data-cl-chart-show');
    }

    function periodMenu() {
        return document.querySelector('[data-cl-period-menu]');
    }

    // The control the change came from, found again in the fresh bar, so the keyboard stays where it was.
    function controlKey(element) {
        if (!element || !element.closest) { return null; }
        if (element.closest('.cl-period > a.cl-button:first-child')) { return 'previous'; }
        if (element.closest('.cl-period > a.cl-button:last-child')) { return 'next'; }
        if (element.closest('[data-cl-period-menu]')) { return 'menu'; }
        return null;
    }

    function focusControl(key) {
        var selector = { previous: '.cl-period > a.cl-button:first-child', next: '.cl-period > a.cl-button:last-child',
            menu: '[data-cl-period-menu] > summary' }[key];
        var control = selector && document.querySelector(selector);
        if (control) { control.focus({ preventScroll: true }); }
    }

    var pending = null;
    var busyTimer = null;

    function load(href, push, focusKey) {
        var target = new URL(href, window.location.href);
        var page = document.querySelector('.cl-page-body');
        if (target.pathname !== PATH || !page || !window.fetch || !window.DOMParser) {
            window.location.assign(href);
            return;
        }
        var menu = periodMenu();
        if (menu) { menu.open = false; }
        if (pending) { pending.abort(); }
        var request = pending = new AbortController();
        clearTimeout(busyTimer);
        var grid = document.querySelector('.cl-chart-grid2');
        if (grid) { grid.setAttribute('aria-busy', 'true'); }
        // an answer within a moment swaps without a flash of the loading state
        busyTimer = setTimeout(function () {
            page.classList.add('is-loading');
            var status = document.querySelector('[data-cl-report-loading]');
            if (status) { status.hidden = false; }
        }, BUSY_AFTER_MS);
        var chosen = chosenChart();
        fetch(target, { headers: { 'X-Requested-With': 'fetch' }, credentials: 'same-origin', redirect: 'manual', signal: request.signal })
            .then(function (response) {
                if (!response.ok) { throw new Error('HTTP ' + response.status); }
                return response.text();
            })
            .then(function (html) {
                var fresh = new DOMParser().parseFromString(html, 'text/html');
                var parts = SWAPPED.map(function (selector) {
                    return [document.querySelector(selector), fresh.querySelector(selector)];
                });
                if (parts.some(function (pair) { return !pair[0] || !pair[1]; })) { throw new Error('page without its parts'); }
                parts.forEach(function (pair) { pair[0].replaceWith(document.importNode(pair[1], true)); });
                if (fresh.title) { document.title = fresh.title; }
                if (push) { history.pushState({ reports: true }, '', target.pathname + target.search); }
                setUpCharts(chosen);
                focusControl(focusKey);
            })
            .catch(function (error) {
                if (error && error.name === 'AbortError') { return; }
                window.location.assign(href);
            })
            .then(function () {
                if (pending !== request) { return; }
                pending = null;
                clearTimeout(busyTimer);
                page.classList.remove('is-loading');
            });
    }

    setUpCharts(null);

    document.addEventListener('pointerup', function (event) {
        if (event.target.closest && event.target.closest('.cl-chart-cols')) { return; }
        document.querySelectorAll('.cl-chart-col.is-active').forEach(function (column) { column.classList.remove('is-active'); });
    });

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        var link = event.target.closest && event.target.closest('.cl-period > a.cl-button[href], [data-cl-period-menu] .cl-date-presets a[href]');
        if (link) {
            event.preventDefault();
            load(link.getAttribute('href'), true, controlKey(link));
            return;
        }
        var menu = periodMenu();
        if (menu && menu.open && !menu.contains(event.target)) { menu.open = false; }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest && event.target.closest('[data-cl-period-menu] form');
        if (!form || !form.checkValidity()) { return; }
        event.preventDefault();
        load(PATH + '?' + new URLSearchParams(new FormData(form)).toString(), true, 'menu');
    });

    window.addEventListener('popstate', function () {
        if (window.location.pathname === PATH) { load(window.location.href, false, null); }
    });
    // the first entry gets a state too, so Back from a swapped period returns to it through popstate
    history.replaceState({ reports: true }, '', window.location.pathname + window.location.search);

    document.addEventListener('keydown', function (event) {
        var menu = periodMenu();
        if (event.key === 'Escape' && menu && menu.open) {
            menu.open = false;
            menu.querySelector('summary').focus();
        }
    });
    // the native date picker opens on a click anywhere in the field, not only on its icon (as on the lists)
    document.addEventListener('click', function (event) {
        var field = event.target.closest && event.target.closest('[data-cl-period-menu] input[type=date]');
        if (!field || typeof field.showPicker !== 'function') { return; }
        try { field.showPicker(); } catch (e) { /* typing still works */ }
    });
})();
