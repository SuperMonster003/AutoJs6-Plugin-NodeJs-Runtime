/// <reference path="../../../docs/nodejs/types/autojs6-node/index.d.ts" />
import opencc = require("autojs6:opencc");
import image = require("image");
import images = require("images");
import compat = require("autojs6:compat");

type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends (<T>() => T extends B ? 1 : 2) ? true : false;
type Assert<T extends true> = T;
type OpenccReturnsPromise = Assert<Equal<ReturnType<typeof opencc.convert>, Promise<string>>>;
type PixelsReturnData = Assert<Equal<ReturnType<typeof images.readPixels>, Promise<image.ImagePixels>>>;

async function checkImageTypes(source: AutoJs6Node.ImageHandle, bytes: Uint8Array): Promise<void> {
  const text: string = await opencc("\u6c49\u8bed", "s2t");
  const regional: string = await opencc.jp2twi(text);
  // @ts-expect-error Null text is rejected rather than silently changed to an empty string.
  opencc.convert(null, "s2t");
  // @ts-expect-error Undefined text is also invalid.
  opencc.s2t(undefined);
  const decoded: AutoJs6Node.ImageHandle = await images.fromBytes(bytes);
  const decodedBase64: AutoJs6Node.ImageHandle = await images.fromBase64("encoded image");
  const encoded: string = await images.toBase64(source, "jpeg", 80);
  const raw: image.ImageBytes = await images.toBytes(source, { format: "rgba", region: [0, 0, 1, 1] });
  const pixels: image.ImagePixels = await compat.images.readPixels(source, { region: [0, 0, 1, 1] });
  const pixel: number = await image.pixel(source, 0, 0);
  const point: AutoJs6Node.Point | null = await images.findColor(source, pixel);
  const points: readonly AutoJs6Node.Point[] = await images.findPointsByColor(source, pixels.data[0]);
  const match: boolean = await images.detectColor(source, pixel, 0, 0, 0);
  const exact = await compat.images.findColorEquals(source, pixel, 0, 0, 1, 1);
  const rotated = await images.rotate(source, 90, { centerX: 1, centerY: 1, filter: false });
  const centered = await compat.images.rotate(source, 45, 1, 1);
  const scaled = await images.scale(source, 2, 2, { interpolation: "INTER_NEAREST" });
  const flipped = await images.flip(source, false, true);
  const blurred = await images.blur(source, [3, 3]);
  const gaussian = await images.gaussianBlur(source, 3, 1, 1);
  const median = await images.medianBlur(source, [3, 3]);
  const copied = await images.copy(source);
  const inverted = await images.invert(source);
  const saved: boolean = await images.save(source, "output.png");
  const height: number = images.getHeight(source);
  const stopped: boolean = await image.stopScreenCapture();
  await images.recycle(decoded);
  const recycled: boolean = images.isRecycled(decoded);
  // @ts-expect-error Encoded-image base64 does not accept raw RGBA.
  images.toBase64(source, "rgba");
  // @ts-expect-error Byte decoding does not accept a text string.
  images.fromBytes("not bytes");
  // @ts-expect-error filter is a boolean.
  images.rotate(source, 90, { filter: "yes" });
  void [regional, decodedBase64, encoded, raw, point, points, match, exact, rotated, centered,
    scaled, flipped, blurred, gaussian, median, copied, inverted, saved, height, stopped, recycled];
}
void checkImageTypes;
