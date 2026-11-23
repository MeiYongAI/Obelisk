function evaluateTurn(facts, code) {
    'use strict';
    const {choices, monsters, playerLevel, damageTiers, ...jpx} = facts;
    let result;
    function pick(kind, name, monster) {
        if (result) return false;
        const id = choices.findIndex(([k, n, aim]) => k === kind && n === name && (aim === null ? monster == null : monster === monsters[aim]));
        if (id < 0) return false;
        result = {kind: 'action', id};
        return true;
    }
    const attack = m => pick('attack', '', m);
    const cast = (name, m) => !!m && pick('spell', name, m);
    const toggle = name => pick('toggle', name.toLowerCase());
    for (const m of monsters) m.click = () => attack(m);
    const bosses = monsters.filter(m => m.type !== 'Normal');
    jpx.monstersObj = {monsters, activeMonsters: monsters.filter(m => m.isAlive), bosses, activeBosses: bosses.filter(m => m.isAlive)};
    jpx.doAttackGoNext = attack;
    jpx.doSpellGoNext = cast;
    jpx.doToggleGoNext = toggle;
    // These handlers can also be called by imported JavaScript. Never silently discard nested conditions.
    function handler(fn) {
        return (rule, m) => {
            if (rule.conditions?.length) throw Error('Nested actionHandlers conditions are not supported');
            return fn(rule, m);
        };
    }
    jpx.actionHandlers = {
        normalAttack: handler((r, m) => attack(m)), skill: handler((r, m) => cast(r.name, m)),
        spellDebuff: handler((r, m) => cast(r.name, m)), spellSupport: handler(r => pick('spell', r.name)),
        spellDamage: handler((r, m) => {
            if (!m || !/^T[1-3]$/.test(r.name)) return cast(r.name, m);
            if (!damageTiers) throw Error('Spell school unavailable');
            return cast(damageTiers[Number(r.name[1]) - 1], m);
        }), item: handler(r => pick('item', r.name)),
        toggle: handler(r => r.name === 'Spirit' && r.toggled === jpx.spiritStatus ? false : toggle(r.name)),
        stop: handler(r => { if (result) return false; result = {kind: 'stop', message: String(r.customMessage ?? '')}; return true; })
    };
    const storage = {getItem(key) {
        if (key === jpx.prefix + 'playerLevel' + jpx.isekaiSuffix) {
            return playerLevel == null ? null : String(playerLevel);
        }
        throw Error('Unsupported localStorage key');
    }};
    const handled = Function('jpx','localStorage', '"use strict";\n' + code)(jpx, storage);
    if (handled?.then) throw Error('Async rules are not supported');
    return JSON.stringify(result ?? (handled === true ? {kind:'stop',message:''} : {kind:'continue'}));
}
