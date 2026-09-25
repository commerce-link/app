// Progressive enhancement of the orders list: a click on a tab, attention figure, chip, menu item, sort header or
// page link, and a submit of the search form, fetch /dashboard/orders/list with the same query and swap the results
// block in place. The address bar follows (pushState), Back re-fetches, and focus lands on the count line so a
// screen reader hears the new number. Emptying the search field refreshes the list too, and the filter menu (a
// native <details>) closes on a click outside or Escape. Anything unexpected falls back to a plain navigation.
(function () {
    'use strict';

    var root = document.querySelector('[data-cl-orders-results]');
    if (!root || !window.fetch || !window.history || !window.DOMParser) {
        return;
    }

    function fragmentUrl(href) {
        var url = new URL(href, window.location.href);
        url.pathname = '/dashboard/orders/list';
        return url;
    }

    function load(href, push, focusSearch) {
        var target = new URL(href, window.location.href);
        if (target.pathname !== '/dashboard/orders') {
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
                var fresh = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-cl-orders-results]');
                if (!fresh) { throw new Error('no results block'); }
                root.replaceWith(fresh);
                root = fresh;
                if (push) { history.pushState({ ordersList: true }, '', target.pathname + target.search); }
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
        load('/dashboard/orders' + (params.toString() ? '?' + params.toString() : ''), true, keepFocus);
    }

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        var link = event.target.closest && event.target.closest('a[data-cl-orders-nav]');
        if (link && root.contains(link)) {
            event.preventDefault();
            load(link.getAttribute('href'), true);
            return;
        }
        // The filter menu is a <details>: close it when the click lands anywhere else.
        var openMenu = root.querySelector('details[data-cl-menu][open]');
        if (openMenu && !openMenu.contains(event.target)) {
            openMenu.removeAttribute('open');
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') { return; }
        var openMenu = root.querySelector('details[data-cl-menu][open]');
        if (openMenu) {
            openMenu.removeAttribute('open');
            var summary = openMenu.querySelector('summary');
            if (summary) { summary.focus(); }
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest && event.target.closest('form[data-cl-orders-form]');
        if (!form || !root.contains(form) || form.method.toLowerCase() !== 'get') { return; }
        event.preventDefault();
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

    history.replaceState({ ordersList: true }, '', window.location.pathname + window.location.search);
})();
