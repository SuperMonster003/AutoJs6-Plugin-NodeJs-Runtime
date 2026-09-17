"nodejs";

const store = require("media_store");

(async function main() {
  const capabilities = await store.capabilities();
  console.log("library.capabilities=" + [
    capabilities.schema,
    capabilities.collections.includes("audio"),
    capabilities.mutatePolicy
  ].join("|"));
  const displayName = "autojs6-node-sample-" + Date.now() + ".wav";
  const item = await store.insert("audio", {
    displayName,
    mimeType: "audio/wav",
    source: "tone.wav",
    relativePath: "Music/AutoJs6Node/"
  });
  console.log("library.inserted=" + [
    item.collection,
    item.displayName === displayName,
    typeof item.id,
    item.uri.startsWith("content://")
  ].join("|"));
  try {
    const owned = await store.query("audio", {
      filter: { displayName: { startsWith: "autojs6-node-sample-" }, ownedOnly: true },
      sort: "-dateAdded",
      limit: 10,
      columns: ["displayName", "size", "mimeType", "relativePath"]
    });
    console.log("library.query=" + [owned.count >= 1, owned.items.some(entry => entry.id === item.id)].join("|"));
    const renamedName = displayName.replace(".wav", "-renamed.wav");
    const renamed = await store.update("audio", item, { displayName: renamedName });
    console.log("library.updated=" + (renamed.displayName === renamedName));
    const exported = await store.exportFile("audio", item, "exported-tone.wav");
    console.log("library.exported=" + [exported.sizeBytes > 0, exported.path.endsWith("exported-tone.wav")].join("|"));
    const scanned = await store.scanFile("tone.wav", { mimeType: "audio/wav" });
    console.log("library.scanned=" + typeof scanned.scanned);
  } finally {
    const removed = await store.delete("audio", item);
    const gone = await store.get("audio", item);
    console.log("library.deleted=" + [removed.deleted, gone === null].join("|"));
  }
  console.log("sample.media-library=PASS");
})().catch(error => {
  console.error(error && error.stack || error);
  process.exitCode = 1;
});
