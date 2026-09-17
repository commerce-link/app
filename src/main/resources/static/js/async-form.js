// Saves a form marked data-cl-async without reloading the page. The server answers the same POST with the form
// re-rendered: 422 with field errors, or 200 with data-success-message once saved. The form is swapped in place,
// so validation markup and saved values come from one template. Without JavaScript the form submits normally.
(function () {
    'use strict';

    // DOMParser gives an inert document: unlike innerHTML, its images are not fetched until the form is on the page.
    function swap(form, html) {
        var parsed = new DOMParser().parseFromString(html, 'text/html');
        var next = parsed.querySelector('form[data-cl-async]');
        if (!next || next.id !== form.id) {
            return null;
        }
        var kept = keepLoadedImages(form, next, parsed);
        form.replaceWith(next);
        kept.forEach(function (image) {
            image.slot.replaceWith(image.loaded);
        });
        return next;
    }

    // An image sent back under the same address is already on screen. Its loaded element takes the place of the new
    // one, so it is neither fetched again nor blank for a frame; a replaced image comes back with a new address.
    function keepLoadedImages(form, next, parsed) {
        var kept = [];
        next.querySelectorAll('img[src]').forEach(function (image) {
            var loaded = Array.prototype.find.call(form.querySelectorAll('img[src]'), function (candidate) {
                return candidate.getAttribute('src') === image.getAttribute('src') && candidate.complete;
            });
            if (loaded) {
                loaded.alt = image.alt;
                var slot = parsed.createElement('span');
                image.replaceWith(slot);
                kept.push({ slot: slot, loaded: loaded });
            }
        });
        return kept;
    }

    // A browser cannot re-render a chosen file, so after a failed save the file moves into the returned form unless
    // the server rejected that very file.
    function keepChosenFiles(form, next) {
        form.querySelectorAll('input[type="file"]').forEach(function (input) {
            var target = input.name && next.querySelector('input[type="file"][name="' + input.name + '"]');
            if (input.files.length && target && target.getAttribute('aria-invalid') !== 'true') {
                target.files = input.files;
            }
        });
    }

    function body(form) {
        var data = new FormData(form);
        return form.enctype === 'multipart/form-data' ? data : new URLSearchParams(data);
    }

    function unlock(form, button) {
        form.removeAttribute('aria-busy');
        if (button) {
            button.disabled = false;
        }
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-cl-async')) {
            return;
        }
        event.preventDefault();
        if (form.getAttribute('aria-busy') === 'true') {
            return;
        }
        var button = form.querySelector('[type="submit"]');
        form.setAttribute('aria-busy', 'true');
        if (button) {
            button.disabled = true;
        }

        fetch(form.action, {
            method: 'POST',
            body: body(form),
            headers: { 'X-Requested-With': 'fetch' },
            // An expired session answers with a redirect to the login page on another origin; following it would
            // fail as a CORS error indistinguishable from a network outage.
            redirect: 'manual'
        })
            .then(function (response) {
                return response.text().then(function (html) {
                    return { status: response.status, html: html };
                });
            })
            .then(function (result) {
                var next = result.status === 200 || result.status === 422 ? swap(form, result.html) : null;
                if (!next) {
                    // Anything but the form coming back (an expired session redirected to the login page, a server
                    // error page) is left to a regular submit, which shows the user what the server has to say.
                    unlock(form, button);
                    HTMLFormElement.prototype.submit.call(form);
                    return;
                }
                if (result.status === 422) {
                    keepChosenFiles(form, next);
                }
                next.dispatchEvent(new CustomEvent('cl:form-replaced', { bubbles: true }));
                var summary = next.querySelector('[data-cl-error-summary]');
                if (summary) {
                    summary.focus();
                    return;
                }
                var message = next.getAttribute('data-success-message');
                if (message) {
                    showToast(message, 'success');
                }
                var nextButton = next.querySelector('[type="submit"]');
                if (nextButton) {
                    nextButton.focus({ preventScroll: true });
                }
            })
            .catch(function () {
                unlock(form, button);
                showToast(form.getAttribute('data-error-message'), 'danger');
            });
    });
})();
