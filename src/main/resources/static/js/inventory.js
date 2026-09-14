(function () {
    'use strict';

    const page = document.querySelector('[data-inventory-page]');
    if (!page) {
        return;
    }

    const STORAGE_KEY = 'cl.inventory.sources.expanded';
    const MIN_QUERY_LENGTH = 3;
    const SPINNER_DELAY_MS = 150;

    const summarySlot = page.querySelector('[data-inventory-summary]');
    const form = page.querySelector('[data-inventory-search]');
    const input = form.querySelector('#inventory-q');
    const clearButton = form.querySelector('[data-inventory-clear]');
    const submitButton = form.querySelector('[data-inventory-submit]');
    const fieldError = form.querySelector('[data-inventory-field-error]');
    const searchError = page.querySelector('[data-inventory-search-error]');
    const retryButton = page.querySelector('[data-inventory-search-retry]');
    const live = page.querySelector('[data-inventory-live]');
    const emptyTemplate = page.querySelector('[data-inventory-empty-template]');
    const results = page.querySelector('#inventory-results');
    const spinner = page.querySelector('[data-inventory-spinner]');

    let currentSearch = null;
    let failedQuery = null;

    function urlQuery() {
        return (new URLSearchParams(window.location.search).get('q') || '').trim();
    }

    function parseFragment(html, name) {
        const template = document.createElement('template');
        template.innerHTML = html;
        return template.content.querySelector('[data-inventory-fragment="' + name + '"]');
    }

    function fetchFragment(url, name, signal) {
        return fetch(url, {credentials: 'same-origin', headers: {'X-Requested-With': 'XMLHttpRequest'}, signal: signal})
            .then(response => response.text().then(html => {
                const fragment = parseFragment(html, name);
                if (!fragment) {
                    if (response.ok) {
                        // an expired session is redirected to the login page, which answers 200 without the fragment
                        window.location.reload();
                    }
                    throw new Error('Fragment "' + name + '" missing (HTTP ' + response.status + ')');
                }
                return fragment;
            }));
    }

    function loadError(onRetry) {
        const box = document.createElement('div');
        box.className = 'cl-inv-load-error';
        box.setAttribute('role', 'alert');
        const text = document.createElement('span');
        text.textContent = page.dataset.msgLoadError;
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.className = 'cl-inv-link';
        retry.textContent = page.dataset.msgRetry;
        retry.addEventListener('click', onRetry);
        box.append(text, retry);
        return box;
    }

    function readExpanded() {
        try {
            return window.localStorage.getItem(STORAGE_KEY) === '1';
        } catch (error) {
            return false;
        }
    }

    function writeExpanded(expanded) {
        try {
            window.localStorage.setItem(STORAGE_KEY, expanded ? '1' : '0');
        } catch (error) {
            // private windows may refuse storage; the bar then simply starts collapsed
        }
    }

    function initSourcesToggle(root) {
        const toggle = root.querySelector('[data-inventory-sources-toggle]');
        if (!toggle) {
            return;
        }
        const list = root.querySelector('#' + toggle.getAttribute('aria-controls'));
        const apply = expanded => {
            toggle.setAttribute('aria-expanded', String(expanded));
            toggle.classList.toggle('is-expanded', expanded);
            toggle.querySelector('[data-when-collapsed]').hidden = expanded;
            toggle.querySelector('[data-when-expanded]').hidden = !expanded;
            list.hidden = !expanded;
        };
        apply(readExpanded());
        toggle.addEventListener('click', () => {
            const expanded = toggle.getAttribute('aria-expanded') !== 'true';
            apply(expanded);
            writeExpanded(expanded);
        });
    }

    function showWarehouseProducts(summary, text) {
        const target = summary.querySelector('[data-inventory-warehouse-products]');
        if (target && text !== null) {
            target.textContent = text;
        }
    }

    function placeWarehouse(summary, request) {
        const slot = summary.querySelector('[data-inventory-slot="warehouse"]');
        if (!slot) {
            return;
        }
        request
            .then(fragment => {
                slot.replaceWith(fragment);
                const label = fragment.querySelector('[data-warehouse-products-label]');
                showWarehouseProducts(summary, label ? label.textContent : null);
            })
            .catch(() => {
                showWarehouseProducts(summary, '—');
                slot.removeAttribute('aria-busy');
                slot.querySelectorAll('.cl-inv-skeleton, .cl-inv-load-error').forEach(node => node.remove());
                slot.appendChild(loadError(() => {
                    slot.querySelectorAll('.cl-inv-load-error').forEach(node => node.remove());
                    slot.setAttribute('aria-busy', 'true');
                    placeWarehouse(summary, fetchFragment(page.dataset.warehouseUrl, 'warehouse'));
                }));
            });
    }

    function loadSummary() {
        const warehouseUrl = page.dataset.warehouseUrl;
        // both requests start together; the warehouse tile is only placed once its slot exists
        const warehouseRequest = warehouseUrl ? fetchFragment(warehouseUrl, 'warehouse') : null;
        if (warehouseRequest) {
            warehouseRequest.catch(() => undefined);
        }
        summarySlot.setAttribute('aria-busy', 'true');
        fetchFragment(page.dataset.summaryUrl, warehouseUrl ? 'summary' : 'technical')
            .then(fragment => {
                summarySlot.replaceChildren(fragment);
                summarySlot.removeAttribute('aria-busy');
                initSourcesToggle(fragment);
                if (warehouseRequest) {
                    placeWarehouse(fragment, warehouseRequest);
                }
            })
            .catch(() => {
                summarySlot.removeAttribute('aria-busy');
                summarySlot.replaceChildren(loadError(loadSummary));
            });
    }

    function showFieldError() {
        fieldError.hidden = false;
        input.classList.add('is-invalid');
        input.setAttribute('aria-invalid', 'true');
    }

    function hideFieldError() {
        fieldError.hidden = true;
        input.classList.remove('is-invalid');
        input.removeAttribute('aria-invalid');
    }

    function updateControls() {
        submitButton.disabled = input.value.trim().length === 0;
        clearButton.hidden = input.value.length === 0;
        if (input.value.trim().length >= MIN_QUERY_LENGTH) {
            hideFieldError();
        }
    }

    function setBusy(busy) {
        results.setAttribute('aria-busy', String(busy));
        if (!busy) {
            spinner.hidden = true;
        }
    }

    function announce(root) {
        const state = root.querySelector('[data-announce]');
        live.textContent = state ? state.getAttribute('data-announce') : '';
    }

    function showEmptyState() {
        const fragment = emptyTemplate.content.firstElementChild.cloneNode(true);
        results.replaceChildren(fragment);
        live.textContent = '';
    }

    function search(query, pushHistory) {
        if (currentSearch) {
            currentSearch.abort();
        }
        const controller = new AbortController();
        currentSearch = controller;
        searchError.hidden = true;
        setBusy(true);
        // a spinner flashing for a fast response is noisier than a short wait without one
        const spinnerTimer = window.setTimeout(() => {
            spinner.hidden = false;
        }, SPINNER_DELAY_MS);
        fetchFragment(page.dataset.searchUrl + '?q=' + encodeURIComponent(query), 'results', controller.signal)
            .then(fragment => {
                results.replaceChildren(fragment);
                failedQuery = null;
                if (pushHistory) {
                    window.history.pushState({q: query}, '', page.dataset.pageUrl + '?q=' + encodeURIComponent(query));
                }
                announce(fragment);
                const heading = fragment.querySelector('[data-inventory-results-heading]');
                if (heading) {
                    heading.focus();
                }
            })
            .catch(error => {
                if (error.name === 'AbortError') {
                    return;
                }
                failedQuery = query;
                searchError.hidden = false;
            })
            .finally(() => {
                window.clearTimeout(spinnerTimer);
                if (currentSearch !== controller) {
                    return;
                }
                currentSearch = null;
                setBusy(false);
            });
    }

    function sortOffers(button) {
        const table = button.closest('[data-inventory-offers]');
        const body = table.querySelector('[data-inventory-sortable]');
        const header = button.closest('th');
        const ascending = header.getAttribute('aria-sort') !== 'ascending';
        table.querySelectorAll('th[aria-sort]').forEach(th => th.setAttribute('aria-sort', 'none'));
        header.setAttribute('aria-sort', ascending ? 'ascending' : 'descending');
        const attribute = 'data-sort-' + button.getAttribute('data-sort-key');
        const rows = Array.from(body.querySelectorAll('tr[' + attribute + ']'));
        rows.sort((left, right) => {
            const a = left.getAttribute(attribute);
            const b = right.getAttribute(attribute);
            const order = attribute === 'data-sort-source'
                ? a.localeCompare(b, undefined, {sensitivity: 'base'})
                : parseFloat(a) - parseFloat(b);
            return ascending ? order : -order;
        });
        rows.forEach(row => body.appendChild(row));
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        const query = input.value.trim();
        if (query.length < MIN_QUERY_LENGTH) {
            showFieldError();
            input.focus();
            return;
        }
        hideFieldError();
        search(query, urlQuery() !== query);
    });

    input.addEventListener('input', updateControls);

    clearButton.addEventListener('click', () => {
        if (currentSearch) {
            currentSearch.abort();
        }
        input.value = '';
        updateControls();
        hideFieldError();
        searchError.hidden = true;
        showEmptyState();
        if (urlQuery()) {
            window.history.pushState({q: ''}, '', page.dataset.pageUrl);
        }
        input.focus();
    });

    retryButton.addEventListener('click', () => {
        const query = failedQuery || input.value.trim();
        if (query.length >= MIN_QUERY_LENGTH) {
            search(query, urlQuery() !== query);
        }
    });

    results.addEventListener('click', event => {
        const button = event.target.closest('button[data-sort-key]');
        if (button) {
            sortOffers(button);
        }
    });

    window.addEventListener('popstate', () => {
        const query = urlQuery();
        input.value = query;
        updateControls();
        hideFieldError();
        searchError.hidden = true;
        if (query.length >= MIN_QUERY_LENGTH) {
            search(query, false);
        } else {
            if (currentSearch) {
                currentSearch.abort();
            }
            showEmptyState();
        }
    });

    updateControls();
    loadSummary();
})();
