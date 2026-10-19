// A read-only value with a Copy button ([data-cl-copy-field]). Optional masking (data-masked): the page shows the
// masked text and the Show button swaps in the full value. Copy always copies the full value. Both buttons switch state
// inside a .cl-swap, so they never change size. Without JavaScript the buttons stay hidden and the full value is
// available in a <details> element.
(function () {
    'use strict';

    var STATUS_MS = 2000;
    // Full values the user chose to show; a form saved without reloading comes back with the value still shown.
    var shownValues = {};

    function init(root) {
        root.querySelectorAll('[data-cl-copy-field]').forEach(function (field) {
            if (field.hasAttribute('data-cl-ready')) {
                return;
            }
            field.setAttribute('data-cl-ready', '');
            var value = field.querySelector('[data-cl-copy-value]');
            var copy = field.querySelector('[data-cl-copy-button]');
            var reveal = field.querySelector('[data-cl-reveal-button]');
            var fallback = field.querySelector('[data-cl-copy-fallback]');
            var status = field.querySelector('[data-cl-copy-status]');
            var full = field.getAttribute('data-value');
            var masked = field.getAttribute('data-masked');

            if (fallback) {
                fallback.hidden = true;
            }
            value.hidden = false;
            value.textContent = masked || full;

            if (navigator.clipboard && copy) {
                copy.hidden = false;
                var copyIcon = copy.querySelector('.cl-swap');
                var timer = null;
                copy.addEventListener('click', function () {
                    navigator.clipboard.writeText(full).then(function () {
                        // The icon turns into a check mark for sighted users; the status region announces it.
                        copyIcon.classList.add('is-swapped');
                        status.textContent = copy.getAttribute('data-copied-text');
                        clearTimeout(timer);
                        timer = setTimeout(function () {
                            copyIcon.classList.remove('is-swapped');
                            status.textContent = '';
                        }, STATUS_MS);
                    });
                });
            }

            if (masked && reveal) {
                reveal.hidden = false;
                var revealLabel = reveal.querySelector('.cl-swap');
                var show = function (shown) {
                    shownValues[full] = shown;
                    revealLabel.classList.toggle('is-swapped', shown);
                    value.textContent = shown ? full : masked;
                };
                if (shownValues[full]) {
                    show(true);
                }
                reveal.addEventListener('click', function () {
                    show(!revealLabel.classList.contains('is-swapped'));
                });
            }
        });
    }

    // Controls that only work with the clipboard API ([data-cl-copy-reveal][hidden], e.g. a row's quick copy icon and its
    // "Kopiuj link" menu entry) are hidden in the markup, so without JavaScript they never show as dead buttons.
    function revealClipboardControls(root) {
        if (!navigator.clipboard) {
            return;
        }
        root.querySelectorAll('[data-cl-copy-reveal][hidden]').forEach(function (control) {
            control.hidden = false;
        });
    }

    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });
    // a list swapped in by list-page.js comes back with the controls hidden again
    document.addEventListener('cl-list:swapped', function (event) {
        revealClipboardControls(event.target);
    });
    init(document);
    revealClipboardControls(document);

    // A button carrying a value to copy (button[data-cl-copy]: a code in a table cell, a client link in a row or a
    // row menu): one click copies it, the toast says so.
    document.addEventListener('click', function (event) {
        var button = event.target.closest && event.target.closest('button[data-cl-copy]');
        if (!button || !navigator.clipboard) {
            return;
        }
        navigator.clipboard.writeText(button.getAttribute('data-cl-copy')).then(function () {
            if (typeof showToast === 'function') {
                showToast(typeof CL_TOAST_COPIED === 'string' ? CL_TOAST_COPIED : '', 'success');
            }
        });
    });
})();
