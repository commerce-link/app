// Supplier selection (POST /dashboard/orders/fulfilment and the warehouse restock; spec
// docs/active/fulfilment-queue-redesign/2026-10-08-fulfilment-select-design.md). The offers' checkboxes
// [data-cl-offer-check] hold the selection. Every item (orderId::itemId) goes to the ticked offer with the lowest gross
// price; a tie goes to the offer the server listed first, so a click elsewhere never flips it. From that the script
// keeps the rows (on, off, covered cheaper), the pieces, profit and margin of each offer, the order chips, the summary
// and the proposal radios in step, filters the table in the browser and, at submit, writes the entries[...] fields
// FulfilmentForm binds. A covered offer sits under the offer that beats it, folded behind one "dearer offers" row
// (variant A4, plan 2026-10-08-fulfilment-select-a4-plan.md). It writes text only: labels, names and categories are
// operator data.
(function () {
    'use strict';

    var form = document.querySelector('form[data-cl-select]');
    if (!form) {
        return;
    }
    var texts = form.querySelector('[data-cl-select-text]').dataset;
    var table = form.querySelector('table[data-cl-offers]');
    var profitKnown = form.getAttribute('data-profit-known') !== 'false';
    var itemsTotal = parseInt(form.getAttribute('data-items-total'), 10) || 0;

    function all(root, selector) {
        return Array.prototype.slice.call(root.querySelectorAll(selector));
    }

    function format(template) {
        var args = Array.prototype.slice.call(arguments, 1);
        return String(template).replace(/\{(\d)\}/g, function (match, i) {
            return args[i] === undefined ? match : String(args[i]);
        });
    }

    // Money.format of the order screens: non-breaking space thousands, comma decimals, U+2212 minus
    function money(value) {
        var cents = Math.round(Math.abs(value) * 100);
        var whole = String(Math.floor(cents / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        var fraction = String(cents % 100);
        return (value < 0 && cents > 0 ? '−' : '') + whole + ',' + (fraction.length < 2 ? '0' + fraction : fraction);
    }

    function signed(value) {
        return (value >= 0 ? '+' : '') + money(value);
    }

    // a share as a percentage with one decimal, like ItemMargin ("15,3")
    function percent(share) {
        var tenths = Math.round(Math.abs(share) * 1000);
        return (share < 0 && tenths > 0 ? '−' : '') + Math.floor(tenths / 10) + ',' + (tenths % 10);
    }

    // rewriting an unchanged text still mutates the node, which a screen reader may announce again in a live region
    function setText(selector, text) {
        var element = form.querySelector(selector);
        if (element && element.textContent !== text) {
            element.textContent = text;
        }
    }

    var offers = all(table, 'tr[data-cl-offer]').map(function (row, index) {
        var net = parseFloat(row.getAttribute('data-net')) || 0;
        var gross = parseFloat(row.getAttribute('data-gross')) || 0;
        return {
            row: row,
            index: index,
            id: row.getAttribute('data-groupid'),
            provider: row.getAttribute('data-provider'),
            label: row.getAttribute('data-provider-label') || row.getAttribute('data-provider'),
            net: net,
            gross: gross,
            vat: net > 0 ? gross / net : 1,
            check: row.querySelector('input[data-cl-offer-check]'),
            nameId: row.querySelector('.cl-cell-toggle .cl-visually-hidden').id,
            category: row.closest('tbody'),
            parent: null,
            folded: false,
            visible: true,
            covered: false,
            won: 0,
            wonProfit: 0,
            wonSales: 0,
            allocations: all(row, '[data-cl-alloc]').map(function (element) {
                var order = element.getAttribute('data-order') || '';
                var item = element.getAttribute('data-item') || '';
                return {
                    element: element,
                    order: order,
                    item: item,
                    key: order + '::' + item,
                    qty: parseInt(element.getAttribute('data-qty'), 10) || 0,
                    price: parseFloat(element.getAttribute('data-price')) || 0
                };
            })
        };
    });
    var categories = all(table, 'tbody[data-cl-category]');
    var chips = all(form, 'button[data-cl-coverage]');
    var variants = all(form, 'input[data-cl-variant]');
    var customVariant = form.querySelector('input[data-cl-variant-custom]');
    var selectVisible = form.querySelector('button[data-cl-select-visible]');
    var expandAllButton = form.querySelector('button[data-cl-expand-all]');
    var collapseAllButton = form.querySelector('button[data-cl-collapse-all]');
    var providerBoxes = all(form, 'input[data-cl-filter-provider]');
    var minInput = form.querySelector('input[data-cl-filter-min]');
    var maxInput = form.querySelector('input[data-cl-filter-max]');
    var hiddenNotice = form.querySelector('[data-cl-hidden-notice]');
    var rangeBox = form.querySelector('[data-cl-price-range]');
    var rangeLo = form.querySelector('input[data-cl-range-lo]');
    var rangeHi = form.querySelector('input[data-cl-range-hi]');
    var rangeClear = form.querySelector('button[data-cl-range-clear]');
    var reason = form.querySelector('[data-cl-select-reason]');
    var needsSelection = all(form, 'button[data-cl-needs-selection]');
    var liveRegion = form.querySelector('[data-cl-live]');
    var focusedOrder = null;
    var filtersActive = false;
    // the last "Zamów tę" that left items of other orders without a supplier: shown in the chosen row until the next change
    var swapped = null;
    var liveTimer = null;
    var liveText = null;
    var livePrefix = '';
    var partialState = null;
    // per winner (group id): whether its dearer offers are unfolded, and which of them were ticked at the last render
    var unfolded = {};
    var foldRows = {};

    function beats(a, b) {
        return a.gross < b.gross || (a.gross === b.gross && a.index < b.index);
    }

    function winners() {
        var winner = {};
        offers.forEach(function (offer) {
            if (!offer.check.checked) {
                return;
            }
            offer.allocations.forEach(function (allocation) {
                var current = winner[allocation.key];
                if (!current || beats(offer, current)) {
                    winner[allocation.key] = offer;
                }
            });
        });
        return winner;
    }

    // net per piece above the winner, in whole grosze; zero or less when the gross decided (same net, other VAT rate)
    function dearerCents(offer, winner) {
        return Math.round((offer.net - winner.net) * 100);
    }

    // a covered offer says how much dearer it is than the offer it sits under (net, per piece), the amount in the bad
    // colour, or only who covers it when it is not dearer in net
    function renderCovered(offer, winner, beaters) {
        var covered = offer.row.querySelector('[data-cl-covered]');
        var key = offer.row.querySelector('th.cl-table-key');
        covered.textContent = '';
        covered.hidden = !offer.covered;
        // a covered row shows no pieces: its line takes the empty quantity cell too, so the pill and the text fit together
        key.colSpan = offer.covered ? 2 : 1;
        offer.row.querySelector('td.cl-cell-qty').hidden = offer.covered;
        if (!offer.covered) {
            return;
        }
        var cents = dearerCents(offer, winner[offer.allocations[0].key]);
        if (cents <= 0) {
            covered.textContent = format(texts.coveredOff, beaters.join(', '));
            return;
        }
        var template = String(texts.dearer);
        var at = template.indexOf('{0}');
        var amount = document.createElement('span');
        amount.className = 'cl-offer-dearer';
        amount.textContent = format(template.slice(at), money(cents / 100));
        covered.appendChild(document.createTextNode(template.slice(0, at)));
        covered.appendChild(amount);
    }

    // WCAG 2.5.3: the checkbox is named by the toggle text on screen ("Zamawiam", "Zamów", "Zamów tę") followed by the
    // offer and the supplier; CSS picks the same text from the same two facts
    function renderToggleName(offer) {
        var state = offer.check.checked ? 'on' : (offer.covered ? 'alt' : 'off');
        var text = offer.check.parentNode.querySelector('.cl-offer-toggle-' + state);
        offer.check.setAttribute('aria-labelledby', text.id + ' ' + offer.nameId);
    }

    function renderOffer(offer, winner) {
        var won = 0, wonProfit = 0, wonSales = 0, open = 0, openProfit = 0, openSales = 0;
        var beaters = [];
        offer.allocations.forEach(function (allocation) {
            var owner = winner[allocation.key];
            var sales = allocation.price / offer.vat * allocation.qty;
            var profit = sales - offer.net * allocation.qty;
            // an allocation is lost only to a ticked winner that beats this offer (cheaper, or equal price listed earlier)
            var lost = !!owner && owner !== offer && beats(owner, offer);
            allocation.element.classList.toggle('is-lost', lost);
            allocation.element.classList.toggle('cl-tooltip', lost);
            if (lost) {
                allocation.element.setAttribute('data-tooltip', format(texts.lost, owner.label));
                if (beaters.indexOf(owner.label) < 0) {
                    beaters.push(owner.label);
                }
                return;
            }
            allocation.element.removeAttribute('data-tooltip');
            open += allocation.qty;
            openProfit += profit;
            openSales += sales;
            if (owner === offer) {
                won += allocation.qty;
                wonProfit += profit;
                wonSales += sales;
            }
        });
        var ticked = offer.check.checked;
        offer.covered = offer.allocations.length > 0 && open === 0;
        offer.won = won;
        offer.wonProfit = wonProfit;
        offer.wonSales = wonSales;
        offer.row.classList.toggle('is-on', ticked && !offer.covered);
        offer.row.classList.toggle('is-off', !ticked && !offer.covered);
        offer.row.classList.toggle('is-covered', offer.covered);

        renderCovered(offer, winner, beaters);
        renderToggleName(offer);
        offer.row.querySelector('[data-cl-qty]').textContent = String(ticked ? won : open);

        if (!profitKnown) {
            return;
        }
        var box = offer.row.querySelector('[data-cl-profit]');
        var value = offer.row.querySelector('[data-cl-profit-value]');
        var margin = offer.row.querySelector('[data-cl-margin]');
        var pieces = ticked ? won : open;
        var profit = ticked ? wonProfit : openProfit;
        var sales = ticked ? wonSales : openSales;
        box.classList.toggle('is-ok', pieces > 0 && profit > 1);
        box.classList.toggle('is-bad', pieces > 0 && profit < -1);
        value.textContent = pieces > 0 ? format(texts.money, signed(profit)) : '—';
        margin.textContent = pieces > 0 && sales > 0 ? format(texts.margin, percent(profit / sales)) : '';
    }

    function foldRow(parent) {
        var fold = foldRows[parent.id];
        if (!fold) {
            var row = document.createElement('tr');
            row.className = 'cl-alt-fold';
            var cell = document.createElement('td');
            cell.colSpan = 5;
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'cl-alt-fold-toggle';
            var icon = document.createElement('i');
            icon.className = 'fas fa-chevron-down';
            icon.setAttribute('aria-hidden', 'true');
            var label = document.createElement('span');
            button.appendChild(icon);
            button.appendChild(label);
            cell.appendChild(button);
            row.appendChild(cell);
            button.addEventListener('click', function () {
                unfolded[parent.id] = button.getAttribute('aria-expanded') !== 'true';
                render();
            });
            fold = foldRows[parent.id] = {row: row, button: button, label: label, parent: parent, children: []};
        }
        return fold;
    }

    // a covered offer moves under the offer that wins its first item, as on the old page, behind one fold row that is
    // closed until the operator opens it
    function reorder(winner) {
        var focused = document.activeElement;
        var used = {};
        offers.forEach(function (offer) {
            offer.parent = null;
            offer.folded = false;
        });
        categories.forEach(function (tbody) {
            var under = new Map();
            var primaries = [];
            offers.forEach(function (offer) {
                if (offer.category !== tbody) {
                    return;
                }
                var owner = offer.covered ? winner[offer.allocations[0].key] : null;
                if (owner && owner !== offer && owner.category === tbody) {
                    if (!under.has(owner)) {
                        under.set(owner, []);
                    }
                    under.get(owner).push(offer);
                    offer.parent = owner;
                } else {
                    primaries.push(offer);
                }
            });
            // move a row only when its place changes: re-inserting a row that holds the focused control drops the focus
            var previous = tbody.firstElementChild;
            var moved = false;
            function place(row) {
                if (previous.nextElementSibling !== row) {
                    previous.after(row);
                    moved = moved || row.contains(focused);
                }
                previous = row;
            }
            primaries.forEach(function (primary) {
                place(primary.row);
                var children = under.get(primary) || [];
                if (!children.length) {
                    return;
                }
                var fold = foldRow(primary);
                used[primary.id] = true;
                // the focus never lands on a row that folds away
                var open = unfolded[primary.id] === true || children.some(function (child) {
                    return child.row.contains(focused);
                });
                var dearer = children.map(function (child) {
                    return dearerCents(child, primary);
                }).filter(function (cents) {
                    return cents > 0;
                });
                fold.children = children;
                fold.label.textContent = dearer.length
                    ? format(texts.fold, children.length, money(Math.min.apply(null, dearer) / 100))
                    : format(texts.foldPlain, children.length);
                fold.button.setAttribute('aria-expanded', String(open));
                fold.button.setAttribute('aria-controls', children.map(function (child) {
                    return child.row.id;
                }).join(' '));
                place(fold.row);
                children.forEach(function (child) {
                    child.folded = !open;
                    place(child.row);
                });
            });
            // a moved row that held the focus is the one case the browser cannot keep
            if (moved && focused && focused !== document.activeElement) {
                focused.focus();
            }
        });
        Object.keys(foldRows).forEach(function (id) {
            if (!used[id]) {
                foldRows[id].row.remove();
                delete foldRows[id];
            }
        });
    }

    function isOpen(tbody) {
        return tbody.querySelector('.cl-group-toggle').getAttribute('aria-expanded') !== 'false';
    }

    // "ofert: n · zamawiane: k" (k = ticked offers that order something); a collapsed category adds who supplies it and
    // the net value: "· Elko, Magazyn sklepu · 1 890,95 zł netto"
    function renderCategoryCounts() {
        categories.forEach(function (tbody) {
            var inGroup = offers.filter(function (offer) {
                return offer.category === tbody;
            });
            var ordering = inGroup.filter(function (offer) {
                return offer.check.checked && offer.won > 0;
            });
            var count = tbody.querySelector('[data-cl-category-count]');
            count.textContent = format(texts.categoryOffers, inGroup.length) + ' · ';
            var ordered = document.createElement('strong');
            ordered.className = ordering.length ? 'is-ok' : '';
            ordered.textContent = format(texts.categoryOrdered, ordering.length);
            count.appendChild(ordered);

            var chosen = tbody.querySelector('[data-cl-category-chosen]');
            var labels = [];
            var value = 0;
            ordering.forEach(function (offer) {
                if (labels.indexOf(offer.label) < 0) {
                    labels.push(offer.label);
                }
                value += offer.won * offer.net;
            });
            chosen.hidden = isOpen(tbody) || !ordering.length;
            chosen.textContent = chosen.hidden ? '' : '· ' + format(texts.categoryChosen, labels.join(', '), money(value));
        });
    }

    function appendOrders(target, label, list) {
        if (!list.length) {
            return;
        }
        target.appendChild(document.createTextNode(label + ' '));
        list.forEach(function (entry, i) {
            var link = document.createElement('a');
            link.href = entry.chip.getAttribute('data-href');
            link.target = '_blank';
            link.rel = 'noopener';
            link.textContent = entry.chip.getAttribute('data-number');
            target.appendChild(link);
            target.appendChild(document.createTextNode(' (' + format(texts.count, entry.done, entry.total) + ')'
                + (i < list.length - 1 ? ', ' : '. ')));
        });
    }

    function renderCoverage(winner) {
        var partial = [];
        var uncovered = [];
        chips.forEach(function (chip) {
            var order = chip.getAttribute('data-order');
            var total = parseInt(chip.getAttribute('data-total'), 10) || 0;
            var keys = {};
            var providers = {};
            offers.forEach(function (offer) {
                offer.allocations.forEach(function (allocation) {
                    if (allocation.order === order && winner[allocation.key]) {
                        keys[allocation.key] = true;
                        providers[winner[allocation.key].provider] = true;
                    }
                });
            });
            var done = Object.keys(keys).length;
            var split = Object.keys(providers).length > 1;
            chip.classList.toggle('is-ok', total > 0 && done === total);
            chip.classList.toggle('is-warn', done > 0 && done < total);
            chip.classList.toggle('is-bad', done === 0);
            chip.querySelector('[data-cl-coverage-count]').textContent = format(texts.ratio, done, total);
            chip.querySelector('[data-cl-coverage-fill]').style.width = (total > 0 ? Math.round(done / total * 100) : 0) + '%';
            chip.querySelector('.cl-coverage-split').hidden = !split;
            // the label replaces the button's content for screen readers, so it carries the split mark too
            chip.setAttribute('aria-label', format(split ? texts.chipFilterSplit : texts.chipFilter,
                chip.getAttribute('data-number'), done, total));
            if (done === 0) {
                uncovered.push({chip: chip, done: done, total: total});
            } else if (done < total) {
                partial.push({chip: chip, done: done, total: total});
            }
        });

        var alert = form.querySelector('[data-cl-partial]');
        if (!alert) {
            return;
        }
        var covered = Object.keys(winner).length;
        // one order on the page has no chips: say only that the rest stays in the queue
        var singleOrderGap = chips.length === 0 && covered < itemsTotal;
        var shown = covered > 0 && (partial.length > 0 || uncovered.length > 0 || singleOrderGap);
        // the alert is a live region: rebuild it only when what it says changes
        var state = JSON.stringify([shown, singleOrderGap, partial, uncovered].map(function (part) {
            return Array.isArray(part) ? part.map(function (entry) {
                return [entry.chip.getAttribute('data-order'), entry.done, entry.total];
            }) : part;
        }));
        if (state === partialState) {
            return;
        }
        partialState = state;
        var text = alert.querySelector('[data-cl-partial-text]');
        text.textContent = '';
        alert.hidden = !shown;
        if (!shown) {
            return;
        }
        appendOrders(text, texts.partial, partial);
        appendOrders(text, texts.uncovered, uncovered);
        text.appendChild(document.createTextNode(texts.partialEnd));
    }

    // one sentence for screen readers a moment after the last change, instead of every number in the summary
    // announcing itself; the page load stays silent
    function announce(summary) {
        if (!liveRegion) {
            return;
        }
        if (liveText === null) {
            liveText = summary;
            return;
        }
        clearTimeout(liveTimer);
        liveTimer = setTimeout(function () {
            var text = (livePrefix ? livePrefix + ' ' : '') + summary;
            livePrefix = '';
            if (text !== liveText) {
                liveText = text;
                liveRegion.textContent = text;
            }
        }, 600);
    }

    function renderTotals(winner) {
        var cost = 0, profit = 0, sales = 0, byProvider = {};
        offers.forEach(function (offer) {
            if (!offer.check.checked || offer.won === 0) {
                return;
            }
            var value = offer.won * offer.net;
            cost += value;
            profit += offer.wonProfit;
            sales += offer.wonSales;
            byProvider[offer.provider] = (byProvider[offer.provider] || 0) + value;
        });
        var covered = format(texts.count, Object.keys(winner).length, itemsTotal);
        var live = [];
        if (form.querySelector('[data-cl-total-items]')) {
            live.push(format(texts.liveItems, covered));
        }
        live.push(format(texts.liveCost, money(cost)));
        if (profitKnown) {
            live.push(format(texts.liveProfit, signed(profit)));
        }
        announce(live.join(', ') + '.');
        setText('[data-cl-total-items]', covered);
        setText('[data-cl-peek-items]', covered);
        setText('[data-cl-total-cost]', money(cost));
        setText('[data-cl-peek-cost]', money(cost));
        if (profitKnown) {
            setText('[data-cl-total-profit]', signed(profit));
            setText('[data-cl-peek-profit]', signed(profit));
            setText('[data-cl-total-margin]', sales > 0 ? percent(profit / sales) + '%' : '—');
            [form.querySelector('[data-cl-total-profit-tone]'), form.querySelector('[data-cl-peek-profit]')].forEach(function (element) {
                var box = element && element.closest('.cl-profit');
                if (box) {
                    box.classList.toggle('is-ok', profit >= 0 && cost > 0);
                    box.classList.toggle('is-bad', profit < 0);
                }
            });
        }

        var list = form.querySelector('[data-cl-supplier-totals]');
        var rows = all(list, 'li[data-cl-supplier-total]');
        var after = list.querySelector('li.is-earlier, li[data-cl-supplier-empty]');
        rows.sort(function (a, b) {
            return (byProvider[b.getAttribute('data-provider')] || 0) - (byProvider[a.getAttribute('data-provider')] || 0);
        });
        var shown = 0;
        rows.forEach(function (row) {
            var value = byProvider[row.getAttribute('data-provider')] || 0;
            row.hidden = value <= 0;
            shown += value > 0 ? 1 : 0;
            row.querySelector('[data-cl-supplier-value]').textContent = money(value);
            list.insertBefore(row, after);
        });
        list.querySelector('li[data-cl-supplier-empty]').hidden = shown > 0 || !!list.querySelector('li.is-earlier');
    }

    function renderButtons(winner) {
        var none = Object.keys(winner).length === 0;
        reason.hidden = !none;
        needsSelection.forEach(function (button) {
            var help = button.getAttribute('data-help');
            button.disabled = none;
            button.setAttribute('aria-describedby', none ? reason.id + ' ' + help : help);
        });
    }

    function bound(input) {
        var value = input ? parseFloat(String(input.value).replace(',', '.')) : NaN;
        return isNaN(value) ? null : value;
    }

    function labelOf(provider) {
        var offer = offers.filter(function (o) {
            return o.provider === provider;
        })[0];
        return offer ? offer.label : provider;
    }

    // Price slider: 0..1000 positions over the page's net prices, on a log scale when they spread wide (a 112 zł cooler
    // and a 2 900 zł GPU each get room) and linear when they are close. The fields hold the filter; the slider writes
    // rounded amounts into them and follows what is typed.
    var rangeNets = offers.map(function (offer) {
        return offer.net;
    }).filter(function (net) {
        return net > 0;
    });
    var rangeFloor = rangeNets.length ? Math.floor(Math.min.apply(null, rangeNets)) : 0;
    var rangeCeil = rangeNets.length ? Math.ceil(Math.max.apply(null, rangeNets)) : 0;
    var rangeLog = rangeFloor >= 1 && rangeCeil / rangeFloor >= 4;
    var rangeOn = !!rangeBox && rangeCeil > rangeFloor;
    var rangeBands = [];

    function rangePrice(position) {
        var t = position / 1000;
        return rangeLog ? rangeFloor * Math.pow(rangeCeil / rangeFloor, t) : rangeFloor + (rangeCeil - rangeFloor) * t;
    }

    function rangePosition(price) {
        var p = Math.min(rangeCeil, Math.max(rangeFloor, price));
        var t = rangeLog ? Math.log(p / rangeFloor) / Math.log(rangeCeil / rangeFloor) : (p - rangeFloor) / (rangeCeil - rangeFloor);
        return Math.round(t * 1000);
    }

    // amounts a dragged thumb writes into a field: whole złoty, then fives and tens as prices grow
    function roundedPrice(price) {
        if (price < 100) {
            return Math.round(price);
        }
        return price < 1000 ? Math.round(price / 5) * 5 : Math.round(price / 10) * 10;
    }

    function buildRange() {
        if (!rangeOn) {
            return;
        }
        rangeBox.hidden = false;
        var hist = rangeBox.querySelector('[data-cl-range-hist]');
        var counts = [];
        for (var i = 0; i < 20; i++) {
            counts.push(0);
            rangeBands.push(document.createElement('span'));
            hist.appendChild(rangeBands[i]);
        }
        rangeNets.forEach(function (net) {
            counts[Math.min(19, Math.floor(rangePosition(net) / 50))]++;
        });
        var most = Math.max.apply(null, counts);
        rangeBands.forEach(function (band, i) {
            band.style.height = (counts[i] ? 6 + Math.round(counts[i] / most * 26) : 2) + 'px';
        });
        rangeBox.querySelector('[data-cl-range-end-lo]').textContent = format(texts.money, money(rangeFloor));
        rangeBox.querySelector('[data-cl-range-end-hi]').textContent = format(texts.money, money(rangeCeil));
    }

    // the thumbs follow the fields: an empty field puts its thumb at the end of the track
    function syncRange() {
        if (!rangeOn) {
            return;
        }
        var min = bound(minInput);
        var max = bound(maxInput);
        rangeLo.value = String(min === null ? 0 : rangePosition(min));
        rangeHi.value = String(max === null ? 1000 : rangePosition(max));
    }

    function paintRange(min, max) {
        var inRange = offers.filter(function (offer) {
            return (min === null || offer.net >= min) && (max === null || offer.net <= max);
        }).length;
        setText('[data-cl-range-count]', format(texts.rangeCount, inRange, offers.length));
        if (rangeClear) {
            rangeClear.disabled = min === null && max === null;
        }
        if (!rangeOn) {
            return;
        }
        var lo = parseInt(rangeLo.value, 10);
        var hi = parseInt(rangeHi.value, 10);
        var fill = rangeBox.querySelector('[data-cl-range-fill]');
        fill.style.left = (lo / 10) + '%';
        fill.style.right = (100 - hi / 10) + '%';
        rangeBands.forEach(function (band, i) {
            band.classList.toggle('is-in', i * 50 + 50 > lo && i * 50 < hi);
        });
        rangeLo.setAttribute('aria-valuetext', min === null ? texts.rangeNoMin : format(texts.money, money(min)));
        rangeHi.setAttribute('aria-valuetext', max === null ? texts.rangeNoMax : format(texts.money, money(max)));
    }

    function applyFilters() {
        var providers = providerBoxes.filter(function (box) {
            return box.checked;
        }).map(function (box) {
            return box.value;
        });
        var min = bound(minInput);
        var max = bound(maxInput);
        var hiddenTicked = 0;
        offers.forEach(function (offer) {
            offer.visible = (!focusedOrder || offer.allocations.some(function (a) {
                    return a.order === focusedOrder;
                }))
                && (!providers.length || providers.indexOf(offer.provider) >= 0)
                && (min === null || offer.net >= min)
                && (max === null || offer.net <= max);
            // only what will be saved: a ticked offer beaten on every item orders nothing
            if (!offer.visible && offer.check.checked && offer.won > 0) {
                hiddenTicked++;
            }
        });
        categories.forEach(function (tbody) {
            var open = isOpen(tbody);
            var any = false;
            offers.forEach(function (offer) {
                if (offer.category !== tbody) {
                    return;
                }
                offer.row.hidden = !onScreen(offer);
                // compact under its winner only while that winner is on screen right above it
                offer.row.classList.toggle('is-nested', !!offer.parent && onScreen(offer.parent));
                any = any || offer.visible;
            });
            tbody.hidden = !any;
        });
        Object.keys(foldRows).forEach(function (id) {
            var fold = foldRows[id];
            fold.row.hidden = !isOpen(fold.row.parentNode) || fold.parent.row.hidden || !fold.children.some(function (child) {
                return child.visible;
            });
        });
        hiddenNotice.hidden = hiddenTicked === 0;
        setText('[data-cl-hidden-text]', format(texts.hidden, hiddenTicked));
        chips.forEach(function (chip) {
            chip.setAttribute('aria-pressed', String(chip.getAttribute('data-order') === focusedOrder));
        });
        setText('[data-cl-filter-value="supplier"]', providers.length === 0 ? texts.all
            : (providers.length === 1 ? labelOf(providers[0]) : format(texts.many, providers.length)));
        paintRange(min, max);
        setText('[data-cl-filter-value="price"]', min === null && max === null ? texts.any
            : (min === null ? format(texts.to, money(max))
                : (max === null ? format(texts.from, money(min)) : format(texts.range, money(min), money(max)))));
        renderSelectVisible();
    }

    // on screen: passing the filters, in an expanded category and not folded under its winner. A winner the filters
    // hide takes its fold row with it, so the offers under it then show on their own instead of becoming unreachable.
    function onScreen(offer) {
        return offer.visible && isOpen(offer.category) && !(offer.folded && offer.parent.visible);
    }

    function shownOffers() {
        return offers.filter(onScreen);
    }

    function narrowing() {
        return providerBoxes.some(function (box) {
            return box.checked;
        }) || bound(minInput) !== null || bound(maxInput) !== null;
    }

    // one button: it ticks the offers on screen while one of them is unticked; otherwise it clears every ticked offer
    // the filters let through, in a collapsed category or a closed fold too, like the old "Odznacz wszystkie", so
    // "clear, then pick by hand" never saves a suggestion left out of sight. Offers the filters hide are never changed.
    // an unticked offer covered by a cheaper ticked one would be unticked again at once, so only the others count
    function tickable(offer) {
        return !offer.check.checked && !offer.covered;
    }

    function selectVisibleMode() {
        if (shownOffers().some(tickable)) {
            return 'tick';
        }
        return offers.some(function (offer) {
            return offer.visible && offer.check.checked;
        }) ? 'clear' : null;
    }

    function renderSelectVisible() {
        if (!selectVisible) {
            return;
        }
        var mode = selectVisibleMode();
        selectVisible.textContent = mode !== 'clear' ? texts.selectVisible
            : (narrowing() || focusedOrder ? texts.clearMatching : texts.clearAll);
        selectVisible.disabled = mode === null;
    }

    function renderSwap() {
        offers.forEach(function (offer) {
            var line = offer.row.querySelector('[data-cl-swap]');
            var mine = !!swapped && swapped.offer === offer;
            line.hidden = !mine;
            line.textContent = mine ? swapped.text : '';
        });
    }

    function matchVariant() {
        if (!variants.length) {
            return;
        }
        var ticked = offers.filter(function (offer) {
            return offer.check.checked;
        }).map(function (offer) {
            return offer.id;
        }).sort().join(' ');
        var match = variants.filter(function (radio) {
            return (radio.getAttribute('data-groups') || '').split(' ').filter(Boolean).sort().join(' ') === ticked;
        })[0];
        (match || customVariant).checked = true;
    }

    function render() {
        var winner = winners();
        offers.forEach(function (offer) {
            renderOffer(offer, winner);
        });
        reorder(winner);
        renderCategoryCounts();
        renderCoverage(winner);
        renderTotals(winner);
        renderButtons(winner);
        renderSwap();
        applyFilters();
    }

    // One ticked offer per item: an offer whose every item a cheaper ticked offer takes orders nothing, so it is
    // unticked instead of staying ticked as a silent reserve (user's decision of 2026-10-09). An offer that still wins
    // one of its items stays ticked.
    function untickIdle() {
        var winner = winners();
        offers.forEach(function (offer) {
            if (offer.check.checked && offer.allocations.length > 0 && !offer.allocations.some(function (allocation) {
                return winner[allocation.key] === offer;
            })) {
                offer.check.checked = false;
            }
        });
    }

    function changed() {
        untickIdle();
        matchVariant();
        render();
    }

    // E7 swap: ticking an offer that cheaper ticked ones cover unticks every ticked offer that beats it on one of its
    // items, so the chosen one really orders them; their other items go to the next ticked offer or stay uncovered.
    // Items left uncovered that way belong to other rows, often other orders, so the chosen row says which.
    function toggled(offer) {
        swapped = null;
        if (offer.check.checked && offer.covered) {
            var keys = offer.allocations.map(function (allocation) {
                return allocation.key;
            });
            var before = winners();
            var unticked = [];
            offers.forEach(function (other) {
                if (other !== offer && other.check.checked && beats(other, offer) && other.allocations.some(function (allocation) {
                    return keys.indexOf(allocation.key) >= 0;
                })) {
                    other.check.checked = false;
                    if (unticked.indexOf(other.label) < 0) {
                        unticked.push(other.label);
                    }
                }
            });
            var after = winners();
            var lost = Object.keys(before).filter(function (key) {
                return !after[key];
            });
            if (lost.length) {
                swapped = {offer: offer, text: swapText(unticked, lost)};
                livePrefix = swapped.text;
            }
        }
        changed();
    }

    function swapText(labels, lostKeys) {
        if (!chips.length) {
            return format(texts.swapItems, labels.join(', '), lostKeys.length);
        }
        var numbers = [];
        lostKeys.forEach(function (key) {
            var order = key.slice(0, key.indexOf('::'));
            var chip = chips.filter(function (c) {
                return c.getAttribute('data-order') === order;
            })[0];
            var number = chip ? chip.getAttribute('data-number') : order;
            if (numbers.indexOf(number) < 0) {
                numbers.push(number);
            }
        });
        return format(texts.swapOrders, labels.join(', '), numbers.join(', '));
    }

    function setAllExpanded(expanded) {
        all(table, '.cl-group-toggle').forEach(function (toggle) {
            toggle.setAttribute('aria-expanded', String(expanded));
        });
        renderCategoryCounts();
        applyFilters();
    }

    offers.forEach(function (offer) {
        offer.check.addEventListener('change', function () {
            toggled(offer);
        });
    });

    // a click anywhere on the row ticks it, except on its own controls and links
    table.addEventListener('click', function (event) {
        if (event.target.closest('a, input, label, button')) {
            return;
        }
        var row = event.target.closest('tr[data-cl-offer]');
        if (!row) {
            return;
        }
        var offer = offers.filter(function (o) {
            return o.row === row;
        })[0];
        offer.check.checked = !offer.check.checked;
        toggled(offer);
    });

    all(table, '.cl-group-toggle').forEach(function (toggle) {
        toggle.addEventListener('click', function () {
            toggle.setAttribute('aria-expanded', String(toggle.getAttribute('aria-expanded') === 'false'));
            renderCategoryCounts();
            applyFilters();
        });
    });
    if (expandAllButton) {
        expandAllButton.addEventListener('click', function () {
            setAllExpanded(true);
        });
    }
    if (collapseAllButton) {
        collapseAllButton.addEventListener('click', function () {
            setAllExpanded(false);
        });
    }

    variants.forEach(function (radio) {
        radio.addEventListener('change', function () {
            if (!radio.checked) {
                return;
            }
            var ids = (radio.getAttribute('data-groups') || '').split(' ');
            swapped = null;
            offers.forEach(function (offer) {
                offer.check.checked = ids.indexOf(offer.id) >= 0;
            });
            render();
        });
    });

    chips.forEach(function (chip) {
        chip.addEventListener('click', function () {
            var order = chip.getAttribute('data-order');
            focusedOrder = focusedOrder === order ? null : order;
            if (focusedOrder) {
                setAllExpanded(true);
            } else {
                applyFilters();
            }
        });
    });

    if (selectVisible) {
        selectVisible.addEventListener('click', function () {
            var mode = selectVisibleMode();
            swapped = null;
            if (mode === 'tick') {
                shownOffers().filter(tickable).forEach(function (offer) {
                    offer.check.checked = true;
                });
            } else if (mode === 'clear') {
                offers.forEach(function (offer) {
                    if (offer.visible) {
                        offer.check.checked = false;
                    }
                });
            }
            changed();
        });
    }

    // a filter that starts narrowing opens every category, as the old page did, so what it lets through is on screen
    // rather than hidden in collapsed ones; later changes keep whatever the operator folded since
    function filtersChanged() {
        var active = narrowing();
        var starts = active && !filtersActive;
        filtersActive = active;
        if (starts) {
            setAllExpanded(true);
        } else {
            applyFilters();
        }
    }

    providerBoxes.forEach(function (box) {
        box.addEventListener('change', filtersChanged);
    });
    [minInput, maxInput].forEach(function (input) {
        if (input) {
            input.addEventListener('input', function () {
                syncRange();
                filtersChanged();
            });
        }
    });
    if (rangeOn) {
        [rangeLo, rangeHi].forEach(function (thumb) {
            thumb.addEventListener('input', function () {
                // the thumbs never cross: the one being dragged pushes the other
                if (parseInt(rangeLo.value, 10) > parseInt(rangeHi.value, 10)) {
                    (thumb === rangeLo ? rangeHi : rangeLo).value = thumb.value;
                }
                var lo = parseInt(rangeLo.value, 10);
                var hi = parseInt(rangeHi.value, 10);
                minInput.value = lo <= 0 ? '' : String(roundedPrice(rangePrice(lo)));
                maxInput.value = hi >= 1000 ? '' : String(roundedPrice(rangePrice(hi)));
                filtersChanged();
            });
        });
    }
    if (rangeClear) {
        rangeClear.addEventListener('click', function () {
            minInput.value = '';
            maxInput.value = '';
            syncRange();
            filtersChanged();
        });
    }
    // Enter on any field of the selection form would confirm it (implicit submission); buttons keep their Enter
    form.addEventListener('keydown', function (event) {
        var target = event.target;
        if (event.key === 'Enter' && target.tagName === 'INPUT' && ['submit', 'button', 'image', 'reset'].indexOf(target.type) < 0) {
            event.preventDefault();
        }
    });
    form.querySelector('[data-cl-show-all]').addEventListener('click', function () {
        focusedOrder = null;
        providerBoxes.forEach(function (box) {
            box.checked = false;
        });
        if (minInput) {
            minInput.value = '';
        }
        if (maxInput) {
            maxInput.value = '';
        }
        filtersActive = false;
        syncRange();
        applyFilters();
    });
    // The filter menus are <details>: one open at a time, closed by a press anywhere else or by Escape (which hands the
    // focus back to its summary). A press, not a click: a slider thumb dragged past the panel ends its click outside.
    var filterMenus = all(form, 'details[data-cl-filter-menu]');
    filterMenus.forEach(function (menu) {
        menu.addEventListener('toggle', function () {
            if (menu.open) {
                filterMenus.forEach(function (other) {
                    if (other !== menu) {
                        other.open = false;
                    }
                });
            }
        });
    });
    document.addEventListener('pointerdown', function (event) {
        filterMenus.forEach(function (menu) {
            if (menu.open && !menu.contains(event.target)) {
                menu.open = false;
            }
        });
    });
    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') {
            return;
        }
        filterMenus.forEach(function (menu) {
            if (menu.open) {
                menu.open = false;
                menu.querySelector('summary').focus();
            }
        });
    });

    buildRange();
    var filters = form.querySelector('[data-cl-select-filters]');
    if (filters) {
        filters.hidden = false;
    }

    function addHidden(container, name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value == null ? '' : value;
        container.appendChild(input);
    }

    form.addEventListener('submit', function (event) {
        var payload = form.querySelector('[data-cl-payload]');
        payload.textContent = '';
        if (event.submitter && event.submitter.hasAttribute('data-cl-skip')) {
            return;
        }
        var winner = winners();
        var k = 0;
        offers.forEach(function (offer) {
            if (!offer.check.checked) {
                return;
            }
            var won = offer.allocations.filter(function (allocation) {
                return winner[allocation.key] === offer;
            });
            if (!won.length) {
                return;
            }
            var entry = 'entries[' + k + ']';
            var row = offer.row;
            addHidden(payload, entry + '.accepted', 'true');
            addHidden(payload, entry + '.source.name', row.getAttribute('data-name'));
            addHidden(payload, entry + '.source.category', row.getAttribute('data-category'));
            addHidden(payload, entry + '.source.ean', row.getAttribute('data-ean'));
            addHidden(payload, entry + '.source.mfn', row.getAttribute('data-mfn'));
            addHidden(payload, entry + '.source.provider', offer.provider);
            addHidden(payload, entry + '.source.qty', row.getAttribute('data-sourceqty'));
            addHidden(payload, entry + '.source.priceNet', row.getAttribute('data-net'));
            addHidden(payload, entry + '.source.priceGross', row.getAttribute('data-gross'));
            won.forEach(function (allocation, j) {
                var prefix = entry + '.allocations[' + j + ']';
                addHidden(payload, prefix + '.orderId', allocation.order);
                addHidden(payload, prefix + '.orderItemId', allocation.item);
                addHidden(payload, prefix + '.orderItemQty', allocation.element.getAttribute('data-qty'));
                addHidden(payload, prefix + '.orderItemPrice', allocation.element.getAttribute('data-price'));
            });
            k++;
        });
    });

    // back from the next page: the cache keeps the ticks the operator left, the numbers follow them
    window.addEventListener('pageshow', function () {
        syncRange();
        changed();
    });
    changed();
})();
