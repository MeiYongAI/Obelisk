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
            tag.textContent = label;
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
    return { renderTags, compareTypes };
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
    const { language, shareId } = storeCall('init');
    const zh = language?.startsWith('zh');
    const words = zh ? {
        library: '配置库', share: '分享配置', download: '导出文件', empty: '配置库中还没有配置', cancel: '取消',
        already: '配置库已有此配置', update: '更新已有配置', copy: '保存副本', error: '无法读取或保存配置',
        remove: '删除本地副本', confirmRemove: '删除此副本及其关联的本地战斗配置？',
    } : {
        library: 'Local library', share: 'Share config', download: 'Export file', empty: 'No saved configurations', cancel: 'Cancel',
        already: 'Already in your library', update: 'Update existing copy', copy: 'Save another copy',
        error: 'Could not read or save the configuration',
        remove: 'Delete local copy', confirmRemove: 'Delete this copy and its linked local battle profiles?',
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
    const setLayout = () => document.body.classList.toggle('jpx-settings-mobile', window.innerWidth < 720);
    window.addEventListener('resize', setLayout);
    setLayout();
    const dark = matchMedia('(prefers-color-scheme: dark)');
    const setTheme = () => { document.documentElement.dataset.jpxTheme = dark.matches ? 'dark' : 'light'; };
    dark.addEventListener('change', setTheme);
    setTheme();
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
        const nav = toolbar.querySelector('.jpx-store-navigation');
        const share = document.createElement('button');
        share.textContent = words.library;
        share.addEventListener('click', async () => {
            share.disabled = true;
            try {
                const entries = listShareConfigs();
                const id = await choose(words.library, entries.length ? '' : words.empty,
                    entries.map(item => [item.id, item.envelope.title]));
                if (id) {
                    const local = getShareConfig(id);
                    if (!local) { notice(words.error); return; }
                    const actions = [['share', words.share], ['export', words.download]];
                    if (local.source) actions.push(['remove', words.remove]);
                    const action = await choose(local.envelope.title, '', actions);
                    if (action === 'share') render(local);
                    if (action === 'export') {
                        const filename = local.envelope.title.replace(/[^\p{L}\p{N}_. -]/gu, '_').slice(0, 100) || 'jpx-config';
                        await exportFile(filename + '.json', JSON.stringify(local.envelope, null, 2));
                    }
                    if (action === 'remove' && await choose(words.confirmRemove, local.envelope.title, [[true, words.remove]])) {
                        storeCall('removeCopy', { id, revision: local.revision });
                        render();
                    }
                }
            } catch { notice(words.error); }
            finally { share.disabled = false; }
        });
        nav.append(share);
    }
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
