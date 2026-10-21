/// <reference path="../../../docs/nodejs/types/autojs6-node/index.d.ts" />
import pinyin = require("pinyin");
import pinyin4j = require("pinyin4j");
import screen = require("screen_metrics");
import compat = require("autojs6:compat");
import accessibility = require("accessibility");
import { getAvailableSources } from "input_observer";
import fetchModule = require("fetch");
import axios = require("axios");
import profile = require("autojs6:profile");

async function verifyPublicEntry(): Promise<void> {
  const text: string = await pinyin.simple("\u5317\u4eac");
  const rows: pinyin.PinyinResult = await pinyin.convert(text, {
    segment: true,
    customDictionary: { "\u4eac": [["jing1"]] }
  });
  const compact: string[][] = await rows.compact();
  const readings: string[][] = await pinyin4j.readings(text, { romanization: "hanyu" });
  const width: number = await screen.cX();
  const ratioCoordinate: number = await screen.cX("0.5", undefined, true);
  // @ts-expect-error Zero arguments queries the display, but an undefined coordinate is invalid.
  screen.cX(undefined);
  // @ts-expect-error The cY value overload also requires a defined coordinate.
  screen.cY(undefined);
  const crossed: number = await compat.cXy(0.5, "16:9", "auto");
  await compat.setScaleBases(720, 1280);
  const info: screen.ScreenInfo = await screen.getInfo();
  await console.show();
  await console.setTitle("Coverage");
  await console.clear();
  await console.hide();
  const swipe: boolean = await accessibility.swipe(0, 0, 10, 10, 300);
  const gesture: boolean = await accessibility.gesture(300, [[0, 0], [10, 10]]);
  await getAvailableSources();
  const fetchRequest: fetchModule.FetchModule = fetchModule.request;
  const axiosError: axios.AxiosError = new axios.AxiosError("test", "TEST");
  const inspectorEnabled: boolean = profile.inspector;
  const processPermissions: AutoJs6Node.ProcessPermissionSnapshot = profile.processPermission;
  // A synchronous result would hide missing awaits during migration.
  // @ts-expect-error Pinyin conversion crosses the asynchronous host bridge.
  const synchronousText: string = pinyin.simple(text);
  // @ts-expect-error Console window setters cannot be chained synchronously.
  console.show().setTitle("Not supported");
  void [compact, readings, width, crossed, info, swipe, gesture, synchronousText,
    fetchRequest, axiosError, inspectorEnabled, processPermissions, ratioCoordinate];
}
void verifyPublicEntry;

// Scope projections retain their result type across callbacks and gestures.
import scopeFlow = require("autojs6:flow");
async function checkFlowScopesAndGestures() {
  const context = scopeFlow.of(7).scope({resultType: "text"});
  const text: string = await context.map(async value => value + 1).sleep(1).wait({text: "ready"});
  const points: scopeFlow.PointValue[] = await context.wait({text: "ready"}, {resultType: "points"});
  const stillText: string = await context.wait({text: "ready"});
  const first: scopeFlow.NodeSnapshot = await scopeFlow.wait({desc: "container"}, {resultType: ["child", 0]});
  const nodes: scopeFlow.NodeSnapshot[] = await scopeFlow.wait({text: "ready"}, {resultType: ["#"]});
  const acted: scopeFlow.NodeSnapshot[] | false = await scopeFlow.of(nodes).click();
  const kept: number = await scopeFlow.of(7).press([10, 20], 100).swipe(200, [[10, 20], [30, 40]]).gesture(200, [10, 20, 30, 40]).gestures([200, [10, 20]], [20, 200, [30, 40]]);
  const callback: string = await scopeFlow.of(7).press(10, 20, value => value.toFixed());
  const swipeCallback: string = await scopeFlow.of(7).swipe([10, 20], [30, 40], 200, value => value.toFixed());
  const gestureCallback: string = await scopeFlow.of(7).gesture(200, [10, 20], [30, 40], value => value.toFixed());
  const strokesCallback: string = await scopeFlow.of(7).gestures([200, [10, 20]], value => value.toFixed());
  const withinCallback: string = await scopeFlow.of(7).within({text: "ready"}, node => node.text);
  await scopeFlow.whenPresent("ready", text => text.toUpperCase(), {resultType: "text"});
  const projectedRepeat: scopeFlow.PointValue[] = await scopeFlow.repeatUntil(() => {}, "ready", {resultType: "points"});
  const directSnapshotWait: boolean = await scopeFlow.waitWhile(first, {timeout: 0});
  return {text, points, stillText, first, acted, kept, callback};
}
