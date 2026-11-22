"""Extract string dictionaries without evaluating the source userscript."""
from pathlib import Path
import json
import re


class JsRegex:
    def __init__(self, pattern, flags):
        self.pattern, self.flags = pattern, flags


class Expression:
    def __init__(self, text):
        self.text = text


class LiteralReader:
    """Read the userscript's data literals; never evaluate JavaScript."""
    def __init__(self, source, variable):
        self.source = source
        self.pos = re.search(r'const\s+' + variable + r'\s*=\s*', source).end()

    def skip(self):
        while True:
            match = re.match(r'\s+|//[^\n]*|/\*[\s\S]*?\*/', self.source[self.pos:])
            if not match:
                return
            self.pos += len(match.group())

    def string(self):
        quote = self.source[self.pos]
        self.pos += 1
        result = []
        while self.pos < len(self.source):
            char = self.source[self.pos]
            self.pos += 1
            if char == quote:
                return ''.join(result)
            if char != '\\':
                result.append(char)
                continue
            char = self.source[self.pos]
            self.pos += 1
            if char in 'ux':
                size = 4 if char == 'u' else 2
                result.append(chr(int(self.source[self.pos:self.pos + size], 16)))
                self.pos += size
            elif char not in '\r\n':
                result.append({'n': '\n', 'r': '\r', 't': '\t', 'b': '\b', 'f': '\f', 'v': '\v'}.get(char, char))
        raise ValueError('Unterminated string')

    def regex(self):
        self.pos += 1
        start = self.pos
        in_class = False
        while self.pos < len(self.source):
            char = self.source[self.pos]
            if char == '\\':
                self.pos += 2
                continue
            if char == '[':
                in_class = True
            elif char == ']':
                in_class = False
            elif char == '/' and not in_class:
                pattern = self.source[start:self.pos].replace(r'\/', '/')
                self.pos += 1
                flags = re.match('[gimsuy]*', self.source[self.pos:]).group()
                self.pos += len(flags)
                return JsRegex(pattern, flags)
            self.pos += 1
        raise ValueError('Unterminated regex')

    def expression(self):
        start = self.pos
        stack = []
        while self.pos < len(self.source):
            char = self.source[self.pos]
            if char in "'\"`":
                self.string()
                continue
            if self.source.startswith(('//', '/*'), self.pos):
                self.skip()
                continue
            if char in '([{':
                stack.append(char)
            elif char in ')]}':
                if not stack:
                    break
                stack.pop()
            elif char in ',;' and not stack:
                break
            self.pos += 1
        return Expression(self.source[start:self.pos].strip())

    def value(self):
        self.skip()
        char = self.source[self.pos]
        if char in "'\"":
            return self.string()
        if char == '/':
            return self.regex()
        if char == '{':
            self.pos += 1
            result = {}
            while True:
                self.skip()
                if self.source[self.pos] == '}':
                    self.pos += 1
                    return result
                if self.source[self.pos] in "'\"":
                    key = self.string()
                else:
                    key = re.match(r'[A-Za-z_$][\w$]*', self.source[self.pos:]).group()
                    self.pos += len(key)
                self.skip()
                assert self.source[self.pos] == ':', self.source[self.pos:self.pos+80]
                self.pos += 1
                result[key] = self.value()
                self.skip()
                if self.source[self.pos] == ',':
                    self.pos += 1
                elif self.source[self.pos] != '}':
                    raise ValueError(self.source[self.pos:self.pos+80])
        if char == '[':
            self.pos += 1
            result = []
            while True:
                self.skip()
                if self.source[self.pos] == ']':
                    self.pos += 1
                    return result
                result.append(self.value())
                self.skip()
                if self.source[self.pos] == ',':
                    self.pos += 1
                elif self.source[self.pos] != ']':
                    raise ValueError(self.source[self.pos:self.pos+80])
        expression = self.expression()
        if re.fullmatch(r'\d+', expression.text):
            return int(expression.text)
        return expression


