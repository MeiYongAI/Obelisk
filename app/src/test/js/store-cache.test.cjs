const assert = require('node:assert/strict');
const { createHash, webcrypto } = require('node:crypto');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scripts = ['downloads.js', 'store.js'].map(file =>
    readFileSync(path.join(__dirname, '../../main/assets/store', file), 'utf8'));

function makeStore(storage = {}) {
    const requests = [];
    const context = vm.createContext({
        URL, TextEncoder, crypto: webcrypto,
        host: {
            readCatalog: () => storage.snapshot,
            writeCatalog: snapshot => { storage.snapshot = snapshot; },
        },
        GM_xmlhttpRequest(request) { requests.push(request); },
    });
    for (const script of scripts) vm.runInContext(script, context);
    return { service: vm.runInContext('createJpxIntegratedStore(host).service', context), requests };
}

function published(title) {
    const envelope = { schemaVersion: 1, title, kind: 'stats', description: '', world: 'Any', scriptVersion: '1',
        config: { statsVersion: 20260109, combatRows: [] } };
    const source = JSON.stringify(envelope);
    const sha256 = createHash('sha256').update(source).digest('hex');
    const entry = { id: 1, title, kind: 'stats', description: '', world: 'Any', scriptVersion: '1', author: 'tester',
        updatedAt: '2026-01-01', sha256, configPath: 'catalog/configs/1-' + sha256 + '.json',
        issueUrl: 'https://github.com/MeiYongAI/JPX-PLUS-Configs/issues/1' };
    return { source, entry };
}

const first = published('First');
const next = published('Next');
const catalog = entry => ({ schemaVersion: 1, updatedAt: entry.updatedAt, entries: [entry] });
const snapshot = (entry, age = 0) => JSON.stringify({ at: Date.now() - age, source: JSON.stringify(catalog(entry)) });
const respond = (req, body) => req.onload({ status: 200, finalUrl: req.url,
    responseText: typeof body === 'string' ? body : JSON.stringify(body) });

test('simultaneous opens use one request and a new store can reopen offline', async () => {
    const storage = {};
    const { service, requests } = makeStore(storage);
    const one = service.catalog(), two = service.catalog();
    assert.equal(requests.length, 1);
    respond(requests[0], catalog(first.entry));
    (await one).entries.length = 0;
    assert.equal((await two).entries.length, 1);
    const reopened = makeStore(storage);
    assert.equal((await reopened.service.catalog()).entries[0].title, 'First');
    assert.equal(reopened.requests.length, 0);
});

test('a stale directory is usable before the network responds and survives failed updates', async () => {
    const original = snapshot(first.entry, 120000);
    const storage = { snapshot: original };
    const { service, requests } = makeStore(storage);
    const titles = [];
    const browse = () => service.catalog({ onCached(value) { titles.push(value.entries[0].title); } });
    const offline = browse();
    assert.deepEqual(titles, ['First']);
    requests[0].onerror();
    await assert.rejects(offline, { code: 'networkError' });
    const invalid = browse();
    respond(requests[1], { schemaVersion: 2, updatedAt: '', entries: [] });
    await assert.rejects(invalid, { code: 'invalidResponse' });
    assert.equal(storage.snapshot, original);
    const recovered = browse();
    assert.deepEqual(titles, ['First', 'First', 'First']);
    respond(requests[2], catalog(next.entry));
    assert.equal((await recovered).entries[0].title, 'Next');
    assert.equal((await makeStore(storage).service.catalog()).entries[0].title, 'Next');
});

test('manual refresh reads the branch and wins over a late background response', async () => {
    const storage = { snapshot: snapshot(first.entry, 120000) };
    const { service, requests } = makeStore(storage);
    const background = service.catalog();
    const manual = service.catalog({ refresh: true });
    assert.equal(requests.length, 2);
    assert.equal(new URL(requests[1].url).hostname, 'api.github.com');
    assert.equal(requests[1].headers['Cache-Control'], 'no-cache');
    respond(requests[1], catalog(next.entry));
    await manual;
    respond(requests[0], catalog(first.entry));
    await background;
    assert.equal((await makeStore(storage).service.catalog()).entries[0].title, 'Next');
});

test('a corrupt disk snapshot can be replaced from the network', async () => {
    const storage = { snapshot: '{' };
    const { service, requests } = makeStore(storage);
    const pending = service.catalog({ onCached: assert.fail });
    respond(requests[0], catalog(first.entry));
    assert.equal((await pending).entries[0].title, 'First');
    assert.equal((await makeStore(storage).service.catalog()).entries[0].title, 'First');
});

test('details retry after integrity failure, share verified downloads, and refetch each new version', async () => {
    const { service, requests } = makeStore();
    const corrupt = service.load(first.entry);
    respond(requests[0], first.source + ' ');
    await assert.rejects(corrupt, { code: 'integrityFailed' });
    const one = service.load(first.entry), two = service.load(first.entry);
    assert.equal(requests.length, 2);
    respond(requests[1], first.source);
    (await one).config.combatRows.push({ id: 'local' });
    assert.equal((await two).config.combatRows.length, 0);
    assert.equal((await service.load(first.entry)).title, 'First');
    await assert.rejects(service.load({ ...first.entry, title: 'Wrong metadata' }), { code: 'integrityFailed' });
    const updated = service.load(next.entry);
    assert.equal(requests.length, 3);
    respond(requests[2], next.source);
    assert.equal((await updated).title, 'Next');
});
