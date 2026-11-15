// A part of a page the server computes only when it is needed. [data-cl-lazy-src="url"] is fetched once, the first time
// the element is shown: it is rendered hidden, or hidden by variant-fields.js until its choice is made. The answer replaces
// the element's content; a failed fetch (an error status, a redirect to the login page, no network) shows the element's
// template[data-cl-lazy-failed] instead. The element is a live region (aria-live on it), busy while the fetch runs.
// Without JavaScript the element stays as rendered, so the page must work without what it would load.
(function () {
    'use strict';

    function show(element, nodes) {
        while (element.firstChild) {
            element.removeChild(element.firstChild);
        }
        nodes.forEach(function (node) {
            element.appendChild(document.importNode(node, true));
        });
        element.removeAttribute('aria-busy');
    }

    function failed(element, template) {
        element.setAttribute('data-cl-lazy-state', 'failed');
        show(element, template ? Array.prototype.slice.call(template.content.childNodes) : []);
    }

    function load(element) {
        if (element.hidden || element.hasAttribute('data-cl-lazy-state')) {
            return;
        }
        var template = element.querySelector('template[data-cl-lazy-failed]');
        element.setAttribute('data-cl-lazy-state', 'loading');
        element.setAttribute('aria-busy', 'true');
        fetch(element.getAttribute('data-cl-lazy-src'), {
            headers: {'X-Requested-With': 'fetch'},
            credentials: 'same-origin'
        }).then(function (response) {
            if (!response.ok || response.redirected) {
                throw new Error('lazy fragment not available');
            }
            return response.text();
        }).then(function (html) {
            // Parsed as a document, never assigned as innerHTML: scripts in the answer do not run.
            var parsed = new DOMParser().parseFromString(html, 'text/html');
            element.setAttribute('data-cl-lazy-state', 'loaded');
            show(element, Array.prototype.slice.call(parsed.body.childNodes));
            element.dispatchEvent(new CustomEvent('cl:lazy-loaded', {bubbles: true}));
        }).catch(function () {
            failed(element, template);
        });
    }

    function watch(element) {
        if (element.hasAttribute('data-cl-lazy-watched')) {
            return;
        }
        element.setAttribute('data-cl-lazy-watched', '');
        new MutationObserver(function () {
            load(element);
        }).observe(element, {attributes: true, attributeFilter: ['hidden']});
        load(element);
    }

    function watchAll() {
        document.querySelectorAll('[data-cl-lazy-src]').forEach(watch);
    }

    // async-form.js swaps the whole form after a refused save, and a new element may come with it.
    document.addEventListener('cl:form-replaced', watchAll);
    watchAll();
})();
