// The offer details page around its items table (offers/details.html, spec docs/active/offer-details-redesign-v2 §4).
// - Actions on the saved offer ([data-cl-offer-action]: create the order, proforma, recalculation, copies, removing an
//   item) run here. An action with its own confirmation (data-cl-confirm-*, fragments/confirm-dialog) asks only that;
//   with unsaved changes in the table the confirmation says so and its "Zapisz i …" saves the items first. The others
//   ask first only when the table has unsaved changes (dialog#offer-unsaved-dialog). The items form is posted in the
//   background, then the action runs exactly as on a clean page. A failed save stays on the page, still unsaved. A table
//   that cannot be saved (a group name, a quantity the browser refuses) opens no dialog: its field is shown instead.
// - The add-items dialog saves the table the same way before it posts its items (cl:item-add-submit), and its button
//   says so while the table is dirty.
// - The terms, client and note dialogs (form[data-cl-async]) close once saved. On a clean page it reloads; with unsaved
//   items the parts the dialog changed are swapped in from the answer's template[data-cl-offer-refresh] (header, card,
//   the summary's delivery line) and the summary is recounted with the new delivery price, so the table is kept.
// - "Kopiuj link" and "Otwórz jak klient" say that the customer sees the saved version while the table is dirty.
// - The peek ([data-cl-peek]) follows the summary's own "Zapisz pozycje" at any width: shown while that button is out of
//   view (a long table under a sticky side column, or the summary below the table), hidden while it is fully in view,
//   but never while the focus is inside the peek.
(function () {
    'use strict';

    var form = document.getElementById('offer-items');
    if (!form) {
        return;
    }
    var unsaved = document.getElementById('offer-unsaved-dialog');
    var confirmDialog = document.getElementById('cl-confirm-dialog');
    var actionForm = document.getElementById('offer-action-form');
    var addDialog = document.getElementById('item-add-dialog');
    var CARD_FORMS = ['offer-terms-form', 'offer-client-form', 'offer-note-form'];
    // the table saved by this script: the rows on screen may include items the save dropped
    var savedHere = false;
    var busy = false;
    var pending = null;

    function isDirty() {
        return typeof window.CL_isDirty === 'function' && window.CL_isDirty(form);
    }

    function focusBack(element) {
        if (!element || !document.contains(element)) {
            return;
        }
        // an action from a menu sits in a closed details by now: its summary takes the focus instead
        var menu = element.closest('details.cl-menu');
        (menu && !menu.open ? menu.querySelector(':scope > summary') : element).focus();
    }

    // The field to fix before the table can be saved (a group name, a quantity the browser refuses), or null. Asked
    // before any dialog opens: a dialog closed afterwards hands the focus back to its opener and takes the browser's
    // message with it.
    function fieldToFix() {
        var invalid = window.CL_offerLines ? window.CL_offerLines.prepare() : null;
        if (invalid) {
            return invalid;
        }
        return form.checkValidity() ? null : form.querySelector(':invalid');
    }

    function showFieldToFix(field) {
        if (field.matches(':invalid')) {
            form.reportValidity();
        } else {
            field.focus();
        }
    }

    // A modal dialog keeps the field out of reach, so it is shown once the last dialog has closed: its close event
    // comes later, after confirm-dialog's and dialog.js's focus restore, which would otherwise win.
    function showFieldAfterDialogs(field) {
        var open = document.querySelectorAll('dialog[open]');
        if (!open.length) {
            showFieldToFix(field);
            return;
        }
        open[open.length - 1].addEventListener('close', function () {
            showFieldToFix(field);
        }, { once: true });
        open.forEach(function (dialog) {
            dialog.close();
        });
    }

    // Resolves true once the items are saved.
    function saveItems() {
        var field = fieldToFix();
        if (field) {
            showFieldAfterDialogs(field);
            return Promise.resolve(false);
        }
        busy = true;
        form.setAttribute('aria-busy', 'true');
        return fetch(form.action, {
            method: 'POST',
            body: new URLSearchParams(new FormData(form)),
            // A saved table redirects back to this offer page; an expired session redirects to the login page instead
            // (another path, or a cross-origin answer the fetch rejects), so only a landing on this page counts. Following
            // the redirect also takes the "items saved" message, which would otherwise show up on a later page.
            redirect: 'follow'
        }).then(function (response) {
            var landed = response.url ? new URL(response.url).pathname : '';
            if (response.status === 403 && /^text\/plain/.test(response.headers.get('Content-Type') || '')) {
                // the dashboard of an inactive store is read-only (DashboardReadOnlyInterceptor): its answer says so,
                // as the page does after a plain save
                return response.text().then(function (reason) {
                    var refused = new Error('items not saved: 403');
                    refused.reason = reason;
                    throw refused;
                });
            }
            if (!response.ok || landed !== new URL(form.action, window.location.href).pathname) {
                throw new Error('items not saved: ' + response.status + ' ' + landed);
            }
            savedHere = true;
            window.CL_markClean(form);
            return true;
        }).catch(function (error) {
            showToast((error && error.reason) || unsaved.getAttribute('data-failed'), 'danger');
            return false;
        }).finally(function () {
            busy = false;
            form.removeAttribute('aria-busy');
        });
    }

    // The address of the action. Removing an item names it by its position in the saved offer; once this script has
    // saved the table that position is counted from the screen, and null means the save already dropped the item.
    function actionUrl(element) {
        var url = element.getAttribute('data-action-url');
        var row = element.closest('tr[data-cl-line]');
        if (!row || !savedHere || !window.CL_offerLines) {
            return url;
        }
        var index = window.CL_offerLines.savedIndex(row);
        return index < 0 ? null : url.replace(/\/remove-item\/\d+/, '/remove-item/' + index);
    }

    // Removing an item after a save: the save drops the incomplete items first, so the offer could end up with none,
    // which the menu refuses on a saved offer ("Oferta musi mieć co najmniej jedną pozycję").
    function leavesNoItem(element) {
        var row = element.closest('tr[data-cl-line]');
        return !!row && !!window.CL_offerLines && /\/remove-item\//.test(element.getAttribute('data-action-url') || '')
            && window.CL_offerLines.keptWithout(row) === 0;
    }

    function hasOwnConfirm(element) {
        return element.hasAttribute('data-cl-confirm-title') && confirmDialog && typeof confirmDialog.showModal === 'function';
    }

    // The action's own confirmation. With unsaved items it is still the only question: it says the items are saved
    // first, its button reads "Zapisz i …", and its submit saves the table before the confirmation form is sent; a
    // cancel saves nothing. The dialog gets its usual message and button back when it closes.
    function confirmThen(element, url, saveFirst) {
        var confirmForm = confirmDialog.querySelector('form');
        var message = confirmDialog.querySelector('#cl-confirm-message');
        var submit = confirmDialog.querySelector('[data-cl-confirm-submit]');
        var primary = element.getAttribute('data-cl-confirm-tone') === 'primary';
        var note = null;
        confirmForm.action = url;
        confirmDialog.querySelector('#cl-confirm-title').textContent = element.getAttribute('data-cl-confirm-title');
        message.textContent = element.getAttribute('data-cl-confirm-message');
        submit.textContent = saveFirst
            ? unsaved.getAttribute('data-save-label').replace('{0}', element.getAttribute('data-label') || '')
            : element.getAttribute('data-cl-confirm-action');
        submit.classList.toggle('is-danger', !primary);
        submit.classList.toggle('is-primary', primary);
        submit.disabled = false;

        function saveThenSend(event) {
            event.preventDefault();
            if (busy) {
                return;
            }
            submit.disabled = true;
            saveItems().then(function (saved) {
                if (!saved) {
                    if (confirmDialog.open) {
                        confirmDialog.close();
                    }
                    return;
                }
                // an item's position is known only now that the table is saved (moved and dropped rows)
                var savedUrl = actionUrl(element);
                if (savedUrl === null) {
                    window.location.reload();
                    return;
                }
                confirmForm.action = savedUrl;
                HTMLFormElement.prototype.submit.call(confirmForm);
            });
        }

        if (saveFirst) {
            note = document.createElement('p');
            note.className = 'cl-dialog-message';
            note.textContent = unsaved.getAttribute('data-confirm-note');
            message.after(note);
            confirmForm.addEventListener('submit', saveThenSend);
        }
        confirmDialog.addEventListener('close', function () {
            if (note) {
                note.remove();
                confirmForm.removeEventListener('submit', saveThenSend);
                submit.textContent = element.getAttribute('data-cl-confirm-action');
                submit.disabled = false;
            }
            focusBack(element);
        }, { once: true });
        confirmDialog.showModal();
        confirmDialog.querySelector('[data-cl-confirm-cancel]').focus();
    }

    function run(element) {
        var url = actionUrl(element);
        if (url === null) {
            window.location.reload();
        } else if (hasOwnConfirm(element)) {
            confirmThen(element, url, false);
        } else if (element.getAttribute('data-action-method') === 'get') {
            window.location.assign(url);
        } else {
            actionForm.action = url;
            actionForm.submit();
        }
    }

    function ask(element) {
        pending = element;
        unsaved.querySelector('[data-cl-unsaved-save]').textContent =
            unsaved.getAttribute('data-save-label').replace('{0}', element.getAttribute('data-label') || '');
        unsaved.showModal();
        unsaved.querySelector('[data-cl-dialog-close]').focus();
    }

    // Before confirm-dialog.js and the browser: an action always goes through run() (or the question first).
    document.addEventListener('click', function (event) {
        var element = event.target.closest && event.target.closest('[data-cl-offer-action]');
        if (!element || element.getAttribute('aria-disabled') === 'true' || typeof unsaved.showModal !== 'function') {
            return;
        }
        event.preventDefault();
        event.stopImmediatePropagation();
        if (busy) {
            return;
        }
        var field = isDirty() ? fieldToFix() : null;
        if (field) {
            showFieldToFix(field);
        } else if (isDirty() && leavesNoItem(element)) {
            showToast(window.CL_offerLines.lastItemMessage(), 'danger');
        } else if (isDirty() && hasOwnConfirm(element)) {
            confirmThen(element, actionUrl(element), true);
        } else if (isDirty()) {
            ask(element);
        } else {
            run(element);
        }
    }, true);

    unsaved.querySelector('form').addEventListener('submit', function (event) {
        event.preventDefault();
        var element = pending;
        pending = null;
        unsaved.close();
        if (!element || busy) {
            return;
        }
        focusBack(element);
        saveItems().then(function (saved) {
            if (saved) {
                run(element);
            }
        });
    });

    unsaved.addEventListener('close', function () {
        if (pending) {
            focusBack(pending);
            pending = null;
        }
    });

    // as the other dialogs: a press that both starts and ends on the backdrop closes it
    var pressStart = null;
    unsaved.addEventListener('pointerdown', function (event) {
        pressStart = event.target;
    });
    unsaved.addEventListener('click', function (event) {
        if (event.target === unsaved && pressStart === unsaved) {
            unsaved.close();
        }
        pressStart = null;
    });

    // --- the add-items dialog

    // its "Zapisz i dodaj" saves the table first, which a field to fix would refuse behind the modal dialog
    document.addEventListener('click', function (event) {
        var opener = event.target.closest && event.target.closest('[data-cl-dialog-open="item-add-dialog"]');
        var field = opener && isDirty() ? fieldToFix() : null;
        if (field) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showFieldToFix(field);
        }
    }, true);

    document.addEventListener('cl:item-add-submit', function (event) {
        if (!isDirty()) {
            return;
        }
        event.preventDefault();
        var detail = event.detail;
        saveItems().then(function (saved) {
            if (saved) {
                detail.proceed();
            } else {
                // the message of the failed save shows on the page, which the modal dialog would cover; the picked
                // items stay in the dialog for the next try
                detail.abort();
                if (addDialog && addDialog.open) {
                    addDialog.close();
                }
            }
        });
    });

    // the list the page was opened from, kept by the add-items post as by the other actions
    (function keepReturnTo() {
        var back = form.querySelector('input[type=hidden][name="returnTo"]');
        if (addDialog && back && back.value) {
            var url = new URL(addDialog.getAttribute('data-add-items-url'), window.location.href);
            url.searchParams.set('returnTo', back.value);
            addDialog.setAttribute('data-add-items-url', url.pathname + url.search);
        }
    })();

    var addLabels = addDialog && {
        label: addDialog.getAttribute('data-submit-label'),
        count: addDialog.getAttribute('data-submit-count')
    };

    function showDirtyState(dirty) {
        document.querySelectorAll('[data-cl-saved-version-note]').forEach(function (note) {
            note.hidden = !dirty;
        });
        if (addDialog) {
            addDialog.setAttribute('data-submit-label', dirty ? unsaved.getAttribute('data-add-label') : addLabels.label);
            addDialog.setAttribute('data-submit-count', dirty ? unsaved.getAttribute('data-add-count') : addLabels.count);
        }
    }

    form.addEventListener('cl:dirty-changed', function (event) {
        showDirtyState(event.detail.dirty);
    });

    // --- the terms, client and note dialogs

    function swapRefreshed(template) {
        Array.prototype.forEach.call(template.content.children, function (fresh) {
            var current = fresh.id && document.getElementById(fresh.id);
            if (!current) {
                return;
            }
            var copy = document.importNode(fresh, true);
            if (navigator.clipboard) {
                copy.querySelectorAll('[data-cl-copy-reveal][hidden]').forEach(function (control) {
                    control.hidden = false;
                });
            }
            current.replaceWith(copy);
        });
    }

    // "Zapisz jako szablon" names the template after the offer, so like the old form it asks for the name first; the
    // name is edited in the terms dialog, whose "Edytuj" takes the focus
    document.addEventListener('click', function (event) {
        var button = event.target.closest && event.target.closest('[data-cl-save-as-template]');
        var name = button && form.querySelector('input[type=hidden][name="name"]');
        if (!name || name.value.trim() !== '') {
            return;
        }
        event.preventDefault();
        event.stopImmediatePropagation();
        showToast(button.getAttribute('data-name-required'), 'danger');
        var edit = document.querySelector('#offer-terms-card [data-cl-dialog-open="offer-terms-dialog"]');
        if (edit) {
            edit.focus();
        }
    }, true);

    // "Zapisz jako szablon" posts the items form, whose copies of the name and the fulfilment type would otherwise keep
    // the values from before the terms dialog until the page reloads
    function keepTermsForTemplate(saved) {
        ['name', 'fulfilmentType'].forEach(function (name) {
            var source = saved.elements.namedItem(name);
            var copy = form.querySelector('input[type=hidden][name="' + name + '"]');
            if (source && copy) {
                copy.value = source.value;
            }
        });
    }

    document.addEventListener('cl:form-replaced', function (event) {
        var saved = event.target;
        if (!(saved instanceof HTMLFormElement) || CARD_FORMS.indexOf(saved.id) < 0
            || saved.getAttribute('data-cl-saved') !== 'true' || saved.querySelector('[data-cl-error-summary]')) {
            return;
        }
        var dialog = saved.closest('dialog');
        if (!isDirty()) {
            if (dialog) {
                dialog.close();
            }
            window.location.reload();
            return;
        }
        var template = saved.querySelector('template[data-cl-offer-refresh]');
        if (saved.id === 'offer-terms-form') {
            keepTermsForTemplate(saved);
        }
        if (template) {
            swapRefreshed(template);
            showDirtyState(true);
            if (window.CL_offerLines) {
                window.CL_offerLines.recount();
            }
        }
        if (dialog) {
            // the card's "Edytuj" that opened the dialog was swapped too: its new copy takes the focus
            dialog.addEventListener('close', function () {
                var opener = document.querySelector('[data-cl-dialog-open="' + dialog.id + '"]');
                if (opener) {
                    opener.focus();
                }
            }, { once: true });
            dialog.close();
        }
    });
    // The save stays in sight at any width (spec D1): without this, CSS shows the peek below 1366 px only.
    (function followSummarySave() {
        var peek = form.querySelector('[data-cl-peek]');
        var summarySave = document.querySelector('#summary [data-cl-save-items]');
        if (!peek || !summarySave || !('IntersectionObserver' in window)) {
            return;
        }
        // the sticky top bar covers the top of the viewport
        var topbar = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--cl-topbar-height'), 10) || 0;
        // Showing or hiding the peek changes the form's height by its box and the gap, which can move the summary (the
        // sticky side column whose run ends with the form, or the page clamped at its bottom) by as much. So it shows once the
        // button leaves the screen, but hides only once the button is clear of the edges by more than that, or the two
        // would trade places on every frame near the end of the table. The band is at least BAND and grows to the shown
        // peek's own box: on a phone the peek is taller than 100 px, and a browser without scroll anchoring (Safari)
        // moved the button by more than the band on each swap.
        var BAND = 100;
        var band = BAND;
        var hideObserver = null;
        // the summary's button is clear of the band: the peek may go, but never from under the focus, which would
        // drop to the page body; it goes once the focus leaves it
        var clear = true;
        function set(state) {
            peek.setAttribute('data-cl-peek-state', state);
        }
        function hideUnless(focused) {
            if (clear && !peek.contains(focused)) {
                set('hidden');
            }
        }
        function observeHide() {
            if (hideObserver) {
                hideObserver.disconnect();
            }
            hideObserver = new IntersectionObserver(function (entries) {
                clear = entries[entries.length - 1].intersectionRatio >= 0.99;
                hideUnless(document.activeElement);
            }, { rootMargin: '-' + (topbar + band) + 'px 0px -' + band + 'px 0px', threshold: [0, 0.99] });
            hideObserver.observe(summarySave);
        }
        // moved: how far showing the peek just moved the button (none where the browser anchors the scroll)
        function fitBand(moved) {
            var gap = parseFloat(getComputedStyle(peek.parentElement).rowGap) || 0;
            var needed = Math.ceil(Math.max(peek.getBoundingClientRect().height + gap, moved)) + 8;
            if (needed > band) {
                band = needed;
                observeHide();
            }
        }
        set('hidden');
        new IntersectionObserver(function (entries) {
            if (entries[entries.length - 1].intersectionRatio < 0.99) {
                var before = summarySave.getBoundingClientRect().top;
                clear = false;
                set('shown');
                fitBand(Math.abs(summarySave.getBoundingClientRect().top - before));
            }
        }, { rootMargin: '-' + topbar + 'px 0px 0px 0px', threshold: [0, 0.99] }).observe(summarySave);
        observeHide();
        peek.addEventListener('focusout', function (event) {
            hideUnless(event.relatedTarget);
        });
    })();
})();
