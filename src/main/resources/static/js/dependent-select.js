// A select whose options depend on another select (select[data-cl-options-for=<id of the parent>]): only the options of
// the chosen parent (option[data-parent]) are kept, plus the ones without data-parent ("all"); Safari ignores hidden
// options, so the list is rebuilt from a copy instead of hiding entries. The value resets when it is no longer offered.
(function () {
    'use strict';
    document.querySelectorAll('select[data-cl-options-for]').forEach(function (child) {
        var parent = document.getElementById(child.getAttribute('data-cl-options-for'));
        if (!parent) {
            return;
        }
        var all = Array.prototype.slice.call(child.options).map(function (option) {
            return option.cloneNode(true);
        });
        function sync() {
            var value = child.value;
            child.replaceChildren();
            all.forEach(function (option) {
                var owner = option.getAttribute('data-parent');
                if (!owner || owner === parent.value) {
                    child.appendChild(option.cloneNode(true));
                }
            });
            child.value = Array.prototype.some.call(child.options, function (o) { return o.value === value; }) ? value : '';
        }
        parent.addEventListener('change', sync);
        sync();
    });
})();
