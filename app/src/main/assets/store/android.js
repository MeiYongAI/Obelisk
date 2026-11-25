'use strict';

// Transport adapter for the locally bundled JPX-PLUS store. All requests go through
// the Android host; community content never becomes a document loaded in this WebView.
function storeCall(method, payload = {}) {
    const result = JSON.parse(window.ObeliskStore.call(method, JSON.stringify(payload)));
    if (!result.ok) throw Object.assign(new Error(result.error), { code: result.error });
    return result.value;
}
const GM_getValue = (key, fallback) => storeCall('gmGet', { key }) ?? fallback;
const GM_setValue = (key, value) => storeCall('gmSet', { key, value });
const GM_deleteValue = key => storeCall('gmDelete', { key });
const GM_listValues = () => storeCall('gmKeys');
const pendingExports = new Map();
window.obeliskExported = (ticket, saved) => {
    pendingExports.get(ticket)?.(saved);
    pendingExports.delete(ticket);
};
function exportFile(filename, text) {
    const ticket = storeCall('download', { filename, text });
    return new Promise(resolve => pendingExports.set(ticket, resolve));
}

// The upstream config manager supplies these two hooks to its integrated store.
// Catalog entries already list modes. Only downloaded envelopes contain full rule groups.
function storeCategoryHooks(labels, styles, scenes, text) {
    const worlds = ['Persistent', 'Isekai', 'Any'];
    const modes = styles.flatMap(style => scenes.map(scene => style + '_' + scene));
    const cache = new WeakMap();
    function classify(entry) {
        if (cache.has(entry)) return cache.get(entry);
        let included = entry.mode ? [entry.mode] : entry.modes || [];
        if (entry.kind === 'battle' && entry.config) {
            included = modes.filter(key => {
                const group = entry.config[key];
                if (!group) return false;
                const groups = Array.isArray(group.rules) ? ['rules'] : ['supports', 'attacks'];
                return Object.keys(group).some(name => (groups.includes(name) || name.startsWith('kb_')) &&
                    Array.isArray(group[name]) && group[name].length);
            });
        }
        const unique = values => [...new Set(values)];
        const parts = included.map(mode => {
            const split = mode.lastIndexOf('_');
            return [mode.slice(0, split), mode.slice(split + 1)];
        });
        const spec = {
            modes: included, styles: unique(parts.map(pair => pair[0])), scenes: unique(parts.map(pair => pair[1])),
            elements: unique(included.map(mode => entry.config?.[mode]?.jpxPlusExecution?.element)
                .filter(element => ['fire', 'cold', 'wind', 'elec', 'holy', 'dark'].includes(element))),
        };
        spec.rank = [entry.kind === 'stats' ? styles.length + 2 : spec.styles.length === 1 ? styles.indexOf(spec.styles[0]) : styles.length,
            spec.scenes.length === 1 ? scenes.indexOf(spec.scenes[0]) : scenes.length, worlds.indexOf(entry.world)];
        cache.set(entry, spec);
        return spec;
    }
    function renderTags(entry, choose) {
        const spec = classify(entry);
        const group = document.createElement('div');
        group.className = 'jpx-config-tags';
        function add(label, kind, value, interactive = false, title = '') {
            const active = interactive && choose;
            const tag = document.createElement(active ? 'button' : 'span');
            if (active) {
                tag.type = 'button';
                tag.addEventListener('click', () => choose(kind, value, label));
            }
            tag.className = 'jpx-config-tag';
            tag.dataset.tagKind = kind;
            tag.dataset.tagValue = value;
            if (active) {
                const caption = document.createElement('span');
                caption.textContent = label;
                tag.append(caption);
            } else tag.textContent = label;
            if (title) tag.title = title;
            group.append(tag);
        }
        if (entry.kind === 'stats') add(labels.stats, 'kind', 'stats', true);
        else {
            for (const [values, kind, mixed] of [[spec.styles, 'style', 'multiStyle'], [spec.scenes, 'scenario', 'multiBattle']]) {
                if (values.length > 3) add(labels[mixed], kind, 'mixed', false, values.map(text).join(' · '));
                else for (const value of values) add(text(value), kind, value, true);
            }
            for (const value of spec.elements) add(labels[value], 'element', value);
            if (entry.kind === 'battle') add(spec.modes.length ? labels.groupCount.replace('${count}', String(spec.modes.length)) :
                labels.allBattle, 'kind', 'battle');
        }
        add(text({Persistent: 'persistent', Isekai: 'isekai', Any: 'anyWorld'}[entry.world]), 'world', entry.world, entry.world !== 'Any');
        return group;
    }
    function compareTypes(a, b) {
        const left = classify(a).rank, right = classify(b).rank;
        return left[0] - right[0] || left[1] - right[1] || left[2] - right[2];
    }
    // Use the same two-tone weapon shapes as the native Abilities screen.
    const marks = {
        sword: ['M23 5L28 4L26 12L15 23L11 20L8 23L10 25L7 28L4 25L8 21L5 18L7 16L11 18Z', 'M24 8L23 12L13 21L12 19Z'],
        greatsword: ['M22 4L28 4L27 11L18 20L18 22L15 24L12 21L8 26L6 28L3 25L10 19L7 16L10 13L13 14Z', 'M24 6L24 11L15 18L14 16Z'],
        blades: ['M4 4L10 7L20 19L22 17L25 20L22 23L26 26L24 28L19 24L16 25L14 23L16 21L5 10ZM28 4L27 11L9 27L6 26L5 24L10 20L8 17L10 15L13 17L23 7Z', 'M24 9L23 12L15 19L14 18ZM7 8L10 10L15 16L13 16Z'],
        staff: ['M7 25L18 12L17 7L21 3L26 4L28 8L25 13L21 14L10 28L6 28Z', 'M21 6L24 5L26 8L23 11L20 10Z'],
        impact: ['M16 4L19 13L26 8L23 16L29 18L21 21L24 27L16 23L9 28L10 21L3 22L9 16L5 9L13 12Z', 'M18 11L17 16L21 16L13 23L15 18L11 18Z'],
        stats: ['M5 17H11V27H5ZM13 10H19V27H13ZM21 5H27V27H21Z', 'M5 17H11V20H5ZM13 10H19V13H13ZM21 5H27V8H21Z'],
        book: ['M4 7Q11 5 16 9Q21 5 28 7V25Q21 23 16 27Q11 23 4 25Z', 'M15 11H17V24H15ZM7 10Q10 9 13 11V13Q10 11 7 12Z'],
    };
    const weapons = { OneHanded: 'sword', '1H_Mage': 'sword', TwoHanded: 'greatsword', '2H_Mage': 'greatsword',
        DualWielding: 'blades', DW_Mage: 'blades', NitenIchiryu: 'blades', NI_Mage: 'blades', Staff: 'staff', Unarmed: 'impact' };
    function renderMark(entry) {
        const styles = classify(entry).styles;
        const kind = entry.kind === 'stats' ? 'stats' : styles.length === 1 ? weapons[styles[0]] || 'book' : 'book';
        const mark = document.createElement('span');
        mark.className = 'store-mark';
        mark.dataset.mark = kind;
        mark.setAttribute('aria-hidden', 'true');
        const [body, accent] = marks[kind];
        mark.innerHTML = '<svg viewBox="0 0 32 32"><path d="' + body + '"/><path d="' + accent + '"/></svg>';
        return mark;
    }
    return { renderTags, compareTypes, renderMark };
}

