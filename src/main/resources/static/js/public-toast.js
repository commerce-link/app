// Same globals as the inline toast of layout.html, which copy-field.js relies on.
(function () {
    window.CL_TOAST_COPIED = document.body.dataset.clCopied || '';

    let timer = null;

    window.showToast = function (message, type) {
        const toast = document.getElementById('clToast');
        toast.textContent = message;
        toast.className = 'cl-toast' + (type ? ' is-' + type : '');
        toast.style.display = 'flex';
        requestAnimationFrame(() => { toast.style.opacity = '1'; });
        clearTimeout(timer);
        timer = setTimeout(() => {
            toast.style.opacity = '0';
            setTimeout(() => { toast.style.display = 'none'; }, 300);
        }, 2000);
    };
})();