# Native labels and the source userscript's battle-log sentences, represented as data.
# Only explicitly typed game-term captures are translated; actor/persona names stay literal.
NATIVE_TERMS = {
    'Primary Stat Bonus Limit': '主要属性奖励上限', 'Trophy Value Offered': '累计献祭奖杯价值', 'Rules': '规则',
    'Equip Winner': '装备中奖者', 'Core Winner': '核心中奖者', 'Winner': '获奖者',
    'Current Drawing': '本期彩票',
    'Character': '角色', 'Bazaar': '集市', 'Battle': '战斗', 'Armory': '军械库',
    'Stat Fusion': '属性融合', 'Arena': '竞技场', 'Persistent': '永久世界', 'Isekai': '异世界',
    'Credits': '信用点', 'Credit': '信用点', 'Attribute Points': '属性点',
    'Attributes': '属性', 'Proficiencies': '熟练度', 'Abilities': '技能',
    'Equipment': '装备', 'Inventory': '物品栏', 'Persona': '人格', 'Personas': '人格',
    'Battle Rounds': '战斗回合', 'World Level': '世界等级', 'Monster LVL': '怪物等级',
    'World Seeds': '世界种子', 'Difficulty': '难度', 'Health': '生命', 'Magic': '魔力',
    'N/A': '不适用',
    'Elemental': '元素', 'Protection': '保护', 'Warding': '护佑', 'Swiftness': '迅捷', 'Focus': '专注',
    'hit': '击中', 'hits': '击中', 'crit': '暴击', 'crits': '暴击',
    'glance': '擦过', 'glances': '擦过', 'glanced': '擦过', 'dodges': '闪避', 'parries': '招架',
    'partially parries': '部分招架', 'block and partially parry': '格挡并部分招架',
    'block and parry': '格挡并招架', 'partially block and parry': '部分格挡并招架',
    'you block and partially parry the attack': '你格挡并部分招架了攻击',
    'you partially block and partially parry the attack': '你部分格挡并部分招架了攻击',
    'you partially block and resist the attack': '你部分格挡并抵抗了攻击',
    'you partially block the attack': '你部分格挡了攻击', 'you partially parry the attack': '你部分招架了攻击',
    'you resist the attack': '你抵抗了攻击', 'you take': '你受到',
    'Spirit Stance Disabled': '灵动架势已关闭。', 'Spirit Stance Engaged': '灵动架势已开启。',
    'Spirit Stance Exhausted': '灵动架势已耗尽。', 'You have escaped from the battle.': '你已逃离战斗。',
    'Spark of Life fails due to insufficient Spirit!': '生命火花因灵力不足而失效！',
    'You have been defeated.': '你已被击败。', 'You are Victorious!': '你胜利了！',
    'The potential of your equipment has grown!': '你的装备潜能提升了！',
    'Spark of Life saves you from the brink of defeat!': '生命火花将你从败北边缘救回！',
}

