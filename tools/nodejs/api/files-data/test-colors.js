// node --test test.js: the colors / Color port against values worked out from the Kotlin sources (ColorsModule,
// ColorValues, Colors, ColorNativeObject), and every case of cases.js evaluated in Node with a stub Java facade.
"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const SOURCE = require("./extract.js")("colors");
const factory = new Function(`${SOURCE};return __autojs6_js_tools_colors;`)();
const {cases: CASES} = require("./cases-colors.js");

const THEME_COLOR = "org.autojs.autojs.theme.ThemeColor";
const IMAGE_WRAPPER = "org.autojs.autojs.core.image.ImageWrapper";

// A small in-memory stand-in for host.java(): arrays, Paint, ColorStateList, Bitmap, ImageWrapper, ColorQuantizer
// and the theme color, enough for the members that go through the facade.
function createJava() {
    let sequence = 0;
    const objects = new Map();
    const calls = [];
    const ARRAY_TYPES = {float: "[F", int: "[I", "int[]": "[[I"};
    const SUPERTYPES = {
        "android.graphics.Paint": ["java.lang.Object"],
        "android.content.res.ColorStateList": ["java.lang.Object"],
        [IMAGE_WRAPPER]: ["java.lang.Object"],
    };

    function handle(className, state, value) {
        const id = `java-${++sequence}`;
        const h = {__autojs6JavaHandle: id, className};
        if (value !== undefined) h.value = value;
        Object.defineProperties(h, {
            call: {value: (method, args, options) => api.call(h, method, args, options)},
            getField: {value: field => Promise.resolve(state[field])},
            instanceOf: {value: type => api.instanceOf(h, type)},
            toArray: {value: () => api.toArray(h)},
            arrayLength: {value: () => api.arrayLength(h)},
            release: {value: () => api.release(h)},
        });
        objects.set(id, {className, state});
        return Object.freeze(h);
    }

    // A second handle of the same Java object, as each facade call returns.
    function alias(h) {
        const entry = objects.get(h.__autojs6JavaHandle);
        return handle(entry.className, entry.state);
    }

    const stateOf = h => {
        const entry = objects.get(h && h.__autojs6JavaHandle);
        if (!entry) throw new Error("Java object handle is released or unknown");
        return entry.state;
    };
    const unwrap = v => v && v.__autojs6JavaHandle ? stateOf(v) : v;

    function newArray(type, value) {
        const className = ARRAY_TYPES[type];
        if (!className) throw new Error(`Unsupported array type ${type}`);
        const items = Array.isArray(value) ? value.map(Number) : new Array(value).fill(type === "int[]" ? null : 0);
        return handle(className, {items: type === "float" ? items.map(Math.fround) : items});
    }

    function quantize(pixels, count) {
        const counts = new Map();
        for (const p of pixels) if ((p >>> 24) >= 128) counts.set(p | 0, (counts.get(p | 0) || 0) + 1);
        const swatches = [...counts].sort((a, b) => b[1] - a[1]).slice(0, count).map(([color, population]) => handle(
            "org.autojs.autojs.theme.color.ColorQuantizer$Swatch", {color, population, methods: {getColor: () => color}}));
        return handle("java.util.ArrayList", {methods: {toArray: () => swatches}});
    }

    function bitmap(width, height) {
        const pixels = new Array(width * height).fill(0);
        return handle("android.graphics.Bitmap", {
            methods: {
                getWidth: () => width,
                getHeight: () => height,
                eraseColor: c => void pixels.fill(c | 0),
                setPixel: (x, y, c) => void (pixels[y * width + x] = c | 0),
                getPixel: (x, y) => pixels[y * width + x],
                getPixels: (row, offset, stride, x, y, w, h) => {
                    const target = unwrap(row).items;
                    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) target[offset + j * stride + i] = pixels[(y + j) * width + x + i];
                },
            },
        });
    }

    function image(bmp) {
        const b = stateOf(bmp);
        let recycled = false;
        return handle(IMAGE_WRAPPER, {
            methods: {
                getWidth: () => b.methods.getWidth(),
                getHeight: () => b.methods.getHeight(),
                getBitmap: () => alias(bmp),
                shoot: () => void (recycled = true),
                isRecycled: () => recycled,
            },
        });
    }

    function paint() {
        let color = -16777216;
        return handle("android.graphics.Paint", {
            methods: {
                setARGB: (a, r, g, b) => void (color = (a << 24) | (r << 16) | (g << 8) | b),
                setColor: c => void (color = c | 0),
                getColor: () => color,
            },
        });
    }

    function colorStateList(defaultColor) {
        return handle("android.content.res.ColorStateList", {methods: {getDefaultColor: () => defaultColor}});
    }

    const api = {
        calls,
        newArray: async (type, value) => newArray(type, value),
        arrayLength: async h => stateOf(h).items.length,
        setArrayItem: async (h, i, v) => {
            const s = stateOf(h);
            if (i < 0 || i >= s.items.length) throw new Error("ArrayIndexOutOfBoundsException");
            s.items[i] = h.className === "[F" ? Math.fround(Number(v)) : v && v.__autojs6JavaHandle ? v : Number(v);
            return true;
        },
        getArrayItem: async (h, i) => stateOf(h).items[i],
        toArray: async h => Object.freeze(stateOf(h).items.slice()),
        release: async h => {
            objects.delete(h.__autojs6JavaHandle);
            return true;
        },
        instanceOf: async (h, type) => {
            const name = typeof type === "string" ? type : type.className;
            const className = objects.get(h.__autojs6JavaHandle).className;
            return className === name || (SUPERTYPES[className] || []).includes(name) || name === "java.lang.Object";
        },
        call: async (h, method, args = [], options) => {
            calls.push([h.className, method]);
            const fn = stateOf(h).methods && stateOf(h).methods[method];
            if (!fn) throw new Error(`No method ${h.className}.${method}`);
            return fn(...args.map(a => a && a.__autojs6JavaHandle ? a : a));
        },
        callStatic: async (type, method, args = []) => {
            const name = typeof type === "string" ? type : type.className;
            calls.push([name, method]);
            if (name === "android.content.res.ColorStateList" && method === "valueOf") return colorStateList(args[0]);
            if (name === "org.autojs.autojs.theme.ThemeColorManager" && method === "getCurrentThemeColor") {
                return handle(THEME_COLOR, {}, {string: "ThemeColor@1", colorPrimary: -12627531, colorPrimaryDark: -14273992});
            }
            if (name === "android.graphics.Bitmap" && method === "createBitmap") return bitmap(args[0], args[1]);
            if (name === IMAGE_WRAPPER && method === "ofBitmap") return image(args[0]);
            throw new Error(`No static method ${name}.${method}`);
        },
        getStatic: async (type, field) => {
            const name = typeof type === "string" ? type : type.className;
            if (name === "org.autojs.autojs.theme.color.ColorQuantizer" && field === "INSTANCE") {
                return handle("org.autojs.autojs.theme.color.ColorQuantizer", {methods: {quantize: (input, count) => quantize(unwrap(input).items, count)}});
            }
            if (name === "android.graphics.Bitmap$Config") return handle(name, {}, {name: field});
            throw new Error(`No static field ${name}.${field}`);
        },
        new: async (type, args = []) => {
            const name = typeof type === "string" ? type : type.className;
            if (name === "android.graphics.Paint") return paint();
            if (name === "android.content.res.ColorStateList") return colorStateList(stateOf(args[1]).items[0]);
            throw new Error(`No constructor ${name}`);
        },
    };
    api.create = api.new;
    return api;
}

