declare module "mediainfo" {
  namespace mediainfo {
    export type StreamKind = "general" | "video" | "audio" | "text" | "other" | "image" | "menu";
    export type PluginSnapshotSchema =
      | "autojs6-plugin-mediainfo-snapshot-v1"
      | "autojs6-plugin-mediainfo-snapshot-v2";

    export interface ReadOptions extends AutoJs6Node.BridgeCallOptions {
      readonly includeInform?: boolean;
      readonly includeSections?: boolean;
      readonly schema?: PluginSnapshotSchema;
    }

    export interface DefaultReadOptions extends ReadOptions {
      readonly schema?: undefined;
    }

    export interface SectionMap {
      readonly [section: string]: readonly Readonly<Record<string, string>>[];
    }

    export interface Snapshot {
      readonly schema: "autojs6-node-mediainfo-snapshot-v1";
      readonly path: string;
      readonly fileName: string;
      readonly sizeBytes: number;
      readonly inform: string;
      readonly sections: SectionMap;
    }

    export interface PluginSnapshotV1 {
      readonly schema: "autojs6-plugin-mediainfo-snapshot-v1";
      readonly fileName: string;
      readonly sizeBytes: number;
      readonly inform: string;
      readonly sections: SectionMap;
    }

    export type SnapshotJsonValue =
      | string
      | number
      | boolean
      | null
      | readonly SnapshotJsonValue[]
      | Readonly<{ [key: string]: SnapshotJsonValue }>;

    export interface PluginSnapshotV2Track {
      readonly fields: Readonly<Record<string, SnapshotJsonValue>>;
      readonly attributes?: Readonly<Record<string, SnapshotJsonValue>>;
      readonly extra?: Readonly<Record<string, SnapshotJsonValue>>;
    }

    export interface PluginSnapshotV2 {
      readonly schema: "autojs6-plugin-mediainfo-snapshot-v2";
      readonly file: Readonly<{
        name: string;
        sizeBytes: number;
      }>;
      readonly engine: Readonly<{
        name: string;
        version: string;
        url?: string;
      }>;
      readonly inform: string;
      readonly tracks: Readonly<Record<string, readonly PluginSnapshotV2Track[]>>;
    }

    export interface Capabilities {
      readonly schema: "autojs6-node-mediainfo-capabilities-v1";
      readonly snapshotSchemas: readonly PluginSnapshotSchema[];
      readonly defaultSnapshotSchema: PluginSnapshotSchema;
      readonly engineVersion?: string;
    }

    export type PluginSnapshot = PluginSnapshotV1 | PluginSnapshotV2;
    export type ReadResult = Snapshot | PluginSnapshot;

    export interface MediainfoModule {
      (path: string, options: ReadOptions & { readonly schema: "autojs6-plugin-mediainfo-snapshot-v1" }): Promise<PluginSnapshotV1>;
      (path: string, options: ReadOptions & { readonly schema: "autojs6-plugin-mediainfo-snapshot-v2" }): Promise<PluginSnapshotV2>;
      (path: string, options?: DefaultReadOptions): Promise<Snapshot>;
      (path: string, options: ReadOptions): Promise<ReadResult>;
      read(path: string, options: ReadOptions & { readonly schema: "autojs6-plugin-mediainfo-snapshot-v1" }): Promise<PluginSnapshotV1>;
      read(path: string, options: ReadOptions & { readonly schema: "autojs6-plugin-mediainfo-snapshot-v2" }): Promise<PluginSnapshotV2>;
      read(path: string, options?: DefaultReadOptions): Promise<Snapshot>;
      read(path: string, options: ReadOptions): Promise<ReadResult>;
      get(path: string, streamKind: StreamKind, parameter?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<string>;
      capabilities(options?: AutoJs6Node.BridgeCallOptions): Promise<Capabilities>;
    }
  }

  const mediainfo: mediainfo.MediainfoModule;
  export = mediainfo;
}
