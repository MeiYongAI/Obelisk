function createBattleDocument(itemSlots, effectIcons, log, useItem) {
    'use strict';
    const hoverNames = new Map();
    const info = {innerHTML: 'Battle Time'};
    const items = itemSlots.map(({id, name}) => {
        const hover = 'battle.set_infopane_item(' + id + ')';
        hoverNames.set(hover, name);
        return {
            getAttribute(attr) { return attr === 'onmouseover' ? hover : null; },
            click() { useItem(name); }
        };
    });
    const icons = effectIcons.map(stem => ({
        getAttribute(attr) { return attr === 'src' ? '/y/e/' + stem + '.png' : null; }
    }));
    const cells = log.map(textContent => ({textContent}));
    const unavailable = selector => { throw Error('Unsupported battle document query: ' + selector); };
    const textlog = {
        querySelectorAll(selector) { return selector === 'tr > td' ? cells.slice() : unavailable(selector); }
    };
    function querySelectorAll(selector) {
        switch (selector) {
            case '.bti3 > div[onclick][onmouseover]': return items.slice();
            case '#pane_effects img': return icons.slice();
            case '#textlog tr > td': return cells.slice();
            case '#textlog': return [textlog];
            case '.btii': return [info];
            default: return unavailable(selector);
        }
    }
    return {
        querySelectorAll,
        querySelector(selector) { return querySelectorAll(selector)[0] ?? null; },
        createElement(tag) {
            if (tag !== 'div') return unavailable(tag);
            let hover;
            return {
                setAttribute(attr, value) {
                    if (attr !== 'onclick' || !hoverNames.has(value)) throw Error('Unsupported battle document handler');
                    hover = value;
                },
                click() { if (hover !== undefined) info.innerHTML = hoverNames.get(hover); }
            };
        }
    };
}
