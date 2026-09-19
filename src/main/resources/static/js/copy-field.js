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

    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });
    init(document);
})();
