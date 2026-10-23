// Loaded without defer inside the content; it waits for the rest of the page (logo, footer) to be parsed so the frame
// is complete while the bridge is visible, then sends the provider form.
(function () {
    function submit() {
        var auto = document.querySelector('.cl-bridge-auto');
        if (auto) {
            auto.hidden = false;
        }
        var form = document.getElementById('paymentForm');
        if (form) {
            form.submit();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', submit);
    } else {
        submit();
    }
})();
