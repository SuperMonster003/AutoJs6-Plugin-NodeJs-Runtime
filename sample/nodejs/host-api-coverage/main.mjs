import assert from "node:assert/strict";
import pinyin from "pinyin";
import pinyin4j from "pinyin4j";
import screen from "screen_metrics";
import hostConsole from "console";

// Install the Pinyin and Pinyin4j plugins and grant overlay permission to AutoJs6.
const text = "\u5317\u4eac\u4e0a\u6d77";
assert.equal(await pinyin.simple(text), "beijingshanghai");
assert.equal(await pinyin4j.of(text, " "), "bei jing shang hai");
const rows = await pinyin.convert(text, { style: pinyin.STYLE_NORMAL });
assert.deepEqual(await rows.compact(), [["bei", "jing", "shang", "hai"]]);
assert.ok((await pinyin4j.readings("\u884c")).flat().length > 1);

const before = await screen.getInfo();
assert.equal(await screen.cX(), before.width);
assert.equal(await screen.cY(), before.height);
assert.equal(await screen.cX(0.5), Math.floor(before.width / 2 + 0.5));
assert.equal(await screen.cY(640, 1280), Math.floor(before.height / 2 + 0.5));
assert.equal(await screen.cXy(0.5, "16:9"), Math.floor(before.height * 0.5 * 16 / 9 + 0.5));
assert.equal(await screen.cYx(0.5, "16:9"), Math.floor(before.width * 0.5 * 9 / 16 + 0.5));
await screen.setScaleBases(1080, 1920);
assert.deepEqual(await screen.getScaleBases(), { x: 1080, y: 1920 });
await assert.rejects(screen.setScaleBaseX(720));
await assert.rejects(screen.cX(NaN));
await hostConsole.setTitle("Node host API coverage");
await hostConsole.setSize(320, 220);
await hostConsole.show();
hostConsole.log("sample.host-api-coverage=PASS", JSON.stringify(before));
await new Promise(resolve => setTimeout(resolve, 1500));
await hostConsole.hide();
