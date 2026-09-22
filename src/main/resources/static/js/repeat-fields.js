// Repeatable groups of fields (the parcels of a package template). A container [data-cl-repeat="prefix"] holds items
// [data-cl-repeat-item]; a <template data-cl-repeat-template> inside it is a blank item with @INDEX@ and @NUMBER@
// placeholders (not __X__: Thymeleaf reads that as a preprocessing expression). [data-cl-repeat-add] appends one, [data-cl-repeat-remove] removes its item. After each change the items
// are renumbered, so the posted names stay a gapless list (prefix[0].x, prefix[1].x) and the ids, the aria references
// and the variant group names (data-cl-repeat-id + '-<index>-' + name) stay unique.
// Without JavaScript both buttons stay hidden and the page offers one spare blank item instead (inside <noscript>).
// [data-cl-repeat-min] on the container is the fewest items it keeps (1 by default; 0 for optional ones, e.g. attachments).
(function () {
    'use strict';

    function items(container) {
        return Array.prototype.filter.call(container.querySelectorAll('[data-cl-repeat-item]'), function (item) {
            return item.closest('[data-cl-repeat]') === container;
        });
    }

    function renumber(container) {
        var prefix = container.getAttribute('data-cl-repeat');
        var idPrefix = container.getAttribute('data-cl-repeat-id');
        var all = items(container);
        var min = parseInt(container.getAttribute('data-cl-repeat-min') || '1', 10);
        all.forEach(function (item, index) {
            item.querySelectorAll('[name]').forEach(function (field) {
                field.name = field.name.replace(new RegExp('^' + prefix + '\\[\\d+\\]'), prefix + '[' + index + ']');
            });
            // The variant attributes carry the same prefix-<index>- name, so the fields of a group keep following
            // their own choice after a renumbering (two groups sharing a name would follow the first select).
            ['id', 'for', 'aria-describedby', 'aria-labelledby', 'data-cl-variant-select', 'data-cl-variant-group',
                'data-cl-variant-when'].forEach(function (attribute) {
                item.querySelectorAll('[' + attribute + ']').forEach(function (element) {
                    element.setAttribute(attribute, element.getAttribute(attribute)
                        .replace(new RegExp(idPrefix + '-\\d+-', 'g'), idPrefix + '-' + index + '-'));
                });
            });
            item.querySelectorAll('[data-cl-repeat-number]').forEach(function (number) {
                number.textContent = String(index + 1);
            });
            item.querySelectorAll('[data-cl-repeat-remove]').forEach(function (button) {
                button.hidden = all.length <= min;
                // "Remove parcel 2", not two identical "Remove parcel" buttons for a screen reader.
                var label = button.getAttribute('data-cl-repeat-label');
                if (label) {
                    button.setAttribute('aria-label', label.replace('@N@', String(index + 1)));
                }
            });
        });
        container.querySelectorAll('[data-cl-repeat-add]').forEach(function (button) {
            button.hidden = false;
        });
    }

    function init(root) {
        root.querySelectorAll('[data-cl-repeat]').forEach(function (container) {
            // A form swapped in by async-form.js is parsed without scripting, so its <noscript> spare item became real
            // fields; left in, it would share its index with the next item added here.
            container.querySelectorAll('noscript').forEach(function (spare) {
                spare.remove();
            });
            renumber(container);
        });
    }

    document.addEventListener('click', function (event) {
        var add = event.target.closest('[data-cl-repeat-add]');
        var remove = event.target.closest('[data-cl-repeat-remove]');
        if (add) {
            var container = add.closest('[data-cl-repeat]');
            var template = container.querySelector('template[data-cl-repeat-template]');
            var index = items(container).length;
            var html = template.innerHTML.replace(/@INDEX@/g, String(index)).replace(/@NUMBER@/g, String(index + 1));
            var holder = document.createElement('div');
            holder.innerHTML = html;
            var item = holder.firstElementChild;
            template.parentNode.insertBefore(item, template);
            renumber(container);
            // Announced before the focus is placed: variant-fields.js may still switch parts of the new group off.
            item.dispatchEvent(new CustomEvent('cl:repeat-added', { bubbles: true }));
            var first = item.querySelector('input, select, textarea');
            if (first) {
                first.focus();
            }
        } else if (remove) {
            var owner = remove.closest('[data-cl-repeat]');
            var removed = remove.closest('[data-cl-repeat-item]');
            var siblings = items(owner);
            var position = siblings.indexOf(removed);
            var next = siblings[position + 1] || siblings[position - 1];
            removed.remove();
            renumber(owner);
            // Focus moves to the neighbouring group without saying why; the status names what went.
            var status = owner.querySelector('[data-cl-repeat-status]');
            if (status) {
                status.textContent = status.getAttribute('data-template').replace('@N@', String(position + 1));
            }
            var focus = (next && next.querySelector('input, select, textarea')) || owner.querySelector('[data-cl-repeat-add]');
            if (focus) {
                focus.focus();
            }
        }
    });

    document.addEventListener('cl:form-replaced', function (event) {
        init(event.target);
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            init(document);
        });
    } else {
        init(document);
    }
})();
