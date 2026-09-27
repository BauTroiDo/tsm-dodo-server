"""Conservative, reproducible DVT protocol evidence inventory (Python stdlib only).

Presence is PARTIAL, never proof of behavioral compatibility. Unresolved dynamic
calls are retained separately. Lua comments/strings are masked preserving offsets.
"""
import collections
import csv
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def mask(text, lua=True):
    pattern = (r'--\[(=*)\[[\s\S]*?\]\1\]|\[(=*)\[[\s\S]*?\]\2\]|--[^\n]*' if lua
               else r'/\*[\s\S]*?\*/|//[^\n]*')
    pattern += r'|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    return re.sub(pattern, lambda m: re.sub(r'[^\n]', ' ', m[0]), text)


def ref(path, text, offset):
    return f'{path.relative_to(ROOT).as_posix()}:{text.count(chr(10), 0, offset) + 1}'


def number(value, constants=None):
    value = value.strip()
    if re.fullmatch(r'\d+|0[xX][\da-fA-F]+', value):
        return int(value, 16 if value.lower().startswith('0x') else 10)
    return (constants or {}).get(value.split('.')[-1])


def args_at(text, start):
    depth, last, args = 0, start, []
    for i in range(start, len(text)):
        c = text[i]
        if c in '({[':
            depth += 1
        elif c in ')}]':
            if c == ')' and depth == 0:
                args.append(text[last:i].strip())
                return args
            depth -= 1
        elif c == ',' and depth == 0:
            args.append(text[last:i].strip())
            last = i + 1
    raise ValueError('Unclosed call')


def block(text, opening):
    depth = 0
    for i in range(opening, len(text)):
        depth += (text[i] == '{') - (text[i] == '}')
        if depth == 0:
            return text[opening + 1:i]
    raise ValueError('Unclosed block')


