// A timeline list with data-cl-timeline-limit="n" shows only its first n events; the rest stay in the same list, hidden,
// so the line runs on unbroken when they come back. The [data-cl-timeline-more] row under the list is hidden in the
// markup and shown here. It holds two buttons (aria-controls = the list id): collapsed, the "expand" node sits on the
// timeline axis and the CSS draws the line on, dashed, down to it; expanded, the node goes away and the "collapse" link
// takes its place. The focus follows to whichever button is left, and a [data-cl-timeline-status] live region in the same
// card reads out what was shown or hidden. Without JavaScript every event stays visible and the row stays hidden.
(function () {
    'use strict';

    function extras(list) {
        var limit = parseInt(list.getAttribute('data-cl-timeline-limit'), 10);
        return Array.prototype.slice.call(list.children, limit);
    }

    function init(more) {
        var expand = more.querySelector('[data-cl-timeline-toggle="expand"]');
        var collapse = more.querySelector('[data-cl-timeline-toggle="collapse"]');
        var list = expand && document.getElementById(expand.getAttribute('aria-controls'));
        if (!collapse || !list || !list.hasAttribute('data-cl-timeline-limit') || more.hasAttribute('data-cl-timeline-ready')) {
            return;
        }
        more.setAttribute('data-cl-timeline-ready', '');
        var card = more.closest('.cl-card') || document;
        var status = card.querySelector('[data-cl-timeline-status]');

        function set(expanded) {
            extras(list).forEach(function (item) {
                item.hidden = !expanded;
            });
            expand.hidden = expanded;
            collapse.hidden = !expanded;
        }

        function toggle(expanded) {
            set(expanded);
            // The button just pressed is gone; focusing the other one also scrolls it into view after collapsing
            (expanded ? collapse : expand).focus();
            if (status) {
                status.textContent = more.getAttribute(expanded ? 'data-shown-text' : 'data-hidden-text');
            }
        }

        set(false);
        more.hidden = false;
        expand.addEventListener('click', function () {
            toggle(true);
        });
        collapse.addEventListener('click', function () {
            toggle(false);
        });
    }

    document.querySelectorAll('[data-cl-timeline-more]').forEach(init);
})();
