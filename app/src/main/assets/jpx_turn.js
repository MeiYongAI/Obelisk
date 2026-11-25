// Battle rule helpers adapted from JPX-PLUS. This realm belongs to a single configuration/encounter.
const __obeliskEvaluate = (() => {
    'use strict';
    const storage = new Map(), session = new Map();
    function storageView(map) {
        return Object.freeze({
            getItem: key => map.get(String(key)) ?? null,
            setItem: (key, value) => map.set(String(key), String(value)),
            removeItem: key => map.delete(String(key)),
            clear: () => map.clear(),
            key: i => [...map.keys()][i] ?? null,
            get length() { return map.size; }
        });
    }
    const localStorage = storageView(storage), sessionStorage = storageView(session);
    let executeRow, counts;
    function frame(facts, newRound) {
        const {choices, monsters, playerLevel, difficulty, totalCounts, damageTiers, log, itemSlots, effectIcons, ...jpx} = facts;
        const {prefix, isekaiSuffix, battleType, towerFloor, roundInfo, monsterData, playerEffectsObj,
            vitals, spiritStatus, spellCooldowns, itemCooldowns} = jpx;
        const allMonsterInfo = facts.allMonsterInfo || {}, monstersEffects = facts.monstersEffects || {};
        const monstersObj = {monsters, activeMonsters: monsters.filter(m => m.isAlive),
            bosses: monsters.filter(m => m.type !== 'Normal'), activeBosses: monsters.filter(m => m.type !== 'Normal' && m.isAlive)};
        if (!counts || newRound) counts = {};
        for (const key of Object.keys(counts)) delete counts[key];
        const actionCounts = Object.assign(counts, facts.actionCounts);
        for (const [key, value] of [['playerLevel', playerLevel], ['difficulty', difficulty]]) {
            const k = prefix + key + isekaiSuffix;
            if (value == null) storage.delete(k); else storage.set(k, String(value));
        }
        let result, conditionsObj;
        function pick(kind, name, aim) {
            if (result) return false;
            const id = choices.findIndex(([k,n,i]) => k === kind && n === name && i === aim);
            if (id < 0) return false;
            result = {kind:'action', id};
            return true;
        }
        const useItem = name => pick('item', name, null);
        const document = createBattleDocument(itemSlots, effectIcons, log, useItem);
        for (const m of monsters) m.click = () => pick('attack', '', m.index);
        function doAttackGoNext(m) {
            if (!m?.isAlive || typeof m.click !== 'function' || result) return false;
            m.click();
            return !!result;
        }
        function doSpellGoNext(name, m) {
            if (!m?.isAlive || result) return false;
            if (monsters.includes(m) && pick('spell', name, m.index)) return true;
            return pick('spell', name, null);
        }
        const doToggleGoNext = name => pick('toggle', name.toLowerCase(), null);
        const jpxUtils = {
            inRange: (n, [lo,hi]) => n >= lo && n <= hi,
            isEmpty: o => Object.keys(o).length === 0,
            daysSince: d => {
                const start = Date.parse(d + 'T00:00:00Z');
                return Number.isFinite(start) ? Math.floor(Date.now() / 86400000) - Math.floor(start / 86400000) : null;
            },
            toRegExp(s) {
                const match = s.match(/^\/(.*?)\/([dgimsuvy]*)$/);
                if (match) { try { return new RegExp(match[1], match[2]); } catch { return null; } }
                return new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i');
            }
        };
        function checkEffectTurns(arr, effects, ignored = false) {
            const range = arr.slice(0, 2);
            const matches = name => {
                let turns = effects[name]?.turns ?? -1;
                if (turns === 'permanent') turns = 9999;
                return isNaN(+turns) || jpxUtils.inRange(+turns, range);
            };
            return ignored ? !arr.slice(2).some(matches) : arr.slice(2).every(matches);
        }

        function checkEffectStacks(arr, effects) {
            const range = arr.slice(0, 2);
            return arr.slice(2).every(name => {
                const stack = effects[name]?.stack ?? -1;
                return isNaN(+stack) || jpxUtils.inRange(+stack, range);
            });
        }

        function initConditions() {
            let { monsters, activeMonsters, bosses, activeBosses } = monstersObj;
            const checkActions = (arr, matches) => {
                const range = arr.slice(0, 2);
                return arr.slice(2).every(action => {
                    const tier = action.match(/^T(\d)$/)?.[1];
                    const name = tier ? damageTiers?.[tier - 1] : action;
                    return !!name && matches(name, range);
                });
            };

            const generalHandlers = {
                world: arr => arr.includes(!isekaiSuffix ? 'Persistent' : 'Isekai'),
                pLevel: range => jpxUtils.inRange(parseInt(localStorage.getItem(prefix + 'playerLevel' + isekaiSuffix) || 1), range),
                pMaxSpellType: arr => arr.includes(facts.effectiveElement),
                battleTypes: arr => arr.includes(battleType),
                difficulty: arr => arr.includes(localStorage.getItem(prefix + 'difficulty' + isekaiSuffix) || 'PFUDOR'),
                roundCurrent: range => jpxUtils.inRange(roundInfo.current, range),
                roundLeft: range => jpxUtils.inRange(roundInfo.total - roundInfo.current, range),
                roundTotal: range => jpxUtils.inRange(roundInfo.total, range),
                floor: range => (battleType !== 'Tower' || jpxUtils.inRange(towerFloor, range)),
                pActionCooldown: arr => checkActions(arr, (name, range) => {
                    const cooldown = spellCooldowns[name] ?? itemCooldowns[name];
                    return jpxUtils.inRange(cooldown === '-' ? 9999 : cooldown, range);
                }),
                pActionHistory: arr => checkActions(arr, (name, range) => {
                    const history = actionCounts.history;
                    const end = Math.min(history.length - 1, Math.floor(range[1] - 1));
                    for (let index = Math.max(0, Math.ceil(range[0] - 1)); index <= end; index++) {
                        if (history[index] === name) return true;
                    }
                    return false;
                }),
                pActionCounts: arr => checkActions(arr, (name, range) => jpxUtils.inRange(actionCounts[name] ?? 0, range)),
                pTotalActionCounts: arr => checkActions(arr, (name, range) =>
                    jpxUtils.inRange(totalCounts[name] || 0, range)),
                pHP: range => jpxUtils.inRange(vitals.hpPercentage, range),
                pMP: range => jpxUtils.inRange(vitals.mpPercentage, range),
                pSP: range => jpxUtils.inRange(vitals.spPercentage, range),
                pOC: range => jpxUtils.inRange(vitals.oc, range),
                pSpiritStatus: flag => flag === spiritStatus,
                pEffects: arr => checkEffectTurns(arr, playerEffectsObj),
                pIgnoredEffects: arr => checkEffectTurns(arr, playerEffectsObj, true),
                pEffectStacks: arr => checkEffectStacks(arr, playerEffectsObj),
                monsters: range => jpxUtils.inRange(monsters.length, range),
                activeMonsters: range => jpxUtils.inRange(activeMonsters.length, range),
                defeatedMonsters: range => jpxUtils.inRange(monsters.length - activeMonsters.length, range),
                bosses: range => jpxUtils.inRange(bosses.length, range),
                activeBosses: range => jpxUtils.inRange(activeBosses.length, range),
                defeatedBosses: range => jpxUtils.inRange(bosses.length - activeBosses.length, range),
                mLevel: range => jpxUtils.inRange(monsters[0].level, range),
                mWithoutEffects: arr => {
                    let range = arr.slice(0, 2);
                    let effects = arr.slice(2);
                    return jpxUtils.inRange(activeMonsters.filter(activeMonster => effects.some(effect => activeMonster.effectObj[effect]?.turns == null)).length, range);
                },
            };

            const targetHandlers = {
                tName: (str, t) => {
                    let regex = jpxUtils.toRegExp(str);
                    return !regex || regex.test(t.name);
                },
                tTypes: (arr, t) => arr.includes(t.type),
                tClasses: (arr, t) => arr.includes(allMonsterInfo?.[`mkey_${(t.index + 1) % 10}`]?.monsterClass),
                tPowerLevel: (range, t) => jpxUtils.inRange(allMonsterInfo?.[`mkey_${(t.index + 1) % 10}`]?.plvl, range),
                tIndex: (range, t) => jpxUtils.inRange(t.index, range.map(n => n = n > 0 ? n - 1 : n === 0 ? 0 : monsters.length + n)),
                tHP: (range, t) => jpxUtils.inRange(t.hpPercentage, range),
                tMP: (range, t) => jpxUtils.inRange(t.mpPercentage, range),
                tSP: (range, t) => jpxUtils.inRange(t.spPercentage, range),
                tEffects: (arr, target) => checkEffectTurns(arr, target.effectObj),
                tIgnoredEffects: (arr, target) => checkEffectTurns(arr, target.effectObj, true),
                tEffectStacks: (arr, target) => checkEffectStacks(arr, target.effectObj),
                tDaysSinceUpdate: (range, t) => {
                    let lastUpdate = allMonsterInfo?.[`mkey_${(t.index + 1) % 10}`]?.lastUpdate;
                    let deltaDays = lastUpdate ? jpxUtils.daysSince(lastUpdate) : 9999;
                    return !jpxUtils.isEmpty(allMonsterInfo) && jpxUtils.inRange(deltaDays, range);
                },
            };

            return { generalHandlers, targetHandlers };
        }

        function checkConditions(conditions = [], target, checkGlobal = true, checkTarget = true) {
            let { monsters, activeMonsters } = monstersObj;

            conditionsObj ||= initConditions();
            let { generalHandlers, targetHandlers } = conditionsObj;

            return conditions.every(condition => {
                let { key, value, offset = [0, 0], matched = [1, 1] } = condition;

                if (key in generalHandlers && checkGlobal) {
                    return generalHandlers[key](value) === true;
                }

                if (key in targetHandlers && checkTarget) {
                    if (!target) return false;
                    let successCount = 0;

                    for (let i = target.index + offset[0]; i <= target.index + offset[1]; i++) {
                        if (i < 0 || i > monsters.length - 1) continue;
                        let m = activeMonsters.find(activeMonster => activeMonster.index === i);
                        if (m && targetHandlers[key](value, m) === true) successCount++;
                    }

                    return jpxUtils.inRange(successCount, matched);
                }

                return true;
            });
        }


        function doSpellsDebuffGoNext({name, targetCount = 3, tailSkip = 0, bottomUp = false, maxAtFirst = Infinity, minMonstersLeft = 10, conditions}) {
            let { monsters, activeMonsters } = monstersObj;

            if (spellCooldowns[name] !== 0) return false;

            let maxDo = 0;
            if (monsters.length - activeMonsters.length <= 1) {
                maxDo = maxAtFirst;
            } else if (activeMonsters.length <= minMonstersLeft) {
                maxDo = Infinity;
            }
            if ((actionCounts[name] ?? 0) >= maxDo) return false;

            let firstIndex = activeMonsters[0].index;
            let lastIndex = activeMonsters.at(-1).index;
            let dir = !bottomUp ? 1 : -1;
            const startIndex = bottomUp ? monsters.length - 1 - lastIndex : firstIndex;
            const limit = bottomUp ? monsters.length - firstIndex : lastIndex + 1;
            const endIndex = Math.min(tailSkip >= 0 ? limit - tailSkip : startIndex + Math.abs(tailSkip), monsters.length);
            if (endIndex <= 0) return false;

            let undebuffedObj = {};
            const isUnDebuffed = (i) => {
                return undebuffedObj[i] ??= (
                    !!monsters[i]?.isAlive &&
                    checkConditions(conditions, monsters[i], false)
                );
            };
            const getIndex = (j) => !bottomUp ? j : monsters.length - 1 - j;
            const castAtEdge = (i, j) => {
                if ((targetCount === 2 && !bottomUp || targetCount === 3) && i === startIndex && monsters[j + dir]?.isAlive) j += dir;
                else if ((targetCount === 2 && bottomUp || targetCount === 3) && i === endIndex - 1 && monsters[j - dir]?.isAlive) j -= dir;
                doSpellGoNext(name, monsters[j]);
                return true;
            };

            if (targetCount >= 3) {
                for (let i = startIndex + 1; i < endIndex - 1; i++) {
                    let j = getIndex(i);
                    if (!isUnDebuffed(j - 1) || !isUnDebuffed(j) || !isUnDebuffed(j + 1)) continue;
                    doSpellGoNext(name, monsters[j]);
                    return true;
                }
            }
            if (targetCount >= 2) {
                for (let i = startIndex; i < endIndex; i++) {
                    let j = getIndex(i);
                    if (!isUnDebuffed(j - 1) || !isUnDebuffed(j)) continue;
                    return castAtEdge(i, j);
                }
            }
            if (targetCount >= 3) {
                for (let i = startIndex + 1; i < endIndex - 1; i++) {
                    let j = getIndex(i);
                    if (!isUnDebuffed(j - 1) || !monsters[j]?.isAlive || !isUnDebuffed(j + 1)) continue;
                    doSpellGoNext(name, monsters[j]);
                    return true;
                }
            }
            for (let i = startIndex; i < endIndex; i++) {
                let j = getIndex(i);
                if (!isUnDebuffed(j)) continue;
                return castAtEdge(i, j);
            }

            return false;
        }
        const targeted = (r,m) => !!m && checkConditions(r.conditions,m) && doSpellGoNext(r.name,m);
        const actionHandlers = {
            target(action, monster) {
                const { priorityRule, conditions } = action;
                if (!checkConditions(conditions, monster, true, false)) return null;

                let { activeMonsters } = monstersObj;
                monstersObj.sorted ??= {};

                const getSorted = fn => monstersObj.sorted[priorityRule] ??= [...activeMonsters].sort(
                    priorityRule.includes('Low to High') ? (a, b) => fn(a) - fn(b) : (a, b) => fn(b) - fn(a));

                let list = activeMonsters;
                switch (priorityRule) {
                    case 'Bottom Up':
                        list = monstersObj.sorted[priorityRule] ??= [...activeMonsters].reverse();
                        break;
                    case 'Current HP Low to High':
                    case 'Current HP High to Low':
                        list = getSorted(m => monsterData[m.index].maxHP * m.hpPercentage);
                        break;
                    case 'Current HP Percent Low to High':
                    case 'Current HP Percent High to Low':
                        list = getSorted(m => m.hpPercentage);
                        break;
                }

                return list.find(activeMonster => checkConditions(conditions, activeMonster, false)) ?? null;
            },
            normalAttack: (r,m) => checkConditions(r.conditions,m) && doAttackGoNext(m),
            spellSupport: (r,m) => checkConditions(r.conditions,m) && pick('spell',r.name,null),
            item: (r,m) => checkConditions(r.conditions,m) && useItem(r.name),
            skill: targeted, spellDebuff: targeted,
            spellDamage(r,m) {
                const tier = /^T([1-3])$/.exec(r.name);
                if (tier && !damageTiers) throw Error('Spell school unavailable');
                return targeted({...r, name:tier ? damageTiers[Number(tier[1])-1] : r.name}, m);
            },
            toggle: (r,m) => (r.name !== 'Spirit' || r.toggled !== spiritStatus) && checkConditions(r.conditions,m) && doToggleGoNext(r.name),
            smartDebuff: (r,m) => checkConditions(r.conditions,m,true,false) && doSpellsDebuffGoNext(r),
            stop(r,m) {
                if (result || !checkConditions(r.conditions,m)) return false;
                result = {kind:'stop', message:String(r.customMessage ?? '')}; return true;
            }
        };
        Object.assign(jpx, {monstersObj, allMonsterInfo, monstersEffects, actionCounts, actionHandlers,
            doAttackGoNext, doSpellGoNext, doToggleGoNext, doSpellsDebuffGoNext});
        return codes => {
            result = null;
            for (let offset = 0; offset < codes.length; offset++) {
                try {
                    const handled = Function('jpx','localStorage','sessionStorage','document','"use strict";\n' + codes[offset])
                        (jpx, localStorage, sessionStorage, document);
                    if (handled?.then) throw Error('Async rules are not supported');
                    if (result || handled === true) return JSON.stringify({...result, kind:result?.kind ?? 'stop', offset});
                } catch (error) {
                    return JSON.stringify({kind:'error', message:String(error?.message ?? error).slice(0,160), offset});
                }
            }
            return '{"kind":"continue"}';
        };
    }
    return (facts, codes, newRound) => {
        if (facts) executeRow = frame(facts, newRound);
        return executeRow(codes);
    };
})();
