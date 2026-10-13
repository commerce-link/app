// The store delete dialog (stores.html): one dialog for every store. dialog.js fires cl:dialog-open with the menu entry
// that opened it, so the title, the trial line, the ID and the form action come from that entry's data attributes.
// "Usuń sklep" stays disabled until the typed ID matches; the server compares the ID again, so this only saves a
// round trip.
(function () {
    'use strict';

    var dialog = document.getElementById('store-delete-dialog');
    if (!dialog) {
        return;
    }
    var form = dialog.querySelector('form');
    var input = dialog.querySelector('#confirm-store-id');
    var submit = dialog.querySelector('[data-store-delete-submit]');
    var idBox = dialog.querySelector('[data-store-delete-id]');
    var title = dialog.querySelector('[data-store-delete-title]');
    var owner = dialog.querySelector('[data-store-delete-owner]');

    function sync() {
        submit.disabled = input.value.trim() !== idBox.textContent.trim();
    }

    function fill(pattern, value) {
        return pattern.split('{0}').join(value);
    }

    dialog.addEventListener('cl:dialog-open', function (event) {
        var trigger = event.detail && event.detail.trigger;
        if (!trigger) {
            return;
        }
        var trial = trigger.getAttribute('data-store-trial') === 'true';
        title.textContent = fill(title.getAttribute('data-pattern'), trigger.getAttribute('data-store-name'));
        dialog.querySelectorAll('[data-store-delete-kind]').forEach(function (line) {
            line.hidden = (line.getAttribute('data-store-delete-kind') === 'trial') !== trial;
        });
        // only the account the server deletes with a trial store (its trial owner e-mail), and only when it is known
        var ownerEmail = trial ? trigger.getAttribute('data-owner-email') : null;
        owner.hidden = !ownerEmail;
        owner.textContent = ownerEmail ? fill(owner.getAttribute('data-pattern'), ownerEmail) : '';
        idBox.textContent = trigger.getAttribute('data-store-id');
        form.setAttribute('action', trigger.getAttribute('data-action'));
        input.value = '';
        sync();
    });

    input.addEventListener('input', sync);
    sync();
})();