def scan():
    records = collections.defaultdict(lambda: collections.defaultdict(list))
    unresolved = []
    documented = []
    resolutions_path = ROOT / 'tools/protocol_dynamic_calls.json'
    resolutions = json.loads(resolutions_path.read_text(encoding='utf-8')) if resolutions_path.exists() else {}
    debug_path = ROOT / 'lua/UI/UIDebug.lua'
    debug = mask(debug_path.read_text(encoding='utf-8-sig'))
    for line, table in [(302, 'argumentFunctions'), (657, 'noneArgumentFunctions')]:
        values = [int(x) for x in re.findall(r'table.insert\('+table+r',\s*\{\s*(\d+)', debug) if int(x) <= 200]
        resolutions[f'lua/UI/UIDebug.lua:{line}'] = {
            'pairs': [[100, x] for x in values],
            'evidence': f'UIDebug.lua {table} literal entries; OnClick handler sends only values <=200.'}
    chat = mask((ROOT / 'lua/Logic/Chat.lua').read_text(encoding='utf-8-sig'))
    channel_table = re.search(r'EChannel\s*=\s*\{([^}]+)', chat)[1]
    channels = [int(x) for x in re.findall(r'=\s*(\d+)', channel_table)]
    resolutions['lua/UI/UINewChat.lua:523'] = {
        'pairs': [[2, x] for x in channels],
        'evidence': 'Chat.lua EChannel; UINewChat.lua:52 iterates channel UI objects. Candidate enum domain: actual reachability depends on available UI objects and ChangeChannel callers.'}
    lua_files = sorted((ROOT / 'lua').rglob('*.lua'))
    for path in lua_files:
        raw = path.read_text(encoding='utf-8-sig')
        code = mask(raw)
        if path.name == 'protocal.lua':
            for m in re.finditer(r'\b([CS]):\s*(\d+)-(\d+)([^\n]*)', raw):
                documented.append({'direction': m[1], 'main': int(m[2]), 'sub': int(m[3]),
                                   'text': m[4].strip(), 'source': ref(path, raw, m.start())})
        pattern = r'\bNetwork\s*\.\s*Send\s*\('
        if path.name == 'Network.lua':
            pattern = r'\b(?:Network|this)\s*\.\s*Send\s*\('
        for m in re.finditer(pattern, code):
            if re.search(r'function\s*$', code[max(0, m.start()-20):m.start()]):
                continue
            args = args_at(code, m.end())
            a = number(args[0]) if args else 0
            b = number(args[1]) if len(args) > 1 and args[1] != 'nil' else 0
            evidence = {'source': ref(path, raw, m.start()), 'arguments': args}
            if a is None or b is None:
                resolution = resolutions.get(evidence['source'])
                if resolution:
                    for main, sub in resolution['pairs']:
                        records['C2S', main, sub]['lua'].append({**evidence, 'resolution': resolution['evidence']})
                else:
                    unresolved.append(evidence)
            else:
                records['C2S', a, b]['lua'].append(evidence)
        for m in re.finditer(r'protocolTable\s*\[\s*(\d+)\s*\]\s*\[\s*(\d+)\s*\]\s*=\s*([\w.]+)', code):
            records['S2C', int(m[1]), int(m[2])]['lua'].append({'source': ref(path, raw, m.start()), 'callback': m[3]})

    opath = ROOT / 'domain/src/main/kotlin/tw/tsm/domain/constants/Opcodes.kt'
    opcode_code = mask(opath.read_text(encoding='utf-8'), False)
    opcode_body = block(opcode_code, opcode_code.index('{', opcode_code.index('object Opcodes')))
    constants = {name: int(value) for name, value in re.findall(r'const val (\w+) = (\d+)', opcode_body)}
    module = (ROOT / 'app/src/main/kotlin/tw/tsm/app/ServerModule.kt').read_text(encoding='utf-8')
    registered = set(re.findall(r'register\(get<(\w+)>\(\)\)', mask(module, False)))
    handlers, wildcard = {}, {}
    for path in sorted((ROOT / 'app/src/main/kotlin').rglob('*Handler.kt')):
        raw = path.read_text(encoding='utf-8-sig')
        code = mask(raw, False)
        m = re.search(r'override val mainKind\s*:\s*Int\s*=\s*(Opcodes\.\w+|\d+)', code)
        if not m or path.stem not in registered:
            continue
        main = number(m[1], constants)
        evidence = ref(path, raw, m.start())
        route = re.search(r'when\s*\(subKind\)\s*\{', code)
        if route:
            body = block(code, route.end()-1)
            # Only top-level numeric branches, not nested game-logic when blocks.
            depth = 0
            for line in body.splitlines():
                branch = re.match(r'\s*((?:in\s+\d+\.\.\d+|\d+)(?:\s*,\s*(?:in\s+\d+\.\.\d+|\d+))*)\s*->', line)
                if depth == 0 and branch:
                    for part in branch[1].split(','):
                        bounds = list(map(int, re.findall(r'\d+', part)))
                        subs = range(bounds[0], bounds[1]+1) if len(bounds) == 2 else bounds
                        for sub in subs:
                            handlers.setdefault((main, sub), []).append(evidence)
                depth += line.count('{') - line.count('}')
        else:
            wildcard[main] = evidence

    senders = collections.defaultdict(list)
    dynamic_writers = []
    for path in sorted(ROOT.glob('*/src/main/kotlin/**/*.kt')):
        raw = path.read_text(encoding='utf-8-sig')
        code = mask(raw, False)
        local_constants = dict(constants)
        for name, value in re.findall(r'val\s+(\w+)\s*=\s*(Opcodes\.\w+)', code):
            local_constants[name] = number(value, constants)
        for m in re.finditer(r'PacketWriter\((?:[^()\n]|\([^()\n]*\))*\)\s*\.writeByte\(([^()]+)\)\s*\.writeByte\(([^()]+)\)\s*\.writeByte\(0\)', code):
            a, b = number(m[1], local_constants), number(m[2], local_constants)
            source = ref(path, raw, m.start())
            if a is None or b is None:
                subs = None
                if path.name == 'ChatResponseSender.kt' and m[2] == 'channel.code':
                    subs = range(8) # domain/enums/ChatChannel.kt
                elif path.name == 'TeleportHandler.kt' and m[2] == 'subKind':
                    subs = [1, 2] # request/result branches
                elif path.name == 'UISellResponseSender.kt' and m[2] == 'subKind':
                    subs = [1, 2] # query/use failure branches
                if a is not None and subs is not None:
                    for sub in subs:
                        senders[a, sub].append(source)
                else:
                    dynamic_writers.append({'source': source, 'main': m[1], 'sub': m[2]})
            else:
                senders[a, b].append(source)
    rows = []
    for (direction, main, sub), evidence in sorted(records.items()):
        kotlin = handlers.get((main, sub), []) if direction == 'C2S' else senders.get((main, sub), [])
        if direction == 'C2S' and not kotlin and main in wildcard:
            kotlin = [wildcard[main]]
        status = 'PARTIAL' if kotlin else 'MISSING'
        note = 'Static route/writer found; payload, state, side effects not fully verified.' if kotlin else 'No literal route/writer found; inspect dynamic candidates before implementation.'
        rows.append(dict(direction=direction, main=main, sub=sub, status=status, note=note,
                         lua=evidence['lua'], kotlin=kotlin))
    for (main, sub), sources in sorted(senders.items()):
        if ('S2C', main, sub) not in records:
            rows.append(dict(direction='S2C', main=main, sub=sub, status='MISMATCH',
                             note='Server emits this opcode but no DVT callback is registered; replacement semantics not established.',
                             lua=[], kotlin=sources))
    # Explicit manual contract reviews, kept separate from automatic presence checks.
    reviews_path = ROOT / 'tools/protocol_reviews.json'
    reviews = json.loads(reviews_path.read_text(encoding='utf-8')) if reviews_path.exists() else {}
    for row in rows:
        review = reviews.get(f"{row['direction']}:{row['main']}:{row['sub']}")
        if review:
            row.update(review)
        row.setdefault('baseline_status', row['status'])
    return {'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'lua_files': len(lua_files), 'rows': rows, 'unresolved_calls': unresolved, 'documented_protocols': documented,
            'dynamic_writers': dynamic_writers, 'resolved_dynamic_calls': resolutions, 'opcodes': constants,
            'registered_handlers': sorted(registered),
            'server_only_literal_writers': [{'main': a, 'sub': b, 'sources': sources} for (a,b), sources in sorted(senders.items()) if ('S2C',a,b) not in records]}


def main():
    result = scan()
    out = ROOT / 'docs/protocol-dvt'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'inventory.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    with (out / 'inventory.csv').open('w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['direction', 'main', 'sub', 'baseline_status', 'status', 'note', 'lua', 'kotlin'])
        writer.writeheader()
        for row in result['rows']:
            writer.writerow({**row, 'lua': '; '.join(x['source'] for x in row['lua']), 'kotlin': '; '.join(row['kotlin'])})
    print(json.dumps({'lua_files': result['lua_files'], 'rows': len(result['rows']),
                      'counts': dict(collections.Counter(r['status'] for r in result['rows'])),
                      'unresolved': result['unresolved_calls'], 'dynamic_writers': result['dynamic_writers']}, indent=2))


if __name__ == '__main__':
    main()
