// Reports page: the daily chart shows one panel at a time behind a segmented switch, and the period menu closes on a
// click elsewhere or Escape. Without this script every panel stays visible and the menu is a plain <details>.
(function () {
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
        show(buttons[0].getAttribute('data-cl-chart-show'));
    });

    var menu = document.querySelector('[data-cl-period-menu]');
    if (menu) {
        document.addEventListener('click', function (event) {
            if (menu.open && !menu.contains(event.target)) { menu.open = false; }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && menu.open) {
                menu.open = false;
                menu.querySelector('summary').focus();
            }
        });
        // the native date picker opens on a click anywhere in the field, not only on its icon (as on the lists)
        menu.addEventListener('click', function (event) {
            var field = event.target.closest('input[type=date]');
            if (!field || typeof field.showPicker !== 'function') { return; }
            try { field.showPicker(); } catch (e) { /* typing still works */ }
        });
    }
})();
