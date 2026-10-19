// Label printing on a goods receipt (warehouse document details): fetch the ZPL from the app, find the Zebra printer
// through the Zebra Browser Print app on this computer (localhost:9100) and send it there. The controls are hidden in
// the markup and shown here, because without this script printing cannot work.
(function () {
    var BROWSER_PRINT_URL = 'http://localhost:9100';
    var root = document.querySelector('[data-cl-label-print]');
    if (!root) {
        return;
    }
    root.hidden = false;

    function message(template, key, value) {
        // split/join, not replace: a value with "$&" must not be read as a replacement pattern
        return template.split('{' + key + '}').join(value);
    }

    // spec §3.4: a compact spinner inside the busy button, besides aria-busy
    function showSpinner(trigger) {
        var spinner = document.createElement('span');
        spinner.className = 'cl-spinner is-compact';
        spinner.setAttribute('aria-hidden', 'true');
        trigger.appendChild(spinner);
        return spinner;
    }

    // showToast is defined by the layout; do not fail the print flow when it is missing
    function toast(text, type) {
        if (typeof showToast === 'function') {
            showToast(text, type);
        }
    }

    async function fetchZpl(printer) {
        var url = root.getAttribute('data-endpoint') + '?documentId=' + encodeURIComponent(root.getAttribute('data-document-id'))
            + '&printer=' + encodeURIComponent(printer);
        var response = await fetch(url, { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        return response.text();
    }

    async function findDevice(deviceId) {
        var response = await fetch(BROWSER_PRINT_URL + '/available');
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        var data = await response.json();
        var printers = (data && data.printer) || [];
        if (!printers.length) {
            throw new Error('no printers available');
        }
        var match = deviceId ? printers.filter(function (p) { return p.uid === deviceId; })[0] : null;
        return match || printers.filter(function (p) { return p.connection === 'usb'; })[0] || printers[0];
    }

    async function send(device, data) {
        var response = await fetch(BROWSER_PRINT_URL + '/write', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ device: device, data: data })
        });
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
    }

    root.addEventListener('click', async function (event) {
        var trigger = event.target.closest('[data-printer]');
        if (!trigger || trigger.getAttribute('aria-busy') === 'true') {
            return;
        }
        var printer = trigger.getAttribute('data-printer');
        var menu = trigger.closest('details');
        if (menu) {
            menu.open = false;
        }
        trigger.setAttribute('aria-busy', 'true');
        var spinner = showSpinner(trigger);
        try {
            var zpl = await fetchZpl(printer);
            var device = await findDevice(trigger.getAttribute('data-device-id'));
            await send(device, zpl);
            // the count first: a printer name is free text and may itself contain "{count}"
            var sent = message(root.getAttribute('data-sent'), 'count', root.getAttribute('data-count'));
            toast(message(sent, 'printer', printer));
        } catch (e) {
            toast(message(root.getAttribute('data-failed'), 'error', e.message), 'danger');
        } finally {
            trigger.removeAttribute('aria-busy');
            spinner.remove();
        }
    });
})();
