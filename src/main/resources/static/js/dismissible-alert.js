// Hides an alert carrying data-cl-dismiss-key for the rest of the browser session once its close button is used.
// Loaded right after the alert, not deferred, so an alert hidden earlier does not flash on the next page.
// Without JavaScript the close button stays hidden and the alert simply stays on the page.
(function () {
    document.querySelectorAll('[data-cl-dismiss-key]').forEach(function (alert) {
        if (alert.dataset.clDismissBound === '1') {
            return;
        }
        alert.dataset.clDismissBound = '1';
        const key = alert.dataset.clDismissKey;
        let dismissed = false;
        try {
            dismissed = window.sessionStorage.getItem(key) === '1';
        } catch (error) {
        }
        if (dismissed) {
            alert.hidden = true;
            return;
        }
        const button = alert.querySelector('[data-cl-dismiss]');
        if (!button) {
            return;
        }
        button.hidden = false;
        button.addEventListener('click', function () {
            alert.hidden = true;
            try {
                window.sessionStorage.setItem(key, '1');
            } catch (error) {
            }
            const content = document.getElementById('clContent');
            if (content) {
                content.focus();
            }
        });
    });
})();
