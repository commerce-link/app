// The filters dialog of the orders list: switches between the list and the form, fills the form for "Edit", posts
// every form inside the dialog with fetch (X-Requested-With: fetch) and swaps the dialog body with the answer. A 200
// answer carrying data-cl-redirect means the list under the dialog changed, so the page follows it; a 422 answer
// shows the rejection inside the dialog. Without JavaScript the same forms post and the page reloads.
(function () {
    'use strict';

    function bodyOf(form) {
        return form.closest('[data-cl-dialog-body], .cl-card-dialog-page');
    }

    function showList(body) {
        var list = body.querySelector('[data-cl-filters-list]');
        var form = body.querySelector('[data-cl-filter-form]');
        if (list) { list.hidden = false; }
        if (form) { form.hidden = true; }
    }

    function showForm(body, creating) {
        var list = body.querySelector('[data-cl-filters-list]');
        var form = body.querySelector('[data-cl-filter-form]');
        form.action = creating ? '/dashboard/orders/filters' : '/dashboard/orders/filters/update';
        list.hidden = true;
        form.hidden = false;
        form.querySelector('[name=label]').focus();
    }

    function selectOption(select, value) {
        var match = Array.prototype.find.call(select.options, function (o) { return o.value.toUpperCase() === value.toUpperCase(); });
        if (match) { select.value = match.value; return; }
        select.add(new Option(value, value, true, true));
    }

    function fill(form, button) {
        form.reset();
        form.querySelector('[data-cl-filter-id]').value = button.getAttribute('data-filter-id');
        form.elements.label.value = button.getAttribute('data-label');
        if (form.elements.sharedWithStore) { form.elements.sharedWithStore.checked = button.getAttribute('data-shared') === 'true'; }
        ['status', 'shipmentType', 'paymentSource', 'sourceName', 'shippingPostalCode', 'shippingDue'].forEach(function (name) {
            var value = button.dataset[name];
            var input = form.elements[name];
            if (!value || !input) { return; }
            if (input.tagName === 'SELECT') { selectOption(input, value); } else { input.value = value; }
        });
    }

    function formatPostalCode(input, appendSeparator) {
        var digits = input.value.replace(/\D/g, '').slice(0, 5);
        input.value = digits.length > 2 ? digits.slice(0, 2) + '-' + digits.slice(2) : (digits.length === 2 && appendSeparator ? digits + '-' : digits);
    }

    document.addEventListener('input', function (event) {
        if (event.target.matches && event.target.matches('[data-cl-postal-code]')) {
            formatPostalCode(event.target, !(event.inputType || '').startsWith('delete'));
        }
    });

    document.addEventListener('click', function (event) {
        var target = event.target;
        var newButton = target.closest && target.closest('[data-cl-filter-new]');
        if (newButton) {
            var body = bodyOf(newButton);
            body.querySelector('[data-cl-filter-form]').reset();
            body.querySelector('[data-cl-filter-id]').value = '';
            showForm(body, true);
            return;
        }
        var back = target.closest && target.closest('[data-cl-filter-back]');
        if (back) { showList(bodyOf(back)); return; }
        var edit = target.closest && target.closest('[data-cl-filter-edit]');
        if (edit) {
            var editBody = bodyOf(edit);
            fill(editBody.querySelector('[data-cl-filter-form]'), edit);
            showForm(editBody, false);
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest && event.target.closest('form[data-cl-filters-form]');
        if (!form || !window.fetch) { return; }
        var body = bodyOf(form);
        if (!body) { return; }
        event.preventDefault();
        var submitter = event.submitter;
        var action = submitter && submitter.getAttribute('formaction') ? submitter.getAttribute('formaction') : form.action;
        fetch(action, {
            method: 'POST',
            headers: { 'X-Requested-With': 'fetch' },
            body: new URLSearchParams(new FormData(form)),
            credentials: 'same-origin',
            redirect: 'manual'
        }).then(function (response) {
            if (response.type === 'opaqueredirect' || (response.status >= 300 && response.status < 400)) {
                form.submit(); // session expired: the plain post shows the login page
                return null;
            }
            if (response.status !== 200 && response.status !== 422) {
                throw new Error('HTTP ' + response.status);
            }
            return response.text().then(function (html) { return { status: response.status, html: html }; });
        }).then(function (result) {
            if (!result) { return; }
            var fresh = new DOMParser().parseFromString(result.html, 'text/html').body.firstElementChild;
            var redirect = fresh && fresh.getAttribute('data-cl-redirect');
            if (result.status === 200 && redirect) {
                window.location.assign(redirect);
                return;
            }
            body.replaceChildren(fresh);
            var error = body.querySelector('[data-cl-error-summary]');
            if (error) {
                var openForm = body.querySelector('[data-cl-filter-form]');
                if (openForm && form.hasAttribute('data-cl-filter-form')) { showForm(body, action.indexOf('/update') < 0); }
                error.focus();
            }
        }).catch(function () {
            if (typeof showToast === 'function') { showToast(document.body.getAttribute('data-cl-server-error'), 'danger'); }
        });
    });
})();
