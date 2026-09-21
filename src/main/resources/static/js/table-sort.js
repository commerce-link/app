// Sorts the rows of a cl-table by a column: th[aria-sort] > button.cl-table-sort[data-sort-key]. A row supplies the
// value in tr[data-sort-<key>] (numbers compare numerically), otherwise the text of the cell in the same column.
// Clicking the sorted column again flips the direction; aria-sort tells screen readers which column is sorted.
(function () {
    'use strict';

    function value(row, key, index) {
        var explicit = row.getAttribute('data-sort-' + key);
        if (explicit !== null) {
            return explicit;
        }
        var cell = row.children[index];
        return cell ? cell.textContent.trim() : '';
    }

    function compare(a, b) {
        var na = parseFloat(String(a).replace(/\s/g, '').replace(',', '.'));
        var nb = parseFloat(String(b).replace(/\s/g, '').replace(',', '.'));
        if (!isNaN(na) && !isNaN(nb)) {
            return na - nb;
        }
        return String(a).localeCompare(String(b), document.documentElement.lang || 'pl', { sensitivity: 'base', numeric: true });
    }

    function sort(table, th, button) {
        var key = button.getAttribute('data-sort-key');
        var index = Array.prototype.indexOf.call(th.parentNode.children, th);
        var direction = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';
        table.querySelectorAll('thead th[aria-sort]').forEach(function (other) {
            other.setAttribute('aria-sort', 'none');
        });
        th.setAttribute('aria-sort', direction);
        var body = table.tBodies[0];
        var rows = Array.prototype.slice.call(body.rows);
        rows.sort(function (a, b) {
            var result = compare(value(a, key, index), value(b, key, index));
            return direction === 'ascending' ? result : -result;
        });
        rows.forEach(function (row) {
            body.appendChild(row);
        });
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest && event.target.closest('button.cl-table-sort');
        if (!button) {
            return;
        }
        var th = button.closest('th');
        var table = button.closest('table');
        if (th && table && table.tBodies.length) {
            sort(table, th, button);
        }
    });
})();
