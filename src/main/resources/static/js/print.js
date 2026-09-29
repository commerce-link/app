// Order printouts (order card, collection protocol): a link marked data-cl-print-frame prints its page without leaving
// the current one. The page is loaded into a hidden same-origin frame and the frame's own print() opens the browser's
// print dialog, so the paper shows exactly that sheet (its layout carries only the sheet) and the order page stays
// where it was. Without this script the link simply opens the sheet and the browser's Print menu prints it; a click
// with a modifier key (new tab or window) keeps that behaviour on purpose.
(function () {
    'use strict';

    // Some browsers never fire afterprint in a frame; the frame is dropped this long after print() returned instead.
    // print() blocks while the dialog is open where it matters (Chromium, Safari), so this never cuts a dialog short.
    var FALLBACK_MS = 60000;
    var current = null;

    function remove(frame) {
        if (frame.parentNode) {
            frame.parentNode.removeChild(frame);
        }
        if (current === frame) {
            current = null;
        }
    }

    function hasSheet(view) {
        try {
            return !!view.document.querySelector('.cl-print-sheet');
        } catch (error) {
            return false;
        }
    }

    function closeMenuOf(link) {
        var menu = link.closest('details');
        if (menu) {
            menu.open = false;
        }
    }

    function printInFrame(href) {
        // a second request (double click, the other printout) replaces the one still loading: one dialog at a time
        if (current) {
            remove(current);
        }
        var frame = document.createElement('iframe');
        frame.className = 'cl-print-frame';
        frame.setAttribute('aria-hidden', 'true');
        frame.tabIndex = -1;
        current = frame;
        frame.addEventListener('load', function () {
            if (current !== frame) {
                return;
            }
            var view = frame.contentWindow;
            // an expired session or a missing order answers with another page (login, error), and a refused frame
            // leaves a browser error page that cannot be read: show the page instead of printing it, the same way
            // the link would without this script
            if (!hasSheet(view)) {
                remove(frame);
                window.location.assign(href);
                return;
            }
            view.addEventListener('afterprint', function () {
                // leave the frame until the print call that fired this event has returned
                window.setTimeout(function () {
                    remove(frame);
                }, 0);
            });
            view.focus();
            view.print();
            window.setTimeout(function () {
                remove(frame);
            }, FALLBACK_MS);
        });
        frame.src = href;
        document.body.appendChild(frame);
    }

    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('a[data-cl-print-frame]') : null;
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
            return;
        }
        event.preventDefault();
        closeMenuOf(link);
        printInFrame(link.href);
    });
})();
