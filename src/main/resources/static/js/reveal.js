// A checkbox with data-cl-reveal="id" shows the container with that id only while it is ticked. The container is only
// hidden, never disabled, so its values are still saved and come back when the feature is switched on again. Without
// JavaScript the container stays visible.
(function () {
    'use strict';

    function sync(checkbox) {
        var target = document.getElementById(checkbox.getAttribute('data-cl-reveal'));
        if (!target) {
            return;
        }
        // A container holding a field error stays open, otherwise the error summary would link to a hidden field.
        target.hidden = !checkbox.checked && !target.querySelector('[aria-invalid="true"]');
        checkbox.setAttribute('aria-expanded', String(!target.hidden));
    }

    function init(root) {
        root.querySelectorAll('input[type="checkbox"][data-cl-reveal]').forEach(sync);
    }

    document.addEventListener('change', function (event) {
        if (event.target.matches && event.target.matches('input[type="checkbox"][data-cl-reveal]')) {
            sync(event.target);
        }
    });
    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });
    init(document);
})();
