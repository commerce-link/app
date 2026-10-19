// Order printouts (order card, collection protocol): a link marked data-cl-print-frame prints its page without leaving
// the current one. The page is loaded into a hidden same-origin frame and the frame's own print() opens the browser's
// print dialog, so the paper shows exactly that sheet (its layout carries only the sheet) and the order page stays
// where it was. Without this script the link simply opens the sheet and the browser's Print menu prints it; a click
// with a modifier key (new tab or window) keeps that behaviour on purpose.
// window.CL_printInFrame(href, opener) prints an address built at click time the same way (the orders list's
// "Print cards" button in table-select.js); opener gets the focus back once the frame is gone.
(function () {
    'use strict';

    // The frame lives until its afterprint, the next print or the page going away (pagehide), never on a timer: in
    // Firefox print() may return while its preview is still open, and a timer would pull the sheet from under it.
    // A frame left off screen costs nothing.
    var current = null;
    // where the keyboard was before printing (the menu's summary, as the link itself hides with its menu): the frame
    // takes the focus to print, and once it is gone the focus would fall back to the start of the page
    var returnFocus = null;

    function restoreFocus() {
        var target = returnFocus;
        returnFocus = null;
        var active = document.activeElement;
        if (target && target.isConnected && (!active || active === document.body || !active.isConnected)) {
            target.focus();
        }
    }

    function remove(frame) {
        if (frame.parentNode) {
            frame.parentNode.removeChild(frame);
        }
        if (current === frame) {
            current = null;
            restoreFocus();
        }
    }

    function hasSheet(view) {
        try {
            return !!view.document.querySelector('.cl-print-sheet');
        } catch (error) {
            return false;
        }
    }

    // closes the link's menu and answers what should get the focus back after printing
    function closeMenuOf(link) {
        var menu = link.closest('details');
        if (menu) {
            menu.open = false;
            return menu.querySelector(':scope > summary') || link;
        }
        return link;
    }

    function printInFrame(href, opener) {
        // a second request (double click, the other printout) replaces the one still loading: one dialog at a time
        if (current) {
            remove(current);
        }
        returnFocus = opener;
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
        });
        frame.src = href;
        document.body.appendChild(frame);
    }

    window.CL_printInFrame = function (href, opener) {
        printInFrame(href, opener);
    };

    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('a[data-cl-print-frame]') : null;
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
            return;
        }
        event.preventDefault();
        printInFrame(link.href, closeMenuOf(link));
    });

    window.addEventListener('pagehide', function () {
        if (current) {
            remove(current);
        }
    });
})();
