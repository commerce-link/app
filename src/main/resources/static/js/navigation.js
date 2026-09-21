(function () {
    const STORAGE_KEY = 'cl.nav.collapsed';
    const DESKTOP_QUERY = window.matchMedia('(min-width: 1024px)');

    const sidebar = document.getElementById('clSidebar');
    const scrim = document.getElementById('clScrim');
    const drawerToggle = document.getElementById('clDrawerToggle');
    const collapseToggle = document.getElementById('clCollapseToggle');
    const userToggle = document.getElementById('clUserToggle');
    const userMenu = document.getElementById('clUserMenu');
    const notificationsToggle = document.getElementById('clNotificationsToggle');
    const notificationsMenu = document.getElementById('clNotificationsMenu');
    const popovers = [];

    let lastFocused = null;

    function applyDrawerLabel(open) {
        drawerToggle.setAttribute('aria-label',
            open ? drawerToggle.dataset.closeLabel : drawerToggle.dataset.openLabel);
    }

    function openDrawer() {
        lastFocused = document.activeElement;
        document.body.classList.add('cl-drawer-open');
        drawerToggle.setAttribute('aria-expanded', 'true');
        applyDrawerLabel(true);
        const first = sidebar.querySelector('.cl-nav-item');
        if (first) {
            first.focus();
        }
    }

    function closeDrawer() {
        if (!document.body.classList.contains('cl-drawer-open')) {
            return;
        }
        document.body.classList.remove('cl-drawer-open');
        drawerToggle.setAttribute('aria-expanded', 'false');
        applyDrawerLabel(false);
        if (lastFocused) {
            lastFocused.focus();
            lastFocused = null;
        }
    }

    function trapFocus(event) {
        if (event.key !== 'Tab' || !document.body.classList.contains('cl-drawer-open')) {
            return;
        }
        const focusable = sidebar.querySelectorAll('a[href], button:not([disabled])');
        if (focusable.length === 0) {
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    function bindPopover(toggle, menu, onOpen) {
        if (!toggle || !menu) {
            return;
        }
        const popover = {
            close: function (returnFocus) {
                if (menu.hidden) {
                    return;
                }
                menu.hidden = true;
                toggle.setAttribute('aria-expanded', 'false');
                if (returnFocus) {
                    toggle.focus();
                }
            }
        };
        popovers.push(popover);

        toggle.addEventListener('click', function (event) {
            event.stopPropagation();
            if (!menu.hidden) {
                popover.close(false);
                return;
            }
            popovers.forEach(function (other) {
                if (other !== popover) {
                    other.close(false);
                }
            });
            menu.hidden = false;
            toggle.setAttribute('aria-expanded', 'true');
            if (onOpen) {
                onOpen(menu);
            }
        });
        document.addEventListener('click', function (event) {
            if (!menu.contains(event.target)) {
                popover.close(false);
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && !menu.hidden) {
                popover.close(true);
            }
        });
    }

    let notificationsRequestId = 0;

    function loadNotifications(menu) {
        const loading = menu.querySelector('[data-notifications-loading]');
        const failure = menu.querySelector('[data-notifications-error]');
        const body = menu.querySelector('[data-notifications-body]');
        // a request counter so a slow, superseded response cannot overwrite what a later open already rendered
        const requestId = ++notificationsRequestId;
        loading.hidden = false;
        failure.hidden = true;
        body.replaceChildren();
        fetch(notificationsToggle.dataset.dropdownHref, { credentials: 'same-origin' })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Notifications dropdown answered ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                if (requestId !== notificationsRequestId) {
                    return;
                }
                const doc = new DOMParser().parseFromString(html, 'text/html');
                // an expired session answers with the login page, which must not end up inside the panel
                const panel = doc.querySelector('[data-notifications-dropdown]');
                if (!panel) {
                    throw new Error('Notifications dropdown answered with an unexpected page');
                }
                const here = window.location.pathname + window.location.search;
                panel.querySelectorAll('input[name="redirect"]').forEach(function (input) {
                    input.value = here;
                });
                loading.hidden = true;
                body.replaceChildren(panel);
            })
            .catch(function () {
                if (requestId !== notificationsRequestId) {
                    return;
                }
                loading.hidden = true;
                body.replaceChildren();
                failure.hidden = false;
            });
    }

    if (drawerToggle && sidebar) {
        applyDrawerLabel(false);
        drawerToggle.addEventListener('click', function () {
            document.body.classList.contains('cl-drawer-open') ? closeDrawer() : openDrawer();
        });
        sidebar.addEventListener('click', function (event) {
            if (event.target.closest('.cl-nav-item')) {
                closeDrawer();
            }
        });
        document.addEventListener('keydown', trapFocus);
    }

    if (scrim) {
        scrim.addEventListener('click', closeDrawer);
    }

    DESKTOP_QUERY.addEventListener('change', function (event) {
        if (event.matches) {
            closeDrawer();
        }
    });

    function applyCollapseLabel(collapsed) {
        collapseToggle.setAttribute('aria-label',
            collapsed ? collapseToggle.dataset.expandLabel : collapseToggle.dataset.collapseLabel);
    }

    if (collapseToggle) {
        applyCollapseLabel(document.documentElement.classList.contains('cl-nav-collapsed'));
        collapseToggle.addEventListener('click', function () {
            const collapsed = document.documentElement.classList.toggle('cl-nav-collapsed');
            try {
                localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
            } catch (error) {
            }
            applyCollapseLabel(collapsed);
        });
    }

    bindPopover(userToggle, userMenu);
    bindPopover(notificationsToggle, notificationsMenu, loadNotifications);

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeDrawer();
        }
    });
})();
