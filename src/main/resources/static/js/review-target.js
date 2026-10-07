/*
 * "Kategoria katalogu" on "Uzupełnij dane" opened from the inventory: choosing another category sends the form at once
 * through its "Zmień kategorię" button (its formaction reviews the rows again for that category, keeping what was
 * typed), so the button itself is hidden. Without JavaScript the button stays and does the same.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var select = document.querySelector('[data-review-target]');
        var button = document.querySelector('[data-review-target-change]');
        if (!select || !button || !select.form || typeof select.form.requestSubmit !== 'function') {
            return;
        }
        button.hidden = true;
        // The footer holds only this button once a category is chosen; an empty footer would still draw its border.
        var actions = button.closest('[data-review-target-actions]');
        if (actions && !actions.querySelector(':scope > :not([hidden])')) {
            actions.hidden = true;
        }
        select.addEventListener('change', function () {
            select.form.requestSubmit(button);
        });
    });
})();
