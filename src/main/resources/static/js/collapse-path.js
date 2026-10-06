/*
 * A long breadcrumb path ([data-cl-collapse-path] holding [data-cl-path-crumb] items, each with its own separator) shows
 * only the first crumb, "…" and the last two. "…" is a button that brings the hidden crumbs back in place and moves the
 * focus to the first of them. The server always renders the whole path, so without the script nothing is hidden.
 * Paths swapped in by list-page.js (cl-list:swapped) are collapsed again.
 */
(function () {
    'use strict';

    var MAX_VISIBLE = 4;

    function collapse(path) {
        if (path.hasAttribute('data-cl-collapsed')) {
            return;
        }
        var crumbs = Array.prototype.slice.call(path.querySelectorAll(':scope > [data-cl-path-crumb]'));
        if (crumbs.length <= MAX_VISIBLE) {
            return;
        }
        var middle = crumbs.slice(1, crumbs.length - 2);
        middle.forEach(function (crumb) { crumb.hidden = true; });

        var more = document.createElement('span');
        more.className = 'cl-path-crumb';
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'cl-path-more';
        button.textContent = '…';
        button.setAttribute('aria-label', path.getAttribute('data-cl-collapse-label') || '');
        button.setAttribute('aria-expanded', 'false');
        var separator = document.createElement('span');
        separator.setAttribute('aria-hidden', 'true');
        separator.textContent = ' › ';
        more.appendChild(button);
        more.appendChild(separator);
        crumbs[0].after(more);
        path.setAttribute('data-cl-collapsed', '');

        button.addEventListener('click', function () {
            button.setAttribute('aria-expanded', 'true');
            middle.forEach(function (crumb) { crumb.hidden = false; });
            more.remove();
            var first = middle[0].querySelector('a, [aria-current]');
            if (first) {
                if (!first.matches('a')) {
                    first.setAttribute('tabindex', '-1');
                }
                first.focus();
            }
        });
    }

    function collapseAll(scope) {
        scope.querySelectorAll('[data-cl-collapse-path]').forEach(collapse);
    }

    document.addEventListener('DOMContentLoaded', function () { collapseAll(document); });
    document.addEventListener('cl-list:swapped', function (event) { collapseAll(event.target); });
})();
