// Variant choice on the customer offer page (form.cl-variant-pick). With JavaScript the "Change variant" button stays
// hidden (CSS) and a choice made with the mouse or a finger is sent at once. A choice made with the arrow keys is not
// sent (WCAG 3.2.2: moving through radios must not change the page); it reveals that group's button instead.
//
// The choice goes to the server in the background and the two cards whose amounts depend on it (items and payment) are
// swapped in place, so the page neither reloads nor jumps; the data card is left alone. While the answer is on its way
// the amounts fade, the spinner next to the total shows after SLOW_MS, and paying or sending a proforma is blocked, so
// nobody pays the old amount. Choices are sent one after another: choices made while one is on its way wait, one per
// group, and go in the order they were made (cancelling a request in the browser would not stop the server from saving
// it, possibly after the newer one).
// A failed answer sends the form the ordinary way (a reload that shows the true state), unless the customer is leaving
// the page: Firefox and Safari reject the request as soon as a navigation starts, and an ordinary submit then would
// cancel that navigation (the data form being saved, the language switch).
// Without JavaScript the button stays visible and sends the choice with a reload.
(function () {
    'use strict';

    var SLOW_MS = 300;
    // a rejected request waits this long for the page to start unloading before it counts as a failure
    var LEAVE_GRACE_MS = 300;
    var pointerForm = null;
    var running = false;
    var queued = [];
    var leaving = false;

    function markLeaving() {
        leaving = true;
    }
    // beforeunload only while a request is on its way: a page that always listens to it is kept out of Firefox's
    // back-forward cache
    function watchLeaving(on) {
        if (on) {
            leaving = false;
            window.addEventListener('beforeunload', markLeaving);
        } else {
            window.removeEventListener('beforeunload', markLeaving);
        }
    }
    window.addEventListener('pagehide', markLeaving);
    // back from the browser cache: the page is alive again
    window.addEventListener('pageshow', function () {
        leaving = false;
    });

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
        var groupId = form.querySelector('input[name="groupId"]').value;
        if (running) {
            enqueue(groupId, form.querySelector('input[type="radio"]:checked'));
            return;
        }
        running = true;
        watchLeaving(true);
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
                throw {answered: true};
            }
            return response.text();
        }).then(function (html) {
            var answer = new DOMParser().parseFromString(html, 'text/html');
            var newItems = answer.getElementById('client-offer-items');
            var newPayment = answer.getElementById('client-offer-payment');
            if (!newItems || !newPayment || !items || !payment) {
                throw {answered: true};
            }
            // the customer may have moved on to the data form meanwhile: focus comes back only if it is still in the card
            var focusInItems = items.contains(document.activeElement);
            items.replaceWith(newItems);
            payment.replaceWith(newPayment);
            // the copy buttons are hidden in the markup until copy-field.js sees the clipboard API
            newPayment.dispatchEvent(new CustomEvent('cl-list:swapped', {bubbles: true}));
            if (focusInItems) {
                restoreFocus(newItems, groupId);
            }
            announce(newItems, groupId);
            clearTimeout(slow);
            running = false;
            watchLeaving(false);
            setBusy(newItems, newPayment, false);
            sendQueued(newItems);
        }).catch(function (error) {
            clearTimeout(slow);
            setTimeout(function () {
                fail(form, items, payment);
            }, error && error.answered ? 0 : LEAVE_GRACE_MS);
        });
    }

    function fail(form, items, payment) {
        queued = [];
        watchLeaving(false);
        if (leaving) {
            // the navigation the customer started wins; if it never happens (a mailto link), the page stays usable and
            // the radios go back to what the server has (their checked attribute), as the choice may not have been saved
            leaving = false;
            running = false;
            setBusy(items, payment, false);
            if (items) {
                items.querySelectorAll('form.cl-variant-pick').forEach(function (pick) {
                    pick.reset();
                });
            }
            return;
        }
        if (form.isConnected) {
            HTMLFormElement.prototype.submit.call(form);
        } else {
            window.location.reload();
        }
    }

    // one waiting choice per group, kept in the order the groups were chosen
    function enqueue(groupId, radio) {
        queued = queued.filter(function (entry) {
            return entry.groupId !== groupId;
        });
        if (radio) {
            queued.push({groupId: groupId, position: radio.value});
        }
    }

    // The waiting choices were made on the old card: shown on the swapped one at once (a choice the server already has is
    // dropped), then sent one by one.
    function sendQueued(card) {
        var pending = [];
        queued.forEach(function (entry) {
            var radio = groupRadio(card, entry.groupId, entry.position);
            if (radio && !radio.checked) {
                radio.checked = true;
                pending.push(entry);
            }
        });
        queued = pending.slice(1);
        if (pending.length) {
            send(groupForm(card, pending[0].groupId));
        }
    }

    function groupRadio(card, groupId, position) {
        var form = groupForm(card, groupId);
        return form && form.querySelector('input[type="radio"][value="' + position + '"]');
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

    // the chosen variant and the new total: a variant of the same price leaves the total as it was, and a live region
    // does not read the same text twice
    function announce(card, groupId) {
        var live = document.getElementById('client-offer-live');
        var label = card.querySelector('.cl-offer-total-row dt');
        var amount = card.querySelector('.cl-offer-total-row dd');
        if (!live || !label || !amount) {
            return;
        }
        var form = groupForm(card, groupId);
        var title = form && form.querySelector('input[type="radio"]:checked + .cl-choice-title');
        var total = label.textContent.trim() + ': ' + amount.textContent.trim();
        live.textContent = title ? title.textContent.trim() + '. ' + total : total;
    }
})();
