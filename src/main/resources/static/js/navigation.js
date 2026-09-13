(function () {
    const STORAGE_KEY = 'cl.nav.collapsed';
    const DESKTOP_QUERY = window.matchMedia('(min-width: 1024px)');

    const sidebar = document.getElementById('clSidebar');
    const scrim = document.getElementById('clScrim');
    const drawerToggle = document.getElementById('clDrawerToggle');
    const collapseToggle = document.getElementById('clCollapseToggle');
    const userToggle = document.getElementById('clUserToggle');
    const userMenu = document.getElementById('clUserMenu');

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

    function closeUserMenu() {
        if (!userMenu || userMenu.hidden) {
            return;
        }
        userMenu.hidden = true;
        userToggle.setAttribute('aria-expanded', 'false');
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

    if (userToggle && userMenu) {
        userToggle.addEventListener('click', function (event) {
            event.stopPropagation();
            const opening = userMenu.hidden;
            userMenu.hidden = !opening;
            userToggle.setAttribute('aria-expanded', String(opening));
        });
        document.addEventListener('click', function (event) {
            if (!userMenu.contains(event.target)) {
                closeUserMenu();
            }
        });
    }

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeDrawer();
            closeUserMenu();
        }
    });
})();
