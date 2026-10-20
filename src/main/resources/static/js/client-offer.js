// Variant choice on the customer offer page (form.cl-variant-pick). With JavaScript the "Change variant" button stays
// hidden (CSS) and a choice made with the mouse or a finger is sent at once. A choice made with the arrow keys is not
// sent (WCAG 3.2.2: moving through radios must not change the page); it reveals that group's button instead.
//
// The choice goes to the server in the background and the two cards whose amounts depend on it (items and payment) are
// swapped in place, so the page neither reloads nor jumps; the data card is left alone. While the answer is on its way
// the amounts fade, the spinner next to the total shows after SLOW_MS, and paying or sending a proforma is blocked, so
// nobody pays the old amount. Choices are sent one after another: a choice made while one is on its way waits and goes
// last (cancelling a request in the browser would not stop the server from saving it, possibly after the newer one).
// Any failure sends the form the ordinary way (a reload that shows the true state).
// Without JavaScript the button stays visible and sends the choice with a reload.
(function () {
    'use strict';

    var SLOW_MS = 300;
    var pointerForm = null;
    var running = false;
    var queued = null;

    // pointerdown comes before the change it causes; a keyboard change has no pointerdown before it
    document.addEventListener('pointerdown', function (event) {
        pointerForm = event.target.closest('form.cl-variant-pick');
    });
    document.addEventListener('keydown', function () {
        pointerForm = null;
    });

    document.addEventListener('change', function (event) {
        var form = event.target.closest('form.cl-variant-pick');
        if (!form || !event.target.matches('input[type="radio"]')) {
            return;
        }
        if (pointerForm === form) {
            pointerForm = null;
            if (typeof form.requestSubmit === 'function') {
                form.requestSubmit();
            } else {
                send(form);
            }
            return;
        }
        var apply = form.querySelector('.cl-variant-apply');
        if (apply) {
            apply.hidden = false;
            apply.setAttribute('data-cl-revealed', '');
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest('form.cl-variant-pick');
        if (!form || !window.fetch || !window.DOMParser) {
            return;
        }
        event.preventDefault();
        send(form);
    });

    function send(form) {
        if (running) {
            var radio = form.querySelector('input[type="radio"]:checked');
            queued = {groupId: form.querySelector('input[name="groupId"]').value, position: radio ? radio.value : null};
            return;
        }
        running = true;
        var groupId = form.querySelector('input[name="groupId"]').value;
        var focused = form.contains(document.activeElement);
        var items = document.getElementById('client-offer-items');
        var payment = document.getElementById('client-offer-payment');
        setBusy(items, payment, true);
        var slow = setTimeout(function () {
            if (items) {
                items.classList.add('is-slow');
            }
        }, SLOW_MS);

        fetch(form.action, {
            method: 'POST',
            body: new URLSearchParams(new FormData(form)),
            headers: {'X-Requested-With': 'fetch'}
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('status ' + response.status);
            }
            return response.text();
        }).then(function (html) {
            var answer = new DOMParser().parseFromString(html, 'text/html');
            var newItems = answer.getElementById('client-offer-items');
            var newPayment = answer.getElementById('client-offer-payment');
            if (!newItems || !newPayment || !items || !payment) {
                throw new Error('cards missing');
            }
            items.replaceWith(newItems);
            payment.replaceWith(newPayment);
            // the copy buttons are hidden in the markup until copy-field.js sees the clipboard API
            newPayment.dispatchEvent(new CustomEvent('cl-list:swapped', {bubbles: true}));
            if (focused) {
                restoreFocus(newItems, groupId);
            }
            announce(newItems);
            clearTimeout(slow);
            running = false;
            setBusy(newItems, newPayment, false);
            sendQueued(newItems);
        }).catch(function () {
            clearTimeout(slow);
            queued = null;
            if (form.isConnected) {
                HTMLFormElement.prototype.submit.call(form);
            } else {
                window.location.reload();
            }
        });
    }

    // the newer choice, made on the old card, is applied to the swapped card and sent
    function sendQueued(card) {
        var next = queued;
        queued = null;
        if (!next || next.position === null) {
            return;
        }
        var form = groupForm(card, next.groupId);
        var radio = form && form.querySelector('input[type="radio"][value="' + next.position + '"]');
        if (radio && !radio.checked) {
            radio.checked = true;
            send(form);
        }
    }

    function groupForm(card, groupId) {
        var forms = card.querySelectorAll('form.cl-variant-pick');
        for (var i = 0; i < forms.length; i++) {
            if (forms[i].querySelector('input[name="groupId"]').value === groupId) {
                return forms[i];
            }
        }
        return null;
    }

    function setBusy(items, payment, busy) {
        [items, payment].forEach(function (card) {
            if (!card) {
                return;
            }
            card.classList.toggle('is-updating', busy);
            if (!busy) {
                card.classList.remove('is-slow');
            }
            card.setAttribute('aria-busy', busy ? 'true' : 'false');
        });
        if (payment) {
            payment.querySelectorAll('form[action$="/checkout"] [type="submit"], form.cl-proforma [type="submit"]').forEach(function (button) {
                if (busy) {
                    if (!button.hasAttribute('data-cl-was-disabled')) {
                        button.setAttribute('data-cl-was-disabled', button.disabled ? 'true' : 'false');
                    }
                    button.disabled = true;
                } else if (button.hasAttribute('data-cl-was-disabled')) {
                    button.disabled = button.getAttribute('data-cl-was-disabled') === 'true';
                    button.removeAttribute('data-cl-was-disabled');
                }
            });
        }
    }

    // the swapped card replaced the focused radio; focus goes to the same group's checked radio
    function restoreFocus(card, groupId) {
        var form = groupForm(card, groupId);
        var checked = form && form.querySelector('input[type="radio"]:checked');
        if (checked) {
            checked.focus({preventScroll: true});
        }
    }

    function announce(card) {
        var live = document.getElementById('client-offer-live');
        var label = card.querySelector('.cl-offer-total-row dt');
        var amount = card.querySelector('.cl-offer-total-row dd');
        if (live && label && amount) {
            live.textContent = label.textContent.trim() + ': ' + amount.textContent.trim();
        }
    }
})();
