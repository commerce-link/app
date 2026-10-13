// A POST form with data-cl-submit-once sends once: a second click while the first request is on its way is ignored
// and the button says it is busy (e.g. "Pobierz wpłaty z systemu fakturowego", which asks the invoicing system about
// every invoice before it answers). The button is disabled after the submit event, so its own value is still sent.
// A button with data-cl-busy-label shows a spinner and that text while the request runs, and [data-cl-busy-note]
// elements of the form are revealed (copying a store, which can take a minute).
(function () {
    'use strict';

    function showBusy(button, label) {
        button.setAttribute('data-cl-idle-label', button.textContent);
        var spinner = document.createElement('span');
        spinner.className = 'cl-spinner is-compact';
        spinner.setAttribute('aria-hidden', 'true');
        var text = document.createElement('span');
        text.textContent = label;
        button.replaceChildren(spinner, text);
    }

    document.addEventListener('submit', function (event) {
        var form = event.target.closest('form[data-cl-submit-once]');
        if (!form) {
            return;
        }
        if (form.getAttribute('aria-busy') === 'true') {
            event.preventDefault();
            return;
        }
        form.setAttribute('aria-busy', 'true');
        var button = event.submitter || form.querySelector('[type="submit"]');
        if (button) {
            var busyLabel = button.getAttribute('data-cl-busy-label');
            if (busyLabel) {
                showBusy(button, busyLabel);
            }
            setTimeout(function () {
                button.disabled = true;
            }, 0);
        }
        form.querySelectorAll('[data-cl-busy-note]').forEach(function (note) {
            note.hidden = false;
        });
    });

    // Back from the next page may restore this one from the cache with the button still disabled.
    window.addEventListener('pageshow', function (event) {
        if (!event.persisted) {
            return;
        }
        document.querySelectorAll('form[data-cl-submit-once][aria-busy="true"]').forEach(function (form) {
            form.removeAttribute('aria-busy');
            form.querySelectorAll('[type="submit"]').forEach(function (button) {
                button.disabled = false;
                var idleLabel = button.getAttribute('data-cl-idle-label');
                if (idleLabel !== null) {
                    button.textContent = idleLabel;
                    button.removeAttribute('data-cl-idle-label');
                }
            });
            form.querySelectorAll('[data-cl-busy-note]').forEach(function (note) {
                note.hidden = true;
            });
        });
    });
})();
