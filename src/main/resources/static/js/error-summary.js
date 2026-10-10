// A page answered with errors (a plain POST that came back 422) puts the keyboard and the screen reader on their
// summary ([data-cl-error-summary], tabindex="-1"), whose links lead to the fields. Forms sent by async-form.js focus
// the summary themselves.
(function () {
    'use strict';

    function focusSummary() {
        var summary = document.querySelector('[data-cl-error-summary]');
        if (summary) {
            summary.focus();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', focusSummary);
    } else {
        focusSummary();
    }
})();
