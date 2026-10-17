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
