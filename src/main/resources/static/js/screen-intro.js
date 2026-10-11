// The screen guide: open on a screen until "Rozumiem" or × is used, then folded into the "?" next to the title.
// Loaded right after the panel, not deferred, so a guide seen earlier stays hidden from the first paint.
(function () {
    const panel = document.querySelector('[data-intro-panel]');
    if (!panel || panel.dataset.introBound === '1') {
        return;
    }
    panel.dataset.introBound = '1';
    const storageKey = 'cl-intro-seen-' + panel.dataset.introPanel;
    const toggle = document.querySelector('[data-intro-toggle]');

    function seen() {
        try {
            return window.localStorage.getItem(storageKey) !== null;
        } catch (error) {
            return false;
        }
    }

    function markSeen() {
        try {
            window.localStorage.setItem(storageKey, 'true');
        } catch (error) {
        }
    }

    function setExpanded(expanded) {
        if (toggle) {
            toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        }
    }

    function dismiss() {
        const hadFocus = panel.contains(document.activeElement);
        markSeen();
        setExpanded(false);
        panel.classList.add('is-closing');
        if (toggle) {
            toggle.classList.remove('is-pulsing');
            void toggle.offsetWidth;
            toggle.classList.add('is-pulsing');
            // The button that had focus disappears with the panel; the "?" is where the guide now lives
            if (hadFocus) {
                toggle.focus();
            }
        }
        window.setTimeout(function () {
            panel.hidden = true;
            panel.classList.remove('is-closing');
        }, 380);
    }

    function open() {
        panel.classList.add('is-closing');
        panel.hidden = false;
        setExpanded(true);
        window.requestAnimationFrame(function () {
            window.requestAnimationFrame(function () {
                panel.classList.remove('is-closing');
            });
        });
    }

    panel.hidden = seen();
    if (toggle) {
        toggle.setAttribute('aria-controls', panel.id);
        setExpanded(!panel.hidden);
        toggle.addEventListener('click', function () {
            if (panel.hidden) {
                open();
            } else {
                dismiss();
            }
        });
    }
    panel.querySelectorAll('[data-intro-dismiss]').forEach(function (button) {
        button.addEventListener('click', dismiss);
    });
})();
