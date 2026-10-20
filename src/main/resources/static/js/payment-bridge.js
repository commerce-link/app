// Loaded without defer at the end of the content so the redirect does not wait for the rest of the page.
(function () {
    var auto = document.querySelector('.cl-bridge-auto');
    if (auto) {
        auto.hidden = false;
    }
    document.getElementById('paymentForm').submit();
})();