# pattern, Chinese template, game-term capture indices (all other captures remain raw)
BATTLE_RULES = [
    (r'^Time Bonus: Recovered ([\d,]+) HP, ([\d,]+) MP and ([\d,]+) SP\.$', '快速回答奖励：恢复 $1 点生命、$2 点法力和 $3 点灵力。', []),
    (r'^Your spike shield hits (.+) for ([\d,]+) points of (.+) damage\.$', '你的尖刺盾击中 $1，造成 $2 点$3伤害。', [3]),
    (r'^Your offhand attack ((?:\d+x-)?(?:hits|crits|glances)) (.+?), which (partially parries|parries), causing ([\d,]+) points of (.+) damage\.$', '你的副手攻击$1 $2，但对方$3，造成 $4 点$5伤害。', [1, 3, 5]),
    (r'^Your offhand attack ((?:\d+x-)?(?:hits|crits|glances)) (.+?), causing ([\d,]+) points of (.+) damage\.$', '你的副手攻击$1 $2，造成 $3 点$4伤害。', [1, 4]),
    (r'^You ((?:\d+x-)?(?:hit|crit|glance)) (.+?), which (partially parries|parries), causing ([\d,]+) points of (.+) damage\.$', '你$1 $2，但对方$3，造成 $4 点$5伤害。', [1, 3, 5]),
    (r'^You ((?:\d+x-)?(?:hit|crit|glance)) (.+?), causing ([\d,]+) points of (.+) damage\.$', '你$1 $2，造成 $3 点$4伤害。', [1, 4]),
    (r'^(.+) (hits|crits|glances) you; (.+), and take ([\d,]+) (?:points of )?(.+) damage\.$', '$1$2你；$3，受到 $4 点$5伤害。', [2, 3, 5]),
    (r'^(.+) (hits|crits|glances) you, causing ([\d,]+) points of (.+) damage\.$', '$1$2你，造成 $3 点$4伤害。', [2, 4]),
    (r'^(.+?) ((?:\d+x-)?(?:hits|crits|glances)) (.+?), causing ([\d,]+) points of (.+) damage\.$', '$1$2 $3，造成 $4 点$5伤害。', [2, 5]),
    (r'^(.+?) (?:is )?eviscerated for ([\d,]+) (.+) damage, putting it out of its misery\.?$', '$1被终结，受到 $2 点$3伤害，就此从痛苦中解脱。', [3]),
    (r'^(.+) hits (.+), causing ([\d,]+) additional points of (.+) damage\.$', '$1击中 $2，额外造成 $3 点$4伤害。', [4]),
    (r'^You counter (.+) for ([\d,]+) points of (.+) damage\.$', '你反击 $1，造成 $2 点$3伤害。', [3]),
    (r'^(.+) hits (.+) for ([\d,]+) damage\.$', '$1击中 $2，造成 $3 点伤害。', []),
    (r'^(.+) vigorously whiffs at a shadow, missing you completely\.$', '$1 猛烈挥向影子，完全没有击中你。', []),
    (r'^You gain the effect (.+)\.$', '你获得效果 $1。', [1]),
    (r'^(.+) gains the effect (.+)\.$', '$1 获得效果 $2。', [2]),
    (r'^The effect (.+) on (.+) has worn off\.$', '$2 身上的 $1 效果已消失。', [1]),
    (r'^You drain ([\d,]+) points of (health|magic|spirit) from (.+?)\.?$', '你从 $3 吸取 $1 点$2。', [2]),
    (r'^(.+) has been defeated\.$', '$1 已被击败。', []),
    (r'^(.+) uses (.+), which (hits|crits|glances)! (.+), and take ([\d,]+) (?:points of )?(.+) damage\.$', '$1使用 $2，$3！$4，受到 $5 点$6伤害。', [2, 3, 4, 6]),
    (r'^(.+) uses (.+), which (hits|crits|glances)! You take ([\d,]+) (?:points of )?(.+) damage\.$', '$1使用 $2，$3！你受到 $4 点$5伤害。', [2, 3, 5]),
    (r'^You (block|parry|evade|block and partially parry|block and parry|partially block and parry) the attack from (.+)\.$', '你$1了来自 $2 的攻击。', [1]),
    (r'^(.+) uses (.+) in the general direction of a shadow, missing you completely\.$', '$1对着影子的大致方向使用 $2，完全没有击中你。', [2]),
    (r'^You cast (.+)\.$', '你施放 $1。', [1]),
    (r'^The effect (.+?)\s+has worn off\.$', '$1 效果已消失。', [1]),
    (r'^Cooldown expired for (.+)$', '$1 冷却结束。', [1]),
    (r'^Recovered ([\d,]+) points of (health|magic|spirit)\.$', '恢复 $1 点$2。', [2]),
    (r'^(.+) restores ([\d,]+) points of (health|magic|spirit)\.$', '$1恢复 $2 点$3。', [1, 3]),
    (r'^You are healed for ([\d,]+) Health Points\.$', '你恢复 $1 点生命。', []),
    (r'^You (block|parry|evade|block and partially parry|block and parry|partially block and parry) the attack\.$', '你$1了攻击。', [1]),
    (r'^You use (.+)\.$', '你使用 $1。', [1]),
    (r'^(.+) casts (.+), which (hits|crits|glances)! (.+), and take ([\d,]+) (?:points of )?(.+) damage\.$', '$1施放 $2，$3！$4，受到 $5 点$6伤害。', [2, 3, 4, 6]),
    (r'^(.+) casts (.+), which (hits|crits|glances)! You take ([\d,]+) (?:points of )?(.+) damage\.$', '$1施放 $2，$3！你受到 $4 点$5伤害。', [2, 3, 5]),
    (r'^(.+) (dodges|parries) your attack\.$', '$1$2了你的攻击。', [2]),
    (r'^(.+) uses (.+), but misses the attack\.$', '$1使用 $2，但攻击未命中。', [2]),
    (r'^(.+) casts (.+), but misses the attack\.$', '$1施放 $2，但攻击未命中。', [2]),
    (r'^(.+) casts (.+) in the general direction of a shadow, missing you completely\.$', '$1对着影子的大致方向施放 $2，完全没有击中你。', [2]),
    (r'^Your spirit shield absorbs ([\d,]+) points of damage from the attack into ([\d,]+) points of spirit damage\.$', '你的灵力盾吸收了此次攻击造成的 $1 点伤害，并转化为 $2 点灵力伤害。', []),
    (r'^(.+) has been roused from its sleep\.$', '$1 从睡眠中醒来。', []),
    (r'^The effect (.+) was dispelled\.$', '$1 效果被驱散。', [1]),
    (r'^(.+) partially resists the effects of your spell\.$', '$1 部分抵抗了你的法术效果。', []),
    (r'^(.+) shrugs off the effects of your spell\.$', '$1 无视了你的法术效果。', []),
    (r'^(.+) resists, and was ((?:\d+x-)?(?:hit|crit|glanced)) for ([\d,]+) (.+) damage$', '$1 抵抗，但仍被$2，受到 $3 点$4伤害。', [2, 4]),
    (r'^(.+?)\s+was ((?:\d+x-)?(?:hit|crit|glanced)) for ([\d,]+) (.+) damage$', '$1 被$2，受到 $3 点$4伤害。', [2, 4]),
    (r'^(.+) deftly evades your spell\.$', '$1灵巧地闪避了你的法术。', []),
    (r'^Initializing arena challenge #(\d+) \(Round (\d+) / (\d+)\) \.\.\.$', '初始化竞技场挑战 #$1（第 $2 / $3 回合）...', []),
    (r'^Initializing Grindfest \(Round (\d+) / (\d+)\) \.\.\.$', '初始化压榨界（第 $1 / $2 回合）...', []),
    (r'^Initializing Item World \(Round (\d+) / (\d+)\) \.\.\.$', '初始化道具界（第 $1 / $2 回合）...', []),
    (r'^Spawned Monster ([A-Z]): MID=(\d+) \((.+)\) LV=(\d+) HP=(\d+)$', '生成怪物 $1：MID=$2（$3）等级=$4 生命=$5', []),
    (r'^You received (?:a |an )?\[(.+)\]$', '你获得了 [$1]', [1]),
    (r'^(.+) dropped \[(.+)\]$', '$1 掉落 [$2]', [2]),
    (r'^A traveling salesmoogle gives you \[([\d,]+) Credits\] for it\.$', '旅行商人莫古力给予了你 [$1 信用点]。', []),
    (r'^Battle Clear Bonus! \[(.+)\]$', '战斗通关奖励！[$1]', [1]),
    (r'^You obtained ([\dx,.]+) \[(.+)\]$', '你获得 $1 [$2]', [2]),
    (r'^Arena Extra Bonus! You obtained ([\dx]+) \[(.+)\]$', '竞技场额外奖励！你获得 $1 [$2]', [2]),
    (r'^Arena Token Bonus! \[(.+)\]$', '竞技场令牌奖励！[$1]', [1]),
    (r'^You gain ([\d,]+) EXP!$', '你获得 $1 经验！', []),
    (r'^You gain ([\d,]+) Credits!$', '你获得 $1 信用点！', []),
    (r'^You gain ([\d.]+) points of (.+) proficiency\.$', '你获得 $1 点$2熟练度。', [2]),
    (r'^You have reached Level (\d+)!$', '你已达到等级 $1！', []),
]

