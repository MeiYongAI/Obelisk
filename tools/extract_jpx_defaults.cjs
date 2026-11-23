// Extract only the three settings declarations from the pinned local sources.
// Usage: node tools/extract_jpx_defaults.cjs /path/to/jpx20261111.js /path/to/JPX-PLUS.user.js /path/to/output-directory [--check]
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const sources = [
  ['jpx', '2026.11.11', '766eb2573dfcdd0f407ba2f250a2b9e1ae76b607126dc008cee483a4b4c519a9'],
  ['jpx-plus', '2026.11.20.1', '4c7717955147ebe8d48fe466caba867cfc4920b93ad81e3a0abdca7a34786d4c'],
];
const [base, plus, output, flag] = process.argv.slice(2);
assert(base && plus && output && !output.startsWith('-') && (!flag || flag === '--check'), 'Expected two pinned userscripts, output directory and optional --check');
const assets = path.resolve(output);
for (const [i, [id, version, hash]] of sources.entries()) {
  const src = fs.readFileSync([base, plus][i], 'utf8').replace(/\r\n/g, '\n');
  assert.equal(crypto.createHash('sha256').update(src).digest('hex'), hash, 'Unreviewed source: ' + id);
  assert.equal(src.match(/^\/\/ @version\s+(\S+)/m)?.[1], version);
  const declaration = name => {
    const match = src.match(new RegExp(`^(?:const|let) ${name} = (\\{[\\s\\S]*?^\\});$`, 'm'));
    assert(match, 'Missing declaration: ' + name);
    return '(' + match[1] + ')';
  };
  const helper = src.match(/^function createDefaultRecoveryRules\([^)]*\) \{[\s\S]*?^\}/m)?.[0] ?? '';
  for (const [file, name] of [['battle','defaultCfgBattle'], ['stats','defaultCfgStats'], ['shortcuts','KEYBINDS']]) {
    const value = vm.runInNewContext((file === 'battle' ? helper : '') + '\n' + declaration(name), {}, {timeout: 1000});
    const json = JSON.stringify(value, null, 2) + '\n';
    const target = path.join(assets, id, file + '.json');
    if (flag) assert.equal(fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n'), json, target);
    else { fs.mkdirSync(path.dirname(target), {recursive: true}); fs.writeFileSync(target, json); }
    if (file === 'battle') console.log(id, Object.fromEntries(Object.entries(value).filter(([, v]) => Array.isArray(v?.rules)).map(([key, v]) => [key, v.rules.length])));
  }
}
