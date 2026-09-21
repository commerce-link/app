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
        keepDrawnIcons(form, next);
        keepOpenedSections(form, next);
        form.replaceWith(next);
        kept.forEach(function (image) {
            image.slot.replaceWith(image.loaded);
        });
        return next;
    }

    // A <details> the user opened or closed stays that way after a save; the server only knows its default.
    function keepOpenedSections(form, next) {
        next.querySelectorAll('details[id]').forEach(function (section) {
            var current = form.querySelector('details[id="' + section.id + '"]');
            if (current) {
                section.open = current.open;
            }
        });
    }

    // Font Awesome draws an <i class="fas fa-…"> as an <svg> only once it is on the page, so a swapped-in button would
    // lose its icon and change width for a frame. An icon already drawn in the old form is reused instead.
    function keepDrawnIcons(form, next) {
        next.querySelectorAll('i[class*="fa-"]').forEach(function (icon) {
            var prefix = ['fas', 'far', 'fab'].find(function (candidate) {
                return icon.classList.contains(candidate);
            });
            var name = Array.prototype.find.call(icon.classList, function (candidate) {
                return candidate.indexOf('fa-') === 0;
            });
            var drawn = prefix && name
                && form.querySelector('svg[data-prefix="' + prefix + '"][data-icon="' + name.slice(3) + '"]');
            if (!drawn) {
                return;
            }
            var copy = drawn.cloneNode(true);
            var own = Array.prototype.filter.call(drawn.classList, function (candidate) {
                return candidate === 'svg-inline--fa' || /^fa-w-\d+$/.test(candidate);
            });
            var extra = Array.prototype.filter.call(icon.classList, function (candidate) {
                return candidate !== prefix && candidate !== name;
            });
            copy.setAttribute('class', own.concat([name], extra).join(' '));
            icon.replaceWith(copy);
        });
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
        // Disabling the button drops focus; an element with an id (e.g. a switch that saves) gets it back in the new form.
        var focusedId = form.contains(document.activeElement) ? document.activeElement.id : '';
        form.setAttribute('aria-busy', 'true');
        if (button) {
            button.disabled = true;
        }

        // A submitter with formaction (e.g. an action confirmed in a dialog) posts the same form to its own address.
        var action = event.submitter && event.submitter.hasAttribute('formaction') ? event.submitter.formAction : form.action;
        fetch(action, {
            method: 'POST',
            body: body(form),
            headers: { 'X-Requested-With': 'fetch' },
            // An expired session answers with a redirect to the login page on another origin; following it would
            // fail as a CORS error indistinguishable from a network outage.
            redirect: 'manual'
        })
            .then(function (response) {
                return response.text().then(function (html) {
                    return { status: response.status, redirected: response.type === 'opaqueredirect', html: html };
                });
            })
            .then(function (result) {
                if (result.redirected) {
                    // An expired session is redirected to the login page; a regular submit takes the user there.
                    unlock(form, button);
                    HTMLFormElement.prototype.submit.call(form);
                    return;
                }
                var next = result.status === 200 || result.status === 422 ? swap(form, result.html) : null;
                if (!next) {
                    // A server error may come after the record was already saved (a failed render), so the form is
                    // not sent again: a second submit of a new record would create a duplicate. The page's own message
                    // blames the connection, which is wrong for an answer the server did give (403, 500).
                    unlock(form, button);
                    showToast(document.body.getAttribute('data-cl-server-error') || form.getAttribute('data-error-message'), 'danger');
                    return;
                }
                if (result.status === 422) {
                    keepChosenFiles(form, next);
                }
                // A form saved on its own page (a new record) returns to the page listing it; the server has already
                // stored the success message for that page.
                var redirect = result.status === 200 && next.getAttribute('data-cl-redirect');
                if (redirect) {
                    window.location.assign(redirect);
                    return;
                }
                next.dispatchEvent(new CustomEvent('cl:form-replaced', { bubbles: true }));
                var summary = next.querySelector('[data-cl-error-summary]');
                if (summary) {
                    summary.focus();
                    return;
                }
                var message = next.getAttribute('data-success-message');
                if (message) {
                    // An outcome left on the page by an earlier reload (e.g. "switched on") would contradict this one.
                    document.querySelectorAll('[data-cl-saved-alert]').forEach(function (alert) {
                        alert.remove();
                    });
                    showToast(message, 'success');
                }
                // Focus goes back to the element that had it (a switch, an action confirmed in a dialog), else to the save button.
                var refocus = (focusedId && next.querySelector('[id="' + focusedId + '"]')) || next.querySelector('[type="submit"]');
                if (refocus) {
                    refocus.focus({ preventScroll: true });
                }
            })
            .catch(function () {
                unlock(form, button);
                showToast(form.getAttribute('data-error-message'), 'danger');
            });
    });
})();
