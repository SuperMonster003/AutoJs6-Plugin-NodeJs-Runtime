/** @remarks 尚未提供宿主录屏 provider。参见 docs/HOST-API.md。 */
declare module "media_projection" {
  namespace mediaProjection {
    export interface ScreenCaptureOptions extends AutoJs6Node.BridgeCallOptions {
      orientation?: "portrait" | "landscape" | "auto";
      width?: number;
      height?: number;
      requireExistingPermission?: boolean;
    }

    export interface ScreenCapturer {
      readonly id: string;
      readonly width: number;
      readonly height: number;
      /** @deprecated 尚未提供宿主 provider; 当前返回 capabilityProviderMissing。 */
      nextImage(options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;
      /** @deprecated 尚未提供宿主 provider; 当前返回 capabilityProviderMissing。 */
      stop(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export interface MediaProjectionModule {
      /** @deprecated 尚未提供宿主 provider; 当前返回 capabilityProviderMissing。 */
      requestScreenCapture(options?: ScreenCaptureOptions): Promise<ScreenCapturer>;
    }
  }

  const mediaProjection: mediaProjection.MediaProjectionModule;
  export = mediaProjection;
}
