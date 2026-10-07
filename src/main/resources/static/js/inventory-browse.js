/*
 * "Dodaj do katalogu" on the inventory page: fetches the dialog for one product (row menu item) or for the checked rows
 * (selection row) and opens it as a modal. Closing (Cancel, Escape, the backdrop) and the guard against a second "Dalej"
 * come from dialog.js; this script only gives the focus back to the opener. Without JavaScript the item's link goes to
 * ?open=add, where the server draws the same dialog open; when the fetch fails a single product falls back to that link
 * and the checked rows get an error above the selection row.
 */
(function () {
    'use strict';

    function checkedEans() {
        return Array.from(document.querySelectorAll('[data-cl-select-row]:checked'))
            .map(function (box) { return box.value; })
            .filter(Boolean);
    }

    // "Inna kategoria": its list shows only while the option is chosen and must then have a choice, so "Dalej" cannot
    // send an empty one. Without the script the list stays visible and the server answers an empty choice.
    function wireOther(dialog) {
        var radio = dialog.querySelector('[data-browse-other-radio]');
        var select = dialog.querySelector('[data-browse-other-select]');
        var field = dialog.querySelector('[data-browse-other-field]');
        if (!radio || !select || !field) {
            return;
        }
        function sync() {
            field.hidden = !radio.checked;
            select.required = radio.checked;
        }
        dialog.querySelectorAll('input[name="target"]').forEach(function (option) {
            option.addEventListener('change', sync);
        });
        sync();
    }

    // The page the dialog returns to after saving, without the parameters that would open the dialog again.
    function returnTo() {
        var params = new URLSearchParams(window.location.search);
        params.delete('open');
        params.delete('ean');
        var query = params.toString();
        return window.location.pathname + (query ? '?' + query : '');
    }

    function showAddError(show) {
        var alert = document.querySelector('[data-browse-add-error]');
        var text = alert && alert.querySelector('[data-message]');
        if (!alert || !text) {
            return;
        }
        alert.hidden = !show;
        text.textContent = show ? text.getAttribute('data-message') : '';
    }

    async function openDialog(eans, opener) {
        var page = document.querySelector('[data-browse-dialog-url]');
        var slot = document.querySelector('[data-browse-dialog-slot]');
        if (!page || !slot || !eans.length || !page.dataset.browseDialogUrl) {
            return false;
        }
        var url = new URL(page.dataset.browseDialogUrl, window.location.origin);
        eans.forEach(function (ean) { url.searchParams.append('ean', ean); });
        url.searchParams.set('returnTo', returnTo());
        var dialog;
        try {
            var response = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
            if (!response.ok) {
                return false;
            }
            var template = document.createElement('template');
            template.innerHTML = await response.text();
            dialog = template.content.querySelector('dialog');
        } catch (error) {
            return false;
        }
        if (!dialog) {
            // An OK answer without the dialog is another page, e.g. the login form after the session expired.
            window.location.reload();
            return true;
        }
        if (typeof dialog.showModal !== 'function') {
            return false;
        }
        slot.replaceChildren(dialog);
        wireOther(dialog);
        dialog.addEventListener('close', function () {
            if (opener && document.contains(opener)) {
                opener.focus();
            }
        });
        if (dialog.open) {
            dialog.close();
        }
        dialog.showModal();
        var first = dialog.querySelector('input[type=radio]:checked, select, .cl-button.is-primary');
        if (first) {
            first.focus();
        }
        return true;
    }

    document.addEventListener('click', function (event) {
        // A modified or non-primary click keeps the browser's own behaviour, e.g. the row link in a new tab.
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        var single = event.target.closest('[data-browse-add]');
        var bulk = event.target.closest('[data-browse-add-selected]');
        if (!single && !bulk) {
            return;
        }
        event.preventDefault();
        var opener = single || bulk;
        // An item of a row menu is hidden once the menu closes: the focus goes back to the menu's trigger instead.
        var menu = opener.closest('details.cl-menu');
        if (menu) {
            menu.open = false;
            opener = menu.querySelector(':scope > summary');
        }
        var eans = single ? [single.dataset.ean] : checkedEans();
        showAddError(false);
        openDialog(eans, opener).then(function (opened) {
            if (opened) {
                return;
            }
            if (single && single.href) {
                window.location.href = single.href;
            } else if (bulk && eans.length) {
                showAddError(true);
            }
        });
    });

    // While the index is being built the results block says so; fetch it again until the list or the tiles are there.
    // The content is swapped inside the same block, which list-page.js keeps a reference to.
    var BUILD_POLL_MS = 3000;
    var BUILD_POLL_LIMIT = 40;
    var polling = false;

    function startPolling() {
        if (!polling) {
            pollWhileBuilding(0);
        }
    }

    function pollWhileBuilding(attempt) {
        var results = document.querySelector('[data-cl-list-results]');
        polling = !!results && !!results.querySelector('[data-browse-building]') && attempt < BUILD_POLL_LIMIT
            && !!window.fetch && !!window.DOMParser;
        if (!polling) {
            return;
        }
        window.setTimeout(function () {
            var url = new URL(window.location.href);
            url.pathname = results.getAttribute('data-cl-list-fragment');
            fetch(url, { headers: { 'X-Requested-With': 'fetch' }, credentials: 'same-origin' })
                .then(function (response) {
                    if (!response.ok) { throw new Error('HTTP ' + response.status); }
                    return response.text();
                })
                .then(function (html) {
                    var fresh = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-cl-list-results]');
                    var current = document.querySelector('[data-cl-list-results]');
                    if (fresh && current && !fresh.querySelector('[data-browse-building]')) {
                        polling = false;
                        current.innerHTML = fresh.innerHTML;
                        current.dispatchEvent(new CustomEvent('cl-list:swapped', { bubbles: true }));
                        return;
                    }
                    pollWhileBuilding(attempt + 1);
                })
                .catch(function () { pollWhileBuilding(attempt + 1); });
        }, BUILD_POLL_MS);
    }

    document.addEventListener('DOMContentLoaded', startPolling);
    document.addEventListener('cl-list:swapped', startPolling);

    // The no-JS page (?open=add) draws the dialog open; dialog.js turns it into a modal, this wires its other category.
    document.addEventListener('DOMContentLoaded', function () {
        var drawn = document.querySelector('[data-browse-dialog-slot] dialog');
        if (drawn) {
            wireOther(drawn);
        }
    });
})();
