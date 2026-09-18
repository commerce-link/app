// A link marked data-cl-confirm leads to a confirmation page. With JavaScript it opens the page's confirmation dialog
// (fragments/confirm-dialog.html) instead, which posts to the same address. The native dialog keeps focus inside,
// closes on Escape and returns focus to the link; Cancel is focused first as the safe choice. A link with
// data-cl-confirm-async inside a form[data-cl-async] is confirmed the same way but saved without reloading the page.
(function () {
    'use strict';

    var dialog = document.getElementById('cl-confirm-dialog');
    if (!dialog || typeof dialog.showModal !== 'function') {
        return;
    }
    var form = dialog.querySelector('form');
    var title = dialog.querySelector('#cl-confirm-title');
    var message = dialog.querySelector('#cl-confirm-message');
    var submit = dialog.querySelector('[data-cl-confirm-submit]');
    var cancel = dialog.querySelector('[data-cl-confirm-cancel]');
    var opener = null;

    document.addEventListener('click', function (event) {
        var link = event.target.closest && event.target.closest('[data-cl-confirm]');
        if (!link) {
            return;
        }
        event.preventDefault();
        opener = link;
        form.action = link.getAttribute('href');
        title.textContent = link.getAttribute('data-cl-confirm-title');
        message.textContent = link.getAttribute('data-cl-confirm-message');
        submit.textContent = link.getAttribute('data-cl-confirm-action');
        submit.disabled = false;
        dialog.showModal();
        cancel.focus();
    });

    cancel.addEventListener('click', function () {
        dialog.close();
    });

    // A click on the backdrop lands on the dialog element itself, outside its content box.
    dialog.addEventListener('click', function (event) {
        if (event.target === dialog) {
            dialog.close();
        }
    });

    form.addEventListener('submit', function (event) {
        // A link inside a form saved without reloading (data-cl-confirm-async) posts through that form instead, so the
        // form is swapped in place with the result rather than the whole page reloading.
        var asyncForm = opener && opener.hasAttribute('data-cl-confirm-async') && opener.closest('form[data-cl-async]');
        if (asyncForm && typeof asyncForm.requestSubmit === 'function') {
            event.preventDefault();
            var link = opener;
            // Submitted once the dialog has closed and handed focus back to the link, so the form can restore focus to
            // the link in its new markup.
            dialog.addEventListener('close', function () {
                var submitter = document.createElement('button');
                submitter.type = 'submit';
                submitter.hidden = true;
                submitter.setAttribute('formaction', link.getAttribute('href'));
                asyncForm.appendChild(submitter);
                asyncForm.requestSubmit(submitter);
                submitter.remove();
            }, { once: true });
            dialog.close();
            return;
        }
        submit.disabled = true;
    });

    dialog.addEventListener('close', function () {
        if (opener && document.contains(opener)) {
            opener.focus();
        }
    });
})();
