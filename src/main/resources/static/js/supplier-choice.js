// A select with a "custom" option (data-cl-custom-option holds its value) reveals the text field next to it
// ([data-cl-custom-field]) only while that option is chosen; the field is required only then. Without JavaScript the
// field is always visible.
(function () {
    'use strict';

    function sync(select) {
        var custom = select.value === select.getAttribute('data-cl-custom-option');
        var container = select.closest('.cl-form-grid, form').querySelector('[data-cl-custom-field]');
        if (!container) {
            return;
        }
        var input = container.querySelector('input');
        container.hidden = !custom && !container.querySelector('[aria-invalid="true"]');
        if (input) {
            input.required = custom;
            if (!custom) {
                input.value = '';
            }
        }
    }

    function init(root) {
        root.querySelectorAll('select[data-cl-custom-option]').forEach(sync);
    }

    document.addEventListener('change', function (event) {
        if (event.target.matches && event.target.matches('select[data-cl-custom-option]')) {
            sync(event.target);
            if (event.target.value === event.target.getAttribute('data-cl-custom-option')) {
                var container = event.target.closest('.cl-form-grid, form').querySelector('[data-cl-custom-field] input');
                if (container) {
                    container.focus();
                }
            }
        }
    });
    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });
    init(document);
})();
