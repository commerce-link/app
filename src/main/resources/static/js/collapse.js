// Cards marked data-cl-collapse collapse to their header below 720 px: the h2 text is wrapped in a disclosure button
// (aria-expanded, aria-controls) that shows and hides .cl-collapse-body. At 720 px and more the button is removed, so wide
// screens have a plain heading and an always-open card. Without JavaScript every card stays open.
(function () {
    'use strict';

    var phone = window.matchMedia('(max-width: 719px)');

    function enhance(card) {
        var title = card.querySelector('.cl-card-title');
        var body = card.querySelector('.cl-collapse-body');
        if (!title || !body || title.querySelector('.cl-card-toggle')) {
            return;
        }
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'cl-card-toggle';
        // a form swapped back in with a 422 validation error (data-cl-error-summary) stays open, so async-form.js's
        // focus() on the summary lands on a visible element instead of one hidden by this very re-collapse
        var open = !!body.querySelector('[data-cl-error-summary]');
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
        button.setAttribute('aria-controls', body.id);
        while (title.firstChild) {
            button.appendChild(title.firstChild);
        }
        title.appendChild(button);
        body.hidden = !open;
        card.classList.add('is-collapsible');
        button.addEventListener('click', function () {
            var expanded = button.getAttribute('aria-expanded') === 'true';
            button.setAttribute('aria-expanded', expanded ? 'false' : 'true');
            body.hidden = expanded;
        });
    }

    function restore(card) {
        var title = card.querySelector('.cl-card-title');
        var button = title && title.querySelector('.cl-card-toggle');
        var body = card.querySelector('.cl-collapse-body');
        if (button) {
            while (button.firstChild) {
                title.insertBefore(button.firstChild, button);
            }
            button.remove();
        }
        if (body) {
            body.hidden = false;
        }
        card.classList.remove('is-collapsible');
    }

    function apply() {
        document.querySelectorAll('[data-cl-collapse]').forEach(phone.matches ? enhance : restore);
    }

    apply();
    phone.addEventListener('change', apply);
    // a card saved without reloading (the order settings form) comes back expanded; collapse its new markup again
    document.addEventListener('cl:form-replaced', apply);
})();
