// New RMA page: a ticked item shows its resolution and reason and enables its quantity; the summary follows the
// ticks; submitting with nothing ticked shows the reason under the button instead of posting. Without this script
// every field is shown and the server validates the same rules.
(function () {
    const form = document.querySelector('[data-cl-rma-new]');
    if (!form) {
        return;
    }
    const rows = Array.from(form.querySelectorAll('[data-cl-rma-item]'));
    const count = form.querySelector('[data-cl-rma-count]');
    const pieces = form.querySelector('[data-cl-rma-pieces]');
    const value = form.querySelector('[data-cl-rma-value]');
    const emptyHelp = document.getElementById('rma-empty-help');

    // Same output as the server's Money.format in both languages (non-breaking space groups, comma decimal); Intl would
    // leave 4-digit amounts ungrouped in Polish.
    function formatMoney(amount) {
        // toPrecision(15) drops the binary noise (1.005 * 100 = 100.49999999999999) so half cents round up like HALF_UP
        const totalCents = Math.round(Number((amount * 100).toPrecision(15)));
        const fixed = (totalCents / 100).toFixed(2);
        const [whole, cents] = fixed.split('.');
        return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ',' + cents;
    }

    function parts(row) {
        return {
            select: row.querySelector('[data-cl-rma-select]'),
            qty: row.querySelector('[data-cl-rma-qty]'),
            detail: row.querySelector('[data-cl-rma-detail]'),
            main: row.querySelector('.cl-row-main')
        };
    }

    function apply(row) {
        const p = parts(row);
        if (!p.select) {
            return;
        }
        const on = p.select.checked;
        p.main.classList.toggle('is-open', on);
        if (p.qty) {
            p.qty.disabled = !on;
        }
        if (p.detail) {
            p.detail.hidden = !on;
            p.detail.querySelectorAll('input, textarea').forEach(field => { field.disabled = !on; });
        }
    }

    function totals() {
        let items = 0;
        let sum = 0;
        let amount = 0;
        rows.forEach(row => {
            const p = parts(row);
            if (p.select && p.select.checked) {
                const qty = Math.max(0, parseInt(p.qty && p.qty.value, 10) || 0);
                items += 1;
                sum += qty;
                amount += qty * (parseFloat(p.qty && p.qty.dataset.price) || 0);
            }
        });
        count.textContent = items;
        pieces.textContent = sum;
        value.textContent = formatMoney(amount);
        if (items > 0) {
            emptyHelp.hidden = true;
        }
    }

    rows.forEach(row => {
        apply(row);
        const p = parts(row);
        if (p.select) {
            p.select.addEventListener('change', () => {
                apply(row);
                totals();
            });
        }
        if (p.qty) {
            p.qty.addEventListener('input', totals);
        }
    });
    totals();

    // Capture phase + stopPropagation: submit-once.js listens on document (bubble) and would otherwise leave the
    // button disabled after a submit that never happened.
    form.addEventListener('submit', event => {
        const anyTicked = rows.some(row => {
            const select = parts(row).select;
            return select && select.checked;
        });
        if (!anyTicked) {
            event.preventDefault();
            event.stopImmediatePropagation();
            emptyHelp.hidden = false;
        }
    }, true);
})();
