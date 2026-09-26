// "Issue" on the order page: every invoice type in the menu opens the same confirmation dialog; the entry's
// data-document-type fills the posted documentType and its label fills the dialog title. "Send to the customer"
// starts unticked on every opening, so an earlier choice is never sent along with another document.
(function () {
    'use strict';

    var dialog = document.getElementById('issue-dialog');
    if (!dialog) {
        return;
    }
    var type = dialog.querySelector('input[name="documentType"]');
    var label = dialog.querySelector('[data-cl-issue-type]');
    var send = dialog.querySelector('input[name="send"]');

    dialog.addEventListener('cl:dialog-open', function (event) {
        var trigger = event.detail && event.detail.trigger;
        if (!trigger) {
            return;
        }
        type.value = trigger.getAttribute('data-document-type') || '';
        label.textContent = trigger.getAttribute('data-label') || '';
        send.checked = false;
    });
})();
