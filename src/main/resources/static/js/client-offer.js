// Variant choice on the customer offer page (form.cl-variant-pick). With JavaScript the "Change variant" button is
// hidden and a choice made with the mouse or a finger is sent at once. A choice made with the arrow keys is not sent
// (WCAG 3.2.2: moving through radios must not change the page); it reveals that group's button instead. Without
// JavaScript the button stays visible and sends the choice.
(function () {
    'use strict';

    document.querySelectorAll('form.cl-variant-pick').forEach(function (form) {
        var apply = form.querySelector('.cl-variant-apply');
        var pointer = false;
        if (apply) {
            apply.hidden = true;
        }
        // pointerdown comes before the change it causes; a keyboard change has no pointerdown before it
        form.addEventListener('pointerdown', function () {
            pointer = true;
        });
        form.addEventListener('change', function (event) {
            if (!event.target.matches('input[type="radio"]')) {
                return;
            }
            if (pointer) {
                pointer = false;
                if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit();
                } else {
                    form.submit();
                }
                return;
            }
            if (apply) {
                apply.hidden = false;
            }
        });
        form.addEventListener('keydown', function () {
            pointer = false;
        });
    });
})();
