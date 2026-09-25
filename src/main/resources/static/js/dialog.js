// Opens dialog.cl-dialog from any [data-cl-dialog-open="<dialog id>"] trigger (a link whose href is the same content
// as a page, for browsers without JavaScript). Native showModal keeps focus inside and closes on Escape; a click on
// the backdrop or on [data-cl-dialog-close] closes too, and focus returns to the trigger. Before opening, the dialog
// receives cl:dialog-open with the trigger in detail, so a page script can fill it from the trigger's data-*.
(function () {
    'use strict';

    var opener = null;

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest && event.target.closest('[data-cl-dialog-open]');
        if (trigger) {
            var dialog = document.getElementById(trigger.getAttribute('data-cl-dialog-open'));
            if (!dialog || typeof dialog.showModal !== 'function' || trigger.getAttribute('aria-disabled') === 'true') {
                return;
            }
            event.preventDefault();
            opener = trigger;
            dialog.dispatchEvent(new CustomEvent('cl:dialog-open', { detail: { trigger: trigger } }));
            dialog.showModal();
            var first = dialog.querySelector('[autofocus], input:not([type=hidden]), select, button');
            if (first) {
                first.focus();
            }
            return;
        }
        var close = event.target.closest && event.target.closest('[data-cl-dialog-close]');
        if (close) {
            var owner = close.closest('dialog');
            if (owner && owner.open) {
                event.preventDefault();
                owner.close();
            }
            return;
        }
        if (event.target instanceof HTMLDialogElement && event.target.classList.contains('cl-dialog') && event.target.open) {
            event.target.close();
        }
    });

    document.addEventListener('close', function (event) {
        if (event.target instanceof HTMLDialogElement && opener && document.contains(opener)) {
            opener.focus();
            opener = null;
        }
    }, true);
})();
