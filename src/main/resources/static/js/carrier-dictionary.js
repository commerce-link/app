(function () {
    const form = document.querySelector('[data-carrier-dictionary]');
    if (!form) {
        return;
    }
    const rows = form.querySelector('[data-carrier-rows]');
    const template = rows.lastElementChild.cloneNode(true);

    form.querySelectorAll('[hidden][data-carrier-move], [hidden][data-carrier-remove], [hidden][data-carrier-add]')
        .forEach(button => button.hidden = false);
    template.querySelectorAll('[hidden]').forEach(button => button.hidden = false);

    rows.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) {
            return;
        }
        const row = button.closest('[data-carrier-row]');
        if (button.hasAttribute('data-carrier-remove')) {
            row.remove();
        } else if (button.dataset.carrierMove === '-1' && row.previousElementSibling) {
            rows.insertBefore(row, row.previousElementSibling);
        } else if (button.dataset.carrierMove === '1' && row.nextElementSibling) {
            rows.insertBefore(row.nextElementSibling, row);
        } else {
            return;
        }
        button.focus();
    });

    form.querySelector('[data-carrier-add]').addEventListener('click', () => {
        const row = template.cloneNode(true);
        rows.appendChild(row);
        row.querySelector('input').focus();
    });
})();