# Native equivalents of parseBattleEvent/renderBattleEvent in the selected source.
# pattern, source-renderer wording, typed term captures, actor captures.
BATTLE_EVENT_RULES = [
    (r'^(.+?) (uses|casts) (.+?), (?:and |which )?(hits|crits|glances|blasts)(?: you for |! You take )([\d,]+) ([A-Za-z]+) damage[.!]?$', '$1$2 $3，$4你，造成 $5 点$6伤害。', [2, 3, 4, 6], [1]),
    (r'^You cast (.+?), healing (you|.+?) for ([\d,]+) points of health[.!]?$', '你施放了 $1，为 $2 恢复了 $3 点生命。', [1], [2]),
    (r'^Your spirit shield absorbs ([\d,]+) damage from the attack into ([\d,]+) points of spirit(?: damage)?[.!]?$', '你的灵力盾吸收了 $1 点伤害，转化为 $2 点灵力损失。', [], []),
    (r'^(.+?) saves you from the brink of defeat!$', '$1让你从死亡的边缘复活了！', [1], []),
    (r'^(.+?) (\d+)x-(hits?|crits?|glances?|blasts) (.+?)(?:, causing | for )([\d,]+)(?: points of)? ([A-Za-z]+) damage[.!]?$', '$1$2 倍$3 $4，造成 $5 点$6伤害。', [3, 6], [1, 4]),
    (r'^(.+?) (hits?|crits?|glances?|counter|blasts) (.+?)(?:, causing | for )([\d,]+)(?: points of)? ([A-Za-z]+) damage[.!]?$', '$1$2 $3，造成 $4 点$5伤害。', [2, 5], [1, 3]),
    (r'^(.+?) (hits?|crits?|glances?|counter|blasts) (.+?)(?:, causing | for )([\d,]+) additional(?: points of)? ([A-Za-z]+) damage[.!]?$', '$1$2 $3，额外造成 $4 点$5伤害。', [2, 5], [1, 3]),
    (r'^(.+?) (hits?|crits?|glances?|counter|blasts) (.+?)(?:, causing | for )([\d,]+) damage[.!]?$', '$1$2 $3，造成 $4 点伤害。', [2], [1, 3]),
    (r'^(.+?) was (hit|crit|glanced|blasted) for ([\d,]+)(?: points of)? ([A-Za-z]+) damage[.!]?$', '$1被$2，受到 $3 点$4伤害。', [2, 4], [1]),
    (r'^(.+?) resists, and was (hit|crit|glanced|blasted) for ([\d,]+)(?: points of)? ([A-Za-z]+) damage[.!]?$', '$1抵抗了部分法术，被$2，受到 $3 点$4伤害。', [2, 4], [1]),
    (r'^(.+?) (uses|casts) (.+?), but (?:it )?is absorbed[.!]?$', '$1$2 $3，但被吸收了。', [2, 3], [1]),
    (r'^(.+?) (uses|casts) (.+?), (?:but misses the attack|(?:in the general direction of a shadow, )?missing you completely)[.!]?$', '$1$2 $3，但没有命中你。', [2, 3], [1]),
    (r'^(.+?) explodes for ([\d,]+) ([A-Za-z]+) damage[.!]?$', '$1爆裂并受到 $2 点$3伤害。', [3], [1]),
    (r'^(.+?) is eviscerated for ([\d,]+) ([A-Za-z]+) damage, putting it out of its misery[.!]?$', '$1被切除了内脏，受到 $2 点$3伤害，随后被击败。', [3], [1]),
    (r'^(You|.+?) (cast|casts|use|uses) ([^,.!?]+)[.!]?$', '$1$2 $3。', [2, 3], [1]),
    (r'^(.+?) gains? the effect (.+?)[.!]?$', '$1获得了状态 $2。', [2], [1]),
    (r'^The effect (.+?) on (.+?) (?:has expired|has worn off)[.!]?$', '$2 身上的状态 $1 已失效。', [1], [2]),
    (r'^The effect (.+?) (?:has expired|has worn off)[.!]?$', '状态 $1 已失效。', [1], []),
    (r'^The effect (.+?) was dispelled[.!]?$', '状态 $1 已被替换。', [1], []),
    (r'^Cooldown expired for (.+?)[.!]?$', '$1 已可使用。', [1], []),
    (r'^(.+?) got knocked out of confuse[.!]?$', '$1从混乱中脱离。', [], [1]),
    (r'^(.+?) has been roused from its sleep[.!]?$', '$1已从睡梦中苏醒。', [], [1]),
    (r'^(.+?) has been defeated[.!]?$', '击败了 $1。', [], [1]),
    (r'^You drain ([\d,]+) (HP|MP|SP|points of health|points of mana|points of spirit) from (.+?)[.!]?$', '你从 $3 身上吸取了 $1 点$2。', [2], [3]),
    (r'^(?:Recovered|You recover) ([\d,]+) (?:points of )?(health|mana|spirit|HP|MP|SP)[.!]?$', '你恢复了 $1 点$2。', [2], []),
    (r'^(.+?) restores ([\d,]+) (?:points of )?(health|mana|spirit)[.!]?$', '$1恢复了你 $2 点$3。', [1, 3], []),
    (r'^(.+?) dropped \[(.+)\][.!]?$', '$1掉落了 $2。', [2], [1]),
    (r'^(.+?) drops a (.+?) power ?up![.]?$', '$1掉落了 $2。', [2], [1]),
    (r'^Scanning (.+)$', '已扫描 $1', [], [1]),
    (r'^(?:Spawned Monster[^:\n]*:\s*)?MID=(\d+) \((.+)\) LV=(\d+) HP=([\d,]+)$', '生成怪物：MID=$1 ($2) 等级=$3 生命=$4', [], [2]),
]

