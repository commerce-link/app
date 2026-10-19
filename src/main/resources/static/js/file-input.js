// A file field ([data-cl-file-input]) drawn as a form field: the native input stays on top of it with opacity 0, so a
// click or a dropped file reaches it as usual. This script switches the look on (is-enhanced; without it the native
// input shows) and writes the chosen file's name and size, since the hidden input cannot show them itself.
(function () {
    'use strict';

    function formatSize(bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024)) + ' KB';
        }
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function show(field, input) {
        var name = field.querySelector('[data-cl-file-input-name]');
        var meta = field.querySelector('[data-cl-file-input-meta]');
        var file = input.files && input.files[0];
        field.classList.toggle('has-file', !!file);
        name.classList.toggle('is-empty', !file);
        if (file) {
            name.textContent = file.name;
            meta.textContent = formatSize(file.size) + ' · ' + field.getAttribute('data-change-text');
        } else {
            name.textContent = field.getAttribute('data-empty-text');
            meta.textContent = meta.getAttribute('data-cl-initial') || meta.textContent;
        }
    }

    function enhance(field) {
        var input = field.querySelector('input[type=file]');
        var meta = field.querySelector('[data-cl-file-input-meta]');
        if (!input || !meta) {
            return;
        }
        // The field's look comes from commercelink.css; a stylesheet without it (an older copy served during a deploy)
        // would show the native input next to the drawn one, so the drawn parts go and the native input stays alone.
        if (getComputedStyle(field).position !== 'relative') {
            Array.prototype.forEach.call(field.querySelectorAll(':scope > :not(input)'), function (part) {
                part.remove();
            });
            return;
        }
        meta.setAttribute('data-cl-initial', meta.textContent);
        field.classList.add('is-enhanced');
        input.addEventListener('change', function () {
            show(field, input);
        });
        ['dragenter', 'dragover'].forEach(function (type) {
            field.addEventListener(type, function () {
                field.classList.add('is-dragover');
            });
        });
        ['dragleave', 'dragend', 'drop'].forEach(function (type) {
            field.addEventListener(type, function () {
                field.classList.remove('is-dragover');
            });
        });
        // A file kept by the browser on back navigation is shown as chosen.
        show(field, input);
    }

    function init() {
        document.querySelectorAll('[data-cl-file-input]').forEach(enhance);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
