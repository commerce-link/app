// Progressive enhancement of the orders list: a click on a segment, tile, chip, sort header or page link, and a
// submit of the filter or search form, fetch /dashboard/orders/list with the same query and swap the results block
// in place. The address bar follows (pushState), Back re-fetches, and focus lands on the results line so a screen
// reader hears the new count. Anything unexpected falls back to a plain navigation to the same address.
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

    function load(href, push) {
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
                var results = root.querySelector('.cl-table-results');
                if (results) { results.setAttribute('tabindex', '-1'); results.focus({ preventScroll: true }); }
                if (window.scrollY > root.getBoundingClientRect().top + window.scrollY) { root.scrollIntoView({ block: 'start' }); }
            })
            .catch(function () { window.location.assign(href); });
    }

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        var link = event.target.closest && event.target.closest('a[data-cl-orders-nav]');
        if (!link || !root.contains(link)) { return; }
        event.preventDefault();
        load(link.getAttribute('href'), true);
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest && event.target.closest('form[data-cl-orders-form]');
        if (!form || !root.contains(form) || form.method.toLowerCase() !== 'get') { return; }
        event.preventDefault();
        var params = new URLSearchParams(new FormData(form));
        load('/dashboard/orders?' + params.toString(), true);
    });

    document.addEventListener('change', function (event) {
        var select = event.target;
        if (select.matches && select.matches('[data-filter-auto-submit]') && root.contains(select) && select.form) {
            select.form.requestSubmit ? select.form.requestSubmit() : select.form.submit();
        }
    });

    window.addEventListener('popstate', function () {
        load(window.location.pathname + window.location.search, false);
    });

    history.replaceState({ ordersList: true }, '', window.location.pathname + window.location.search);
})();