ATTRIBUTION_RULES = [
    (r'^Rewarded as a Clear Bonus to (.+?) on (\d{4}-\d{2}-\d{2})$', '作为通关奖励授予 $1，于 $2'),
    (r"^Rewarded from Snowflake's Shrine to (.+?) on (\d{4}-\d{2}-\d{2})$", '由雪花祭坛奖励给 $1，于 $2'),
    (r'^Dropped by (.+?) for (.+?) on (\d{4}-\d{2}-\d{2})$', '由 $1 掉落给 $2，于 $3'),
]


# Selectors describe browser DOM, so native screens supply a URL + battle context.
# These groups target browser-only widgets or Battle DOM despite a site-wide URL.
BATTLE_GROUPS = {28, 29, 30, 31, 32, 33}
NATIVE_ROUTE_SCOPES = {
    2: ['Character/ch', 'Character/eq'], 3: ['Character/eq', 'Bazaar/am'],
    23: ['Character/eq', 'Bazaar/am'],
    25: ['Bazaar/ss'], 34: ['Battle/*'], 42: ['Bazaar/ml'],
    43: ['Bazaar/ml'], 44: ['Bazaar/ml'], 49: ['Character/ch', 'Character/eq'],
    40: ['Bazaar/am'], 51: ['Bazaar/ss'],
}
BROWSER_ONLY_GROUPS = {21, 36, 37, 38}


