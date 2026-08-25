"use strict";
// M2.4 npm ecosystem smoke: one minimal assertion per package. Each line
// prints npm.<package>=ok only when the package's core behavior works.

function check(label, action) {
  try {
    const value = action();
    if (value !== true) {
      throw new Error("assertion returned " + value);
    }
    console.log("npm." + label + "=ok");
  } catch (error) {
    console.error("npm." + label + "=fail " + (error && error.message));
    process.exitCode = 1;
  }
}

async function checkAsync(label, action) {
  try {
    const value = await action();
    if (value !== true) {
      throw new Error("assertion returned " + value);
    }
    console.log("npm." + label + "=ok");
  } catch (error) {
    console.error("npm." + label + "=fail " + (error && error.message));
    process.exitCode = 1;
  }
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      server.removeListener("error", reject);
      resolve(server.address().port);
    });
  });
}

function close(server) {
  return new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}

function httpGet(port, path) {
  const http = require("node:http");
  return new Promise((resolve, reject) => {
    const request = http.get({ hostname: "127.0.0.1", port, path }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { body += chunk; });
      response.on("end", () => resolve({ status: response.statusCode, body }));
    });
    request.on("error", reject);
  });
}

check("lodash", () => {
  const _ = require("lodash");
  return _.chunk([1, 2, 3, 4], 2).length === 2 &&
      _.get({ a: { b: [0, { c: 7 }] } }, "a.b[1].c") === 7 &&
      _.debounce(() => 0, 10) !== undefined;
});

check("dayjs", () => {
  const dayjs = require("dayjs");
  const day = dayjs("2026-08-18T12:34:56.000Z");
  return day.isValid() && day.add(1, "day").format("YYYY-MM-DD").length === 10;
});

check("ms", () => {
  const ms = require("ms");
  return ms("2h") === 7200000 && ms(60000) === "1m";
});

check("semver", () => {
  const semver = require("semver");
  return semver.gt("2.0.0", "1.9.9") &&
      semver.satisfies("24.5.0", ">=24 <25") &&
      semver.parse(process.version.slice(1)) !== null;
});

check("uuid", () => {
  const { v4, validate } = require("uuid");
  const id = v4();
  return validate(id) && id.length === 36;
});

check("debug", () => {
  const debug = require("debug");
  const log = debug("m2:test");
  log("invisible unless DEBUG set");
  debug.enable("m2:*");
  return debug.enabled("m2:test") === true;
});

check("mime", () => {
  const mime = require("mime");
  return mime.getType("json") === "application/json" &&
      mime.getExtension("text/html") === "html";
});

check("qs", () => {
  const qs = require("qs");
  const parsed = qs.parse("a[b]=1&list[0]=x&list[1]=y");
  return parsed.a.b === "1" && parsed.list.length === 2 &&
      qs.stringify({ q: "node js" }) === "q=node%20js";
});

check("js-yaml", () => {
  const yaml = require("js-yaml");
  const doc = yaml.load("name: autojs6\nitems:\n  - 1\n  - 2\n");
  return doc.name === "autojs6" && doc.items[1] === 2 &&
      yaml.dump({ ok: true }).includes("ok: true");
});

check("ajv", () => {
  const Ajv = require("ajv");
  const ajv = new Ajv();
  const validateFn = ajv.compile({
    type: "object",
    properties: { count: { type: "integer", minimum: 1 } },
    required: ["count"],
  });
  return validateFn({ count: 3 }) === true && validateFn({ count: 0 }) === false;
});

(async () => {
  await checkAsync("express", async () => {
    const express = require("express");
    const app = express();
    app.get("/health", (_request, response) => {
      response.json({ runtime: "autojs6", ok: true });
    });
    const server = app.listen(0, "127.0.0.1");
    try {
      if (!server.listening) {
        await new Promise((resolve, reject) => {
          server.once("listening", resolve);
          server.once("error", reject);
        });
      }
      const response = await httpGet(server.address().port, "/health");
      return response.status === 200 &&
          JSON.parse(response.body).runtime === "autojs6";
    } finally {
      await close(server);
    }
  });

  await checkAsync("axios", async () => {
    const http = require("node:http");
    const axios = require("axios");
    const server = http.createServer((_request, response) => {
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({ via: "axios", ok: true }));
    });
    const port = await listen(server);
    try {
      const response = await axios.get("http://127.0.0.1:" + port + "/probe", {
        proxy: false,
        timeout: 5000,
      });
      return response.status === 200 && response.data.via === "axios";
    } finally {
      await close(server);
    }
  });

  await checkAsync("nanoid", async () => {
    const { nanoid } = await import("nanoid");
    const id = nanoid(18);
    return typeof id === "string" && id.length === 18;
  });

  await checkAsync("p-limit", async () => {
    const { default: pLimit } = await import("p-limit");
    const limit = pLimit(1);
    const order = [];
    await Promise.all([
      limit(async () => { order.push("a"); }),
      limit(async () => { order.push("b"); }),
    ]);
    return order.join("") === "ab" && limit.activeCount === 0;
  });

  await checkAsync("yocto-queue", async () => {
    const { default: Queue } = await import("yocto-queue");
    const queue = new Queue();
    queue.enqueue("first");
    queue.enqueue("second");
    return queue.dequeue() === "first" && queue.dequeue() === "second" && queue.size === 0;
  });

  console.log("npm.suite=done");
})().catch((error) => {
  console.error("npm.suite=fail " + (error && error.stack || error));
  process.exitCode = 1;
});