function load(sdkInt = 34) {
    const java = createJava();
    const host = {java: () => java, device: {sdkInt}};
    return {...factory(host), java};
}

const {colors, Color, java} = load();
const int = v => v | 0;
const nameOf = fn => {
    try {
        fn();
    } catch (e) {
        return e.name;
    }
    return null;
};
const rejection = async promise => {
    try {
        await promise;
    } catch (e) {
        return e.name;
    }
    return null;
};

test("source shape", () => {
    assert.equal(SOURCE.split("\n")[0], "    function __autojs6_js_tools_colors(host) {");
    assert.ok(!SOURCE.includes(")JS\""));
    assert.ok(!/[^\x00-\x7f]/.test(SOURCE), "ASCII only");
    assert.ok(!SOURCE.includes("\0"));
    assert.ok(!/\brequire\(|\bprocess\.|\bBuffer\b/.test(SOURCE));
    assert.equal(typeof factory, "function");
    assert.ok(!Object.keys(colors).includes("theme"));
    assert.ok(!("theme" in colors));
});

test("toInt", () => {
    assert.equal(colors.toInt("red"), -65536);
    assert.equal(colors.toInt("#f00"), -65536);
    assert.equal(colors.toInt(0xff123456), int(0xff123456));
    assert.equal(colors.toInt(4294967301), 5);
    assert.equal(colors.toInt(1e20), -1);
    assert.equal(colors.toInt(-1e20), 0);
    assert.equal(colors.toInt(NaN), 0);
    assert.equal(colors.toInt(1.9), 1);
    assert.equal(colors.toInt(-1.9), -1);
    assert.equal(colors.toInt("4294967296"), 0);
    assert.equal(colors.toInt("+16"), 16);
    assert.equal(colors.toInt("green"), int(0xff00ff00));
    assert.equal(colors.toInt("Dark-Gray"), int(0xff444444));
    assert.equal(colors.toInt("#" + String.fromCharCode(0x661, 0x662, 0x663)), int(0xff112233));
    assert.equal(colors.toInt(Color("blue")), int(0xff0000ff));
    assert.equal(colors.toInt("red", undefined), -65536);
    assert.equal(nameOf(() => colors.toInt("nope")), "RangeError");
    assert.equal(nameOf(() => colors.toInt("instance")), "RangeError");
    assert.equal(nameOf(() => colors.toInt(" red")), "RangeError");
    assert.equal(nameOf(() => colors.toInt(null)), "TypeError");
    assert.equal(nameOf(() => colors.toInt([1, 2, 3])), "TypeError");
    assert.throws(() => colors.toInt(), {name: "TypeError", message: "Failed to call method \"colors.toInt\": [ Method only accepts 1 argument ]"});
    assert.throws(() => colors.toInt("nope"), {message: "Failed to call method \"colors.toInt\": [ Argument \"color\" must be of type OmniColor, got \"nope\" ]"});
    assert.throws(() => colors.toInt(true), {message: "Failed to call method \"colors.toInt\": [ Argument \"color\" must be of type OmniColor, got boolean ]"});
});

test("toHex and toString", () => {
    assert.equal(colors.toHex(0x80ff0000), "#80FF0000");
    assert.equal(colors.toHex(0), "#00000000");
    assert.equal(colors.toHex("red", true), "#FFFF0000");
    assert.equal(colors.toHex("#80ff0000", false), "#FF0000");
    assert.equal(colors.toHex("#aabbcc", 3), "#ABC");
    assert.equal(colors.toHex("#80aabbcc", 3), "#ABC");
    assert.equal(colors.toHex("#12345678", "none"), "#345678");
    assert.equal(colors.toHex("red", 8.9), "#FFFF0000");
    assert.equal(nameOf(() => colors.toHex("#aabbcd", 3)), "RangeError");
    assert.equal(nameOf(() => colors.toHex("red", "x")), "RangeError");
    assert.equal(nameOf(() => colors.toHex("red", {})), "TypeError");
    assert.equal(colors.toFullHex("#123"), "#FF112233");
    assert.equal(colors.toString(), "[object Object]");
    assert.equal(colors.toString(undefined, 8), "[object Object]");
    assert.equal(String(colors), "[object Object]");
    assert.equal(colors.toString("red", 8), "#FFFF0000");
});

test("channels", () => {
    assert.equal(colors.alpha("#80ff0000"), 128);
    assert.equal(colors.alpha("#80ff0000", {max: 1}), 128 / 255);
    assert.equal(colors.alpha("red", 1), 255);
    assert.equal(nameOf(() => colors.alpha("red", {max: 2})), "RangeError");
    assert.equal(nameOf(() => colors.alpha("red", {max: "x"})), "TypeError");
    assert.equal(colors.alphaDouble("#01000000"), 1 / 255);
    assert.equal(colors.setAlpha("red", 0.5), int(0x80ff0000));
    assert.equal(colors.setAlpha("red", "50%"), int(0x80ff0000));
    assert.equal(colors.setAlpha("red", 1), -65536);
    assert.equal(colors.setAlpha("red", 300), -65536);
    assert.equal(nameOf(() => colors.setAlpha("red", -1)), "RangeError");
    assert.equal(nameOf(() => colors.setAlpha("red", "x")), "TypeError");
    assert.equal(colors.setAlphaRelative("#80ff0000", 0.5), int(0x40ff0000));
    assert.equal(colors.setAlphaRelative("red", 2), -65536);
    assert.equal(nameOf(() => colors.setAlphaRelative("red", -1)), "RangeError");
    assert.equal(colors.removeAlpha("red"), 0x00ff0000);
    assert.equal(colors.red("#123456"), 0x12);
    assert.equal(colors.setRed("#123456", 1), int(0xff013456));
    assert.equal(colors.setRed("#123456", 0.5), int(0xff803456));
    assert.equal(colors.setRedRelative("#803456", 0.5), int(0xff403456));
    assert.equal(colors.removeGreen("#123456"), int(0xff120056));
    assert.equal(colors.setBlue("#123456", 0.25), int(0xff123440));
});

test("rgb, argb, rgba", () => {
    assert.equal(colors.rgb(255, 0, 0), -65536);
    assert.equal(colors.rgb(1, 0.5, 0), int(0xffff8000));
    assert.equal(colors.rgb(1, 1, 1), int(0xff010101));
    assert.equal(colors.rgb("#80112233"), int(0xff112233));
    assert.equal(colors.rgb(255, 0, 0, undefined), -65536);
    assert.equal(nameOf(() => colors.rgb(1, 2)), "TypeError");
    assert.equal(nameOf(() => colors.rgb([1, 2])), "TypeError");
    assert.equal(nameOf(() => colors.rgb(-1, 0, 0)), "RangeError");
    assert.equal(nameOf(() => colors.rgb([1, , 3])), "TypeError");
    assert.equal(colors.argb([0.5, 1, 0, 0]), int(0x80ff0000));
    assert.equal(colors.argb(1, 255, 0, 0), -65536);
    assert.equal(colors.rgba("#ff000080"), int(0x80ff0000));
    assert.equal(colors.rgba("#ff0000"), -65536);
    assert.equal(colors.rgba("#f00"), -65536);
    assert.equal(colors.rgba([255, 0, 0], 0.5), int(0x80ff0000));
    assert.equal(nameOf(() => colors.rgba(1, 2, 3)), "TypeError");
});

test("hsv and hsl", () => {
    assert.equal(colors.hsv(0, 1, 1), -65536);
    assert.equal(colors.hsv(120, 100, 100), int(0xff00ff00));
    assert.equal(colors.hsv(360, 1, 1), -65536);
    assert.equal(colors.hsv(-120, 1, 1), int(0xff0000ff));
    assert.equal(colors.hsv("50%", 1, 1), int(0xff00ffff));
    assert.equal(colors.hsva(0, 1, 1, 0.5), int(0x80ff0000));
    assert.equal(nameOf(() => colors.hsva(0, 1, 1)), "TypeError");
    assert.equal(nameOf(() => colors.hsv(0, 101, 1)), "RangeError");
    assert.equal(colors.hsl(0, 1, 0.5), -65536);
    assert.equal(colors.hsl(0, 0, 1), -1);
    assert.equal(colors.hsl([120, 1, 0.5]), int(0xff00ff00));
    assert.equal(colors.hsla(0, 1, 0.5, 0.5), int(0x80ff0000));
    assert.deepEqual(colors.toHsv("red"), [0, 1, 1]);
    assert.deepEqual(colors.toHsv(255, 0, 255), [300, 1, 1]);
    assert.deepEqual(colors.toHsv("#336699"), [210, Math.fround(2 / 3), Math.fround(0.6)]);
    assert.deepEqual(colors.toHsl("red"), [0, 1, 0.5]);
    assert.deepEqual(colors.toHsla(255, 0, 0, 0.5), [0, 1, 0.5, 0.5]);
    assert.deepEqual(colors.toHsva("#80ff0000"), [0, 1, 1, 128 / 255]);
    assert.deepEqual(colors.toRgb("#123456"), [18, 52, 86]);
    assert.deepEqual(colors.toRgba("#80123456", {maxAlpha: 1}), [18, 52, 86, 128 / 255]);
    assert.deepEqual(colors.toArgb("#80123456"), [128, 18, 52, 86]);
    assert.equal(nameOf(() => colors.toHsla(255, 0, 0, "0.5")), "TypeError");
    assert.equal(nameOf(() => colors.toHsla(255, 0, 0, 300)), "RangeError");
});

test("strings", () => {
    assert.equal(colors.toRgbString("#123456"), "rgb(18, 52, 86)");
    assert.equal(colors.toRgbaString("#80123456"), "rgba(18, 52, 86, 0.5)");
    assert.equal(colors.toRgbaString("red"), "rgba(255, 0, 0, 1.0)");
    assert.equal(colors.toRgbaString("red", false), "rgba(255, 0, 0, 1)");
    assert.equal(colors.toRgbaString("red", {keepTrailingZeroForFullAlpha: 0}), "rgba(255, 0, 0, 1)");
    assert.equal(colors.toRgbaString(0), "rgba(0, 0, 0, 0)");
    assert.equal(colors.toRgbaString("#F2000000"), "rgba(0, 0, 0, 0.9)");
    assert.equal(colors.toArgbString("#80123456"), "argb(0.5, 18, 52, 86)");
    assert.equal(colors.toHsvString("red"), "hsv(0, 100%, 100%)");
    assert.equal(colors.toHsvString(0, 0, 0), "hsv(0, 0%, 0%)");
    assert.equal(colors.toHsvString("#336699"), "hsv(210, 66.7%, 60%)");
    assert.equal(colors.toHslString("red"), "hsl(0, 100%, 50%)");
    assert.equal(colors.toHsvaString("#80ff0000"), "hsva(0, 100%, 100%, 0.5)");
    assert.equal(colors.summary("red"), "hex(#FF0000), rgba(255,0,0/1.0), int(-65536)");
    assert.equal(colors.summary("#80123456"), "hex(#80123456), rgba(18,52,86/0.5), int(-2146290602)");
    assert.equal(colors.summary(0), "hex(#00000000), rgba(0,0,0/0.0), int(0)");
});

test("comparison and mixing", () => {
    assert.equal(colors.isSimilar("red", "#fe0000"), true);
    assert.equal(colors.isSimilar("red", "#f00000", 4), false);
    assert.equal(colors.isSimilar("red", "#f00000", 5), true);
    assert.equal(colors.isSimilar("red", "#f00000", {similarity: 0.98}), true);
    assert.equal(colors.isSimilar("red", "#f00000", 5, " RGB "), true);
    assert.equal(nameOf(() => colors.isSimilar("red", "red", {threshold: 1, similarity: 1})), "TypeError");
    assert.equal(nameOf(() => colors.isSimilar("red", "red", 4, "bogus")), "RangeError");
    assert.equal(colors.isEqual("red", "#80ff0000"), true);
    assert.equal(colors.isEqual("red", "#80ff0000", true), false);
    assert.equal(colors.isEqual("red", [255, 0, 0]), true);
    assert.equal(nameOf(() => colors.isEqual("red", "red", "yes")), "TypeError");
    assert.equal(colors.distance("red", "#f00000"), 5);
    assert.equal(colors.distance("red", "#f00000", "rgb"), 5);
    assert.ok(Math.abs(colors.distance("red", "blue", {algorithm: "h"}) - 170) < 1e-9);
    assert.equal(colors.distance("red", "#f0fe00", "equal"), 254);
    assert.equal(colors.invert("red"), int(0xff00ffff));
    assert.equal(colors.blend("red", "blue"), int(0xff7f007f));
    assert.equal(colors.blend("red", "blue", 0.25), int(0xffbf003f));
    assert.equal(colors.blend("#00ff0000", "red", 0.5), int(0x7fff0000));
    assert.equal(nameOf(() => colors.blend("red", "blue", 2)), "RangeError");
    assert.ok(Math.abs(colors.contrast("black", "white") - 21) < 1e-9);
    assert.ok(Math.abs(colors.luminance("white") - 1) < 1e-12);
    assert.equal(colors.luminance("black"), 0);
    assert.equal(nameOf(() => colors.contrast("red", "#80ffffff")), "RangeError");
});

test("perceptual and theme functions", () => {
    assert.deepEqual(colors.toOklab("black"), [0, 0, 0]);
    assert.ok(Math.abs(colors.toOklab("white")[0] - 1) < 1e-6);
    assert.equal(colors.oklab([0.5, 0, 0, 0.5]) >>> 24, 128);
    assert.equal(nameOf(() => colors.oklab(2, 0, 0)), "RangeError");
    assert.equal(nameOf(() => colors.oklab(0.5, "x", 0)), "TypeError");
    assert.equal(nameOf(() => colors.oklab([0.5, 0])), "TypeError");
    assert.equal(nameOf(() => colors.oklab(0.5, 0)), "TypeError");
    assert.equal(nameOf(() => colors.oklch(2, -1, 0)), "RangeError");
    assert.equal(nameOf(() => colors.oklch([0.7, , 30])), "TypeError");
    assert.equal(colors.tones("red").length, 10);
    assert.equal(nameOf(() => colors.tones("red", 1)), "RangeError");
    assert.equal(nameOf(() => colors.tones("red", "x")), "TypeError");
    const complementary = colors.harmony("red", "complementary");
    assert.equal(complementary.length, 2);
    assert.equal(complementary[0], -65536);
    assert.deepEqual(colors.harmony("gray", "triadic"), [colors.toInt("gray"), colors.toInt("gray"), colors.toInt("gray")]);
    assert.equal(colors.simulate("#80ff0000", "protanopia") >>> 24, 128);
    assert.equal(nameOf(() => colors.simulate("red", "x")), "RangeError");
    assert.equal(colors.ensureContrast("black", "white"), -16777216);
    assert.equal(colors.ensureContrast("#80777777", "white") >>> 24, 0x80);
    assert.equal(nameOf(() => colors.ensureContrast("red", "#80ffffff")), "RangeError");
    assert.equal(colors.derive("#803366cc").primary, int(0xff3366cc));
    assert.equal(colors.nightVariant("#80ffffff") >>> 24, 255);
});

test("tables, all and the prototype chain", () => {
    assert.equal(colors.android.BLACK, -16777216);
    assert.equal(colors.css.GREEN, int(0xff008000));
    assert.equal(colors.GREEN, int(0xff00ff00));
    assert.equal(colors.all.GREEN, int(0xff00ff00));
    assert.equal(colors.BLUE_GREY_900 !== undefined, true);
    assert.equal(Color.TEAL, int(0xff008080));
    assert.ok(!Object.keys(colors).includes("all"));
    assert.notEqual(colors.all, colors.all);
    assert.equal(Object.keys(colors.all)[0], "AQUA");
    assert.equal(colors.parseColor("#ff0000"), -65536);
    assert.equal(colors.parseColor("Navy"), int(0xff000080));
    assert.equal(nameOf(() => colors.parseColor("#f00")), "IllegalArgumentException");
    assert.equal(nameOf(() => colors.parseColor("")), "StringIndexOutOfBoundsException");
    assert.equal(nameOf(() => colors.parseColor("#gg0000")), "NumberFormatException");
    assert.equal(colors.HSVToColor([0, 1, 1]), -65536);
    assert.equal(colors.HSVToColor(128, [120, 1, 1]), int(0x8000ff00));
    assert.equal(colors.equals(colors), false);
    assert.equal(nameOf(() => colors.equals()), "InternalError");
});

test("Color construction", () => {
    assert.equal(Color().toInt(), -16777216);
    assert.equal(new Color("red").toInt(), -65536);
    assert.equal(Color(255, 0, 0, 0.5).toInt(), int(0x80ff0000));
    assert.throws(() => Color(1, 2), {name: "TypeError", message: /^Failed to call method "Color": \[ No overload accepts the arguments \(number, number\)/});
    assert.throws(() => new Color(1, 2), {message: /"new Color"/});
    assert.equal(nameOf(() => Color(1, 2, 3, 4, 5)), "TypeError");
    assert.equal(nameOf(() => Color(undefined)), "TypeError");
    assert.equal(nameOf(() => colors.build("red", undefined)), "TypeError");
    assert.equal(colors.build(255, 0, 0).toInt(), -65536);
});

test("Color members of the older module", () => {
    assert.equal(Color("red").toHex(null), "#FF0000");
    assert.equal(nameOf(() => Color("red").toHex("keep")), "InvocationTargetException");
    assert.equal(nameOf(() => Color("red").toHex(undefined)), "InvocationTargetException");
    assert.equal(String(Color("#80ff0000")), "#80FF0000");
    assert.equal(Color("#01000000").alpha({max: 1}), 1);
    assert.equal(Color("#01000000").alphaDouble(), 1);
    assert.deepEqual(Color("#80ff0000").argb(), [128, 255, 0, 0]);
    assert.deepEqual(Color(0xff010000).toHsla(), [0, 1, 0.5, 1]);
    assert.equal(Color("#01ff0000").toHsva()[3], 1);
    assert.equal(Color(5).build(0, 0).toInt(), int(0xff050000));
    assert.equal(nameOf(() => Color("red").build(0, 0)), "InvocationTargetException");
    assert.equal(Color(0).oklab(0, 0), -16777216);
    assert.equal(nameOf(() => Color("red").extract()), "InvocationTargetException");
    assert.equal(Color("#000100").toHslString(), "hsl(120, 100%, 50%)");
    assert.equal(colors.toHslString("#000100"), "hsl(120, 100%, 0.2%)");
    assert.equal(Color("red").toStringReadable(), "Color { #FF0000 | rgb(255, 0, 0) | hsl(0, 100%, 50%) | hsv(0, 100%, 100%) | int(-65536) }");
    assert.equal(Color("#80ff0000").toStringReadable(),
        "Color { #80FF0000 | rgba(255, 0, 0, 0.5) | hsla(0, 100%, 50%, 0.5) | hsva(0, 100%, 100%, 0.5) | int(-2130771968) }");
    assert.equal(Color("red").summary(), Color("red").toStringReadable());
    assert.equal(Color("red").equals("#80ff0000"), true);
    assert.equal(Color("red").equals("nope"), false);
    assert.equal(nameOf(() => Color("red").equals()), "InternalError");
    assert.equal(nameOf(() => Color("red").toStringReadable(1)), "InternalError");
    assert.deepEqual(Color("#336699").hsv(), [210, Math.fround(2 / 3), Math.fround(0.6)]);
});

test("Color setters", () => {
    const c = Color("red");
    assert.equal(c.setAlpha(0.5), c);
    assert.equal(c.toInt(), int(0x80ff0000));
    assert.equal(Color("red").setAlpha(0.5).setBlue(255).toHex(), "#80FF00FF");
    assert.equal(nameOf(() => Color("red").setAlpha("50%")), "InternalError");
    assert.equal(nameOf(() => Color("red").setAlpha(NaN)), "IllegalArgumentException");
    assert.equal(nameOf(() => Color("red").setAlpha()), "InternalError");
    assert.equal(Color("#000100").setRed(0).toInt(), int(0xff00ff00));
    assert.equal(Color("blue").setRed(128).toInt(), int(0xff8000ff));
    assert.equal(nameOf(() => Color("blue").setRed("x")), "IllegalArgumentException");
    assert.equal(nameOf(() => Color("blue").setRed(-1)), "InternalError");
    assert.equal(Color("#02ff0000").setAlphaRelative(0.5).toInt(), -65536);
    assert.equal(nameOf(() => Color("red").setAlphaRelative(-1)), "IllegalArgumentException");
    assert.equal(Color("red").setRgba("#ff0000").toInt(), 0x00ffff00);
    assert.equal(Color("red").setRgba("#ff000080").toInt(), int(0x80ff0000));
    assert.equal(Color("#80ff0000").setRgb(0, 0, 255).toInt(), int(0x800000ff));
    assert.equal(nameOf(() => Color("red").setRgb([1, 2])), "RuntimeException");
    assert.equal(nameOf(() => Color("red").setRgb("nope")), "IllegalArgumentException");
    assert.equal(nameOf(() => Color("red").setRgb(1, 2)), "InternalError");
    assert.equal(nameOf(() => Color("red").setHsv(0, 101, 1)), "InternalError");
    assert.equal(nameOf(() => Color("red").setHsva(0, 1, 1)), "InternalError");
    assert.equal(Color("#80000000").setHsv(120, 1, 1).toInt(), int(0x8000ff00));
    assert.equal(Color("red").setHsla(240, 1, 0.5, 0.5).toInt(), int(0x800000ff));
    assert.equal(Color("red").removeAlpha().toInt(), 0x00ff0000);
});

test("members through the Java facade", async () => {
    const floats = await java.newArray("float", 3);
    assert.deepEqual(await colors.toHsv("#336699", floats), [210, Math.fround(2 / 3), Math.fround(0.6)]);
    assert.deepEqual(await java.toArray(floats), [210, Math.fround(2 / 3), Math.fround(0.6)]);
    const longer = await java.newArray("float", 5);
    assert.deepEqual(await colors.toHsv("red", longer), [0, 1, 1, 0, 0]);
    const four = await java.newArray("float", 5);
    assert.deepEqual(await colors.toHsva("#80ff0000", four), [0, 1, 1, 128 / 255]);
    assert.equal(await rejection(colors.toHsv("red", await java.newArray("float", 2))), "RangeError");
    assert.equal(nameOf(() => colors.toHsv("red", [0, 0, 0])), "TypeError");
    assert.deepEqual(await Color("red").toHsv(await java.newArray("float", 4)), [0, 1, 1, 0]);
    assert.equal(nameOf(() => Color("red").toHsv("x")), "InvocationTargetException");
    assert.equal(await (await colors.toColorStateList("red")).call("getDefaultColor"), -65536);
    assert.equal(await (await colors.toColorStateList("blue", "red")).call("getDefaultColor"), int(0xff0000ff));
    assert.equal(nameOf(() => colors.toColorStateList()), "TypeError");
    const paint = await java.create("android.graphics.Paint");
    assert.equal(await colors.setPaintColor(paint, "#80ff0000"), undefined);
    assert.equal(await paint.call("getColor"), int(0x80ff0000));
    assert.equal(nameOf(() => colors.setPaintColor({}, "red")), "TypeError");
    const color = Color("blue");
    assert.equal(await color.setPaintColor(paint), color);
    assert.equal(await paint.call("getColor"), int(0xff0000ff));
    assert.equal(nameOf(() => Color("red").setPaintColor("x")), "IllegalArgumentException");
    const theme = await colors.themeColor;
    assert.equal(theme.className, THEME_COLOR);
    assert.equal(colors.toInt(theme), -12627531);
    assert.equal(Color(theme).toInt(), -12627531);
});

test("extract", async () => {
    const config = await java.getStatic("android.graphics.Bitmap$Config", "ARGB_8888");
    const bmp = await java.callStatic("android.graphics.Bitmap", "createBitmap", [4, 4, config]);
    await bmp.call("eraseColor", [-65536]);
    for (let i = 0; i < 4; i++) await bmp.call("setPixel", [i, 3, -16776961]);
    const img = await java.callStatic(IMAGE_WRAPPER, "ofBitmap", [bmp]);
    assert.deepEqual(await colors.extract(img, {count: 2}), [-65536, -16776961]);
    assert.equal(await img.call("isRecycled"), true);
    assert.deepEqual(await colors.extract(img, {region: [0, 3, 4, 1]}), [-16776961]);
    assert.equal(await rejection(colors.extract(img, {region: [0, 0, 9, 9]})), "IllegalArgumentException");
    const paint = await java.create("android.graphics.Paint");
    assert.equal(await rejection(colors.extract(paint, {})), "TypeError");
    assert.equal(await paint.call("getColor"), -16777216);
    assert.equal(nameOf(() => colors.extract("red")), "TypeError");
    assert.equal(nameOf(() => colors.extract(img, 5)), "TypeError");
    assert.equal(nameOf(() => colors.extract(img, {count: 0})), "RangeError");
    // A Bitmap handle stays the caller's; pixels as images.readPixels gives them are sampled here.
    const bmp2 = await java.callStatic("android.graphics.Bitmap", "createBitmap", [4, 4, config]);
    await bmp2.call("eraseColor", [-65536]);
    assert.deepEqual(await colors.extract(bmp2, {count: 2}), [-65536]);
    assert.equal(await bmp2.call("getWidth"), 4);
    const data = Array(16).fill(-65536).fill(-16776961, 12);
    assert.deepEqual(await colors.extract({data, width: 4, height: 4}, {count: 2}), [-65536, -16776961]);
    assert.deepEqual(await colors.extract({data, width: 4, height: 4}, {region: [0, 3, 4, 1]}), [-16776961]);
    assert.equal(await rejection(colors.extract({data, width: 4, height: 4}, {region: [0, 0, 9, 9]})), "IllegalArgumentException");
    assert.equal(nameOf(() => colors.extract({data: [1], width: 4, height: 4})), "TypeError");
});

test("older Android degrees before API 33", () => {
    const old = load(30);
    assert.equal(typeof old.colors.oklch(0.7, 0.1, 30), "number");
    assert.deepEqual(old.colors.toOklch("red").map(v => Math.round(v * 1e6)), colors.toOklch("red").map(v => Math.round(v * 1e6)));
});

test("every case evaluates in Node", async () => {
    assert.ok(CASES.length >= 300, `${CASES.length} cases`);
    const ids = new Set();
    const failures = [];
    for (const entry of CASES) {
        const [id, rhino, node] = entry;
        assert.ok(entry.length === 2 || entry.length === 3, id);
        assert.ok(!ids.has(id), `duplicate id ${id}`);
        ids.add(id);
        const expression = entry.length === 3 ? node : rhino;
        if (expression === null) continue;
        const fresh = load();
        let fn;
        try {
            fn = new Function("colors", "Color", "java", `return (${expression});`);
        } catch (e) {
            failures.push(`${id}: ${e.name}: ${e.message}`);
            continue;
        }
        try {
            await fn(fresh.colors, fresh.Color, fresh.java);
        } catch (e) {
            if (e instanceof ReferenceError || e instanceof SyntaxError) failures.push(`${id}: ${e.name}: ${e.message}`);
        }
    }
    assert.deepEqual(failures, []);
});
