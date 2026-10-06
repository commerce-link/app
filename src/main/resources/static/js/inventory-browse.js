/*
 * "Dodaj do katalogu" on the inventory page: fetches the dialog for one product (row link, product header) or for the
 * checked rows (selection row), and opens it. Without JavaScript the row link goes to ?open=add, where the server draws
 * the same dialog open; when the fetch fails the script falls back to that link.
 */
(function () {
    'use strict';

    function root() {
        return document.querySelector('[data-browse-root]');
    }

    function checkedEans() {
        return Array.from(document.querySelectorAll('[data-cl-select-row]:checked'))
            .map(function (box) { return box.value; })
            .filter(Boolean);
    }

    function wireOther(dialog) {
        var select = dialog.querySelector('[data-browse-other-select]');
        var radio = dialog.querySelector('[data-browse-other-radio]');
        if (select && radio) {
            select.addEventListener('change', function () {
                if (select.value) {
                    radio.checked = true;
                }
            });
        }
    }

    async function openDialog(eans, opener) {
        var page = root();
        var slot = document.querySelector('[data-browse-dialog-slot]');
        if (!page || !slot || !eans.length || !page.dataset.browseDialogUrl) {
            return false;
        }
        var url = new URL(page.dataset.browseDialogUrl, window.location.origin);
        eans.forEach(function (ean) { url.searchParams.append('ean', ean); });
        url.searchParams.set('returnTo', window.location.pathname + window.location.search);
        try {
            var response = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
            if (!response.ok) {
                return false;
            }
            slot.innerHTML = await response.text();
        } catch (error) {
            return false;
        }
        var dialog = slot.querySelector('dialog');
        if (!dialog || typeof dialog.showModal !== 'function') {
            return false;
        }
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
        var close = event.target.closest('[data-browse-dialog-close]');
        if (close && close.closest('dialog')) {
            event.preventDefault();
            close.closest('dialog').close();
            return;
        }
        var single = event.target.closest('[data-browse-add]');
        var bulk = event.target.closest('[data-browse-add-selected]');
        if (!single && !bulk) {
            return;
        }
        event.preventDefault();
        var opener = single || bulk;
        var eans = single ? [single.dataset.ean] : checkedEans();
        openDialog(eans, opener).then(function (opened) {
            if (!opened && single && single.href) {
                window.location.href = single.href;
            }
        });
    });

    // The no-JS page (?open=add) draws the dialog open but not modal; with the script running make it a real modal.
    document.addEventListener('DOMContentLoaded', function () {
        var drawn = document.querySelector('[data-browse-dialog-slot] dialog[open]');
        if (drawn && typeof drawn.showModal === 'function') {
            wireOther(drawn);
            drawn.close();
            drawn.showModal();
        }
    });
})();
