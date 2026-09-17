// A hex colour field ([data-cl-color-field]): shows the colour picker, keeps it and the text field in step, and updates
// the sample button while typing. The text field is what gets submitted and validated; the page renders the same
// sample without JavaScript.
(function () {
    'use strict';

    function normalize(value) {
        var color = value.trim().toLowerCase();
        if (color && color.charAt(0) !== '#') {
            color = '#' + color;
        }
        if (/^#[0-9a-f]{3}$/.test(color)) {
            color = '#' + color[1] + color[1] + color[2] + color[2] + color[3] + color[3];
        }
        return /^#[0-9a-f]{6}$/.test(color) ? color : null;
    }

    function render(field, color) {
        var sample = field.querySelector('[data-cl-brand-sample]');
        sample.hidden = !color;
        if (color) {
            sample.style.setProperty('--cl-brand', color);
        }
    }

    function init(root) {
        root.querySelectorAll('[data-cl-color-field]').forEach(function (field) {
            field.querySelector('[data-cl-color-picker]').hidden = false;
        });
    }

    document.addEventListener('input', function (event) {
        var field = event.target.closest && event.target.closest('[data-cl-color-field]');
        if (!field) {
            return;
        }
        var text = field.querySelector('.cl-color-input');
        var picker = field.querySelector('[data-cl-color-picker]');
        if (event.target === picker) {
            text.value = picker.value;
        }
        var color = normalize(text.value);
        if (color && event.target === text) {
            picker.value = color;
        }
        render(field, color);
    });

    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });
    init(document);
})();
