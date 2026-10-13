// A select or radio with data-cl-submit-on-change sends its GET form as soon as another option is chosen ("Zamów
// odbiór": the page reloads with the packages and windows of the chosen carrier; "Wyślij przez": with the fields of
// the chosen integration). Without JavaScript the form keeps its own submit button inside <noscript>.
(function () {
    'use strict';

    document.addEventListener('change', function (event) {
        var field = event.target;
        if (!field.matches || !field.matches('select[data-cl-submit-on-change], input[type=radio][data-cl-submit-on-change]')
                || !field.form) {
            return;
        }
        field.form.requestSubmit ? field.form.requestSubmit() : field.form.submit();
    });
})();