# These decorations belong to the userscript's equipment presentation. Native
# screens draw icons separately; only known dictionary values are normalized.
# Never strip symbols from input text or captured player/equipment names.
EQUIPMENT_DECORATIONS = {
    'Main Hand': ('🖐️',), 'Off Hand': ('🤚',),
    'Body': ('🥼',), 'Hands': ('🤲',), 'Legs': ('🦵',), 'Feet': ('👣',),
    'Exquisite': ('✧',), 'Magnificent': ('☆',), 'Legendary': ('✪',),
    'Peerless': ('☯',), 'Ultimate': ('𖣔',), 'Radiant': ('✪',),
    'Axe': ('🪓',), 'Club': ('🦯',), 'Rapier': ('🤺',),
    'Shortsword': ('⚔️',), 'Wakizashi': ('🔪',), 'Dagger': ('🗡️',),
    'Estoc': ('🤺',), 'Longsword': ('⚔️',), 'Katana': ('※',),
    'Scythe': ('𖤠',), 'Mace': ('🔨',), 'Great Mace': ('🔨',),
    'Swordchucks': ('⚔︎',), 'Staff': ('🧙‍♂️',),
    'Buckler': ('🛡️',), 'Kite Shield': ('🔰',), 'Tower Shield': ('🚪',),
    'Cap': ('🪖',), 'Helmet': ('🪖',),
    'Robe': ('🥼',), 'Breastplate': ('🥼',), 'Cuirass': ('🥼',),
    'Power Armor': ('🥼', '*'), 'Phase Armor': ('*',),
    'Shade Armor': ('*',), 'Reactive Armor': ('*',), 'Force Shield': ('*',),
    'Gloves': ('🤲',), 'Gauntlets': ('🤲',),
    'Pants': ('🦵',), 'Leggings': ('🦵',), 'Greaves': ('🦵',),
    'Shoes': ('👣',), 'Boots': ('👣',), 'Sabatons': ('👣',),
}


def plain_text(value):
    from html import unescape
    # Styles/HTML belong to the userscript. Compose controls typography and color.
    value = re.sub(r'<br\s*/?>', '\n', value, flags=re.I)
    value = re.sub(r'</?(?:span|b|strong|i|em|div|p|font|small|a)\b[^>]*>', '', value, flags=re.I)
    return unescape(value).replace('\u00a0', ' ')