const pendingRequests = new Map();
let nextRequestId = 0;
window.obeliskReply = (id, result) => {
    const handler = pendingRequests.get(String(id));
    if (!handler) return;
    pendingRequests.delete(String(id));
    clearTimeout(handler.timer);
    if (result.error) handler.request.onerror?.();
    else handler.request.onload?.(result);
};
function GM_xmlhttpRequest(request) {
    const id = String(++nextRequestId);
    const end = event => {
        if (!pendingRequests.has(id)) return;
        clearTimeout(pendingRequests.get(id).timer);
        pendingRequests.delete(id);
        window.ObeliskStore.abort(id);
        request[event]?.();
    };
    const timer = setTimeout(() => end('ontimeout'), request.timeout || 25000);
    pendingRequests.set(id, { request, timer });
    try {
        window.ObeliskStore.request(id, JSON.stringify({ method: request.method, url: request.url,
            headers: request.headers, body: request.data ?? null }));
    } catch (error) {
        clearTimeout(timer);
        pendingRequests.delete(id);
        throw error;
    }
    return { abort: () => end('onabort') };
}

(() => {
    const { language, shareId, theme, darkTheme } = storeCall('init');
    const nativeNavigation = typeof window.ObeliskStore.navigation === 'function';
    document.body.classList.toggle('native-navigation', nativeNavigation);
    window.obeliskTheme = value => {
        const root = document.documentElement;
        root.dataset.jpxTheme = value.dark ? 'dark' : 'light';
        for (const [role, color] of Object.entries(value.colors || {})) root.style.setProperty('--jpx-theme-' + role, color);
    };
    window.obeliskTheme(theme || { dark: darkTheme });
    const zh = language?.startsWith('zh');
    const words = zh ? {
        cancel: '取消',
        already: '配置库已有此配置', update: '更新已有配置', copy: '保存副本', error: '无法读取或保存配置',
    } : {
        cancel: 'Cancel',
        already: 'Already in your library', update: 'Update existing copy', copy: 'Save another copy', error: 'Could not read or save the configuration',
    };
    const pack = storeLanguages[zh ? 'zh' : 'en'];
    document.documentElement.lang = zh ? 'zh-CN' : 'en';
    const store = createJpxIntegratedStore({ jpxI18N: pack,
        createCategoryHooks: (styles, scenes, text) => storeCategoryHooks(pack.cM, styles, scenes, text),
    }, key => {
        const [group, name] = key.split('.');
        return pack[group]?.[name];
    });
    const panel = document.getElementById('panel_configStore');
    const toolbar = document.getElementById('storeToolbar');
    const setLayout = () => {
        document.body.classList.toggle('jpx-settings-mobile', window.innerWidth < 720);
        document.body.classList.toggle('store-short', (window.visualViewport?.height || window.innerHeight) <= 480);
    };
    window.addEventListener('resize', setLayout);
    window.visualViewport?.addEventListener('resize', setLayout);
    setLayout();
    const status = document.getElementById('localStatus');
    let dispose;
    let noticeTimer;
    function notice(message) {
        clearTimeout(noticeTimer);
        status.textContent = message;
        status.hidden = false;
        noticeTimer = setTimeout(() => { status.hidden = true; }, 4500);
    }
    function choose(title, message, choices) {
        return new Promise(resolve => {
            const dialog = document.createElement('dialog');
            const header = document.createElement('h2');
            header.textContent = title;
            dialog.append(header);
            dialog.setAttribute('aria-label', title);
            if (message) {
                const body = document.createElement('p');
                body.textContent = message;
                dialog.append(body);
            }
            let result = null;
            for (const [value, label] of [...choices, [null, words.cancel]]) {
                const button = document.createElement('button');
                button.textContent = label;
                button.addEventListener('click', () => { result = value; dialog.close(); });
                dialog.append(button);
            }
            dialog.addEventListener('close', () => { dialog.remove(); resolve(result); }, { once: true });
            document.body.append(dialog);
            dialog.showModal();
        });
    }
    const listShareConfigs = () => storeCall('listProfiles');
    const getShareConfig = id => storeCall('getProfile', { id });
    function render(entry) {
        dispose?.();
        panel.replaceChildren();
        dispose = store.render(panel, {
            listShareConfigs, getShareConfig,
            onNavigate: nativeNavigation ? (page, user) => window.ObeliskStore.navigation(page, user || '') : undefined,
            savedVersions: () => storeCall('savedVersions'),
            removeCopy: (id, revision) => storeCall('removeCopy', { id, revision }),
            async saveConfig(envelope, remote) {
                const existing = storeCall('findSource', { source: remote.source });
                let choice = 'new';
                if (existing) {
                    choice = await choose(words.already, existing.envelope.title, [['update', words.update], ['copy', words.copy]]);
                    if (!choice) return false;
                }
                storeCall('saveConfig', { envelope, remote, choice, expectedId: existing?.id, expectedRevision: existing?.revision });
                return true;
            },
            download: exportFile,
            confirm: async (title, message, action) => !!await choose(title, message, [[true, action]]),
            notify: notice,
        }, { toolbarHost: toolbar, showBrowseTab: true, ...(entry && { share: entry.envelope,
            localSource: { id: entry.id, revision: entry.revision, title: entry.envelope.title }, onBack: () => render() }) });
    }
    window.obeliskNavigate = destination => {
        if (!document.querySelector('dialog[open]')) dispose?.navigate(destination);
    };
    document.addEventListener('click', event => {
        const link = event.target.closest?.('a[href]');
        if (!link) return;
        event.preventDefault();
        if (link.protocol === 'https:' && link.hostname === 'github.com') storeCall('openExternal', { url: link.href });
    });
    window.obeliskBack = () => {
        const dialog = document.querySelector('dialog[open]');
        if (dialog) { dialog.close(); return true; }
        const filters = toolbar.querySelector('.jpx-store-filter-toggle[aria-expanded=true]');
        if (document.body.classList.contains('jpx-settings-mobile') && filters) { filters.click(); filters.focus(); return true; }
        return dispose?.back() || false;
    };
    window.addEventListener('pagehide', () => dispose?.(), { once: true });
    try { render(shareId ? getShareConfig(shareId) : null); }
    catch { notice(words.error); }
})();
