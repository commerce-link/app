// An image upload field ([data-cl-image-field]): previews the chosen file before it is saved, unticks "remove" once a
// new file is chosen, and stops a file over data-max-bytes from being sent at all. The server validates the file anyway.
(function () {
    'use strict';

    function clearClientError(field, input) {
        var error = field.querySelector('[data-cl-client-error]');
        if (error) {
            error.remove();
            input.removeAttribute('aria-invalid');
            input.classList.remove('is-invalid');
            input.setAttribute('aria-describedby', input.getAttribute('aria-describedby').replace(' ' + error.id, ''));
        }
    }

    function showClientError(field, input, message) {
        var error = document.createElement('p');
        error.className = 'cl-field-error';
        error.id = input.id + '-client-error';
        error.setAttribute('data-cl-client-error', '');
        var icon = document.createElement('i');
        icon.className = 'fas fa-exclamation-circle';
        icon.setAttribute('aria-hidden', 'true');
        var text = document.createElement('span');
        text.textContent = message;
        error.append(icon, text);
        field.querySelector('.cl-help').after(error);
        input.classList.add('is-invalid');
        input.setAttribute('aria-invalid', 'true');
        input.setAttribute('aria-describedby', input.getAttribute('aria-describedby') + ' ' + error.id);
    }

    function preview(field, file) {
        var image = document.createElement('img');
        image.src = URL.createObjectURL(file);
        image.alt = field.getAttribute('data-selected-alt');
        image.onload = function () {
            URL.revokeObjectURL(image.src);
        };
        field.querySelector('[data-cl-image-preview]').replaceChildren(image);
    }

    function handle(field, input) {
        clearClientError(field, input);
        var file = input.files[0];
        if (!file) {
            return;
        }
        if (file.size > Number(field.getAttribute('data-max-bytes'))) {
            input.value = '';
            showClientError(field, input, field.getAttribute('data-too-large-message'));
            input.focus();
            return;
        }
        preview(field, file);
        var remove = field.querySelector('[data-cl-image-remove]');
        if (remove) {
            remove.checked = false;
        }
    }

    document.addEventListener('change', function (event) {
        var field = event.target.closest && event.target.closest('[data-cl-image-field]');
        if (field && event.target.type === 'file') {
            handle(field, event.target);
        }
    });

    // A file carried over by async-form.js after a failed save gets its preview back.
    document.addEventListener('cl:form-replaced', function (event) {
        event.target.querySelectorAll('[data-cl-image-field] input[type="file"]').forEach(function (input) {
            if (input.files.length) {
                handle(input.closest('[data-cl-image-field]'), input);
            }
        });
    });
})();