def replacement(value):
    if isinstance(value, dict):
        value = value.get('replace')
    if isinstance(value, str):
        return plain_text(value), []
    if isinstance(value, Expression):
        # Lower ONLY template interpolation of parameters (and the source's
        # transItem parameter marker). No JS is invoked, including the callback.
        match = re.fullmatch(r'\(([^)]*)\)\s*=>\s*`([^`]*)`', value.text)
        if not match:
            return None
        params = [p.strip() for p in match[1].split(',')]
        translated = []
        valid = True
        def reference(capture):
            nonlocal valid
            expr = capture[1]
            item = re.fullmatch(r'transItem\((\w+)\)', expr)
            name = item[1] if item else expr
            if name not in params or params.index(name) == 0:
                valid = False
                return ''
            index = params.index(name)
            if item:
                translated.append(index)
            return f'${index}'
        template = re.sub(r'\$\{([^}]+)}', reference, match[2])
        if valid:
            return plain_text(template), translated
    return None


def export(source, output):
    script = source.read_text(encoding='utf-8')
    groups = LiteralReader(script, 'pageTranslations').value()
    result_groups = []
    skipped = []
    callback_count = 0

    def build_rule(key, value, regex=False):
        nonlocal callback_count
        parsed = replacement(value)
        if parsed is None:
            skipped.append(value.text if isinstance(value, Expression) else str(value))
            return None
        if isinstance(value, Expression):
            callback_count += 1
        text, captures = parsed
        if not regex:
            for decoration in EQUIPMENT_DECORATIONS.get(key, ()):
                text = text.replace(decoration, '')
            # Equipment Energy is a game resource, not a device battery level.
            # Keep the field and the source's equipment help text consistent.
            if re.search(r'\bEnergy\b', key):
                text = text.replace('电量', '能量')
        # The script intentionally keeps Credits in prose; native UI uses the
        # same Chinese currency label in both resources and server messages.
        text = re.sub(r'(?<![A-Za-z])credits?(?![A-Za-z])', '信用点', text, flags=re.I)
        rule = {'value': text}
        if regex:
            rule.update(pattern=key.pattern, flags=key.flags)
            if captures:
                rule['translateGroups'] = captures
        else:
            if not key.strip() or key == text:
                return None
            rule['text'] = key
        return rule

    for index, group in enumerate(groups):
        if index in BROWSER_ONLY_GROUPS:
            continue
        rules = []
        for key, value in group.get('translations', {}).items():
            rule = build_rule(key, value)
            if rule:
                rules.append(rule)
        for pattern in group.get('patterns', []):
            if not isinstance(pattern.get('pattern'), JsRegex) or 'replace' not in pattern:
                continue
            rule = build_rule(pattern['pattern'], pattern['replace'], regex=True)
            if rule:
                rules.append(rule)
        if not rules:
            continue
        metadata = {'id': group.get('id', f'page-{index}'), 'sourceIndex': index,
                    'priority': group.get('priority', 1), 'selector': group.get('selector', ''),
                    'rules': rules}
        conditions = []
        for condition in group.get('conditions', []):
            value = condition.get('match')
            if isinstance(value, JsRegex) and 'hentaiverse' in value.pattern:
                conditions.append({'pattern': value.pattern, 'flags': value.flags})
            elif isinstance(value, str) and 'hentaiverse.org' in value:
                conditions.append({'pattern': '^' + re.escape(value)})
        metadata['conditions'] = conditions
        if index in NATIVE_ROUTE_SCOPES:
            metadata['routes'] = NATIVE_ROUTE_SCOPES[index]
            # These replace DOM selector scope (including obsolete aliases in
            # imported legacy groups); raw original conditions remain recorded.
        if index in BATTLE_GROUPS:
            metadata['battleOnly'] = True
        result_groups.append(metadata)

    for name in ('sysMessages', 'dynamicItemDict'):
        rules = [build_rule(key, value) for key, value in LiteralReader(script, name).value().items()]
        result_groups.append({'id': name, 'priority': 1, 'rules': [r for r in rules if r]})
    sys_rules = []
    for entry in LiteralReader(script, 'sysRegex').value():
        rule = build_rule(entry['reg'], entry['repl'], regex=True)
        if rule:
            sys_rules.append(rule)
    result_groups.append({'id': 'sysRegex', 'priority': 20, 'rules': sys_rules})

    # Native full-sentence adapters keep actor names sealed while translating
    # typed damage/effect/item captures through the selected userscript terms.
    # They are restricted to active battles, not applied to every page.
    result_groups.append({'id': 'native-battle-events', 'priority': 40, 'battleOnly': True,
        'rules': [{'pattern': pattern, 'value': value, 'translateGroups': captures, 'actorGroups': actors}
                  for pattern, value, captures, actors in BATTLE_EVENT_RULES] + [
            {'text': key, 'value': value} for key, value in {
                'hit': '击中了', 'hits': '击中了', 'crit': '暴击了', 'crits': '暴击了',
                'blasts': '暴击了', 'blasted': '暴击了', 'glance': '擦伤了', 'glances': '擦伤了',
                'glanced': '擦伤了', 'counter': '反击了', 'use': '使用了', 'uses': '使用了',
                'cast': '施放了', 'casts': '施放了', 'HP': '生命', 'MP': '魔力', 'SP': '灵力',
                'Spirit': '灵力', 'Spirit Stance': '灵动架势',
                'points of health': '生命', 'points of mana': '魔力', 'points of spirit': '灵力',
            }.items()]})
    result_groups.append({'id': 'native-battle-sentences', 'priority': 30, 'battleOnly': True,
        'rules': [{'pattern': pattern, 'flags': 'i', 'value': value, 'translateGroups': captures}
                  for pattern, value, captures in BATTLE_RULES]})
    result_groups.append({'id': 'native-attribution', 'priority': 30,
        'rules': [{'pattern': pattern, 'value': value} for pattern, value in ATTRIBUTION_RULES] + [
            {'pattern': r'^(.+) brought you a gift!$', 'value': '$1 送来了礼物！'}]})
    # A complete date keeps ordinal days such as 21st from leaking into Chinese.
    # Month names still use the selected userscript's lottery dictionary.
    result_groups.append({'id': 'native-lottery-date', 'priority': 30,
        'routes': ['Bazaar/lt', 'Bazaar/la'], 'rules': [{
            'pattern': r'^Grand Prize for (January|February|March|April|May|June|July|August|September|October|November|December) (\d{1,2})(?:st|nd|rd|th):$',
            'value': '一等奖 · $1 $2 日', 'translateGroups': [1],
        }]})
    # Only app-specific labels missing from the selected catalog are fallbacks.
    imported = {r['text'].strip().lower() for g in result_groups for r in g['rules'] if 'text' in r}
    result_groups.append({'id': 'native-labels', 'priority': 0,
        'rules': [{'text': key, 'value': value} for key, value in NATIVE_TERMS.items()
                  if key.strip().lower() not in imported]})
    # Composite item/equipment names are the only captures that use token
    # translation. Unknown skill/effect names otherwise stay verbatim.
    for group in result_groups:
        for rule in group['rules']:
            pattern = rule.get('pattern', '')
            # Settings dropdown captures are equipment qualities, not user names.
            if group.get('sourceIndex') == 53 and pattern in (r'^Sell (\w+)$', r'^Salvage (\w+)$'):
                rule['translateGroups'] = [1]
            if group['id'] in ('native-battle-sentences', 'native-battle-events') and any(
                token in pattern for token in ('received', 'obtained', 'dropped', 'Clear Bonus', 'Token Bonus')
            ):
                rule['compoundGroups'] = rule.get('translateGroups', [])
    result = {
        'source': 'JPX-PLUS/' + source.name,
        'version': re.search(r'// @version\s+(\S+)', script)[1],
        'author': re.search(r'// @author\s+([^\n]+)', script)[1].strip(),
        'license': re.search(r'// @license\s+(\S+)', script)[1],
        'groups': result_groups,
        'extraction': {'loweredTemplateCallbacks': callback_count, 'skippedDynamicExpressions': skipped,
                       'excludedBrowserOnlyGroups': sorted(BROWSER_ONLY_GROUPS)},
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    count = sum(len(g['rules']) for g in result_groups)
    print(f'Extracted {count} rules in {len(result_groups)} groups; '
          f'{callback_count} static template callbacks; {len(skipped)} unsupported expressions')


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    export(root.parent / 'JPX-PLUS/HentaiVerse-Chinese.user.js', root / 'app/src/main/assets/hv_zh.json')
