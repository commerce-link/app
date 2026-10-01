// The delivery details page (templates/deliveries/details.html). Everything works without JavaScript: an opener is a
// link to the page with its dialog open, the selection row's noscript buttons post to …/confirm/…. This script adds:
// the destination rows' toggles; the selection row docked to the bottom on phones while the items card is on screen
// (P2); a "…-all" opener (data-cl-select-pending) checking every waiting destination before its dialog opens and
// restoring the selection when the dialog closes unsent; the list of what a selection dialog acts on, rebuilt from
// the checked rows; Enter inside a dialog field running that dialog's own action, never the form's first button.
(function () {
    'use strict';

    var form = document.getElementById('allocationsForm');

    function rowBoxes() {
        return form ? Array.prototype.slice.call(form.querySelectorAll('input[data-cl-select-row]')) : [];
    }

    function checkedBoxes() {
        return rowBoxes().filter(function (box) {
            return box.checked;
        });
    }

    // table-select.js refreshes the count and the selection row on a change event from its table
    function refreshSelection() {
        var table = form && form.querySelector('table[data-cl-select-table]');
        if (table) {
            table.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    // Collapsing a product unchecks its destinations: table-select.js counts and selects only visible rows, and a
    // hidden checked box would still post, so the action would touch what the operator cannot see.
    function setOpen(button, open) {
        var unchecked = false;
        button.setAttribute('aria-expanded', String(open));
        (button.getAttribute('aria-controls') || '').split(' ').forEach(function (id) {
            var row = id && document.getElementById(id);
            if (!row) {
                return;
            }
            row.hidden = !open;
            if (!open) {
                row.querySelectorAll('input[data-cl-select-row]').forEach(function (box) {
                    unchecked = unchecked || box.checked;
                    box.checked = false;
                });
            }
        });
        var main = button.closest('tr');
        if (main) {
            main.classList.toggle('is-open', open);
        }
        if (unchecked) {
            refreshSelection();
        }
    }

    function expandAll() {
        document.querySelectorAll('[data-cl-alloc-toggle][aria-expanded="false"]').forEach(function (button) {
            setOpen(button, true);
        });
    }

    function initToggles() {
        document.querySelectorAll('[data-cl-alloc-toggle]').forEach(function (button) {
            button.hidden = false;
            button.addEventListener('click', function () {
                setOpen(button, button.getAttribute('aria-expanded') !== 'true');
            });
        });
    }

    function initDockedBar() {
        var bar = form && form.querySelector('.cl-selection-row.is-docked');
        var card = form && form.closest('.cl-card');
        if (!bar || !card || typeof IntersectionObserver !== 'function') {
            return;
        }
        // A hidden bar measures 0; writing that would override the stylesheet fallback and let the bar, once shown,
        // cover the last destination row, so the height is taken whenever the selection (and so the bar) changes.
        var measure = function () {
            var height = bar.offsetHeight;
            if (height === 0) {
                return;
            }
            form.style.setProperty('--cl-docked-bar', (height + 8) + 'px');
        };
        new IntersectionObserver(function (entries) {
            bar.classList.toggle('is-in-view', entries[entries.length - 1].isIntersecting);
            measure();
        }).observe(card);
        form.addEventListener('change', measure);
        window.addEventListener('resize', measure);
    }

    function fillList(list) {
        while (list.firstChild) {
            list.removeChild(list.firstChild);
        }
        checkedBoxes().forEach(function (box) {
            var item = document.createElement('li');
            var name = document.createElement('span');
            name.textContent = box.getAttribute('data-name');
            var qty = document.createElement('span');
            qty.className = 'is-numeric';
            qty.textContent = box.getAttribute('data-qty-label');
            var destination = document.createElement('span');
            destination.className = 'cl-dialog-list-sub';
            destination.textContent = '→ ' + box.getAttribute('data-dest');
            item.appendChild(name);
            item.appendChild(qty);
            item.appendChild(destination);
            list.appendChild(item);
        });
    }

    function initDialogs() {
        document.querySelectorAll('dialog.cl-dialog').forEach(function (dialog) {
            var before = null;
            dialog.addEventListener('cl:dialog-open', function (event) {
                var trigger = event.detail && event.detail.trigger;
                dialog.removeAttribute('data-cl-submitted');
                before = null;
                if (trigger && trigger.hasAttribute('data-cl-select-pending')) {
                    before = checkedBoxes();
                    // collapsed rows would be checked but not counted, so every product opens first
                    expandAll();
                    rowBoxes().forEach(function (box) {
                        box.checked = true;
                    });
                    refreshSelection();
                }
                dialog.querySelectorAll('ul[data-cl-selection-list]').forEach(fillList);
                dialog.querySelectorAll('[data-cl-field-error]').forEach(function (error) {
                    error.hidden = true;
                });
            });
            dialog.addEventListener('close', function () {
                if (before !== null && !dialog.hasAttribute('data-cl-submitted')) {
                    var kept = before;
                    rowBoxes().forEach(function (box) {
                        box.checked = kept.indexOf(box) >= 0;
                    });
                    refreshSelection();
                }
                before = null;
            });
            dialog.querySelectorAll('[data-cl-dialog-submit]').forEach(function (button) {
                button.addEventListener('click', function (event) {
                    if (button.closest('#allocationsForm') && checkedBoxes().length === 0) {
                        event.preventDefault();
                        return;
                    }
                    if (!event.defaultPrevented) {
                        dialog.setAttribute('data-cl-submitted', '');
                    }
                });
            });
        });
    }

    function initEnter() {
        if (!form) {
            return;
        }
        form.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' || !event.target.matches('input')) {
                return;
            }
            var dialog = event.target.closest('dialog');
            if (!dialog) {
                return;
            }
            event.preventDefault();
            var submit = dialog.querySelector('[data-cl-dialog-submit]');
            if (submit && !submit.disabled) {
                submit.click();
            }
        });
    }

    // dialogs of Task 11

    function init() {
        initToggles();
        initDockedBar();
        initDialogs();
        initEnter();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
