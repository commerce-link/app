// A select marked data-cl-variant-select="group" shows only the fieldset of its group whose data-cl-variant matches the
// selected value. Other fieldsets are hidden and disabled, so their fields are not sent. Without JavaScript every
// fieldset is visible and the server reads only the fields of the selected type.
(function () {
    'use strict';

    function sync(select) {
        var group = select.getAttribute('data-cl-variant-select');
        document.querySelectorAll('fieldset[data-cl-variant-group="' + group + '"]').forEach(function (fieldset) {
            var active = fieldset.getAttribute('data-cl-variant') === select.value;
            fieldset.hidden = !active;
            fieldset.disabled = !active;
        });
    }

    function init(root) {
        root.querySelectorAll('select[data-cl-variant-select]').forEach(sync);
    }

    document.addEventListener('change', function (event) {
        if (event.target.matches && event.target.matches('select[data-cl-variant-select]')) {
            sync(event.target);
        }
    });
    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });
    init(document);
})();
