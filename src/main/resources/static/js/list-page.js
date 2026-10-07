// Progressive enhancement of a server-rendered list (orders, deliveries). The page marks its results block with
// data-cl-list-results, data-cl-list-path (the list page itself) and data-cl-list-fragment (the endpoint returning only
// that block). A click on a chip, menu item, sort header or page link (a[data-cl-list-nav]), a submit of a
// form[data-cl-list-form], and a tick in an auto-submitting menu fetch the fragment with the same query and swap the
// results block in place. The address bar follows (pushState), Back re-fetches, and focus lands on the count line so
// a screen reader hears the new number. A tick keeps its menu open on the fresh block (with focus on the same
// box) so several values can be picked in a row; its "Zastosuj" button is hidden because every tick applies at
// once. Emptying the search field refreshes the list too, and an open menu (a native <details>) closes on a click
// outside or Escape. After each swap the fresh block dispatches `cl-list:swapped` (bubbles), so other scripts can set
// it up again. Anything unexpected falls back to a plain navigation.
(function () {
    'use strict';

    var root = document.querySelector('[data-cl-list-results]');
    var listPath = root && root.getAttribute('data-cl-list-path');
    var fragmentPath = root && root.getAttribute('data-cl-list-fragment');
    if (!root || !listPath || !fragmentPath || !window.fetch || !window.history || !window.DOMParser) {
        return;
    }

    function fragmentUrl(href) {
        var url = new URL(href, window.location.href);
        url.pathname = fragmentPath;
        return url;
    }

    // A menu to reopen after the swap, set by a tick in an auto-submitting menu form: { menu, value }.
    var reopen = null;

    function hideAutosubmitButtons() {
        root.querySelectorAll('[data-cl-autosubmit-hide]').forEach(function (button) {
            button.hidden = true;
            // an actions row left with nothing visible would be an empty strip under the last checkbox
            var row = button.parentElement;
            if (row && !Array.prototype.some.call(row.children, function (c) { return !c.hidden; })) { row.hidden = true; }
        });
    }

    // Links outside the results block that lead away and back ("Zamów odbiór" in the orders list header) return to
    // the list as it is narrowed now, not as the page was first loaded.
    function syncBackLinks(target) {
        document.querySelectorAll('a[data-cl-list-back]').forEach(function (link) {
            var url = new URL(link.getAttribute('href'), window.location.href);
            url.searchParams.set('back', target.pathname + target.search);
            link.setAttribute('href', url.pathname + url.search);
        });
    }

    function load(href, push, focusSearch) {
        var target = new URL(href, window.location.href);
        if (target.pathname !== listPath) {
            window.location.assign(href);
            return;
        }
        root.setAttribute('aria-busy', 'true');
        fetch(fragmentUrl(href), { headers: { 'X-Requested-With': 'fetch' }, credentials: 'same-origin', redirect: 'manual' })
            .then(function (response) {
                if (!response.ok) { throw new Error('HTTP ' + response.status); }
                return response.text();
            })
            .then(function (html) {
                var fresh = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-cl-list-results]');
                if (!fresh) { throw new Error('no results block'); }
                root.replaceWith(fresh);
                root = fresh;
                // scripts that decorate the results (row-toggle.js) set up the fresh block again
                root.dispatchEvent(new CustomEvent('cl-list:swapped', { bubbles: true }));
                applyFold();
                hideAutosubmitButtons();
                if (push) { history.pushState({ listPage: true }, '', target.pathname + target.search); }
                syncBackLinks(target);
                if (reopen) {
                    var menu = root.querySelector('details[data-cl-filter-menu="' + reopen.menu + '"]');
                    var box = menu && menu.querySelector('input[value="' + reopen.value + '"]');
                    reopen = null;
                    if (menu) { menu.setAttribute('open', ''); }
                    if (box) { box.focus({ preventScroll: true }); }
                    return;
                }
                if (focusSearch) {
                    var field = root.querySelector('.cl-table-search');
                    if (field) { field.focus(); field.setSelectionRange(field.value.length, field.value.length); }
                    return;
                }
                var results = root.querySelector('.cl-table-results');
                if (results) { results.setAttribute('tabindex', '-1'); results.focus({ preventScroll: true }); }
                if (window.scrollY > root.getBoundingClientRect().top + window.scrollY) { root.scrollIntoView({ block: 'start' }); }
            })
            .catch(function () { window.location.assign(href); });
    }

    function submitSearch(form, keepFocus) {
        var params = new URLSearchParams(new FormData(form));
        if (!params.get('q')) { params.delete('q'); }
        load(listPath + (params.toString() ? '?' + params.toString() : ''), true, keepFocus);
    }

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        var link = event.target.closest && event.target.closest('a[data-cl-list-nav]');
        if (link && root.contains(link)) {
            event.preventDefault();
            load(link.getAttribute('href'), true);
            return;
        }
        // The filter menu is a <details>: close it when the click lands anywhere else.
        var openMenu = root.querySelector('details[data-cl-filter-menu][open]');
        if (openMenu && !openMenu.contains(event.target)) {
            openMenu.removeAttribute('open');
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') { return; }
        var openMenu = root.querySelector('details[data-cl-filter-menu][open]');
        if (openMenu) {
            openMenu.removeAttribute('open');
            var summary = openMenu.querySelector('summary');
            if (summary) { summary.focus(); }
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest && event.target.closest('form[data-cl-list-form]');
        // the attribute, not the property: a field named "method" (the payment-method filter on Payments) shadows it
        if (!form || !root.contains(form) || (form.getAttribute('method') || 'get').toLowerCase() !== 'get') { return; }
        event.preventDefault();
        submitSearch(form, false);
    });

    // A tick in the Status menu applies at once; the menu stays open on the fresh block for the next tick.
    document.addEventListener('change', function (event) {
        var box = event.target;
        var form = box.form;
        if (!form || !form.hasAttribute('data-cl-autosubmit') || !root.contains(form)) { return; }
        var menu = form.closest('details[data-cl-filter-menu]');
        reopen = menu ? { menu: menu.getAttribute('data-cl-filter-menu'), value: box.value } : null;
        submitSearch(form, false);
    });

    // Emptying the search field (Backspace, the field's own clear) shows the unsearched list again without a click.
    document.addEventListener('input', function (event) {
        var field = event.target;
        if (!field.matches || !field.matches('.cl-table-search') || !root.contains(field)) { return; }
        if (field.value === '' && field.getAttribute('data-cl-search-had') === 'true') {
            field.setAttribute('data-cl-search-had', 'false');
            submitSearch(field.form, true);
        }
    });

    window.addEventListener('popstate', function () {
        load(window.location.pathname + window.location.search, false);
    });

    // Phone only (< 720 px): the filter menus fold under "Filtry: n" next to the search. Without the script they stay
    // open, so every filter is reachable; the folded state survives a swap of the results block.
    var phone = window.matchMedia ? window.matchMedia('(max-width: 719px)') : null;
    var folded = true;

    function applyFold() {
        var toggle = root.querySelector('[data-cl-toolbar-toggle]');
        var toolbar = toggle && toggle.closest('.cl-table-toolbar');
        if (!toolbar) { return; }
        var fold = !!(phone && phone.matches) && folded;
        toolbar.classList.toggle('is-collapsed', fold);
        toggle.setAttribute('aria-expanded', fold ? 'false' : 'true');
    }

    document.addEventListener('click', function (event) {
        var toggle = event.target.closest && event.target.closest('[data-cl-toolbar-toggle]');
        if (!toggle || !root.contains(toggle)) { return; }
        folded = !folded;
        applyFold();
    });
    if (phone && phone.addEventListener) { phone.addEventListener('change', applyFold); }

    // A click anywhere on a date field opens the browser's date picker, not only a click on its calendar icon. Only a
    // click: opening it on focus would get in the way of typing the date from the keyboard. Browsers without
    // showPicker (or refusing it, e.g. when the picker is already open) keep the plain field.
    document.addEventListener('click', function (event) {
        var field = event.target.closest && event.target.closest('input[type="date"]');
        if (!field || !root.contains(field) || typeof field.showPicker !== 'function') { return; }
        try { field.showPicker(); } catch (e) { /* the icon and typing still work */ }
    });

    hideAutosubmitButtons();
    applyFold();
    // An outcome message rendered after a redirect (e.g. "Usunięto ofertę …", [data-cl-list-notice][tabindex=-1]) gets the
    // focus once, so a screen reader reads it; a role=status present at load is usually not announced. A swapped block
    // never carries it (no flash on the fragment request).
    var notice = root.querySelector('[data-cl-list-notice]');
    if (notice) { notice.focus(); }
    history.replaceState({ listPage: true }, '', window.location.pathname + window.location.search);
})();
