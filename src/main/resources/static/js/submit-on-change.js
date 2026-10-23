// A select, radio or file input with data-cl-submit-on-change sends its form as soon as another option (or a file) is
// chosen ("Zamów odbiór": the page reloads with the packages and windows of the chosen carrier; "Wyślij przez": with
// the fields of the chosen integration; the statement format: the sample file is read at once). A value names the id
// of the submit button to send the form with, for a form whose buttons tell the server what to do. Without
// JavaScript the form keeps its own submit button inside <noscript>.
(function () {
    'use strict';

    var FIELDS = 'select[data-cl-submit-on-change], input[type=radio][data-cl-submit-on-change], '
        + 'input[type=file][data-cl-submit-on-change]';

    document.addEventListener('change', function (event) {
        var field = event.target;
        if (!field.matches || !field.matches(FIELDS) || !field.form) {
            return;
        }
        if (field.type === 'file' && !field.value) {
            return;
        }
        var submitterId = field.getAttribute('data-cl-submit-on-change');
        var submitter = submitterId ? document.getElementById(submitterId) : null;
        if (field.form.requestSubmit) {
            field.form.requestSubmit(submitter || undefined);
            return;
        }
        // form.submit() sends no button: the button's own name and value go as a field instead
        if (submitter && submitter.name) {
            var value = document.createElement('input');
            value.type = 'hidden';
            value.name = submitter.name;
            value.value = submitter.value;
            field.form.appendChild(value);
        }
        field.form.submit();
    });
})();
