// Turnstile's flexible widget is at least 300 px wide; in a narrower form column (a 320 px phone) it would stick out
// of the column, so the compact widget is used there. Runs right after the widget's markup, before api.js renders it.
(function () {
    document.querySelectorAll('.cf-turnstile').forEach(function (widget) {
        if (widget.clientWidth < 300) {
            widget.dataset.size = 'compact';
        }
    });
})();
