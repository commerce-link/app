// Dialogs with a form (dialog.cl-dialog.is-form). An element with data-cl-dialog-open="id" opens dialog#id with
// showModal (focus stays inside, Escape closes); before that the dialog receives cl:dialog-open with detail.trigger, so
// a page script can fill it from the opener's data attributes. [data-cl-dialog-close] and a click on the backdrop close
// it (a press that started or ended inside the dialog is not such a click); the focus goes back to the opener, or to its menu's summary when the opener sat in an action menu (the menu is
// closed by then). Without dialog support the opener's href, when it has one, is followed.
(function () {
    'use strict';

    var FOCUSABLE = 'input:not([type="hidden"]):not([disabled]), select:not([disabled]), '
        + 'textarea:not([disabled]), button:not([disabled])';
    var returnTo = new WeakMap();

    function focusTarget(trigger) {
        var menu = trigger.closest('details.cl-menu');
        return menu ? menu.querySelector(':scope > summary') : trigger;
    }

    // A click is sent to the nearest element holding both ends of the press, so pressing inside the dialog (e.g. to
    // select text) and letting go over the backdrop — or the other way round — clicks the dialog element itself, just
    // like a click on the backdrop does. The backdrop closes the dialog only when the press both started and ended on it.
    var pressStart = null;
    var pressEnd = null;
    document.addEventListener('pointerdown', function (event) {
        pressStart = event.target;
    }, true);
    document.addEventListener('pointerup', function (event) {
        pressEnd = event.target;
    }, true);

    document.addEventListener('click', function (event) {
        if (!event.target.closest) {
            return;
        }
        var trigger = event.target.closest('[data-cl-dialog-open]');
        if (trigger) {
            var dialog = document.getElementById(trigger.getAttribute('data-cl-dialog-open'));
            if (!dialog || typeof dialog.showModal !== 'function') {
                return;
            }
            event.preventDefault();
            returnTo.set(dialog, focusTarget(trigger));
            dialog.dispatchEvent(new CustomEvent('cl:dialog-open', { detail: { trigger: trigger } }));
            dialog.showModal();
            var first = dialog.querySelector('[autofocus]') || dialog.querySelector(FOCUSABLE);
            if (first) {
                first.focus();
            }
            return;
        }
        var closer = event.target.closest('[data-cl-dialog-close]');
        if (closer && closer.closest('dialog')) {
            event.preventDefault();
            closer.closest('dialog').close();
            return;
        }
        // a click on the backdrop lands on the dialog element itself; the confirmation dialog handles its own
        var onBackdrop = event.target instanceof HTMLDialogElement && event.target.classList.contains('is-form')
            && pressStart === event.target && pressEnd === event.target;
        pressStart = null;
        pressEnd = null;
        if (onBackdrop) {
            event.target.close();
        }
    });

    // close does not bubble
    document.addEventListener('close', function (event) {
        var target = event.target instanceof HTMLDialogElement ? returnTo.get(event.target) : null;
        if (target && document.contains(target)) {
            target.focus();
        }
    }, true);
})();

document.addEventListener('cl:form-replaced', (e) => {
  const form = e.target;
  if (!(form instanceof HTMLFormElement) || form.dataset.clDialogCloseOnSuccess !== 'true') return;
  if (form.querySelector('[data-cl-error-summary]')) return;
  const dialog = form.closest('dialog');
  if (dialog && dialog.open) {
    dialog.close();
    // the read-only card shows the saved values only after a reload; the settings dialog's toast already said
    // "Zapisano", an address dialog's notice waits for the reloaded page (flash)
    const card = document.getElementById('settings-body');
    if (card) card.setAttribute('aria-busy', 'true');
    window.location.reload();
  }
});
