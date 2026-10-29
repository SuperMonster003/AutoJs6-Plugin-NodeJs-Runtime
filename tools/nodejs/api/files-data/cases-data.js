// Rhino / Node comparison cases of storages, mediainfo and the global object (AutoJs6 api-coverage P3.2b), in the
// form of the other cases files: [id, expression] or [id, expression, nodeExpression], with storages, mediainfo, G (the
// Rhino global object; autojs6:global in Node) and D in scope. The cases use one store, p32b_data, and remove it.
var DATA_CASES = [
    ["storages.put", "(function () { var s = storages.create('p32b_data'); s.clear(); s.put('a', 1); s.put('b', [1, 'x']); return s.get('b'); })()",
        "(async () => { const s = storages.create('p32b_data'); await s.clear(); await s.put('a', 1); await s.put('b', [1, 'x']); return s.get('b'); })()"],
    // Rhino's size is a property, Node's a method.
    ["Storage.size", "storages.create('p32b_data').size", "storages.create('p32b_data').size()"],
    // Rhino lists the non-empty preference files, which SharedPreferences writes in the background: the Rhino
    // expressions wait for the write before they list.
    ["storages.names.includes", "(sleep(1000), storages.names().indexOf('p32b_data') >= 0)", "storages.names().then(n => n.indexOf('p32b_data') >= 0)"],
    ["storages.names.strings", "storages.names().every(function (n) { return typeof n === 'string'; })",
        "storages.names().then(n => n.every(x => typeof x === 'string'))"],
    ["storages.all.count", "storages.all().length === storages.names().length",
        "Promise.all([storages.all(), storages.names()]).then(([a, n]) => a.length === n.length)"],
    ["storages.names.cleared", "(function () { storages.create('p32b_data').clear(); sleep(1000); return storages.names().indexOf('p32b_data') >= 0; })()",
        "storages.create('p32b_data').clear().then(() => storages.names()).then(n => n.indexOf('p32b_data') >= 0)"],
    ["storages.remove", "(function () { storages.create('p32b_data').put('a', 1); storages.remove('p32b_data'); sleep(1000); return storages.names().indexOf('p32b_data') >= 0; })()",
        "(async () => { await storages.create('p32b_data').put('a', 1); await storages.remove('p32b_data'); return (await storages.names()).indexOf('p32b_data') >= 0; })()"],

    ["mediainfo.SNAPSHOT_SCHEMA_V1", "mediainfo.SNAPSHOT_SCHEMA_V1"],
    ["mediainfo.SNAPSHOT_SCHEMA_V2", "mediainfo.SNAPSHOT_SCHEMA_V2"],
    ["mediainfo.snapshot.type", "typeof mediainfo.snapshot"],

    ["global.open.type", "typeof G.open"],
    ["global.Color.type", "typeof G.Color"],
    ["global.Color.value", "G.Color('#ff8000').toHex()"],
];

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: DATA_CASES};
}
