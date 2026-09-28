// Order printouts (card, collection protocol): the Print button stays hidden until this script runs, since without it
// the button could not print; the browser's own Print menu works either way.
(function () {
    'use strict';

    document.querySelectorAll('[data-cl-print]').forEach(function (button) {
        button.hidden = false;
        button.addEventListener('click', function () {
            window.print();
        });
    });
})();
