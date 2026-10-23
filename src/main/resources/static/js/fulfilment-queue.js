// Fulfilment queue (/dashboard/fulfilment/queue, spec docs/active/fulfilment-queue-redesign): the group's checkboxes
// [data-cl-queue-row] are the form's selectedOrders, ticked by the server, so the page works without this script. The
// script reveals the header's select-all box [data-cl-queue-all] (it clears everything when anything is ticked and
// ticks everything otherwise, like table-select.js), keeps the summary's "k z n" [data-cl-queue-count][data-template]
// and the items total [data-cl-queue-items] (sum of data-items) in step with the ticks, and, when nothing is ticked,
// disables the strategy buttons and shows the reason [data-cl-queue-reason], which the buttons then name in
// aria-describedby next to their own help [data-help].
(function () {
    'use strict';

    var form = document.querySelector('form[data-cl-queue]');
    if (!form) {
        return;
    }
    var rows = Array.prototype.slice.call(form.querySelectorAll('input[data-cl-queue-row]'));
    var all = form.querySelector('input[data-cl-queue-all]');
    var count = form.querySelector('[data-cl-queue-count]');
    var items = form.querySelector('[data-cl-queue-items]');
    var reason = form.querySelector('[data-cl-queue-reason]');
    var buttons = Array.prototype.slice.call(form.querySelectorAll('button[name="pathSelector"]'));

    function checkedRows() {
        return rows.filter(function (box) {
            return box.checked;
        });
    }

    function update() {
        var checked = checkedRows();
        var total = checked.reduce(function (sum, box) {
            return sum + (parseInt(box.getAttribute('data-items'), 10) || 0);
        }, 0);
        var none = checked.length === 0;
        count.textContent = count.getAttribute('data-template')
            .replace('{k}', String(checked.length)).replace('{n}', String(rows.length));
        items.textContent = String(total);
        reason.hidden = !none;
        buttons.forEach(function (button) {
            var help = button.getAttribute('data-help');
            button.disabled = none;
            button.setAttribute('aria-describedby', none ? reason.id + ' ' + help : help);
        });
        if (all) {
            all.checked = checked.length === rows.length;
            all.indeterminate = !none && checked.length < rows.length;
            all.setAttribute('aria-label', all.getAttribute(none ? 'data-label-select' : 'data-label-clear'));
        }
    }

    if (all) {
        all.hidden = false;
        all.addEventListener('change', function () {
            var tick = checkedRows().length === 0;
            rows.forEach(function (box) {
                box.checked = tick;
            });
            update();
        });
    }
    rows.forEach(function (box) {
        box.addEventListener('change', update);
    });
    // a page restored from the back-forward cache keeps the ticks the operator left, not the server's
    window.addEventListener('pageshow', update);
    update();
})();
