// Sorts the rows of a cl-table by a column: th[aria-sort] > button.cl-table-sort[data-sort-key]. A row supplies the
// value in tr[data-sort-<key>], otherwise the text of the cell in the same column. Two values compare as numbers only
// when both are nothing but a number ("1 500,00"); anything else -- a date, a code, a name -- compares as text with
// numeric collation, so "01.12.2026" is not read as the number 1 and "5060-RTX" is not read as 5060.
// Clicking the sorted column again flips the direction; aria-sort tells screen readers which column is sorted.
(function () {
    'use strict';

    var NUMBER = /^-?\d+(?:[.,]\d+)?$/;

    function value(row, key, index) {
        var explicit = row.getAttribute('data-sort-' + key);
        if (explicit !== null) {
            return explicit;
        }
        var cell = row.children[index];
        return cell ? cell.textContent.trim() : '';
    }

    function compare(a, b) {
        // The thousands separator is a space in Polish, so it is stripped before the value is judged to be a number.
        var sa = String(a).replace(/\s/g, '');
        var sb = String(b).replace(/\s/g, '');
        if (NUMBER.test(sa) && NUMBER.test(sb)) {
            return parseFloat(sa.replace(',', '.')) - parseFloat(sb.replace(',', '.'));
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
