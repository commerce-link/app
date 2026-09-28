// A timeline list with data-cl-timeline-limit="n" shows only its first n events; the rest stay in the same list, hidden,
// so the line runs on unbroken when they come back. The [data-cl-timeline-toggle] button (aria-controls = the list id)
// sits under the list: it is hidden in the markup and shown here, flips between its data-more-text and data-less-text,
// keeps the focus, and a [data-cl-timeline-status] live region in the same card reads out what was shown or hidden.
// Without JavaScript every event stays visible and the button stays hidden.
(function () {
    'use strict';

    function extras(list) {
        var limit = parseInt(list.getAttribute('data-cl-timeline-limit'), 10);
        return Array.prototype.slice.call(list.children, limit);
    }

    function set(button, list, expanded) {
        extras(list).forEach(function (item) {
            item.hidden = !expanded;
        });
        button.setAttribute('aria-expanded', String(expanded));
        button.textContent = button.getAttribute(expanded ? 'data-less-text' : 'data-more-text');
    }

    function init(button) {
        var list = document.getElementById(button.getAttribute('aria-controls'));
        if (!list || !list.hasAttribute('data-cl-timeline-limit') || button.hasAttribute('data-cl-timeline-ready')) {
            return;
        }
        button.setAttribute('data-cl-timeline-ready', '');
        var card = button.closest('.cl-card') || document;
        var status = card.querySelector('[data-cl-timeline-status]');
        set(button, list, false);
        button.parentElement.hidden = false;
        button.addEventListener('click', function () {
            var expanded = button.getAttribute('aria-expanded') !== 'true';
            set(button, list, expanded);
            // WebKit does not focus a clicked button; focusing it also scrolls it back into view after collapsing
            button.focus();
            if (status) {
                status.textContent = button.getAttribute(expanded ? 'data-shown-text' : 'data-hidden-text');
            }
        });
    }

    document.querySelectorAll('[data-cl-timeline-toggle]').forEach(init);
})();
