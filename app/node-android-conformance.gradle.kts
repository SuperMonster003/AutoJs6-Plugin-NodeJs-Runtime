import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile

private data class NodeAndroidConformanceTreeIdentity(
    val path: String,
    val files: Int,
    val bytes: Long,
    val pathSizeContentSha256: String,
)

private data class NodeAndroidConformanceFileIdentity(
    val path: String,
    val bytes: Long,
    val sha256: String,
)

private val nodeAndroidConformanceSchema =
    "autojs6-node-plugin-android-conformance-harness-v1"
private val nodeAndroidConformanceTestClass =
    "io.github.supermonster003.autojs6.plugin.nodejs.NodeRuntimePluginAndroidConformanceTest"
private val nodeAndroidConformanceTypeScriptSelectors = listOf(
    "x3e_06_tsEntryRunsAsCommonJsWithPluginStripper",
    "x3e_07_tsEntryFollowsPackageTypeModule",
    "x3e_08_mtsEntryRemovesTypeOnlyImport",
    "x3e_09_ctsModuleSourcesCanBeRequired",
    "x3e_10_packagedTsAndMtsModuleSourcesGraphPasses",
    "x3e_11_tsxEntryFailsCanonicalBeforeNative",
    "x3e_12_enumEntryFailsCanonicalBeforeNative",
)
private val nodeAndroidConformanceX3gSelectors = listOf(
    "x3g_18_nodeCompatCorpusV1PassesThroughPublishedBinder",
    "x3g_19_preloadShapedModuleSourcesOverridesWorkspaceDisk",
)
private val nodeAndroidConformanceX3hGraphSelectors = listOf(
    "x3h_20_rawTsEntryLoadsExtensionlessCjsThroughExactProvider",
    "x3h_21_packageMetadataMainIndexAndBareGraphUsesExactProvider",
)
private val nodeAndroidConformanceX3hSelectors = nodeAndroidConformanceX3hGraphSelectors + listOf(
    "x3h_22_workspaceCloseSerializesWithProviderMaterialization",
)
private val nodeAndroidConformanceX3iSelectors = listOf(
    "x3i_23_zlibCallbacksStreamsAndShadowingPassThroughPublishedBinder",
)
private val nodeAndroidConformanceX3jSelectors = listOf(
    "x3j_24_cryptoSafeExpansionAndShadowingPassThroughPublishedBinder",
)
private val nodeAndroidConformanceSelectors = listOf(
    "x3d_01_remoteRuntimeInfoAndPrewarmUseDedicatedNativeProcess",
    "x3d_02_realCjsAndEsmExecuteThroughPublishedBinder",
    "x3d_03_materializedPlaintextCtsUsesNotEncryptedPrivatePreparation",
    "x3d_04_tsxFailsCanonicalAndStillEmitsOneTerminalEvent",
    "x3e_05_nodeCompatCorpusV2PassesThroughPublishedBinder",
) + nodeAndroidConformanceTypeScriptSelectors + listOf(
    "x3f_13_missingComputedCtsMaterializesThroughProviderV2AndStaysProtected",
    "x3f_14_workspaceDirectExclusivePublicationUsesMode0600",
    "x3f_15_workspaceExclusiveConflictPreservesSentinel",
    "x3f_16_workspaceDeadlineRemovesOwnedPartialAndDirectories",
    "x3f_17_workspaceTimeoutNeverDeletesForeignReplacement",
) + nodeAndroidConformanceX3gSelectors + nodeAndroidConformanceX3hSelectors +
    nodeAndroidConformanceX3iSelectors + nodeAndroidConformanceX3jSelectors
private val nodeAndroidConformanceCorpusV1RelativeRoot =
    "src/androidTest/assets/node_compat_corpus"
private val nodeAndroidConformanceCorpusV1Files = listOf(
    "cjs-basic/index.js",
    "cjs-basic/package.json",
    "cjs-buffer-path-util/index.js",
    "cjs-buffer-path-util/package.json",
    "cjs-deep-deps/index.js",
    "cjs-deep-deps/node_modules/first-dep/index.js",
    "cjs-deep-deps/node_modules/first-dep/node_modules/second-dep/index.js",
    "cjs-deep-deps/node_modules/first-dep/node_modules/second-dep/package.json",
    "cjs-deep-deps/node_modules/first-dep/package.json",
    "cjs-deep-deps/package.json",
    "cjs-disabled-feature-detection/index.js",
    "cjs-disabled-feature-detection/package.json",
    "cjs-fs-promisify/index.js",
    "cjs-fs-promisify/package.json",
    "cjs-json-cache/data.json",
    "cjs-json-cache/index.js",
    "cjs-json-cache/package.json",
    "cjs-package-exports/feature.js",
    "cjs-package-exports/index.js",
    "cjs-package-exports/package.json",
    "cjs-package-exports/private.js",
    "cjs-package-imports/index.js",
    "cjs-package-imports/lib/alias.js",
    "cjs-package-imports/package.json",
    "cjs-self-reference/feature.js",
    "cjs-self-reference/index.js",
    "cjs-self-reference/package.json",
)
private val nodeAndroidConformanceCorpusV1ContentSha256 =
    "f3d06e371255d9997aa478e57d75ca62c3905fdf83e04b39909c342a1bc3aa3d"
private val nodeAndroidConformanceCorpusV1OwnershipState =
    "migrated-from-host"
private val nodeAndroidConformanceCorpusRelativeRoot =
    "src/androidTest/assets/node_compat_corpus_v2"
private val nodeAndroidConformanceCorpusFiles = listOf(
    "async-esm.mjs",
    "cjs-cycle.cjs",
    "data.json",
    "dynamic.json",
    "esm-cycle.mjs",
    "esm-harness.mjs",
    "json-require.cjs",
    "meta-target.mjs",
    "node_modules/custom-condition-pkg/auto.mjs",
    "node_modules/custom-condition-pkg/default.mjs",
    "node_modules/custom-condition-pkg/import.mjs",
    "node_modules/custom-condition-pkg/package.json",
    "sync-esm.mjs",
    "ts-cjs.cts",
    "ts-esm.mts",
)
private val nodeAndroidConformanceCorpusContentSha256 =
    "b8f7f73d0476d00a55f6010a66a76c2860316d78332b020caa69359fa90cd937"
private val nodeAndroidConformanceHostMigrationState =
    "node_compat_v1_v2_typescript_zlib_and_crypto_android_migrated_from_host"
private val nodeAndroidConformanceHostOwnershipAllowlistSha256 =
    "f3b0d131f33471a264d8092a7edadcadc7fcd4684a7022320351d81451b45aaa"
private val nodeAndroidConformanceHostOwnershipReportSha256 =
    "3cac92586d89c5c5251791e6a4ca3c40e15183256e5a957a2bdb57e2d10a3f3b"
private val nodeAndroidConformanceX3gHostOwnershipAllowlistSha256 =
    "58cfc8d9f111a3b8c1946e789fc863ad384d87d5293405cc5cde7ad9055698dd"
private val nodeAndroidConformanceX3gHostOwnershipReportSha256 =
    "978fa611cc061d9c017f2d3910b4dc3390982ce3c54480bc57d011bac894a714"
private val nodeAndroidConformanceX3gDirectedDeviceReportPath =
    "tools/nodejs/ownership/evidence/x3g-device-run-api34-x86_64.json"
private val nodeAndroidConformanceX3gDirectedDeviceReportBytes = 6200L
private val nodeAndroidConformanceX3gDirectedDeviceReportSha256 =
    "7c3977c0bd0c3a7e750455ebc6611fd35263192e3cab2eb64b5b3095e0d85c42"
private val nodeAndroidConformanceX3gRawDeviceOutputPath =
    "tools/nodejs/ownership/evidence/x3g-device-run-api34-x86_64.raw.txt"
private val nodeAndroidConformanceX3gRawDeviceOutputBytes = 1696L
private val nodeAndroidConformanceX3gRawDeviceOutputSha256 =
    "d51d0991962982a516527e42a42378495a90b8f5b7dd51da86e1c8ea8f101d21"
private val nodeAndroidConformanceX3gOriginalDirectedDeviceReportPath =
    "build/reports/nodejs/android-conformance/device-run-x3g-api34-x86_64.json"
private val nodeAndroidConformanceX3gOriginalRawDeviceOutputPath =
    "build/reports/nodejs/android-conformance/device-run-x3g-api34-x86_64.raw.txt"
private val nodeAndroidConformanceX3gInstrumentationPathSetSha256 =
    "e73f249cdbe7db836b7f3397c5fdc0604f7c47ac4ddee8b21e44f90597728c70"
private val nodeAndroidConformanceX3gCorpusPathSetSha256 =
    "7d00b2347f7ac53c56e24f9a2ea2d50aa0466b17b920e27b5ef31c0b443348c5"
private val nodeAndroidConformanceX3gAnnotationAccountingNote =
    "The older 752 narrative omitted the X3f annotation added to an existing frozen " +
        "instrumentation file; current-filesystem arithmetic is 760 - 7 - 1 + 1 - 2 = 751."
private val nodeAndroidConformanceX3iHostRemovalReceiptPath =
    "tools/nodejs/ownership/evidence/x3i-host-removal.json"
private val nodeAndroidConformanceX3iHostRemovalReceiptBytes = 5836L
private val nodeAndroidConformanceX3iHostRemovalReceiptSha256 =
    "80f8f4a86359fb94a926a813bb8c49c5611b9b1ecbbd90a97d0dda1a078c3aa4"
private val nodeAndroidConformanceX3iDirectedDeviceReportPath =
    "tools/nodejs/ownership/evidence/x3i-device-run-api34-x86_64.json"
private val nodeAndroidConformanceX3iDirectedDeviceReportBytes = 9404L
private val nodeAndroidConformanceX3iDirectedDeviceReportSha256 =
    "7096aaa64c452668a0d9702b21bfd02fcbae216eafaee58046dc3ccb28b7caf2"
private val nodeAndroidConformanceX3iRawDeviceOutputPath =
    "tools/nodejs/ownership/evidence/x3i-device-run-api34-x86_64.raw.txt"
private val nodeAndroidConformanceX3iRawDeviceOutputBytes = 957L
private val nodeAndroidConformanceX3iRawDeviceOutputSha256 =
    "2b984dc7d588d467e21fe109eb11690103bde01289df52c8c5e64c623108eb4f"
private val nodeAndroidConformanceX3iAppBytes = 33650087L
private val nodeAndroidConformanceX3iAppSha256 =
    "845d98057290cd36ff41fabf5f1d23b364ec80012aaec9ed6aa52167f2a6b54c"
private val nodeAndroidConformanceX3iTestApkBytes = 988486L
private val nodeAndroidConformanceX3iTestApkSha256 =
    "f5cf53c7f4b63ebc6cc0b5c54a41cab4f4d859c7d065b9427f7f21e09de61bd4"
private val nodeAndroidConformanceX3iTestSourceBytes = 127311L
private val nodeAndroidConformanceX3iTestSourceSha256 =
    "c0732b8e70290859ef1582390c0ad3c5c234c61e5761f642c59edcf9aa658566"
private val nodeAndroidConformanceX3iSignerSha256 =
    "31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213"
private val nodeAndroidConformanceX3iHostOwnershipReportSha256 =
    "9270e91ab51f63ac5b3a7d620b3d0aab8b09b9d284439e7643156fc71add957d"
private val nodeAndroidConformanceX3iHostOwnershipAllowlistSha256 =
    "8694bdb1ca5448bdca494056176822ca9bcebffef9ceb00f705d21ebae0089c8"
private val nodeAndroidConformanceX3iInstrumentationPathSetSha256 =
    "860f6e684f5251dcef6f33f63d8d5415137461cecd1bcb4810bd758cd50d9ae1"
private val nodeAndroidConformanceX3jHostRemovalReceiptPath =
    "tools/nodejs/ownership/evidence/x3j-host-removal.json"
private val nodeAndroidConformanceX3jHostRemovalReceiptBytes = 9269L
private val nodeAndroidConformanceX3jHostRemovalReceiptSha256 =
    "ec7a5f25581ede1350dce4c7cf236da26a939e1589cc6006bfc3f8ab5ca9bd7e"
private val nodeAndroidConformanceX3jDirectedDeviceReportPath =
    "tools/nodejs/ownership/evidence/x3j-device-run-api34-x86_64.json"
private val nodeAndroidConformanceX3jDirectedDeviceReportBytes = 12632L
private val nodeAndroidConformanceX3jDirectedDeviceReportSha256 =
    "dc34afd236864d5a79a9bbb7bac746c451f6502e53a3952f2ff42c3875e30c29"
private val nodeAndroidConformanceX3jRawDeviceOutputPath =
    "tools/nodejs/ownership/evidence/x3j-device-run-api34-x86_64.raw.txt"
private val nodeAndroidConformanceX3jRawDeviceOutputBytes = 954L
private val nodeAndroidConformanceX3jRawDeviceOutputSha256 =
    "861eec05bcc73202f3dae515f93ffc9903581c8bc7a79df6e4417b34d34b8222"
private val nodeAndroidConformanceX3jAppBytes = 33764503L
private val nodeAndroidConformanceX3jAppSha256 =
    "93f4dd32076a4f4f2d24b9c614962dddf83c3c6fad420c986d5f7c8d4a419de9"
private val nodeAndroidConformanceX3jTestApkBytes = 988518L
private val nodeAndroidConformanceX3jTestApkSha256 =
    "bf56d938648de2f89185b7f4fc3595bfcb8776b2988fef11eac026b6dbd943ca"
private val nodeAndroidConformanceX3jTestSourceBytes = 132491L
private val nodeAndroidConformanceX3jTestSourceSha256 =
    "53b493aeaf6d26c99c48560dc6ee5c24bbf9d8a4635def58da8c3002cb662e56"
private val nodeAndroidConformanceX3jSignerSha256 =
    "31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213"
private val nodeAndroidConformanceX3jHostOwnershipReportSha256 =
    "447fe69b935beaaeb3af285ce0b386c7d967feef20add861891e0a0d942eb20c"
private val nodeAndroidConformanceX3jHostOwnershipAllowlistSha256 =
    "0b44512fd0e37c1dfc7b2e23b6ba44a441d5d8d248699a5be1265b881592215b"
private val nodeAndroidConformanceX3jCurrentOwnershipPolicyBytes = 20704L
private val nodeAndroidConformanceX3jCurrentOwnershipPolicySha256 =
    "4fc6c3bb06a3a4fce8e01e6d3c178a5ba4386fd6d931e2c9ed44adb86cfd8b21"
private val nodeAndroidConformanceX3jCurrentOwnershipVerifierBytes = 56036L
private val nodeAndroidConformanceX3jCurrentOwnershipVerifierSha256 =
    "bb49002a6a8e9197cfdce31a4efaa21cbe258b0c46f096b99747890806757ef9"
private val nodeAndroidConformanceX3jCurrentOwnershipReportBytes = 144027L
private val nodeAndroidConformanceX3jCurrentOwnershipReportSha256 =
    "0bf82b787cfa03a1a34b0b531a952975fdba7e52ac490ddb763bc057d5f486bb"
private val nodeAndroidConformanceX3jHostBacklogPathSetSha256 =
    "2aa453ef274dd3c260c071b1194cf62d6e138c89dc58c0ac865856689853e521"
private val nodeAndroidConformanceX3jInstrumentationPathSetSha256 =
    "b13fe21f53bfb699acb14688736775e01e375871bdebda0b8473d990fb9eb306"
private val nodeAndroidConformanceX3jAppMainIdentity = NodeAndroidConformanceTreeIdentity(
    path = "app/src/main",
    files = 206,
    bytes = 333246859L,
    pathSizeContentSha256 =
        "08b6d70bab11335b3412fbf98cfce4cfb1ef034f500effd964b7a7d2113646b5",
)
private val nodeAndroidConformanceX3jPluginApiIdentity = NodeAndroidConformanceTreeIdentity(
    path = "plugin-api/nodejs-api",
    files = 13,
    bytes = 35361L,
    pathSizeContentSha256 =
        "e060e6ce0015452aa1c7b97c7817cd2ed4da2089cc5b38d012dc0d8d61f7d5ff",
)
private val nodeAndroidConformanceX3jBuildLogicIdentity = NodeAndroidConformanceTreeIdentity(
    path = "build-logic",
    files = 18,
    bytes = 67258L,
    pathSizeContentSha256 =
        "b43f2fd3d39d467fb0babb6db6078fa0a6d127332c4cef8a5c3392a1a1c4a47b",
)
private val nodeAndroidConformanceX3jGradleIdentity = NodeAndroidConformanceTreeIdentity(
    path = "gradle",
    files = 12,
    bytes = 75104L,
    pathSizeContentSha256 =
        "7fc35cc730ed29d6a0a2991202ce4890c61374777642b45320b106efa3eeb604",
)
private val nodeAndroidConformanceX3jRuntimeBuildInputIdentities = linkedMapOf(
    "app/build.gradle.kts" to NodeAndroidConformanceFileIdentity(
        "app/build.gradle.kts",
        7000L,
        "7bcfed41c2c026dec7672302a223ad3d494401d78e37cbfe968002630303aef4",
    ),
    "build.gradle.kts" to NodeAndroidConformanceFileIdentity(
        "build.gradle.kts",
        807L,
        "d05171adeb99c67243593977e40a160ff03dda29f48c391af5c410a04ebf5c93",
    ),
    "settings.gradle.kts" to NodeAndroidConformanceFileIdentity(
        "settings.gradle.kts",
        32456L,
        "1aca2a225531651d5e5f8f4d1fc361da1c69b0bdee03bbf47c85733904764ab9",
    ),
    "gradle.properties" to NodeAndroidConformanceFileIdentity(
        "gradle.properties",
        1037L,
        "078aa3443b6663a4613a9dd7cec56003ce4a5216734dea488fcc8b2f04c35db2",
    ),
    "gradle/libs.versions.toml" to NodeAndroidConformanceFileIdentity(
        "gradle/libs.versions.toml",
        1393L,
        "7c4a409e1b3badd668d3a54bd71ee4ab3395f786a7c39591b8fbac7cc462b41b",
    ),
    "node-plugin-tools.gradle.kts" to NodeAndroidConformanceFileIdentity(
        "node-plugin-tools.gradle.kts",
        9095L,
        "c816fcb0dd1c132bdf2ce524b80b988a927204e360d276be2aeb11f18550cf7d",
    ),
    "app/node-runtime-kit.gradle.kts" to NodeAndroidConformanceFileIdentity(
        "app/node-runtime-kit.gradle.kts",
        9108L,
        "1f31b0bb80b5ed6fe7aa792c13dccb0859e29786a41c8a61c224a130aac6dbba",
    ),
    "app/proguard-rules.pro" to NodeAndroidConformanceFileIdentity(
        "app/proguard-rules.pro",
        591L,
        "c91ca7c26e4c43041664e7a55aa74b9fd34d2328b5b48d47e6bc0d61bb200796",
    ),
    "app/multidex-keep.pro" to NodeAndroidConformanceFileIdentity(
        "app/multidex-keep.pro",
        286L,
        "e512453c0c41d78d304f14d5fdb79d90b607dfa78a9360784b4e1bd821ff6936",
    ),
)
private val nodeAndroidConformanceX3jVersionInputIdentity = NodeAndroidConformanceFileIdentity(
    path = "version.properties",
    bytes = 700L,
    sha256 = "4572f9799fb7cadae5f28cb51ca0cb8b38d7ddf67347970c555d77c399c839d3",
)
private val nodeAndroidConformanceX3jRuntimeKitManifestIdentity =
    NodeAndroidConformanceFileIdentity(
        path = "releases/nodejs-plugin-runtime-kit/1.1.3/node-plugin-runtime-kit.json",
        bytes = 9527L,
        sha256 = "918035e5fddc75a56303c37422ec16dac90b256d9fde3340b39a603ae6cfffd1",
    )
private val nodeAndroidConformanceX3jRuntimeKitAssetIdentity =
    NodeAndroidConformanceFileIdentity(
        path = "app/src/main/assets/nodejs/node-plugin-runtime-kit.json",
        bytes = 9527L,
        sha256 = "918035e5fddc75a56303c37422ec16dac90b256d9fde3340b39a603ae6cfffd1",
    )
private val nodeAndroidConformanceX3jRuntimeKitLockIdentity =
    NodeAndroidConformanceFileIdentity(
        path = "releases/nodejs-plugin-runtime-kit/1.1.3/node-plugin-runtime-kit.lock",
        bytes = 1680L,
        sha256 = "8ad51463971a326df55196717776fb78c4bdfe95cfe560392e5be0989f98d838",
    )
private val nodeAndroidConformanceX3jCapabilityCatalogIdentity =
    NodeAndroidConformanceFileIdentity(
        path = "app/src/main/assets/nodejs/node-capability-catalog.json",
        bytes = 60990L,
        sha256 = "1a33e3f3df88412ea3cc1dbf125886e0daa01862663157eaf2c5c284857a89e7",
    )
private val nodeAndroidConformanceSource = project.layout.projectDirectory.file(
    "src/androidTest/java/io/github/supermonster003/autojs6/plugin/nodejs/" +
        "NodeRuntimePluginAndroidConformanceTest.java",
)
private val nodeAndroidConformanceWorkspaceSource = project.layout.projectDirectory.file(
    "src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/" +
        "PluginWorkspaceArchiveSession.java",
)
private val nodeAndroidConformanceProviderTransportSource =
    project.layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/" +
            "PluginModuleSourceProviderFileTransportSession.java",
    )
private val nodeAndroidConformanceRuntimeServiceSource =
    project.layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/" +
            "NodeJsRuntimePluginService.java",
    )
private val nodeAndroidConformancePermissionManifestSource =
    project.layout.projectDirectory.file(
        "src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/" +
            "NodeBridgePermissionManifest.java",
    )
private val nodeAndroidConformanceNativeBridgeSource = project.layout.projectDirectory.file(
    "src/main/cpp/node_bridge_sources.cpp",
)
private val nodeAndroidConformanceCorpus = project.layout.projectDirectory.dir(
    nodeAndroidConformanceCorpusRelativeRoot,
)
private val nodeAndroidConformanceCorpusV1 = project.layout.projectDirectory.dir(
    nodeAndroidConformanceCorpusV1RelativeRoot,
)
private val nodeAndroidConformanceReport = rootProject.layout.buildDirectory.file(
    "reports/nodejs/android-conformance/harness-package.json",
)
private val nodeAndroidConformanceX3eHostRemovalReceipt =
    rootProject.layout.projectDirectory.file(
        "tools/nodejs/ownership/evidence/x3e-host-removal.json",
    )
private val nodeAndroidConformanceX3gHostRemovalReceipt =
    rootProject.layout.projectDirectory.file(
        "tools/nodejs/ownership/evidence/x3g-host-removal.json",
    )
private val nodeAndroidConformanceX3gDirectedDeviceReport =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3gDirectedDeviceReportPath,
    )
private val nodeAndroidConformanceX3gRawDeviceOutput =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3gRawDeviceOutputPath,
    )
private val nodeAndroidConformanceX3iHostRemovalReceipt =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3iHostRemovalReceiptPath,
    )
private val nodeAndroidConformanceX3iDirectedDeviceReport =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3iDirectedDeviceReportPath,
    )
private val nodeAndroidConformanceX3iRawDeviceOutput =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3iRawDeviceOutputPath,
    )
private val nodeAndroidConformanceX3jHostRemovalReceipt =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3jHostRemovalReceiptPath,
    )
private val nodeAndroidConformanceX3jDirectedDeviceReport =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3jDirectedDeviceReportPath,
    )
private val nodeAndroidConformanceX3jRawDeviceOutput =
    rootProject.layout.projectDirectory.file(
        nodeAndroidConformanceX3jRawDeviceOutputPath,
    )
private val nodeAndroidConformanceCurrentOwnershipPolicy =
    rootProject.layout.projectDirectory.file(
        "tools/nodejs/ownership/runtime-ownership-policy.json",
    )
private val nodeAndroidConformanceCurrentOwnershipVerifier =
    rootProject.layout.projectDirectory.file(
        "tools/nodejs/ownership/verify-runtime-ownership.js",
    )
private val nodeAndroidConformanceCurrentOwnershipReport =
    rootProject.layout.buildDirectory.file(
        "reports/nodejs/runtime-ownership.json",
    )

private fun ByteArray.nodeAndroidConformanceSha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun File.nodeAndroidConformanceSha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(256 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun File.nodeAndroidConformanceNormalizedCorpusSha256(
    relativePaths: List<String>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    relativePaths.sorted().forEach { relativePath ->
        val normalizedText = resolve(relativePath).readText()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        digest.update(relativePath.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        digest.update(normalizedText.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun File.nodeAndroidConformanceRelativePath(): String =
    relativeTo(rootProject.projectDir).invariantSeparatorsPath

private fun nodeAndroidConformanceTreeIdentity(
    root: File,
    excludedPrefixes: Set<String> = emptySet(),
): NodeAndroidConformanceTreeIdentity {
    require(root.isDirectory) { "Missing conformance identity tree: $root" }
    val files = root.walkTopDown()
        .filter(File::isFile)
        .map { file -> file.relativeTo(root).invariantSeparatorsPath to file }
        .filter { (relativePath, _) ->
            excludedPrefixes.none { prefix -> relativePath.startsWith(prefix) }
        }
        .sortedBy { (relativePath, _) -> relativePath }
        .toList()
    val canonical = buildString {
        files.forEach { (relativePath, file) ->
            append(relativePath)
            append('\u0000')
            append(file.length())
            append('\u0000')
            append(file.nodeAndroidConformanceSha256())
            append('\n')
        }
    }
    return NodeAndroidConformanceTreeIdentity(
        path = root.nodeAndroidConformanceRelativePath(),
        files = files.size,
        bytes = files.sumOf { (_, file) -> file.length() },
        pathSizeContentSha256 =
            canonical.toByteArray(StandardCharsets.UTF_8).nodeAndroidConformanceSha256(),
    )
}

private fun nodeAndroidConformanceFileIdentity(
    file: File,
): NodeAndroidConformanceFileIdentity {
    require(file.isFile) { "Missing conformance identity file: $file" }
    return NodeAndroidConformanceFileIdentity(
        path = file.nodeAndroidConformanceRelativePath(),
        bytes = file.length(),
        sha256 = file.nodeAndroidConformanceSha256(),
    )
}

private fun NodeAndroidConformanceTreeIdentity.nodeAndroidConformanceReport(): Map<String, Any> =
    linkedMapOf(
        "path" to path,
        "files" to files,
        "bytes" to bytes,
        "pathSizeContentSha256" to pathSizeContentSha256,
    )

private fun NodeAndroidConformanceFileIdentity.nodeAndroidConformanceReport(): Map<String, Any> =
    linkedMapOf(
        "path" to path,
        "bytes" to bytes,
        "sha256" to sha256,
    )

private val nodeAndroidConformanceX3jVersionInputAtConfiguration =
    nodeAndroidConformanceFileIdentity(rootProject.file("version.properties"))
private val nodeAndroidConformanceX3jNetworkExperimentalAtConfiguration =
    providers.gradleProperty("autojs.nodejs.network.experimental").orElse("false").get()

private fun nodeAndroidConformanceZipEntries(apk: File): Set<String> =
    ZipFile(apk).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }

private fun ByteArray.nodeAndroidConformanceContains(needle: ByteArray): Boolean {
    if (needle.isEmpty()) return true
    if (needle.size > size) return false
    for (start in 0..size - needle.size) {
        var index = 0
        while (index < needle.size && this[start + index] == needle[index]) index++
        if (index == needle.size) return true
    }
    return false
}

private fun File.nodeAndroidConformanceDexContainsUtf8(value: String): Boolean {
    val needle = value.toByteArray(StandardCharsets.UTF_8)
    return ZipFile(this).use { zip ->
        zip.entries().asSequence()
            .filter { Regex("^classes(?:\\d+)?\\.dex$").matches(it.name) }
            .any { entry ->
                zip.getInputStream(entry).use { input ->
                    input.readBytes().nodeAndroidConformanceContains(needle)
                }
            }
    }
}

private fun nodeAndroidConformanceX3iSourceSemantics(testSource: String): Boolean =
    nodeAndroidConformanceX3iSelectors.all { selector ->
        Regex("\\b${Regex.escape(selector)}\\s*\\(").containsMatchIn(testSource)
    } && listOf(
        "node_modules/zlib/index.js",
        "module.exports = { shadow: true }",
        "zlibCallbacksStreamsAndShadowingSource",
        "const zlib = require('zlib')",
        "const nodeZlib = require('node:zlib')",
        "assert.strictEqual(zlib, nodeZlib)",
        "assert.strictEqual(zlib.shadow, undefined)",
        "require.resolve('node:zlib')",
        "rejectsBuiltin('zlib/promises')",
        "rejectsBuiltin('node:zlib/promises')",
        "Buffer.alloc(3 * 1024 * 1024, 65)",
        "promisify(zlib.gzip)",
        "promisify(zlib.brotliCompress)",
        "zlib.createGzip()",
        "zlib.createGunzip()",
        "fs.createReadStream('./stream-source.txt')",
        "fs.createWriteStream('./stream-source.txt.gz')",
        "await collectError([Buffer.from('not gzip')], [zlib.createGunzip()])",
        "Object.isFrozen(zlib.constants)",
        "zlib.buffer.focused.ready=true",
        "x3i-zlib-callbacks-streams-shadowing",
        "assertOneStartedAndOneTerminalEvent",
        "assertWorkspaceOutputCommitted",
    ).all(testSource::contains) && listOf(
        "embedded_script.pending_zlib_callbacks" to "0",
        "embedded_script.pending_zlib_streams" to "false",
        "embedded_script.pending_zlib_stream_count" to "0",
    ).all { diagnostic ->
        Regex(
            "assertNativeValue\\(\\s*invocation\\.result,\\s*" +
                "\"${Regex.escape(diagnostic.first)}\",\\s*" +
                "\"${Regex.escape(diagnostic.second)}\"\\s*\\)",
        ).containsMatchIn(testSource)
    }

@Suppress("UNCHECKED_CAST")
private fun nodeAndroidConformanceX3iOwnershipMigrated(
    ownershipPolicy: Map<String, Any?>,
): Boolean {
    val slices = ownershipPolicy["stagedPluginOnlySlices"] as? List<Map<String, Any?>>
        ?: return false
    if (slices.any { it["id"] == "x3i_zlib_android_handoff" }) return false
    val authorities = (ownershipPolicy["declaredAuthorities"] as? Map<String, Any?>)
        ?.get("plugin") as? List<*>
        ?: return false
    if (authorities.map { it.toString() }.count { it == "zlib_android_conformance" } != 1) {
        return false
    }
    val backlogs = ownershipPolicy["externalMigrationBacklog"] as? List<Map<String, Any?>>
        ?: return false
    val backlog = backlogs.singleOrNull { it["id"] == "host_node_device_tests_and_corpora" }
        ?: return false
    val current = backlog["currentHostBacklog"] as? Map<String, Any?> ?: return false
    val completed = backlog["completedX3iSlice"] as? Map<String, Any?> ?: return false
    val receipt = completed["hostRemovalReceipt"] as? Map<String, Any?> ?: return false
    val directed = completed["directedDevicePrerequisite"] as? Map<String, Any?> ?: return false
    val annotation = backlog["annotationAccounting"] as? Map<String, Any?> ?: return false
    val evidence = (backlog["evidence"] as? List<*>)?.map { it.toString() }?.toSet()
        ?: return false
    return backlog["state"] ==
        nodeAndroidConformanceHostMigrationState &&
        (current["nodeRelatedSourceFiles"] as? Number)?.toInt() == 132 &&
        (current["testAnnotations"] as? Number)?.toInt() == 749 &&
        (current["corpusFiles"] as? Number)?.toInt() == 493 &&
        completed["state"] == "migrated-from-host" &&
        completed["pluginAuthority"] == "zlib_android_conformance" &&
        completed["hostSourceSelector"] ==
            "zlibBufferCallbacksStreamsAndShadowingWork" &&
        completed["pluginSelector"] == nodeAndroidConformanceX3iSelectors.single() &&
        (completed["selectorCount"] as? Number)?.toInt() == 1 &&
        completed["hostRemovalVerified"] == true &&
        completed["migratedAuthorityClaimed"] == true &&
        completed["deviceExecuted"] == true &&
        receipt["path"] == nodeAndroidConformanceX3iHostRemovalReceiptPath &&
        (receipt["bytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3iHostRemovalReceiptBytes &&
        receipt["sha256"] == nodeAndroidConformanceX3iHostRemovalReceiptSha256 &&
        directed["path"] == nodeAndroidConformanceX3iDirectedDeviceReportPath &&
        (directed["bytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3iDirectedDeviceReportBytes &&
        directed["sha256"] == nodeAndroidConformanceX3iDirectedDeviceReportSha256 &&
        directed["rawPath"] == nodeAndroidConformanceX3iRawDeviceOutputPath &&
        (directed["rawBytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3iRawDeviceOutputBytes &&
        directed["rawSha256"] == nodeAndroidConformanceX3iRawDeviceOutputSha256 &&
        (directed["attemptCount"] as? Number)?.toInt() == 1 &&
        (directed["testsRun"] as? Number)?.toInt() == 1 &&
        (directed["testsPassed"] as? Number)?.toInt() == 1 &&
        directed["baselineRestored"] == true &&
        directed["temporaryAvdStartedAndStopped"] == true &&
        directed["serialAbsentAfterShutdown"] == true &&
        (annotation["x3iRemovedZlibAnnotations"] as? Number)?.toInt() == 1 &&
        (annotation["currentHostNodeTestAnnotations"] as? Number)?.toInt() == 749 &&
        evidence.containsAll(
            setOf(
                nodeAndroidConformanceX3iHostRemovalReceiptPath,
                nodeAndroidConformanceX3iDirectedDeviceReportPath,
                nodeAndroidConformanceX3iRawDeviceOutputPath,
            ),
        )
}

private fun nodeAndroidConformanceX3iPackaged(testApk: File): Boolean {
    val requiredValues = listOf(
        "x3i_23_zlibCallbacksStreamsAndShadowingPassThroughPublishedBinder",
        "zlib.buffer.focused.ready=true",
        "embedded_script.pending_zlib_callbacks",
        "embedded_script.pending_zlib_streams",
        "embedded_script.pending_zlib_stream_count",
    )
    for (requiredValue in requiredValues) {
        if (!testApk.nodeAndroidConformanceDexContainsUtf8(requiredValue)) return false
    }
    return true
}

private fun nodeAndroidConformanceX3jSourceSemantics(testSource: String): Boolean =
    nodeAndroidConformanceX3jSelectors.all { selector ->
        Regex("\\b${Regex.escape(selector)}\\s*\\(").containsMatchIn(testSource)
    } && listOf(
        "node_modules/crypto/index.js",
        "module.exports = { shadow: true }",
        "cryptoSafeExpansionAndShadowingSource",
        "const crypto = require(\\\"crypto\\\")",
        "const nodeCrypto = require(\\\"node:crypto\\\")",
        "assert.strictEqual(crypto, nodeCrypto)",
        "assert.strictEqual(crypto.shadow, undefined)",
        "require.resolve(\\\"crypto\\\")",
        "require.resolve(\\\"node:crypto\\\")",
        "crypto.randomUUID()",
        "assert.notStrictEqual(uuidA, uuidB)",
        "crypto.createHmac(\\\"sha256\\\", \\\"key\\\")",
        "9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab",
        "crypto.timingSafeEqual(Buffer.from(\\\"same\\\"), Buffer.from(\\\"same\\\"))",
        "crypto.timingSafeEqual(Buffer.from(\\\"same\\\"), Buffer.from(\\\"diff\\\"))",
        "crypto.timingSafeEqual(Buffer.from(\\\"a\\\"), Buffer.from(\\\"aa\\\"))",
        "typeof crypto.createPrivateKey",
        "typeof crypto.createPublicKey",
        "typeof crypto.generateKeyPair",
        "typeof crypto.createSign",
        "typeof crypto.createVerify",
        "typeof crypto.webcrypto",
        "crypto.subtle, crypto.webcrypto.subtle",
        "typeof crypto.webcrypto.createHmac",
        "typeof crypto.webcrypto.createPrivateKey",
        "crypto.expansion.focused.ready=true",
        "embedded_script.pending_crypto_callbacks",
        "embedded_script.timed_out",
        "assertOneStartedAndOneTerminalEvent",
        "assertWorkspaceOutputCommitted",
        "Crypto staging unexpectedly used a module-source provider",
        "Crypto staging unexpectedly used an exact-graph provider",
        "long timeoutMs",
        "request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs)",
    ).all(testSource::contains) &&
        Regex(
            "execute\\(\\s*\"x3j-crypto-safe-expansion-shadowing\",\\s*" +
                "\"main\\.cjs\",\\s*files,\\s*false,\\s*10_000L\\s*\\)",
        ).containsMatchIn(testSource) &&
        Regex(
            "assertNativeValue\\(\\s*invocation\\.result,\\s*" +
                "\"embedded_script\\.pending_crypto_callbacks\",\\s*\"0\"\\s*\\)",
        ).containsMatchIn(testSource) &&
        Regex(
            "assertNativeValue\\(\\s*invocation\\.result,\\s*" +
                "\"embedded_script\\.timed_out\",\\s*\"false\"\\s*\\)",
        ).containsMatchIn(testSource)

@Suppress("UNCHECKED_CAST")
private fun nodeAndroidConformanceX3jOwnershipMigrated(
    ownershipPolicy: Map<String, Any?>,
): Boolean {
    val slices = ownershipPolicy["stagedPluginOnlySlices"] as? List<Map<String, Any?>>
        ?: return false
    if (slices.any { it["id"] == "x3j_crypto_android_handoff" }) return false
    val authorities = (ownershipPolicy["declaredAuthorities"] as? Map<String, Any?>)
        ?.get("plugin") as? List<*> ?: return false
    if (authorities.map { it.toString() }.count { it == "crypto_android_conformance" } != 1) {
        return false
    }
    val backlogs = ownershipPolicy["externalMigrationBacklog"] as? List<Map<String, Any?>>
        ?: return false
    val backlog = backlogs.singleOrNull { it["id"] == "host_node_device_tests_and_corpora" }
        ?: return false
    val current = backlog["currentHostBacklog"] as? Map<String, Any?> ?: return false
    val completed = backlog["completedX3jSlice"] as? Map<String, Any?> ?: return false
    val receipt = completed["hostRemovalReceipt"] as? Map<String, Any?> ?: return false
    val directed = completed["directedDevicePrerequisite"] as? Map<String, Any?> ?: return false
    val annotation = backlog["annotationAccounting"] as? Map<String, Any?> ?: return false
    val evidence = (backlog["evidence"] as? List<*>)?.map { it.toString() }?.toSet()
        ?: return false
    return backlog["state"] == nodeAndroidConformanceHostMigrationState &&
        (current["nodeRelatedSourceFiles"] as? Number)?.toInt() == 132 &&
        (current["testAnnotations"] as? Number)?.toInt() == 749 &&
        (current["corpusFiles"] as? Number)?.toInt() == 493 &&
        completed["state"] == "migrated-from-host" &&
        completed["pluginAuthority"] == "crypto_android_conformance" &&
        completed["hostSourceSelector"] == "safeCryptoExpansionAndShadowingWork" &&
        completed["pluginSelector"] == nodeAndroidConformanceX3jSelectors.single() &&
        (completed["selectorCount"] as? Number)?.toInt() == 1 &&
        completed["hostRemovalVerified"] == true &&
        completed["migratedAuthorityClaimed"] == true &&
        completed["deviceExecuted"] == true &&
        receipt["path"] == nodeAndroidConformanceX3jHostRemovalReceiptPath &&
        (receipt["bytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3jHostRemovalReceiptBytes &&
        receipt["sha256"] == nodeAndroidConformanceX3jHostRemovalReceiptSha256 &&
        directed["path"] == nodeAndroidConformanceX3jDirectedDeviceReportPath &&
        (directed["bytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3jDirectedDeviceReportBytes &&
        directed["sha256"] == nodeAndroidConformanceX3jDirectedDeviceReportSha256 &&
        directed["rawPath"] == nodeAndroidConformanceX3jRawDeviceOutputPath &&
        (directed["rawBytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3jRawDeviceOutputBytes &&
        directed["rawSha256"] == nodeAndroidConformanceX3jRawDeviceOutputSha256 &&
        (directed["attemptCount"] as? Number)?.toInt() == 1 &&
        (directed["testsRun"] as? Number)?.toInt() == 1 &&
        (directed["testsPassed"] as? Number)?.toInt() == 1 &&
        directed["baselineRestored"] == true &&
        directed["temporaryAvdStartedAndStopped"] == true &&
        directed["serialAbsentAfterShutdown"] == true &&
        (annotation["x3jRemovedCryptoAnnotations"] as? Number)?.toInt() == 1 &&
        (annotation["currentHostNodeTestAnnotations"] as? Number)?.toInt() == 749 &&
        evidence.containsAll(
            setOf(
                nodeAndroidConformanceX3jHostRemovalReceiptPath,
                nodeAndroidConformanceX3jDirectedDeviceReportPath,
                nodeAndroidConformanceX3jRawDeviceOutputPath,
            ),
        )
}

private fun nodeAndroidConformanceX3jPackaged(testApk: File): Boolean {
    val requiredValues = listOf(
        "x3j_24_cryptoSafeExpansionAndShadowingPassThroughPublishedBinder",
        "node_modules/crypto/index.js",
        "crypto.expansion.focused.ready=true",
        "embedded_script.pending_crypto_callbacks",
        "embedded_script.timed_out",
        "9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab",
        "Crypto staging unexpectedly used a module-source provider",
        "Crypto staging unexpectedly used an exact-graph provider",
    )
    for (requiredValue in requiredValues) {
        if (!testApk.nodeAndroidConformanceDexContainsUtf8(requiredValue)) return false
    }
    return true
}

@Suppress("UNCHECKED_CAST")
private fun nodeAndroidConformanceMap(value: Any?): Map<String, Any?>? =
    value as? Map<String, Any?>

private fun nodeAndroidConformanceStrings(value: Any?): List<String>? =
    (value as? List<*>)?.map { it.toString() }

private fun nodeAndroidConformanceX3iEvidenceValid(
    receiptFile: File,
    receipt: Map<String, Any?>,
    deviceReportFile: File,
    device: Map<String, Any?>,
    rawFile: File,
    testApk: File,
    testSourceFile: File,
): Boolean = runCatching {
    val selector = nodeAndroidConformanceX3iSelectors.single()
    val plugin = nodeAndroidConformanceMap(receipt["pluginPrerequisite"]) ?: return@runCatching false
    val receiptDevice = nodeAndroidConformanceMap(plugin["directedDeviceReport"])
        ?: return@runCatching false
    val receiptRaw = nodeAndroidConformanceMap(plugin["rawDirectedDeviceOutput"])
        ?: return@runCatching false
    val receiptArtifacts = nodeAndroidConformanceMap(plugin["artifacts"])
        ?: return@runCatching false
    val receiptApp = nodeAndroidConformanceMap(receiptArtifacts["appDebugX86_64"])
        ?: return@runCatching false
    val receiptTest = nodeAndroidConformanceMap(receiptArtifacts["androidTest"])
        ?: return@runCatching false
    val receiptSource = nodeAndroidConformanceMap(receiptArtifacts["testSource"])
        ?: return@runCatching false
    val receiptPackage = nodeAndroidConformanceMap(plugin["packageOnlyHarnessPrerequisite"])
        ?: return@runCatching false
    val ownership = nodeAndroidConformanceMap(receipt["ownershipGate"])
        ?: return@runCatching false
    val compile = nodeAndroidConformanceMap(receipt["androidTestCompile"])
        ?: return@runCatching false
    val invocation = nodeAndroidConformanceMap(receipt["invocation"])
        ?: return@runCatching false
    val removals = nodeAndroidConformanceMap(receipt["removals"])
        ?: return@runCatching false
    val removedTest = nodeAndroidConformanceMap(removals["hostTestFile"])
        ?: return@runCatching false
    val removedGateRefs = nodeAndroidConformanceMap(removals["hostGateReferences"])
        ?: return@runCatching false
    val references = nodeAndroidConformanceMap(receipt["referenceAudit"])
        ?: return@runCatching false
    val remaining = nodeAndroidConformanceMap(receipt["remainingHostBacklog"])
        ?: return@runCatching false

    val selection = nodeAndroidConformanceMap(device["deviceSelection"])
        ?: return@runCatching false
    val execution = nodeAndroidConformanceMap(device["executionPolicy"])
        ?: return@runCatching false
    val preflight = nodeAndroidConformanceMap(device["preflight"])
        ?: return@runCatching false
    val artifacts = nodeAndroidConformanceMap(device["artifacts"])
        ?: return@runCatching false
    val app = nodeAndroidConformanceMap(artifacts["appDebugX86_64"])
        ?: return@runCatching false
    val test = nodeAndroidConformanceMap(artifacts["androidTest"])
        ?: return@runCatching false
    val source = nodeAndroidConformanceMap(artifacts["testSource"])
        ?: return@runCatching false
    val packageOnly = nodeAndroidConformanceMap(artifacts["packageOnlyHarnessReport"])
        ?: return@runCatching false
    val instrumentation = nodeAndroidConformanceMap(device["instrumentation"])
        ?: return@runCatching false
    val selectorResults = instrumentation["selectors"] as? List<*>
        ?: return@runCatching false
    val selectorResult = nodeAndroidConformanceMap(selectorResults.singleOrNull())
        ?: return@runCatching false
    val restoration = nodeAndroidConformanceMap(device["restoration"])
        ?: return@runCatching false
    val temporaryAvd = nodeAndroidConformanceMap(device["temporaryAvd"])
        ?: return@runCatching false
    val deviceEvidence = nodeAndroidConformanceMap(device["evidence"])
        ?: return@runCatching false
    val rawEvidence = nodeAndroidConformanceMap(deviceEvidence["rawInstrumentationOutput"])
        ?: return@runCatching false
    val managedCopy = nodeAndroidConformanceMap(deviceEvidence["managedImmutableCopy"])
        ?: return@runCatching false
    val raw = rawFile.readText()
    val rawLines = raw.lineSequence().map { it.trimEnd('\r') }.toList()
    val expectedCommand =
        "adb -s emulator-5562 shell am instrument -w -r -e class " +
            "$nodeAndroidConformanceTestClass#$selector " +
            "io.github.supermonster003.autojs6.plugin.nodejs.test/" +
            "androidx.test.runner.AndroidJUnitRunner"

    receiptFile.length() == nodeAndroidConformanceX3iHostRemovalReceiptBytes &&
        receiptFile.nodeAndroidConformanceSha256() ==
            nodeAndroidConformanceX3iHostRemovalReceiptSha256 &&
        receipt["schema"] == "autojs6-node-host-removal-receipt-v1" &&
        receipt["repository"] == "AutoJs6" &&
        receipt["repositoryRole"] == "host" &&
        receipt["scope"] == "x3i-zlib-focused-instrumentation-migration" &&
        plugin["selector"] == selector &&
        (plugin["selectorCount"] as? Number)?.toInt() == 1 &&
        receiptDevice["path"] == nodeAndroidConformanceX3iDirectedDeviceReportPath &&
        (receiptDevice["bytes"] as? Number)?.toLong() ==
            nodeAndroidConformanceX3iDirectedDeviceReportBytes &&
        receiptDevice["sha256"] == nodeAndroidConformanceX3iDirectedDeviceReportSha256 &&
        receiptDevice["device"] == "DEX_R1_API34_X64" &&
        receiptDevice["serial"] == "emulator-5562" &&
        (receiptDevice["apiLevel"] as? Number)?.toInt() == 34 &&
        receiptDevice["abi"] == "x86_64" && receiptDevice["emulator"] == true &&
        (receiptDevice["attemptCount"] as? Number)?.toInt() == 1 &&
        (receiptDevice["testsRun"] as? Number)?.toInt() == 1 &&
        (receiptDevice["testsPassed"] as? Number)?.toInt() == 1 &&
        (receiptDevice["testsFailed"] as? Number)?.toInt() == 0 &&
        (receiptDevice["testsIgnored"] as? Number)?.toInt() == 0 &&
        receiptDevice["runnerSummary"] == "OK (1 test)" &&
        receiptDevice["baselineRestored"] == true &&
        receiptDevice["temporaryAvdStartedAndStopped"] == true &&
        receiptDevice["serialAbsentAfterShutdown"] == true &&
        (receiptDevice["physicalDeviceDirectedCommandCount"] as? Number)?.toInt() == 0 &&
        receiptDevice["connectedTaskUsed"] == false && receiptDevice["soakRun"] == false &&
        receiptRaw["path"] == nodeAndroidConformanceX3iRawDeviceOutputPath &&
        (receiptRaw["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iRawDeviceOutputBytes &&
        receiptRaw["sha256"] == nodeAndroidConformanceX3iRawDeviceOutputSha256 &&
        receiptRaw["verbatimAdbStdoutCaptured"] == true &&
        receiptApp["path"] == app["path"] &&
        (receiptApp["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iAppBytes &&
        receiptApp["sha256"] == nodeAndroidConformanceX3iAppSha256 &&
        receiptApp["packageName"] == "io.github.supermonster003.autojs6.plugin.nodejs" &&
        receiptApp["versionName"] == "1.1.3" &&
        (receiptApp["versionCode"] as? Number)?.toInt() == 40 &&
        receiptApp["signerSha256"] == nodeAndroidConformanceX3iSignerSha256 &&
        receiptTest["path"] == test["path"] &&
        (receiptTest["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iTestApkBytes &&
        receiptTest["sha256"] == nodeAndroidConformanceX3iTestApkSha256 &&
        receiptTest["signerSha256"] == nodeAndroidConformanceX3iSignerSha256 &&
        receiptSource["path"] == source["path"] &&
        (receiptSource["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iTestSourceBytes &&
        receiptSource["sha256"] == nodeAndroidConformanceX3iTestSourceSha256 &&
        (receiptPackage["bytes"] as? Number)?.toLong() == 14227L &&
        receiptPackage["sha256"] ==
            "60e00c1c906eef4853a94303b4832da84742aebd348054b0340854506915119f" &&
        receiptPackage["decision"] == "harness_packaged_not_executed" &&
        (receiptPackage["selectorCount"] as? Number)?.toInt() == 23 &&
        (receiptPackage["passedChecks"] as? Number)?.toInt() == 36 &&
        (receiptPackage["totalChecks"] as? Number)?.toInt() == 36 &&
        ownership["task"] == ":app:verifyNodeRuntimeOwnershipGate" &&
        (ownership["reportBytes"] as? Number)?.toLong() == 10267L &&
        ownership["reportSha256"] == nodeAndroidConformanceX3iHostOwnershipReportSha256 &&
        (ownership["allowlistBytes"] as? Number)?.toLong() == 19520L &&
        ownership["allowlistSha256"] == nodeAndroidConformanceX3iHostOwnershipAllowlistSha256 &&
        ownership["ready"] == true &&
        (ownership["candidateCount"] as? Number)?.toInt() == 2206 &&
        (ownership["classifiedCandidateCount"] as? Number)?.toInt() == 2206 &&
        (ownership["violationCount"] as? Number)?.toInt() == 0 &&
        (ownership["migrationBacklogFileCount"] as? Number)?.toInt() == 1708 &&
        compile["task"] == ":app:compileAppDebugAndroidTestKotlin" &&
        compile["result"] == "passed" && compile["sameInvocationAsOwnershipGate"] == true &&
        invocation["singleGradleInvocation"] == true && invocation["rerunPerformed"] == false &&
        invocation["outerToolTimedOut"] == true && invocation["gradleDaemonResult"] == "BUILD SUCCESSFUL" &&
        invocation["gradleDaemonDuration"] == "3m 14s" &&
        (invocation["actionableTaskCount"] as? Number)?.toInt() == 328 &&
        (invocation["executedTaskCount"] as? Number)?.toInt() == 14 &&
        (invocation["upToDateTaskCount"] as? Number)?.toInt() == 314 &&
        removedTest["path"] ==
            "app/src/androidTest/java/org/autojs/autojs/engine/NodeZlibBufferInstrumentationTest.kt" &&
        removedTest["exists"] == false &&
        removedTest["preRemovalIdentitySource"] == "current_filesystem_including_untracked" &&
        (removedTest["preRemovalBytes"] as? Number)?.toLong() == 6522L &&
        removedTest["preRemovalSha256"] ==
            "815d19a58d0f44fd1db88cc8114ede7ebd89a7fd6c6fd5a14bc9a4a66cf36031" &&
        (removedTest["headBlobBytes"] as? Number)?.toLong() == 6374L &&
        removedTest["headBlobSha256"] ==
            "a1a975eb12083893117f05437f40e84ed2f102b532386138b2ec73aa7adcc68b" &&
        removedTest["symbol"] == "zlibBufferCallbacksStreamsAndShadowingWork" &&
        (removedTest["remainingMatches"] as? Number)?.toInt() == 0 &&
        (removedTest["removedTestAnnotations"] as? Number)?.toInt() == 1 &&
        removedGateRefs["path"] == "app/node-instrumentation-gates.gradle.kts" &&
        (removedGateRefs["removedReferences"] as? Number)?.toInt() == 2 &&
        (removedGateRefs["remainingMatches"] as? Number)?.toInt() == 0 &&
        (references["activeOldClassReferences"] as? Number)?.toInt() == 0 &&
        (references["activeOldSymbolReferences"] as? Number)?.toInt() == 0 &&
        (references["historicalClassReferencesRetained"] as? Number)?.toInt() == 4 &&
        (remaining["nodeRelatedSourceFiles"] as? Number)?.toInt() == 133 &&
        (remaining["testAnnotations"] as? Number)?.toInt() == 750 &&
        (remaining["corpusFiles"] as? Number)?.toInt() == 493 &&
        remaining["instrumentationPathSetSha256"] ==
            nodeAndroidConformanceX3iInstrumentationPathSetSha256 &&
        remaining["corpusPathSetSha256"] == nodeAndroidConformanceX3gCorpusPathSetSha256 &&
        deviceReportFile.length() == nodeAndroidConformanceX3iDirectedDeviceReportBytes &&
        deviceReportFile.nodeAndroidConformanceSha256() ==
            nodeAndroidConformanceX3iDirectedDeviceReportSha256 &&
        rawFile.length() == nodeAndroidConformanceX3iRawDeviceOutputBytes &&
        rawFile.nodeAndroidConformanceSha256() == nodeAndroidConformanceX3iRawDeviceOutputSha256 &&
        device["schema"] == "autojs6-node-plugin-x3i-directed-device-run-v1" &&
        device["milestone"] == "X3i" && device["result"] == "pass" &&
        device["evidenceLevel"] == "directed-device" &&
        selection["requestedAvdName"] == "DEX_R1_API34_X64" &&
        (selection["requestedPort"] as? Number)?.toInt() == 5562 &&
        selection["observedSerial"] == "emulator-5562" &&
        selection["uniqueNewEmulatorSerial"] == true && selection["serialAmbiguous"] == false &&
        (selection["apiLevel"] as? Number)?.toInt() == 34 &&
        nodeAndroidConformanceStrings(selection["abis"]) == listOf("x86_64") &&
        selection["emulator"] == true && selection["roKernelQemu"] == "1" &&
        selection["bootCompletedBeforeRun"] == true &&
        execution["allDirectedDeviceCommandsUsedExplicitSerial"] == true &&
        execution["adbEnumerationCommandsWereReadOnly"] == true &&
        (execution["physicalDeviceDirectedCommandCount"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(execution["physicalSerialsNeverTargeted"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        execution["connectedTaskUsed"] == false && execution["genericConnectedTaskUsed"] == false &&
        execution["gradleTaskUsed"] == false && execution["soakRun"] == false &&
        execution["fullTwentyThreeSelectorHarnessRun"] == false &&
        (execution["selectorCount"] as? Number)?.toInt() == 1 &&
        (execution["attemptCount"] as? Number)?.toInt() == 1 &&
        (execution["retryCount"] as? Number)?.toInt() == 0 &&
        execution["sourceChangedDuringRun"] == false &&
        execution["verbatimAdbStdoutCaptured"] == true &&
        preflight["artifactHashesAndSizesVerifiedBeforeLaunch"] == true &&
        preflight["aaptIdentityVerifiedBeforeInstall"] == true &&
        preflight["localApkSignersVerifiedBeforeInstall"] == true &&
        preflight["allRelevantPackagesAbsent"] == true &&
        preflight["activeInstrumentationDetected"] == false &&
        preflight["nodeOrAutoJsProcessDetected"] == false &&
        app["path"] ==
            "app/build/outputs/apk/debug/autojs6-plugin-nodejs-runtime-v1.1.3-x86_64.apk" &&
        (app["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iAppBytes &&
        app["sha256"] == nodeAndroidConformanceX3iAppSha256 &&
        app["packageName"] == "io.github.supermonster003.autojs6.plugin.nodejs" &&
        app["versionName"] == "1.1.3" && (app["versionCode"] as? Number)?.toInt() == 40 &&
        app["signerSha256"] == nodeAndroidConformanceX3iSignerSha256 &&
        app["deviceInstalledIdentityVerified"] == true &&
        test["path"] == testApk.nodeAndroidConformanceRelativePath() &&
        (test["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iTestApkBytes &&
        test["sha256"] == nodeAndroidConformanceX3iTestApkSha256 &&
        test["packageName"] == "io.github.supermonster003.autojs6.plugin.nodejs.test" &&
        test["targetPackageName"] == "io.github.supermonster003.autojs6.plugin.nodejs" &&
        test["runner"] == "androidx.test.runner.AndroidJUnitRunner" &&
        test["signerSha256"] == nodeAndroidConformanceX3iSignerSha256 &&
        test["deviceInstalledIdentityVerified"] == true &&
        source["path"] == testSourceFile.nodeAndroidConformanceRelativePath() &&
        (source["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iTestSourceBytes &&
        source["sha256"] == nodeAndroidConformanceX3iTestSourceSha256 &&
        (packageOnly["bytes"] as? Number)?.toLong() == 14227L &&
        packageOnly["sha256"] ==
            "60e00c1c906eef4853a94303b4832da84742aebd348054b0340854506915119f" &&
        packageOnly["decision"] == "harness_packaged_not_executed" &&
        (packageOnly["selectorCount"] as? Number)?.toInt() == 23 &&
        (packageOnly["x3iSelectorCount"] as? Number)?.toInt() == 1 &&
        instrumentation["command"] == expectedCommand &&
        instrumentation["className"] == nodeAndroidConformanceTestClass &&
        selectorResult["name"] == selector && selectorResult["result"] == "pass" &&
        (selectorResult["terminalStatusCode"] as? Number)?.toInt() == 0 &&
        (instrumentation["testsRun"] as? Number)?.toInt() == 1 &&
        (instrumentation["testsPassed"] as? Number)?.toInt() == 1 &&
        (instrumentation["testsFailed"] as? Number)?.toInt() == 0 &&
        (instrumentation["testsIgnored"] as? Number)?.toInt() == 0 &&
        instrumentation["runnerSummary"] == "OK (1 test)" &&
        (instrumentation["instrumentationCode"] as? Number)?.toInt() == -1 &&
        (instrumentation["adbExitCode"] as? Number)?.toInt() == 0 &&
        instrumentation["firstAndOnlyRunPassed"] == true &&
        instrumentation["failureEvidence"] == null &&
        instrumentation["hostRemovalPrerequisitePassed"] == true &&
        restoration["baselineRestored"] == true &&
        restoration["pluginPackageAbsentAfterRestore"] == true &&
        restoration["pluginTestPackageAbsentAfterRestore"] == true &&
        restoration["hostAndHostTestPackagesAbsentAfterRestore"] == true &&
        restoration["nodeOrAutoJsProcessAbsentAfterRestore"] == true &&
        restoration["activeInstrumentationAbsentAfterRestore"] == true &&
        restoration["nodeRuntimeServiceAbsentAfterRestore"] == true &&
        restoration["temporaryPulledAppApkDeleted"] == true &&
        restoration["temporaryPulledTestApkDeleted"] == true &&
        temporaryAvd["startedByThisRun"] == true && temporaryAvd["avdName"] == "DEX_R1_API34_X64" &&
        (temporaryAvd["port"] as? Number)?.toInt() == 5562 && temporaryAvd["wipeDataUsed"] == true &&
        temporaryAvd["snapshotsDisabled"] == true && temporaryAvd["windowDisabled"] == true &&
        temporaryAvd["avdDefinitionDeletedOrModified"] == false &&
        (temporaryAvd["preexistingOnlineEmulatorCount"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(temporaryAvd["adbSerialsBeforeLaunch"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        temporaryAvd["newSerial"] == "emulator-5562" && temporaryAvd["newSerialWasUnique"] == true &&
        temporaryAvd["shutdownCommand"] == "adb -s emulator-5562 emu kill" &&
        (temporaryAvd["shutdownExitCode"] as? Number)?.toInt() == 0 &&
        temporaryAvd["shutdownAcknowledged"] == true && temporaryAvd["serialAbsentAfterShutdown"] == true &&
        temporaryAvd["newEmulatorAndQemuProcessIdsAbsentAfterShutdown"] == true &&
        nodeAndroidConformanceStrings(temporaryAvd["adbSerialsAfterShutdown"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        temporaryAvd["serialSetRestored"] == true &&
        temporaryAvd["temporaryAvdStartedAndStopped"] == true &&
        rawEvidence["path"] == nodeAndroidConformanceX3iRawDeviceOutputPath &&
        (rawEvidence["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3iRawDeviceOutputBytes &&
        rawEvidence["sha256"] == nodeAndroidConformanceX3iRawDeviceOutputSha256 &&
        rawEvidence["verbatimAdbStdoutCaptured"] == true &&
        managedCopy["jsonPath"] == nodeAndroidConformanceX3iDirectedDeviceReportPath &&
        managedCopy["rawPath"] == nodeAndroidConformanceX3iRawDeviceOutputPath &&
        rawLines.count { it == "INSTRUMENTATION_STATUS: test=$selector" } == 2 &&
        rawLines.count { it == "INSTRUMENTATION_STATUS_CODE: 1" } == 1 &&
        rawLines.count { it == "INSTRUMENTATION_STATUS_CODE: 0" } == 1 &&
        rawLines.count { it == "OK (1 test)" } == 1 &&
        rawLines.count { it == "INSTRUMENTATION_CODE: -1" } == 1
}.getOrDefault(false)

private fun nodeAndroidConformanceX3jEvidenceValid(
    receiptFile: File,
    receipt: Map<String, Any?>,
    deviceReportFile: File,
    device: Map<String, Any?>,
    rawFile: File,
    x86AppApk: File,
    testApk: File,
    testSourceFile: File,
): Boolean = runCatching {
    val selector = nodeAndroidConformanceX3jSelectors.single()
    val sourceState = nodeAndroidConformanceMap(receipt["sourceState"])
        ?: return@runCatching false
    val plugin = nodeAndroidConformanceMap(receipt["pluginPrerequisite"])
        ?: return@runCatching false
    val receiptDevice = nodeAndroidConformanceMap(plugin["directedDeviceReport"])
        ?: return@runCatching false
    val receiptRaw = nodeAndroidConformanceMap(plugin["rawDirectedDeviceOutput"])
        ?: return@runCatching false
    val receiptAssertions = nodeAndroidConformanceMap(plugin["verifiedAssertions"])
        ?: return@runCatching false
    val receiptArtifacts = nodeAndroidConformanceMap(plugin["artifacts"])
        ?: return@runCatching false
    val receiptApp = nodeAndroidConformanceMap(receiptArtifacts["appDebugX86_64"])
        ?: return@runCatching false
    val receiptTest = nodeAndroidConformanceMap(receiptArtifacts["androidTest"])
        ?: return@runCatching false
    val receiptSource = nodeAndroidConformanceMap(receiptArtifacts["testSource"])
        ?: return@runCatching false
    val receiptPackage = nodeAndroidConformanceMap(plugin["packageOnlyHarnessPrerequisite"])
        ?: return@runCatching false
    val receiptStaging = nodeAndroidConformanceMap(plugin["stagingInputs"])
        ?: return@runCatching false
    val receiptStagingGate = nodeAndroidConformanceMap(receiptStaging["androidConformanceGate"])
        ?: return@runCatching false
    val receiptStagingPolicy = nodeAndroidConformanceMap(receiptStaging["ownershipPolicy"])
        ?: return@runCatching false
    val receiptStagingVerifier = nodeAndroidConformanceMap(receiptStaging["ownershipVerifier"])
        ?: return@runCatching false
    val receiptStagingVersion = nodeAndroidConformanceMap(receiptStaging["versionProperties"])
        ?: return@runCatching false
    val ownership = nodeAndroidConformanceMap(receipt["ownershipGate"])
        ?: return@runCatching false
    val compile = nodeAndroidConformanceMap(receipt["androidTestCompile"])
        ?: return@runCatching false
    val invocation = nodeAndroidConformanceMap(receipt["invocation"])
        ?: return@runCatching false
    val removals = nodeAndroidConformanceMap(receipt["removals"])
        ?: return@runCatching false
    val removedTest = nodeAndroidConformanceMap(removals["hostTestFile"])
        ?: return@runCatching false
    val removedGate = nodeAndroidConformanceMap(removals["hostGateReferences"])
        ?: return@runCatching false
    val removedDocs = nodeAndroidConformanceMap(removals["activeDocumentationReferences"])
        ?: return@runCatching false
    val references = nodeAndroidConformanceMap(receipt["referenceAudit"])
        ?: return@runCatching false
    val stale = nodeAndroidConformanceMap(receipt["staleBuildOutputAudit"])
        ?: return@runCatching false
    val staleDex = nodeAndroidConformanceMap(stale["stalePreExistingDex"])
        ?: return@runCatching false
    val remaining = nodeAndroidConformanceMap(receipt["remainingHostBacklog"])
        ?: return@runCatching false

    val selection = nodeAndroidConformanceMap(device["deviceSelection"])
        ?: return@runCatching false
    val execution = nodeAndroidConformanceMap(device["executionPolicy"])
        ?: return@runCatching false
    val preflight = nodeAndroidConformanceMap(device["preflight"])
        ?: return@runCatching false
    val frozen = nodeAndroidConformanceMap(device["frozenInputs"])
        ?: return@runCatching false
    val frozenApp = nodeAndroidConformanceMap(frozen["appDebugX86_64"])
        ?: return@runCatching false
    val frozenTest = nodeAndroidConformanceMap(frozen["androidTest"])
        ?: return@runCatching false
    val frozenSource = nodeAndroidConformanceMap(frozen["testSource"])
        ?: return@runCatching false
    val frozenPackage = nodeAndroidConformanceMap(frozen["packageOnlyHarnessReport"])
        ?: return@runCatching false
    val frozenGate = nodeAndroidConformanceMap(frozen["androidConformanceGate"])
        ?: return@runCatching false
    val frozenPolicy = nodeAndroidConformanceMap(frozen["runtimeOwnershipPolicy"])
        ?: return@runCatching false
    val frozenVerifier = nodeAndroidConformanceMap(frozen["runtimeOwnershipVerifier"])
        ?: return@runCatching false
    val frozenVersion = nodeAndroidConformanceMap(frozen["versionProperties"])
        ?: return@runCatching false
    val artifacts = nodeAndroidConformanceMap(device["artifacts"])
        ?: return@runCatching false
    val app = nodeAndroidConformanceMap(artifacts["appDebugX86_64"])
        ?: return@runCatching false
    val test = nodeAndroidConformanceMap(artifacts["androidTest"])
        ?: return@runCatching false
    val installation = nodeAndroidConformanceMap(device["installation"])
        ?: return@runCatching false
    val instrumentation = nodeAndroidConformanceMap(device["instrumentation"])
        ?: return@runCatching false
    val assertionReceipt = nodeAndroidConformanceMap(instrumentation["verifiedAssertions"])
        ?: return@runCatching false
    val selectorResult = nodeAndroidConformanceMap(
        (instrumentation["selectors"] as? List<*>)?.singleOrNull(),
    ) ?: return@runCatching false
    val restoration = nodeAndroidConformanceMap(device["restoration"])
        ?: return@runCatching false
    val temporaryAvd = nodeAndroidConformanceMap(device["temporaryAvd"])
        ?: return@runCatching false
    val postRun = nodeAndroidConformanceMap(device["postRunBaseline"])
        ?: return@runCatching false
    val evidence = nodeAndroidConformanceMap(device["evidence"])
        ?: return@runCatching false
    val rawEvidence = nodeAndroidConformanceMap(evidence["rawInstrumentationOutput"])
        ?: return@runCatching false
    val managedCopy = nodeAndroidConformanceMap(evidence["managedImmutableCopy"])
        ?: return@runCatching false
    val rawLines = rawFile.readText().lineSequence().map { it.trimEnd('\r') }.toList()
    val expectedCommand =
        "adb -s emulator-5562 shell am instrument -w -r -e class " +
            "$nodeAndroidConformanceTestClass#$selector " +
            "io.github.supermonster003.autojs6.plugin.nodejs.test/" +
            "androidx.test.runner.AndroidJUnitRunner"
    val requiredAssertions = mapOf(
        "realPublishedBinderExecute" to true,
        "workspaceArchiveV2Committed" to true,
        "oneStartedOneTerminalEvent" to true,
        "pendingCryptoCallbacks" to 0,
        "timedOut" to false,
        "moduleSourceProviderUsed" to false,
        "exactGraphProviderUsed" to false,
    )

    if (!(receiptFile.length() == nodeAndroidConformanceX3jHostRemovalReceiptBytes &&
        receiptFile.nodeAndroidConformanceSha256() ==
            nodeAndroidConformanceX3jHostRemovalReceiptSha256 &&
        receipt["schema"] == "autojs6-node-host-removal-receipt-v1" &&
        receipt["repository"] == "AutoJs6" && receipt["repositoryRole"] == "host" &&
        receipt["scope"] == "x3j-crypto-focused-instrumentation-migration" &&
        receipt["evidenceLevel"] == "S/A with prerequisite directed-device D" &&
        sourceState["decisionInput"] == "current_filesystem_including_untracked" &&
        sourceState["baseCommit"] == "25ec9f8488d942dd13791bfad600fadbc5429893" &&
        sourceState["wholeRepositoryHeadUsedForDecision"] == false &&
        plugin["selector"] == selector && (plugin["selectorCount"] as? Number)?.toInt() == 1 &&
        receiptDevice["path"] == nodeAndroidConformanceX3jDirectedDeviceReportPath &&
        (receiptDevice["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jDirectedDeviceReportBytes &&
        receiptDevice["sha256"] == nodeAndroidConformanceX3jDirectedDeviceReportSha256 &&
        receiptDevice["schema"] == "autojs6-node-plugin-x3j-directed-device-run-v1" &&
        receiptDevice["result"] == "pass" && receiptDevice["device"] == "DEX_R1_API34_X64" &&
        receiptDevice["serial"] == "emulator-5562" &&
        (receiptDevice["apiLevel"] as? Number)?.toInt() == 34 &&
        receiptDevice["abi"] == "x86_64" && receiptDevice["emulator"] == true
    )) return@runCatching false

    if (!((receiptDevice["attemptCount"] as? Number)?.toInt() == 1 &&
        (receiptDevice["retryCount"] as? Number)?.toInt() == 0 &&
        (receiptDevice["testsRun"] as? Number)?.toInt() == 1 &&
        (receiptDevice["testsPassed"] as? Number)?.toInt() == 1 &&
        (receiptDevice["testsFailed"] as? Number)?.toInt() == 0 &&
        (receiptDevice["testsIgnored"] as? Number)?.toInt() == 0 &&
        receiptDevice["runnerSummary"] == "OK (1 test)" &&
        (receiptDevice["instrumentationCode"] as? Number)?.toInt() == -1 &&
        receiptDevice["firstAndOnlyRunPassed"] == true &&
        receiptDevice["baselineRestored"] == true &&
        receiptDevice["temporaryAvdStartedAndStopped"] == true &&
        receiptDevice["serialAbsentAfterShutdown"] == true &&
        (receiptDevice["physicalDeviceDirectedCommandCount"] as? Number)?.toInt() == 0 &&
        receiptDevice["connectedTaskUsed"] == false && receiptDevice["soakRun"] == false &&
        receiptRaw["path"] == nodeAndroidConformanceX3jRawDeviceOutputPath &&
        (receiptRaw["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jRawDeviceOutputBytes &&
        receiptRaw["sha256"] == nodeAndroidConformanceX3jRawDeviceOutputSha256 &&
        receiptRaw["verbatimAdbStdoutCaptured"] == true &&
        requiredAssertions.all { (key, value) -> receiptAssertions[key] == value }
    )) return@runCatching false

    if (!(receiptApp["path"] == frozenApp["path"] &&
        (receiptApp["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jAppBytes &&
        receiptApp["sha256"] == nodeAndroidConformanceX3jAppSha256 &&
        receiptApp["versionName"] == "1.1.3" &&
        (receiptApp["versionCode"] as? Number)?.toInt() == 40 &&
        receiptApp["signerSha256"] == nodeAndroidConformanceX3jSignerSha256 &&
        receiptTest["path"] == frozenTest["path"] &&
        (receiptTest["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jTestApkBytes &&
        receiptTest["sha256"] == nodeAndroidConformanceX3jTestApkSha256 &&
        receiptTest["signerSha256"] == nodeAndroidConformanceX3jSignerSha256 &&
        receiptSource["path"] == frozenSource["path"] &&
        (receiptSource["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jTestSourceBytes &&
        receiptSource["sha256"] == nodeAndroidConformanceX3jTestSourceSha256 &&
        (receiptPackage["bytes"] as? Number)?.toLong() == 25584L &&
        receiptPackage["sha256"] == "b4d98a45aa631c239ac6649b7fce9779cd179144568077df2e1e6faf1e1ff361" &&
        receiptPackage["decision"] == "harness_packaged_not_executed" &&
        receiptPackage["verificationLevel"] == "A" &&
        (receiptPackage["selectorCount"] as? Number)?.toInt() == 24 &&
        (receiptPackage["x3jSelectorCount"] as? Number)?.toInt() == 1 &&
        (receiptPackage["passedChecks"] as? Number)?.toInt() == 41 &&
        (receiptPackage["totalChecks"] as? Number)?.toInt() == 41 &&
        receiptPackage["deviceOrSoakExecuted"] == false
    )) return@runCatching false

    if (!(receiptStagingGate["path"] == "app/node-android-conformance.gradle.kts" &&
        (receiptStagingGate["bytes"] as? Number)?.toLong() == 143871L &&
        receiptStagingGate["sha256"] == "1518e8131e73d9d0ca2f78080500e5b8aba2a674dd965681c819ad3349de48f8" &&
        receiptStagingPolicy["path"] == "tools/nodejs/ownership/runtime-ownership-policy.json" &&
        (receiptStagingPolicy["bytes"] as? Number)?.toLong() == 20053L &&
        receiptStagingPolicy["sha256"] == "a2c8a458c7d2aecf48abdf116403ef339c9d36ea75f70390f3d173245ecedc5b" &&
        receiptStagingVerifier["path"] == "tools/nodejs/ownership/verify-runtime-ownership.js" &&
        (receiptStagingVerifier["bytes"] as? Number)?.toLong() == 38000L &&
        receiptStagingVerifier["sha256"] == "9469d2316e4750f6f425079c4575bcee583abd476f8e95a9a5f3d24756fd854e" &&
        receiptStagingVersion["path"] == "version.properties" &&
        (receiptStagingVersion["bytes"] as? Number)?.toLong() == 700L &&
        receiptStagingVersion["sha256"] == "4572f9799fb7cadae5f28cb51ca0cb8b38d7ddf67347970c555d77c399c839d3" &&
        receiptStagingVersion["versionName"] == "1.1.3" &&
        (receiptStagingVersion["versionCode"] as? Number)?.toInt() == 40 &&
        (receiptStagingVersion["buildTime"] as? Number)?.toLong() == 1786621005354L
    )) return@runCatching false

    if (!(ownership["task"] == ":app:verifyNodeRuntimeOwnershipGate" &&
        (ownership["reportBytes"] as? Number)?.toLong() == 10267L &&
        ownership["reportSha256"] == nodeAndroidConformanceX3jHostOwnershipReportSha256 &&
        (ownership["allowlistBytes"] as? Number)?.toLong() == 19520L &&
        ownership["allowlistSha256"] == nodeAndroidConformanceX3jHostOwnershipAllowlistSha256 &&
        ownership["ready"] == true &&
        (ownership["candidateCount"] as? Number)?.toInt() == 2205 &&
        (ownership["classifiedCandidateCount"] as? Number)?.toInt() == 2205 &&
        (ownership["violationCount"] as? Number)?.toInt() == 0 &&
        (ownership["migrationBacklogFileCount"] as? Number)?.toInt() == 1707 &&
        compile["task"] == ":app:compileAppDebugAndroidTestKotlin" &&
        compile["result"] == "passed" && compile["sameInvocationAsOwnershipGate"] == true &&
        (compile["freshTargetClassCount"] as? Number)?.toInt() == 0 &&
        invocation["singleGradleInvocation"] == true && invocation["rerunPerformed"] == false &&
        invocation["outerToolTimedOut"] == false && invocation["gradleDaemonResult"] == "BUILD SUCCESSFUL" &&
        invocation["gradleDaemonDuration"] == "2m 31s" &&
        (invocation["totalTasks"] as? Number)?.toInt() == 328 &&
        (invocation["executedTasks"] as? Number)?.toInt() == 9 &&
        (invocation["upToDateTasks"] as? Number)?.toInt() == 319
    )) return@runCatching false

    if (!(removedTest["path"] ==
            "app/src/androidTest/java/org/autojs/autojs/engine/NodeCryptoExpansionInstrumentationTest.kt" &&
        removedTest["exists"] == false &&
        removedTest["preRemovalIdentitySource"] == "current_filesystem_including_untracked" &&
        (removedTest["preRemovalBytes"] as? Number)?.toLong() == 4485L &&
        removedTest["preRemovalSha256"] == "63c28215f332c9a68bc9709638cef3842edd242dd9ad7a2d58c0b5f2d6874033" &&
        (removedTest["headBlobBytes"] as? Number)?.toLong() == 4384L &&
        removedTest["headBlobSha256"] == "fd124349f3149a996ca27d77e55924f4220d559722534749736175bcb5bdb58b" &&
        removedTest["headGitBlobObjectIdSha1"] == "22130a83a0d21645de5c1805d4e68e474afe6571" &&
        removedTest["symbol"] == "safeCryptoExpansionAndShadowingWork" &&
        (removedTest["remainingMatches"] as? Number)?.toInt() == 0 &&
        (removedTest["removedTestAnnotations"] as? Number)?.toInt() == 1 &&
        removedGate["path"] == "app/node-instrumentation-gates.gradle.kts" &&
        removedGate["className"] == "org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest" &&
        (removedGate["removedReferences"] as? Number)?.toInt() == 1 &&
        (removedGate["remainingMatches"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(removedDocs["paths"]) ==
            listOf("docs/nodejs/TESTING.md", "docs/nodejs/RELEASE_CHECKLIST.md") &&
        removedDocs["className"] == "org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest" &&
        (removedDocs["removedReferences"] as? Number)?.toInt() == 2 &&
        (removedDocs["remainingOldClassReferences"] as? Number)?.toInt() == 0 &&
        (references["activeOldClassReferences"] as? Number)?.toInt() == 0 &&
        (references["activeOldSymbolReferences"] as? Number)?.toInt() == 0 &&
        (references["activeOldGateOrDocumentationReferences"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(references["activeReferencePaths"])?.isEmpty() == true &&
        (references["historicalClassReferencesRetained"] as? Number)?.toInt() == 4 &&
        nodeAndroidConformanceStrings(references["historicalReferenceLocations"])?.toSet() == setOf(
            "docs/nodejs/reports/BASELINE_AFTER_TASK_49.md:264",
            "docs/nodejs/reports/BASELINE_AFTER_TASK_18.md:265",
            "docs/nodejs/reports/BASELINE_AFTER_TASK_18.md:379",
            "docs/nodejs/reports/BASELINE_AFTER_TASK_18.md:628",
        )
    )) return@runCatching false

    if (!((stale["freshTargetClassCount"] as? Number)?.toInt() == 0 &&
        (stale["stalePreExistingDexCount"] as? Number)?.toInt() == 1 &&
        staleDex["path"] ==
            "app/build/intermediates/project_dex_archive/appDebugAndroidTest/" +
            "dexBuilderAppDebugAndroidTest/out/org/autojs/autojs/engine/" +
            "NodeCryptoExpansionInstrumentationTest.dex" &&
        (staleDex["bytes"] as? Number)?.toLong() == 6940L &&
        staleDex["sha256"] == "770c6c393e36f58e8d23215ce9c2b9e28ab7e299fe940556c49adcb333036760" &&
        staleDex["classification"] == "pre-existing dexBuilder archive; not fresh Kotlin compile output" &&
        staleDex["deleted"] == false && staleDex["claimedFresh"] == false &&
        (remaining["nodeRelatedSourceFiles"] as? Number)?.toInt() == 132 &&
        (remaining["testAnnotations"] as? Number)?.toInt() == 749 &&
        (remaining["corpusFiles"] as? Number)?.toInt() == 493 &&
        (remaining["migrationBacklogFileCount"] as? Number)?.toInt() == 1707 &&
        (remaining["instrumentationFileCount"] as? Number)?.toInt() == 132 &&
        remaining["migrationBacklogPathSetSha256"] == nodeAndroidConformanceX3jHostBacklogPathSetSha256 &&
        remaining["instrumentationPathSetSha256"] == nodeAndroidConformanceX3jInstrumentationPathSetSha256 &&
        remaining["corpusPathSetSha256"] == nodeAndroidConformanceX3gCorpusPathSetSha256
    )) return@runCatching false

    if (!(deviceReportFile.length() == nodeAndroidConformanceX3jDirectedDeviceReportBytes &&
        deviceReportFile.nodeAndroidConformanceSha256() == nodeAndroidConformanceX3jDirectedDeviceReportSha256 &&
        rawFile.length() == nodeAndroidConformanceX3jRawDeviceOutputBytes &&
        rawFile.nodeAndroidConformanceSha256() == nodeAndroidConformanceX3jRawDeviceOutputSha256 &&
        device["schema"] == "autojs6-node-plugin-x3j-directed-device-run-v1" &&
        device["milestone"] == "X3j" && device["result"] == "pass" &&
        device["evidenceLevel"] == "directed-device" &&
        selection["requestedAvdName"] == "DEX_R1_API34_X64" &&
        (selection["requestedPort"] as? Number)?.toInt() == 5562 &&
        selection["observedSerial"] == "emulator-5562" &&
        (selection["apiLevel"] as? Number)?.toInt() == 34 &&
        nodeAndroidConformanceStrings(selection["abis"]) == listOf("x86_64") &&
        selection["uniqueNewEmulatorSerial"] == true && selection["serialAmbiguous"] == false &&
        selection["emulator"] == true && selection["roKernelQemu"] == "1" &&
        selection["bootCompletedBeforeRun"] == true && selection["bootAnimationState"] == "stopped"
    )) return@runCatching false

    if (!(execution["allDirectedDeviceCommandsUsedExplicitSerial"] == true &&
        execution["adbEnumerationCommandsWereReadOnly"] == true &&
        (execution["physicalDeviceDirectedCommandCount"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(execution["physicalSerialsNeverTargeted"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        execution["connectedTaskUsed"] == false && execution["genericConnectedTaskUsed"] == false &&
        execution["gradleTaskUsed"] == false && execution["soakRun"] == false &&
        execution["fullTwentyFourSelectorHarnessRun"] == false &&
        (execution["selectorCount"] as? Number)?.toInt() == 1 &&
        (execution["attemptCount"] as? Number)?.toInt() == 1 &&
        (execution["retryCount"] as? Number)?.toInt() == 0 &&
        execution["hostRemovalPerformed"] == false && execution["sourceChangedDuringRun"] == false &&
        execution["verbatimAdbStdoutCaptured"] == true
    )) return@runCatching false

    if (!(preflight["artifactHashesAndSizesVerifiedBeforeLaunch"] == true &&
        preflight["artifactHashesAndSizesReverifiedBeforeInstall"] == true &&
        preflight["aaptIdentityVerifiedBeforeInstall"] == true &&
        preflight["localApkSignersVerifiedBeforeInstall"] == true &&
        preflight["requestedPortPairFreeBeforeLaunch"] == true &&
        nodeAndroidConformanceStrings(preflight["preexistingAdbSerials"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        nodeAndroidConformanceStrings(preflight["preexistingEmulatorOrQemuProcessIds"])?.isEmpty() == true &&
        (preflight["preexistingGradleClientCount"] as? Number)?.toInt() == 0 &&
        preflight["deviceState"] == "device" && preflight["bootCompleted"] == true &&
        preflight["avdNameVerified"] == true && preflight["apiAndAbiVerified"] == true &&
        preflight["pluginPackageExisted"] == false && preflight["pluginTestPackageExisted"] == false &&
        preflight["allRelevantPluginPackagesAbsent"] == true
    )) return@runCatching false

    if (!(frozenApp["sha256"] == nodeAndroidConformanceX3jAppSha256 &&
        frozenTest["sha256"] == nodeAndroidConformanceX3jTestApkSha256 &&
        frozenSource["sha256"] == nodeAndroidConformanceX3jTestSourceSha256 &&
        frozenPackage["sha256"] == "b4d98a45aa631c239ac6649b7fce9779cd179144568077df2e1e6faf1e1ff361" &&
        frozenGate["bytes"] == 143871 &&
        frozenGate["sha256"] == "1518e8131e73d9d0ca2f78080500e5b8aba2a674dd965681c819ad3349de48f8" &&
        frozenPolicy["bytes"] == 20053 &&
        frozenPolicy["sha256"] == "a2c8a458c7d2aecf48abdf116403ef339c9d36ea75f70390f3d173245ecedc5b" &&
        frozenVerifier["bytes"] == 38000 &&
        frozenVerifier["sha256"] == "9469d2316e4750f6f425079c4575bcee583abd476f8e95a9a5f3d24756fd854e" &&
        frozenVersion["bytes"] == 700 &&
        frozenVersion["sha256"] == "4572f9799fb7cadae5f28cb51ca0cb8b38d7ddf67347970c555d77c399c839d3" &&
        frozenVersion["versionName"] == "1.1.3" &&
        (frozenVersion["versionCode"] as? Number)?.toInt() == 40 &&
        (frozenVersion["buildTime"] as? Number)?.toLong() == 1786621005354L
    )) return@runCatching false

    if (!(x86AppApk.length() == nodeAndroidConformanceX3jAppBytes &&
        x86AppApk.nodeAndroidConformanceSha256() == nodeAndroidConformanceX3jAppSha256 &&
        testApk.length() == nodeAndroidConformanceX3jTestApkBytes &&
        testApk.nodeAndroidConformanceSha256() == nodeAndroidConformanceX3jTestApkSha256 &&
        testSourceFile.length() == nodeAndroidConformanceX3jTestSourceBytes &&
        testSourceFile.nodeAndroidConformanceSha256() == nodeAndroidConformanceX3jTestSourceSha256 &&
        (app["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jAppBytes &&
        app["sha256"] == nodeAndroidConformanceX3jAppSha256 &&
        app["packageName"] == "io.github.supermonster003.autojs6.plugin.nodejs" &&
        app["versionName"] == "1.1.3" && (app["versionCode"] as? Number)?.toInt() == 40 &&
        app["signerSha256"] == nodeAndroidConformanceX3jSignerSha256 &&
        app["deviceInstalledIdentityVerified"] == true &&
        (test["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jTestApkBytes &&
        test["sha256"] == nodeAndroidConformanceX3jTestApkSha256 &&
        test["packageName"] == "io.github.supermonster003.autojs6.plugin.nodejs.test" &&
        test["targetPackageName"] == "io.github.supermonster003.autojs6.plugin.nodejs" &&
        test["runner"] == "androidx.test.runner.AndroidJUnitRunner" &&
        test["signerSha256"] == nodeAndroidConformanceX3jSignerSha256 &&
        test["deviceInstalledIdentityVerified"] == true &&
        installation["onlyAppAndAndroidTestInstalled"] == true &&
        (installation["appInstallExitCode"] as? Number)?.toInt() == 0 &&
        installation["appInstallResult"] == "Success" &&
        (installation["testInstallExitCode"] as? Number)?.toInt() == 0 &&
        installation["testInstallResult"] == "Success" &&
        installation["installedBaseApksHashedInPlaceOnDevice"] == true &&
        installation["deviceBaseApksMatchedFrozenArtifacts"] == true &&
        installation["temporaryApkPullsCreated"] == false
    )) return@runCatching false

    if (!(instrumentation["command"] == expectedCommand &&
        (instrumentation["commandUtf8BytesWithoutTrailingNewline"] as? Number)?.toInt() ==
            expectedCommand.toByteArray(StandardCharsets.UTF_8).size &&
        instrumentation["commandSha256"] ==
            expectedCommand.toByteArray(StandardCharsets.UTF_8).nodeAndroidConformanceSha256() &&
        instrumentation["className"] == nodeAndroidConformanceTestClass &&
        selectorResult["name"] == selector && selectorResult["result"] == "pass" &&
        (selectorResult["terminalStatusCode"] as? Number)?.toInt() == 0 &&
        (instrumentation["testsRun"] as? Number)?.toInt() == 1 &&
        (instrumentation["testsPassed"] as? Number)?.toInt() == 1 &&
        (instrumentation["testsFailed"] as? Number)?.toInt() == 0 &&
        (instrumentation["testsIgnored"] as? Number)?.toInt() == 0 &&
        instrumentation["runnerSummary"] == "OK (1 test)" &&
        (instrumentation["instrumentationCode"] as? Number)?.toInt() == -1 &&
        (instrumentation["adbExitCode"] as? Number)?.toInt() == 0 &&
        instrumentation["firstAndOnlyRunPassed"] == true &&
        instrumentation["failureEvidence"] == null &&
        instrumentation["hostRemovalPrerequisitePassed"] == true &&
        requiredAssertions.all { (key, value) -> assertionReceipt[key] == value }
    )) return@runCatching false

    if (!(restoration["baselineRestored"] == true &&
        restoration["testPackageUninstalledBecauseAbsentAtBaseline"] == true &&
        restoration["testPackageUninstallResult"] == "Success" &&
        restoration["pluginPackageUninstalledBecauseAbsentAtBaseline"] == true &&
        restoration["pluginPackageUninstallResult"] == "Success" &&
        restoration["pluginPackageAbsentAfterRestore"] == true &&
        restoration["pluginTestPackageAbsentAfterRestore"] == true &&
        restoration["nodeOrPluginProcessAbsentAfterRestore"] == true &&
        restoration["activeInstrumentationAbsentAfterRestore"] == true &&
        restoration["nodeRuntimeServiceAbsentAfterRestore"] == true &&
        restoration["temporaryApkPullsCreated"] == false &&
        restoration["sourceAndFrozenInputIdentitiesUnchanged"] == true
    )) return@runCatching false

    if (!(temporaryAvd["startedByThisRun"] == true && temporaryAvd["avdName"] == "DEX_R1_API34_X64" &&
        (temporaryAvd["port"] as? Number)?.toInt() == 5562 && temporaryAvd["wipeDataUsed"] == true &&
        temporaryAvd["snapshotLoadDisabled"] == true && temporaryAvd["snapshotSaveDisabled"] == true &&
        temporaryAvd["windowDisabled"] == true && temporaryAvd["avdDefinitionDeleted"] == false &&
        (temporaryAvd["preexistingOnlineEmulatorCount"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(temporaryAvd["adbSerialsBeforeLaunch"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        temporaryAvd["newSerial"] == "emulator-5562" && temporaryAvd["newSerialWasUnique"] == true &&
        temporaryAvd["shutdownCommand"] == "adb -s emulator-5562 emu kill" &&
        (temporaryAvd["shutdownExitCode"] as? Number)?.toInt() == 0 &&
        temporaryAvd["shutdownAcknowledged"] == true && temporaryAvd["serialAbsentAfterShutdown"] == true &&
        temporaryAvd["newEmulatorAndQemuProcessIdsAbsentAfterShutdown"] == true &&
        temporaryAvd["allEmulatorAndQemuProcessesAbsentAfterShutdown"] == true &&
        (temporaryAvd["requestedPortPairListenerCountAfterShutdown"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(temporaryAvd["adbSerialsAfterShutdown"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        temporaryAvd["serialSetRestored"] == true &&
        temporaryAvd["temporaryAvdStartedAndStopped"] == true
    )) return@runCatching false

    if (!((postRun["physicalDeviceDirectedCommandCount"] as? Number)?.toInt() == 0 &&
        nodeAndroidConformanceStrings(postRun["adbSerials"]) ==
            listOf("968e9f18", "QV710AF65F") &&
        (postRun["emulatorOrQemuProcessCount"] as? Number)?.toInt() == 0 &&
        (postRun["requestedPortPairListenerCount"] as? Number)?.toInt() == 0 &&
        (postRun["gradleClientCount"] as? Number)?.toInt() == 0 &&
        rawEvidence["path"] == nodeAndroidConformanceX3jRawDeviceOutputPath &&
        (rawEvidence["bytes"] as? Number)?.toLong() == nodeAndroidConformanceX3jRawDeviceOutputBytes &&
        rawEvidence["sha256"] == nodeAndroidConformanceX3jRawDeviceOutputSha256 &&
        rawEvidence["lineEnding"] == "LF" && rawEvidence["trailingNewline"] == true &&
        rawEvidence["verbatimAdbStdoutCaptured"] == true &&
        managedCopy["jsonPath"] == nodeAndroidConformanceX3jDirectedDeviceReportPath &&
        managedCopy["rawPath"] == nodeAndroidConformanceX3jRawDeviceOutputPath &&
        rawLines.count { it == "INSTRUMENTATION_STATUS: test=$selector" } == 2 &&
        rawLines.count { it == "INSTRUMENTATION_STATUS_CODE: 1" } == 1 &&
        rawLines.count { it == "INSTRUMENTATION_STATUS_CODE: 0" } == 1 &&
        rawLines.count { it == "OK (1 test)" } == 1 &&
        rawLines.count { it == "INSTRUMENTATION_CODE: -1" } == 1 &&
        rawFile.readBytes().let { bytes ->
            bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte() &&
                bytes.none { value -> value == '\r'.code.toByte() }
        }
    )) return@runCatching false

    true
}.getOrDefault(false)

private fun nodeAndroidConformanceX3jCurrentOwnershipValid(
    policyFile: File,
    ownershipPolicy: Map<String, Any?>,
    verifierFile: File,
    reportFile: File,
    report: Map<String, Any?>,
): Boolean = runCatching {
    val reportPolicy = nodeAndroidConformanceMap(report["policy"])
        ?: return@runCatching false
    val repository = nodeAndroidConformanceMap(report["repository"])
        ?: return@runCatching false
    val decision = nodeAndroidConformanceMap(report["decision"])
        ?: return@runCatching false
    val summary = nodeAndroidConformanceMap(report["summary"])
        ?: return@runCatching false
    val declared = nodeAndroidConformanceMap(report["declaredAuthorities"])
        ?: return@runCatching false
    val authorityEvidence = nodeAndroidConformanceMap(report["authorityEvidence"])
        ?: return@runCatching false
    val x3jEvidence = nodeAndroidConformanceMap(
        authorityEvidence["x3jCryptoAndroidConformance"],
    ) ?: return@runCatching false
    val identities = (x3jEvidence["identities"] as? List<*>)
        ?.mapNotNull { value -> nodeAndroidConformanceMap(value) }
        ?: return@runCatching false
    val identityByPath = identities.associateBy { it["path"].toString() }
    val selfTests = (report["selfTests"] as? List<*>)
        ?.mapNotNull { value -> nodeAndroidConformanceMap(value) }
        ?: return@runCatching false
    val authorities = nodeAndroidConformanceStrings(declared["plugin"])
        ?: return@runCatching false

    policyFile.length() == nodeAndroidConformanceX3jCurrentOwnershipPolicyBytes &&
        policyFile.nodeAndroidConformanceSha256() ==
            nodeAndroidConformanceX3jCurrentOwnershipPolicySha256 &&
        verifierFile.length() == nodeAndroidConformanceX3jCurrentOwnershipVerifierBytes &&
        verifierFile.nodeAndroidConformanceSha256() ==
            nodeAndroidConformanceX3jCurrentOwnershipVerifierSha256 &&
        reportFile.length() == nodeAndroidConformanceX3jCurrentOwnershipReportBytes &&
        reportFile.nodeAndroidConformanceSha256() ==
            nodeAndroidConformanceX3jCurrentOwnershipReportSha256 &&
        report["schema"] == "autojs6-node-runtime-ownership-report-v1" &&
        reportPolicy["path"] == "tools/nodejs/ownership/runtime-ownership-policy.json" &&
        reportPolicy["sha256"] == nodeAndroidConformanceX3jCurrentOwnershipPolicySha256 &&
        repository["role"] == "node_runtime_plugin" &&
        decision["status"] == "boundary_passed" && decision["gatePassed"] == true &&
        decision["evidenceLevel"] == "S" && decision["deviceOrSoakExecuted"] == false &&
        (summary["candidatePaths"] as? Number)?.toInt() == 291 &&
        (summary["classifiedCandidatePaths"] as? Number)?.toInt() == 291 &&
        (summary["violations"] as? Number)?.toInt() == 0 &&
        (summary["selfTests"] as? Number)?.toInt() == 17 &&
        (report["violations"] as? List<*>)?.isEmpty() == true &&
        selfTests.size == 17 && selfTests.all { it["passed"] == true } &&
        authorities.count { it == "crypto_android_conformance" } == 1 &&
        nodeAndroidConformanceX3jOwnershipMigrated(ownershipPolicy) &&
        x3jEvidence["authority"] == "crypto_android_conformance" &&
        x3jEvidence["state"] == "migrated-from-host" &&
        x3jEvidence["selector"] == nodeAndroidConformanceX3jSelectors.single() &&
        x3jEvidence["receiptFieldsValidated"] == true && x3jEvidence["passed"] == true &&
        (x3jEvidence["violations"] as? List<*>)?.isEmpty() == true &&
        identityByPath.size == 3 &&
        listOf(
            Triple(
                nodeAndroidConformanceX3jHostRemovalReceiptPath,
                nodeAndroidConformanceX3jHostRemovalReceiptBytes,
                nodeAndroidConformanceX3jHostRemovalReceiptSha256,
            ),
            Triple(
                nodeAndroidConformanceX3jDirectedDeviceReportPath,
                nodeAndroidConformanceX3jDirectedDeviceReportBytes,
                nodeAndroidConformanceX3jDirectedDeviceReportSha256,
            ),
            Triple(
                nodeAndroidConformanceX3jRawDeviceOutputPath,
                nodeAndroidConformanceX3jRawDeviceOutputBytes,
                nodeAndroidConformanceX3jRawDeviceOutputSha256,
            ),
        ).all { (path, bytes, sha256) ->
            val identity = identityByPath[path] ?: return@all false
            (identity["expectedBytes"] as? Number)?.toLong() == bytes &&
                (identity["actualBytes"] as? Number)?.toLong() == bytes &&
                identity["expectedSha256"] == sha256 && identity["actualSha256"] == sha256 &&
                identity["passed"] == true
        }
}.getOrDefault(false)

private fun nodeAndroidConformanceUniversalDebugApk(): File {
    val metadataFile = layout.buildDirectory
        .file("outputs/apk/debug/output-metadata.json")
        .get().asFile
    check(metadataFile.isFile) { "Debug APK metadata is missing: $metadataFile" }
    @Suppress("UNCHECKED_CAST")
    val metadata = JsonSlurper().parseText(metadataFile.readText()) as Map<String, Any?>
    val elements = metadata["elements"] as? List<*>
        ?: error("Debug APK metadata has no elements array: $metadataFile")
    val universal = elements
        .mapNotNull { it as? Map<*, *> }
        .singleOrNull { element ->
            element["type"] == "UNIVERSAL" &&
                (element["filters"] as? List<*>)?.isEmpty() == true
        }
        ?: error("Debug APK metadata must contain exactly one universal output")
    val outputFile = universal["outputFile"] as? String
        ?: error("Universal debug APK metadata has no outputFile")
    return metadataFile.parentFile.resolve(outputFile).also { apk ->
        check(apk.isFile && apk.length() > 0L) { "Universal debug APK is missing or empty: $apk" }
    }
}

private fun nodeAndroidConformanceTestApk(): File {
    val root = layout.buildDirectory.dir("outputs/apk/androidTest/debug").get().asFile
    val apks = if (root.isDirectory) {
        root.walkTopDown().filter { it.isFile && it.extension.equals("apk", true) }.toList()
    } else {
        emptyList()
    }
    check(apks.size == 1) {
        "Expected exactly one debug Android-test APK below $root, found ${apks.size}: $apks"
    }
    return apks.single().also { apk ->
        check(apk.length() > 0L) { "Android-test APK is empty: $apk" }
    }
}

val verifyNodePluginAndroidConformanceHarnessGate = tasks.register(
    "verifyNodePluginAndroidConformanceHarnessGate",
) {
    group = "verification"
    description =
        "Builds and verifies the plugin Android conformance harness without running a device."
    dependsOn(
        "assembleDebug",
        "assembleDebugAndroidTest",
        "verifyNodePluginRuntimeKitRelease",
        "verifyNodeRuntimeOwnershipGate",
    )

    inputs.file(nodeAndroidConformanceSource)
    inputs.file(nodeAndroidConformanceWorkspaceSource)
    inputs.file(nodeAndroidConformanceProviderTransportSource)
    inputs.file(nodeAndroidConformanceRuntimeServiceSource)
    inputs.file(nodeAndroidConformancePermissionManifestSource)
    inputs.file(nodeAndroidConformanceNativeBridgeSource)
    inputs.dir(nodeAndroidConformanceCorpus)
    inputs.dir(nodeAndroidConformanceCorpusV1)
    inputs.dir(project.layout.projectDirectory.dir("src/main"))
    inputs.files(
        rootProject.fileTree("plugin-api/nodejs-api") {
            exclude("build/**")
        },
        rootProject.fileTree("build-logic") {
            exclude(
                ".gradle/**",
                ".kotlin/**",
                "build/**",
                "convention/.gradle/**",
                "convention/.kotlin/**",
                "convention/build/**",
            )
        },
        rootProject.fileTree("gradle"),
        nodeAndroidConformanceX3jRuntimeBuildInputIdentities.keys.map { path ->
            rootProject.file(path)
        },
        rootProject.file(nodeAndroidConformanceX3jRuntimeKitManifestIdentity.path),
        rootProject.file(nodeAndroidConformanceX3jRuntimeKitAssetIdentity.path),
        rootProject.file(nodeAndroidConformanceX3jRuntimeKitLockIdentity.path),
        rootProject.file(nodeAndroidConformanceX3jCapabilityCatalogIdentity.path),
    )
    inputs.files(
        project.layout.projectDirectory.file("build.gradle.kts"),
        project.layout.projectDirectory.file("node-android-conformance.gradle.kts"),
        project.layout.projectDirectory.file("src/main/AndroidManifest.xml"),
        rootProject.layout.projectDirectory.file(
            "plugin-api/nodejs-api/src/main/aidl/org/autojs/plugin/nodejs/api/" +
                "INodeJsRuntimePlugin.aidl",
        ),
        nodeAndroidConformanceCurrentOwnershipPolicy,
        nodeAndroidConformanceCurrentOwnershipVerifier,
        nodeAndroidConformanceCurrentOwnershipReport,
        nodeAndroidConformanceX3eHostRemovalReceipt,
        nodeAndroidConformanceX3gHostRemovalReceipt,
        nodeAndroidConformanceX3gDirectedDeviceReport,
        nodeAndroidConformanceX3gRawDeviceOutput,
        nodeAndroidConformanceX3iHostRemovalReceipt,
        nodeAndroidConformanceX3iDirectedDeviceReport,
        nodeAndroidConformanceX3iRawDeviceOutput,
        nodeAndroidConformanceX3jHostRemovalReceipt,
        nodeAndroidConformanceX3jDirectedDeviceReport,
        nodeAndroidConformanceX3jRawDeviceOutput,
    )
    outputs.file(nodeAndroidConformanceReport)
    outputs.upToDateWhen { false }

    doFirst {
        // Never leave an older passing package receipt visible when a current package
        // check or the immutable historical-device prerequisite fails later in this task.
        nodeAndroidConformanceReport.get().asFile.delete()
    }

    doLast {
        val checks = linkedMapOf<String, Boolean>()
        fun requireCheck(name: String, condition: Boolean, detail: String) {
            checks[name] = condition
            check(condition) { detail }
        }

        val testFile = nodeAndroidConformanceSource.asFile
        val testSource = testFile.readText()
        val workspaceSource = nodeAndroidConformanceWorkspaceSource.asFile.readText()
        val providerTransportSource = nodeAndroidConformanceProviderTransportSource.asFile.readText()
        val runtimeServiceSource = nodeAndroidConformanceRuntimeServiceSource.asFile.readText()
        val permissionManifestSource = nodeAndroidConformancePermissionManifestSource.asFile.readText()
        val nativeBridgeSource = nodeAndroidConformanceNativeBridgeSource.asFile.readText()
        val appBuildSource = project.file("build.gradle.kts").readText()
        val manifestSource = project.file("src/main/AndroidManifest.xml").readText()
        val aidlSource = rootProject.file(
            "plugin-api/nodejs-api/src/main/aidl/org/autojs/plugin/nodejs/api/" +
                "INodeJsRuntimePlugin.aidl",
        ).readText()
        val ownershipFile = nodeAndroidConformanceCurrentOwnershipPolicy.asFile
        val ownershipSource = ownershipFile.readText()
        @Suppress("UNCHECKED_CAST")
        val ownershipPolicy = JsonSlurper().parseText(ownershipSource) as Map<String, Any?>
        val x3eHostRemovalReceiptFile = nodeAndroidConformanceX3eHostRemovalReceipt.asFile
        @Suppress("UNCHECKED_CAST")
        val x3eHostRemovalReceipt =
            JsonSlurper().parseText(x3eHostRemovalReceiptFile.readText()) as Map<String, Any?>
        val x3gHostRemovalReceiptFile = nodeAndroidConformanceX3gHostRemovalReceipt.asFile
        @Suppress("UNCHECKED_CAST")
        val x3gHostRemovalReceipt =
            JsonSlurper().parseText(x3gHostRemovalReceiptFile.readText()) as Map<String, Any?>
        val x3gDirectedDeviceReportFile =
            nodeAndroidConformanceX3gDirectedDeviceReport.asFile
        @Suppress("UNCHECKED_CAST")
        val x3gDirectedDeviceEvidence =
            JsonSlurper().parseText(x3gDirectedDeviceReportFile.readText()) as Map<String, Any?>
        val x3gRawDeviceOutputFile = nodeAndroidConformanceX3gRawDeviceOutput.asFile
        val x3iHostRemovalReceiptFile = nodeAndroidConformanceX3iHostRemovalReceipt.asFile
        @Suppress("UNCHECKED_CAST")
        val x3iHostRemovalReceipt = JsonSlurper().parseText(
            x3iHostRemovalReceiptFile.readText(),
        ) as Map<String, Any?>
        val x3iDirectedDeviceReportFile = nodeAndroidConformanceX3iDirectedDeviceReport.asFile
        @Suppress("UNCHECKED_CAST")
        val x3iDirectedDeviceEvidence = JsonSlurper().parseText(
            x3iDirectedDeviceReportFile.readText(),
        ) as Map<String, Any?>
        val x3iRawDeviceOutputFile = nodeAndroidConformanceX3iRawDeviceOutput.asFile
        val x3jHostRemovalReceiptFile = nodeAndroidConformanceX3jHostRemovalReceipt.asFile
        @Suppress("UNCHECKED_CAST")
        val x3jHostRemovalReceipt = JsonSlurper().parseText(
            x3jHostRemovalReceiptFile.readText(),
        ) as Map<String, Any?>
        val x3jDirectedDeviceReportFile = nodeAndroidConformanceX3jDirectedDeviceReport.asFile
        @Suppress("UNCHECKED_CAST")
        val x3jDirectedDeviceEvidence = JsonSlurper().parseText(
            x3jDirectedDeviceReportFile.readText(),
        ) as Map<String, Any?>
        val x3jRawDeviceOutputFile = nodeAndroidConformanceX3jRawDeviceOutput.asFile
        val currentOwnershipVerifierFile = nodeAndroidConformanceCurrentOwnershipVerifier.asFile
        val currentOwnershipReportFile = nodeAndroidConformanceCurrentOwnershipReport.get().asFile
        @Suppress("UNCHECKED_CAST")
        val currentOwnershipReport = JsonSlurper().parseText(
            currentOwnershipReportFile.readText(),
        ) as Map<String, Any?>
        val corpusRoot = nodeAndroidConformanceCorpus.asFile
        val actualCorpusFiles = corpusRoot.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(corpusRoot).invariantSeparatorsPath }
            .sorted()
            .toList()
        val corpusV1Root = nodeAndroidConformanceCorpusV1.asFile
        val actualCorpusV1Files = corpusV1Root.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(corpusV1Root).invariantSeparatorsPath }
            .sorted()
            .toList()
        val appApk = nodeAndroidConformanceUniversalDebugApk()
        val testApk = nodeAndroidConformanceTestApk()
        val appEntries = nodeAndroidConformanceZipEntries(appApk)
        val testEntries = nodeAndroidConformanceZipEntries(testApk)
        val x3jAppMainIdentity = nodeAndroidConformanceTreeIdentity(
            rootProject.file(nodeAndroidConformanceX3jAppMainIdentity.path),
        )
        val x3jPluginApiIdentity = nodeAndroidConformanceTreeIdentity(
            rootProject.file(nodeAndroidConformanceX3jPluginApiIdentity.path),
            excludedPrefixes = setOf("build/"),
        )
        val x3jBuildLogicIdentity = nodeAndroidConformanceTreeIdentity(
            rootProject.file(nodeAndroidConformanceX3jBuildLogicIdentity.path),
            excludedPrefixes = setOf(
                ".gradle/",
                ".kotlin/",
                "build/",
                "convention/.gradle/",
                "convention/.kotlin/",
                "convention/build/",
            ),
        )
        val x3jGradleIdentity = nodeAndroidConformanceTreeIdentity(
            rootProject.file(nodeAndroidConformanceX3jGradleIdentity.path),
        )
        val x3jRuntimeBuildInputIdentities =
            nodeAndroidConformanceX3jRuntimeBuildInputIdentities.mapValues { (path, _) ->
                nodeAndroidConformanceFileIdentity(rootProject.file(path))
            }
        val x3jRuntimeKitManifestIdentity = nodeAndroidConformanceFileIdentity(
            rootProject.file(nodeAndroidConformanceX3jRuntimeKitManifestIdentity.path),
        )
        val x3jRuntimeKitAssetIdentity = nodeAndroidConformanceFileIdentity(
            rootProject.file(nodeAndroidConformanceX3jRuntimeKitAssetIdentity.path),
        )
        val x3jRuntimeKitLockIdentity = nodeAndroidConformanceFileIdentity(
            rootProject.file(nodeAndroidConformanceX3jRuntimeKitLockIdentity.path),
        )
        val x3jCapabilityCatalogIdentity = nodeAndroidConformanceFileIdentity(
            rootProject.file(nodeAndroidConformanceX3jCapabilityCatalogIdentity.path),
        )
        val x3jProductionInputsUnchanged =
            x3jAppMainIdentity == nodeAndroidConformanceX3jAppMainIdentity &&
                x3jPluginApiIdentity == nodeAndroidConformanceX3jPluginApiIdentity &&
                x3jBuildLogicIdentity == nodeAndroidConformanceX3jBuildLogicIdentity &&
                x3jGradleIdentity == nodeAndroidConformanceX3jGradleIdentity &&
                x3jRuntimeBuildInputIdentities ==
                    nodeAndroidConformanceX3jRuntimeBuildInputIdentities &&
                nodeAndroidConformanceX3jVersionInputAtConfiguration ==
                    nodeAndroidConformanceX3jVersionInputIdentity &&
                nodeAndroidConformanceX3jNetworkExperimentalAtConfiguration == "false" &&
                x3jRuntimeKitManifestIdentity ==
                    nodeAndroidConformanceX3jRuntimeKitManifestIdentity &&
                x3jRuntimeKitAssetIdentity ==
                    nodeAndroidConformanceX3jRuntimeKitAssetIdentity &&
                x3jRuntimeKitLockIdentity == nodeAndroidConformanceX3jRuntimeKitLockIdentity &&
                x3jCapabilityCatalogIdentity ==
                    nodeAndroidConformanceX3jCapabilityCatalogIdentity

        requireCheck(
            "android_junit_runner",
            "testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"" in
                appBuildSource,
            "The app must declare AndroidJUnitRunner.",
        )
        requireCheck(
            "android_test_dependencies",
            "androidTestImplementation(libs.test.ext.junit)" in appBuildSource &&
                "androidTestImplementation(libs.test.runner)" in appBuildSource,
            "The app must compile the conformance suite with AndroidX ext-junit and runner.",
        )
        requireCheck(
            "gate_applied",
            "apply(from = \"node-android-conformance.gradle.kts\")" in appBuildSource,
            "The app must apply the Android conformance gate.",
        )
        requireCheck(
            "reviewed_exact_selectors",
            nodeAndroidConformanceSelectors.all { selector ->
                Regex("\\b${Regex.escape(selector)}\\s*\\(").containsMatchIn(testSource)
            } && Regex("(?m)^\\s*@Test\\s*$").findAll(testSource).count() == 24,
            "The Android conformance harness must expose exactly the twenty-four reviewed X3d-X3j selectors.",
        )
        requireCheck(
            "no_assumption_skip",
            "Assume" !in testSource && "assumeTrue" !in testSource,
            "The Android conformance harness must not skip acceptance with JUnit assumptions.",
        )
        requireCheck(
            "published_aidl_only",
            "INodeJsRuntimePlugin" in testSource && ".runScript(request, callback)" in testSource &&
                "NativeNodeEmbeddedRuntimeBridge" !in testSource,
            "The harness must enter through the published AIDL rather than a direct native helper.",
        )
        requireCheck(
            "actionless_explicit_remote_bind",
            "new Intent().setComponent(component)" in testSource &&
                "RUNTIME_PROCESS_SUFFIX" in testSource && "Process.myPid()" in testSource,
            "The harness must use actionless explicit binding and prove a distinct runtime PID.",
        )
        requireCheck(
            "workspace_v2_distinct_pfd",
            listOf(
                "KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION",
                "KEY_WORKSPACE_ARCHIVE_INPUT_FD",
                "KEY_WORKSPACE_ARCHIVE_OUTPUT_FD",
                "ParcelFileDescriptor.MODE_READ_ONLY",
                "ParcelFileDescriptor.MODE_READ_WRITE",
                "workspace archive transport reused one descriptor",
                "INPUT_MANIFEST_PATH",
                "OUTPUT_TOMBSTONE_PATH",
                "input-manifest-v1.json",
                "deletions-v1.json",
            ).all(testSource::contains),
            "The harness must use the real workspace-v2 protocol with distinct regular-file PFDs.",
        )
        requireCheck(
            "plaintext_private_preparation_receipts",
            listOf(
                "MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED",
                "embedded_script.runtime_plugin.module_provider.request_count",
                "embedded_script.runtime_plugin.module_provider.not_encrypted_count",
                "embedded_script.runtime_plugin.module_provider.mapped_request_path_count",
                "embedded_script.runtime_plugin.module_provider.mapped_response_path_count",
                "embedded_script.runtime_plugin.module_provider.typescript.stripped_count",
                "embedded_script.module_provider.transport_failure_count",
                "plaintext_preparation_request_count",
                "plaintext_preparation_count",
                "prepared_source_count",
                "last_extension",
            ).all(testSource::contains),
            "The materialized plaintext CTS selector must assert the X3c private preparation receipts.",
        )
        requireCheck(
            "tsx_canonical_terminal",
            "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION" in testSource &&
                "assertOneStartedAndOneTerminalEvent" in testSource &&
                "TSX admission should fail before provider dispatch" in testSource,
            "The TSX selector must assert its canonical failure and single terminal event.",
        )
        requireCheck(
            "materialization_scope_explicit",
            "does not claim that the" in testSource &&
                "Host archive writer discovers unknown computed dependencies" in testSource,
            "The test must state that it covers an already materialized plugin workspace only.",
        )
        requireCheck(
            "node_compat_corpus_v2_exact_inventory",
            actualCorpusFiles == nodeAndroidConformanceCorpusFiles.sorted(),
            "node_compat_corpus_v2 must contain exactly the reviewed 15-file inventory.",
        )
        requireCheck(
            "node_compat_corpus_v2_content",
            corpusRoot.nodeAndroidConformanceNormalizedCorpusSha256(actualCorpusFiles) ==
                nodeAndroidConformanceCorpusContentSha256,
            "node_compat_corpus_v2 content drifted from the reviewed Host staging source.",
        )
        requireCheck(
            "node_compat_corpus_v2_remote_semantics",
            listOf(
                "loadNodeCompatCorpusV2Assets",
                "node-compat-corpus-v2",
                "compat.v2.require_esm=PASS",
                "compat.v2.tla_rejection=PASS",
                "compat.v2.json_attributes=PASS",
                "compat.v2.esm_cjs_cycle=PASS",
                "compat.v2.import_meta_resolve=PASS",
                "compat.v2.package_custom_condition=PASS",
                "compat.v2.typescript_mixed_graph=PASS",
                "compat.v2.total=7",
                "assertOneStartedAndOneTerminalEvent",
                "assertWorkspaceOutputCommitted",
            ).all(testSource::contains),
            "The X3e selector must preserve all seven Host v2 semantic receipts through the remote harness.",
        )
        requireCheck(
            "node_compat_corpus_v1_exact_inventory",
            actualCorpusV1Files == nodeAndroidConformanceCorpusV1Files.sorted(),
            "The migrated node_compat_corpus v1 tree must contain exactly the reviewed 27-file inventory.",
        )
        requireCheck(
            "node_compat_corpus_v1_content",
            corpusV1Root.nodeAndroidConformanceNormalizedCorpusSha256(actualCorpusV1Files) ==
                nodeAndroidConformanceCorpusV1ContentSha256,
            "The migrated node_compat_corpus v1 content drifted from the reviewed Host source.",
        )
        requireCheck(
            "node_compat_corpus_v1_remote_and_preload_semantics",
            listOf(
                "x3g_18_nodeCompatCorpusV1PassesThroughPublishedBinder",
                "x3g_19_preloadShapedModuleSourcesOverridesWorkspaceDisk",
                "loadNodeCompatCorpusV1Assets",
                "node-compat-corpus-v1",
                "NODE_COMPAT_CORPUS_V1_FIXTURES",
                "compat.fixture.",
                "compat.corpus.total=9",
                "module.exports = () => 'DISK'",
                "compat.preload.fixture=PASS",
                "assertOneStartedAndOneTerminalEvent",
                "assertWorkspaceOutputCommitted",
            ).all(testSource::contains) &&
                testSource.lineSequence().count { line ->
                    line.trim() ==
                        "\"const fixture = require('./preloaded-fixture');\\n\" +"
                } == 1 &&
                "require('./preloaded-fixture.js')" !in testSource &&
                listOf(
                    "Staged copy of the Host v1 corpus",
                    "Ownership remains provisional",
                    "staged-for-host-removal",
                ).none(testSource::contains),
            "The two X3g selectors must retain exact Host v1 CJS/preload semantics and migrated commentary.",
        )
        requireCheck(
            "typescript_android_granular_cases",
            nodeAndroidConformanceTypeScriptSelectors.all { selector ->
                Regex("\\b${Regex.escape(selector)}\\s*\\(").containsMatchIn(testSource)
            } && listOf(
                "ts.cjs=cjs",
                "ts.esm.type=module",
                "ts.mts=mts",
                "ts.cts=cts",
                "ts.module_sources=ts:mts",
                "ERROR_TYPESCRIPT_UNSUPPORTED_EXTENSION",
                "ERROR_UNSUPPORTED_TYPESCRIPT_SYNTAX",
                "embedded_script.typescript.module_sources.stripped_count",
                "embedded_script.package_type_module_policy",
                "embedded_script.typescript.syntax_kind",
                "assertWorkspaceNotMaterializedBeforeNative",
            ).all(testSource::contains),
            "The harness must retain seven granular TypeScript Android cases, including true entry negatives.",
        )
        requireCheck(
            "provider_v2_missing_plaintext_materialization",
            listOf(
                "x3f_13_missingComputedCtsMaterializesThroughProviderV2AndStaysProtected",
                "MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT",
                "MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT",
                "KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS",
                "KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD",
                "embedded_script.module_provider.missing_candidate_request_count",
                "embedded_script.module_provider.materialized_count",
                "embedded_script.runtime_plugin.module_provider.plaintext_count",
                "embedded_script.runtime_plugin.module_provider.materialized_count",
                "raw_already_accounted_preparation_count",
                "embedded_script.runtime_plugin.workspace.provider_materialized_count",
                "embedded_script.runtime_plugin.workspace.provider_protected_output_file_count",
                "embedded_script.runtime_plugin.workspace.provider_protected_tombstone_count",
                "assertWorkspaceExcludes(\"computed.cts\"",
            ).all(testSource::contains),
            "The X3f selector must stage the exact provider-v2 missing-CTS PFD flow and protected-output receipts.",
        )
        requireCheck(
            "x3h_zero_preload_exact_provider_graphs",
            nodeAndroidConformanceX3hGraphSelectors.all { selector ->
                Regex("\\b${Regex.escape(selector)}\\s*\\(").containsMatchIn(testSource)
            } && listOf(
                "X3h initial moduleSources must be zero",
                "X3h initial runtimeModuleSources must be zero",
                "MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING",
                "resolve_existing:project.json:not_found",
                "resolve_existing:package.json:not_found",
                "resolve_existing:feature.js:not_found",
                "resolve_existing:feature.cjs:decrypted",
                "resolve_existing:mainpkg/package.json:decrypted",
                "resolve_existing:indexpkg/index.cjs:decrypted",
                "resolve_existing:node_modules/barepkg/package.json:decrypted",
                "embedded_script.runtime_plugin.module_provider.metadata_preflight.provider_request_count",
                "embedded_script.runtime_plugin.module_provider.request_count\", \"19",
                "exact provider operation/path/status order",
                "unexpected exact provider path",
                "x3h.raw_ts_extensionless=42",
                "x3h.package_graph=main/index/bare",
            ).all(testSource::contains),
            "The two X3h graph selectors must package zero-preload raw TS/CJS and package-main/index/bare graphs with exact ordered provider-v2 events.",
        )
        val providerFailureStop = runtimeServiceSource.indexOf(
            "moduleSourceProviderSession.stop(\"Node.js runtime plugin execution finished after failure.\")",
        )
        val workspaceClose = runtimeServiceSource.indexOf("workspaceSession.close();", providerFailureStop)
        requireCheck(
            "x3h_provider_workspace_quiescent_cleanup",
            listOf(
                "x3h_22_workspaceCloseSerializesWithProviderMaterialization",
                "workspace close bypassed in-flight provider materialization",
                "provider plaintext survived serialized workspace cleanup",
                "provider-created directory survived serialized workspace cleanup",
                "provider materializer thread did not start",
                "provider materialization failed before monitor hook",
                "awaitWorkerStageOrFailure(",
                "releaseMaterialization.countDown();",
                "runtimeFilePreservingCredentialAlias(",
                "Do not canonicalize this path",
            ).all(testSource::contains) &&
                "return containedFile(new File(session.sandboxRoot()), relativePath);" !in
                    testSource &&
                "synchronized ProviderMaterialization materializeProviderSourceNoReplace(" in
                    workspaceSource &&
                "public synchronized void close()" in workspaceSource &&
                providerFailureStop >= 0 && workspaceClose > providerFailureStop,
            "Provider transport must quiesce before synchronized workspace cleanup, with a fail-diagnostic Android concurrency regression.",
        )
        requireCheck(
            "x3h_policy_metadata_shared_session_fail_closed",
            listOf(
                "readPolicyMetadataSnapshot(",
                "withPluginRuntimeModules(",
                "BridgeLimitPolicy.fromMetadata(",
                "PolicyMetadataException",
                "exact_metadata_snapshot",
                "shouldCommitWorkspaceAfterFailure(nativeDispatchStarted)",
                "embedded_script.runtime_plugin.native_dispatch_started=",
                "embedded_script.runtime_plugin.workspace.predispatch_private_source_exported=false",
            ).all(runtimeServiceSource::contains) &&
                "runtimeModuleSourceForMetadata(" in runtimeServiceSource &&
                "runtimeModuleSourceForMetadata(" in permissionManifestSource &&
                listOf(
                    "policyMetadataExactPaths(",
                    "requestStartedAtElapsedRealtimeMs",
                    "MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING",
                    "OPERATION_MATERIALIZE_MISSING_PLAINTEXT",
                    "metadataPreflightCacheReplayCount",
                    "missingCandidateRequestCount.incrementAndGet()",
                    "plaintextCount.incrementAndGet()",
                    "metadataProviderStatusAllowsDefault",
                    "readExactRuntimeMetadataNoFollow",
                    "privateSource.delete()",
                ).all(providerTransportSource::contains),
            "X3h policy metadata must share the request-scoped provider/deadline/budgets, default only on not_found, replay without Binder double-counting, and clean private PFD sources.",
        )
        requireCheck(
            "x3h_materialized_metadata_verified_plaintext_record",
            listOf(
                "function __autojs6_module_metadata_record",
                "providerResult.status === \"materialized_plaintext\"",
                "__autojs6_plaintext_module_source_record(readable, allowEsm, providerResult)",
                "resolve_missing_candidate",
            ).all(nativeBridgeSource::contains),
            "Native materialized package metadata must re-enter the verified plaintext source record instead of reading the materialized file directly.",
        )
        requireCheck(
            "x3i_zlib_callbacks_streams_shadowing_source_semantics",
            nodeAndroidConformanceX3iSourceSemantics(testSource),
            "The X3i ZLIB selector must preserve the Host callback, stream, buffer, error, builtin-shadowing, Binder callback, committed-workspace, and exact zero-pending semantics.",
        )
        requireCheck(
            "x3j_crypto_safe_expansion_shadowing_source_semantics",
            nodeAndroidConformanceX3jSourceSemantics(testSource),
            "The X3j crypto selector must preserve every Host identity, shadowing, UUID, HMAC, timing-safe, asymmetric-denial, limited-WebCrypto, ten-second timeout, Binder lifecycle, committed-workspace, and zero-pending semantic.",
        )
        requireCheck(
            "x3j_production_inputs_unchanged",
            x3jProductionInputsUnchanged,
            "X3j staging must retain the exact production source/native tree, public Plugin API tree, build logic, Gradle inputs including multidex retention, configuration-time version baseline, effective default-off network property, capability catalog, and frozen Runtime Kit 1.1.3 identities; the dependent Runtime Kit release gate must also pass.",
        )
        requireCheck(
            "selinux_safe_direct_target_publication",
            listOf(
                "x3f_14_workspaceDirectExclusivePublicationUsesMode0600",
                "x3f_15_workspaceExclusiveConflictPreservesSentinel",
                "x3f_16_workspaceDeadlineRemovesOwnedPartialAndDirectories",
                "x3f_17_workspaceTimeoutNeverDeletesForeignReplacement",
                "provider target mode",
                "sentinel inode changed",
                "owned partial target survived timeout",
                "foreign replacement was deleted or changed",
            ).all(testSource::contains) && listOf(
                "OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL",
                "OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW",
                "Os.open(target.getAbsolutePath(), outputFlags, 0000)",
                "Os.fchmod(output.getFD(), PROVIDER_TARGET_PRIVATE_MODE)",
                "deleteProviderTargetIfOwnedRegularInode",
                "sameOwnedRegularInode",
            ).all(workspaceSource::contains) && "Os.link(" !in workspaceSource,
            "X3f workspace publication must package the SELinux-safe direct-target tests and no hard-link path.",
        )
        requireCheck(
            "runtime_service_manifest",
            "android:name=\".NodeJsRuntimePluginService\"" in manifestSource &&
                "android:process=\":nodejs_runtime\"" in manifestSource &&
                "android:permission=\"org.autojs.permission.PLUGIN\"" in manifestSource,
            "The packaged runtime service must retain its remote process and permission.",
        )
        requireCheck(
            "published_runtime_transactions",
            listOf("getRuntimeInfo()", "runScript(", "cancelScript(", "prewarmRuntime(")
                .all(aidlSource::contains),
            "The published runtime AIDL must retain its four version-1 transactions.",
        )
        @Suppress("UNCHECKED_CAST")
        val declaredAuthorities = ownershipPolicy["declaredAuthorities"] as Map<String, Any?>
        val pluginAuthorities = (declaredAuthorities["plugin"] as List<*>)
            .map(Any?::toString)
            .toSet()
        @Suppress("UNCHECKED_CAST")
        val migrationBacklog = ownershipPolicy["externalMigrationBacklog"] as List<Map<String, Any?>>
        val hostBacklog = migrationBacklog.single {
            it["id"] == "host_node_device_tests_and_corpora"
        }
        @Suppress("UNCHECKED_CAST")
        val ownershipSurfaces = ownershipPolicy["surfaces"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val runtimeTrees = ownershipSurfaces.single { it["id"] == "native_runtime_lifecycle" }
            .getValue("trees") as List<Map<String, Any?>>
        val corpusV1OwnershipTree = runtimeTrees.single {
            it["prefix"] == "app/src/androidTest/assets/node_compat_corpus/"
        }
        val provisionalMigrationToken = "sta" + "ged"
        requireCheck(
            "ownership_migrated_authority",
            setOf(
                "node_compat_corpus_v1",
                "node_compat_corpus_v2",
                "typescript_android_conformance",
                "zlib_android_conformance",
                "crypto_android_conformance",
            )
                .all(pluginAuthorities::contains) &&
                pluginAuthorities.none {
                    it.contains(provisionalMigrationToken, ignoreCase = true)
                } &&
                !Regex(provisionalMigrationToken, RegexOption.IGNORE_CASE)
                    .containsMatchIn(JsonOutput.toJson(hostBacklog)) &&
                hostBacklog["state"] == nodeAndroidConformanceHostMigrationState &&
                (corpusV1OwnershipTree["expectedFileCount"] as Number).toInt() == 27 &&
                corpusV1OwnershipTree["pathInventorySha256"] ==
                    "0ecb3c94cec23fec250b4a95593d415b94bc85f83b6068cbecf03847016e3af6" &&
                corpusV1OwnershipTree["migrationState"] ==
                    nodeAndroidConformanceCorpusV1OwnershipState,
            "Migrated Host harness/corpus authority must reject provisional tokens after the X3i ZLIB and X3j crypto authority migrations.",
        )
        requireCheck(
            "ownership_x3i_zlib_migrated_from_host",
            nodeAndroidConformanceX3iOwnershipMigrated(ownershipPolicy),
            "X3i ZLIB must claim migrated authority only with exact directed D and Host-removal evidence.",
        )
        requireCheck(
            "ownership_x3j_crypto_migrated_from_host",
            nodeAndroidConformanceX3jCurrentOwnershipValid(
                ownershipFile,
                ownershipPolicy,
                currentOwnershipVerifierFile,
                currentOwnershipReportFile,
                currentOwnershipReport,
            ),
            "X3j crypto must claim exactly one migrated Plugin authority, with the current 291/291 ownership report, all 17 self-tests, and exact receipt/D/raw identities.",
        )
        @Suppress("UNCHECKED_CAST")
        val completedSlice = hostBacklog["completedSlice"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val directedDevicePrerequisite =
            completedSlice["directedDevicePrerequisite"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val policyAnnotationAccounting =
            hostBacklog["annotationAccounting"] as Map<String, Any?>
        val policyEvidence = (hostBacklog["evidence"] as List<*>).map(Any?::toString).toSet()
        @Suppress("UNCHECKED_CAST")
        val policyCurrentHostBacklog = hostBacklog["currentHostBacklog"] as Map<String, Any?>
        requireCheck(
            "ownership_x3g_migrated_from_host",
            (completedSlice["nodeCompatCorpusV1Files"] as Number).toInt() == 27 &&
                (completedSlice["nodeCompatCorpusV1SelectorCount"] as Number).toInt() == 2 &&
                completedSlice["hostRemovalVerified"] == true &&
                completedSlice["migratedAuthorityClaimed"] == true &&
                completedSlice["retainedHostSelector"] ==
                    "moduleConformanceV2PackagedModuleSourcesGraphPasses" &&
                directedDevicePrerequisite["path"] ==
                    nodeAndroidConformanceX3gDirectedDeviceReportPath &&
                (directedDevicePrerequisite["bytes"] as Number).toLong() ==
                    nodeAndroidConformanceX3gDirectedDeviceReportBytes &&
                directedDevicePrerequisite["sha256"] ==
                    nodeAndroidConformanceX3gDirectedDeviceReportSha256 &&
                directedDevicePrerequisite["rawPath"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputPath &&
                (directedDevicePrerequisite["rawBytes"] as Number).toLong() ==
                    nodeAndroidConformanceX3gRawDeviceOutputBytes &&
                directedDevicePrerequisite["rawSha256"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputSha256 &&
                (directedDevicePrerequisite["attemptCount"] as Number).toInt() == 1 &&
                (directedDevicePrerequisite["testsRun"] as Number).toInt() == 2 &&
                (directedDevicePrerequisite["testsPassed"] as Number).toInt() == 2 &&
                directedDevicePrerequisite["baselineRestored"] == true &&
                directedDevicePrerequisite["device"] == "DEX_R1_API34_X64" &&
                directedDevicePrerequisite["serial"] == "emulator-5554" &&
                (directedDevicePrerequisite["apiLevel"] as Number).toInt() == 34 &&
                directedDevicePrerequisite["abi"] == "x86_64" &&
                directedDevicePrerequisite["emulator"] == true &&
                directedDevicePrerequisite["emptyBaseline"] == true &&
                directedDevicePrerequisite["temporaryAvdStartedAndStopped"] == true &&
                directedDevicePrerequisite["serialAbsentAfterShutdown"] == true &&
                (policyCurrentHostBacklog["nodeRelatedSourceFiles"] as Number).toInt() == 132 &&
                (policyCurrentHostBacklog["testAnnotations"] as Number).toInt() == 749 &&
                (policyCurrentHostBacklog["corpusFiles"] as Number).toInt() == 493 &&
                (policyAnnotationAccounting["currentHostNodeTestAnnotations"] as Number).toInt() == 749 &&
                policyEvidence.containsAll(setOf(
                    "tools/nodejs/ownership/evidence/x3e-host-removal.json",
                    "tools/nodejs/ownership/evidence/x3g-host-removal.json",
                    nodeAndroidConformanceX3gDirectedDeviceReportPath,
                    nodeAndroidConformanceX3gRawDeviceOutputPath,
                    nodeAndroidConformanceX3iHostRemovalReceiptPath,
                    nodeAndroidConformanceX3iDirectedDeviceReportPath,
                    nodeAndroidConformanceX3iRawDeviceOutputPath,
                    nodeAndroidConformanceX3jHostRemovalReceiptPath,
                    nodeAndroidConformanceX3jDirectedDeviceReportPath,
                    nodeAndroidConformanceX3jRawDeviceOutputPath,
                )),
            "The v1 corpus must retain exact X3g removal/D prerequisites while the current policy records the subsequent X3i and X3j removals and evidence.",
        )

        @Suppress("UNCHECKED_CAST")
        val x3eOwnershipGate = x3eHostRemovalReceipt["ownershipGate"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3eAndroidTestCompile = x3eHostRemovalReceipt["androidTestCompile"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3eRemovals = x3eHostRemovalReceipt["removals"] as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val x3eRemainingHostBacklog =
            x3eHostRemovalReceipt["remainingHostBacklog"] as Map<String, Any?>
        val removedTypeScriptTest = x3eRemovals.getValue("nodeTypeScriptInstrumentationTest")
        val removedCorpusSelector = x3eRemovals.getValue("nodeCompatCorpusV2Selector")
        val removedCorpusAssets = x3eRemovals.getValue("nodeCompatCorpusV2Assets")
        val x3eHostRemovalReceiptValid =
            x3eHostRemovalReceipt["schema"] == "autojs6-node-host-removal-receipt-v1" &&
                x3eHostRemovalReceipt["repository"] == "AutoJs6" &&
                x3eHostRemovalReceipt["repositoryRole"] == "host" &&
                x3eHostRemovalReceipt["scope"] == "x3e-node-android-test-migration" &&
                x3eOwnershipGate["task"] == ":app:verifyNodeRuntimeOwnershipGate" &&
                x3eOwnershipGate["ready"] == true &&
                (x3eOwnershipGate["violationCount"] as Number).toInt() == 0 &&
                x3eOwnershipGate["reportSha256"] ==
                    nodeAndroidConformanceHostOwnershipReportSha256 &&
                x3eOwnershipGate["allowlistSha256"] ==
                    nodeAndroidConformanceHostOwnershipAllowlistSha256 &&
                x3eAndroidTestCompile["task"] == ":app:compileAppDebugAndroidTestKotlin" &&
                x3eAndroidTestCompile["result"] == "passed" &&
                x3eAndroidTestCompile["sameInvocationAsOwnershipGate"] == true &&
                removedTypeScriptTest["path"] ==
                    "app/src/androidTest/java/org/autojs/autojs/engine/" +
                    "NodeTypeScriptInstrumentationTest.kt" &&
                removedTypeScriptTest["exists"] == false &&
                (removedTypeScriptTest["removedTestAnnotations"] as Number).toInt() == 7 &&
                removedCorpusSelector["path"] ==
                    "app/src/androidTest/java/org/autojs/autojs/engine/" +
                    "NodeCompatCorpusInstrumentationTest.kt" &&
                removedCorpusSelector["symbol"] == "moduleConformanceV2CorpusPasses" &&
                (removedCorpusSelector["remainingMatches"] as Number).toInt() == 0 &&
                (removedCorpusSelector["removedTestAnnotations"] as Number).toInt() == 1 &&
                removedCorpusAssets["root"] ==
                    "app/src/androidTest/assets/node_compat_corpus_v2" &&
                (removedCorpusAssets["remainingFileCount"] as Number).toInt() == 0 &&
                (removedCorpusAssets["removedFileCount"] as Number).toInt() == 15 &&
                (removedCorpusAssets["removedPaths"] as List<*>).map(Any?::toString).toSet() ==
                    nodeAndroidConformanceCorpusFiles.toSet() &&
                (x3eRemainingHostBacklog["nodeRelatedSourceFiles"] as Number).toInt() == 134 &&
                (x3eRemainingHostBacklog["testAnnotations"] as Number).toInt() == 752 &&
                (x3eRemainingHostBacklog["corpusFiles"] as Number).toInt() == 520

        @Suppress("UNCHECKED_CAST")
        val x3gPluginPrerequisite =
            x3gHostRemovalReceipt["pluginPrerequisite"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDirectedDeviceReport =
            x3gPluginPrerequisite["directedDeviceReport"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gRawDirectedDeviceOutput =
            x3gPluginPrerequisite["rawDirectedDeviceOutput"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceSelection =
            x3gDirectedDeviceEvidence["deviceSelection"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gExecutionPolicy =
            x3gDirectedDeviceEvidence["executionPolicy"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceArtifacts =
            x3gDirectedDeviceEvidence["artifacts"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceAppArtifact =
            x3gDeviceArtifacts["appDebugUniversal"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceTestArtifact =
            x3gDeviceArtifacts["androidTest"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceBaseline =
            x3gDirectedDeviceEvidence["baseline"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDevicePreflight =
            x3gDirectedDeviceEvidence["preflight"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceInstrumentation =
            x3gDirectedDeviceEvidence["instrumentation"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceSelectorResults =
            x3gDeviceInstrumentation["selectors"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceRestoration =
            x3gDirectedDeviceEvidence["restoration"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gDeviceEvidence =
            x3gDirectedDeviceEvidence["evidence"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gEmbeddedRawOutput =
            x3gDeviceEvidence["rawInstrumentationOutput"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gManagedImmutableCopy =
            x3gDeviceEvidence["managedImmutableCopy"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gTemporaryAvd =
            x3gDirectedDeviceEvidence["temporaryAvd"] as Map<String, Any?>
        val x3gRawDeviceOutput = x3gRawDeviceOutputFile.readText()
        val x3gRawSelectorNames =
            Regex("""(?m)^INSTRUMENTATION_STATUS: test=([A-Za-z0-9_]+)\r?$""")
                .findAll(x3gRawDeviceOutput)
                .map { match -> match.groupValues[1] }
                .distinct()
                .toList()
        val x3gEmptyBaselineRestored =
            listOf(
                "pluginVersionName",
                "pluginVersionCode",
                "pluginBaseApkBytes",
                "pluginBaseApkSha256",
            ).all(x3gDeviceBaseline::containsKey) &&
                x3gDeviceBaseline["pluginPackageExisted"] == false &&
                x3gDeviceBaseline["pluginPackageName"] ==
                    "io.github.supermonster003.autojs6.plugin.nodejs" &&
                x3gDeviceBaseline["pluginVersionName"] == null &&
                x3gDeviceBaseline["pluginVersionCode"] == null &&
                x3gDeviceBaseline["pluginBaseApkBytes"] == null &&
                x3gDeviceBaseline["pluginBaseApkSha256"] == null &&
                x3gDeviceBaseline["baselineApkPullRequired"] == false &&
                x3gDeviceBaseline["pluginTestPackageExisted"] == false &&
                x3gDeviceBaseline["hostPackageExisted"] == false &&
                x3gDeviceBaseline["hostTestPackageExisted"] == false &&
                x3gDeviceBaseline["allRelevantPackagesAbsent"] == true &&
                x3gDeviceRestoration["baselineRestored"] == true &&
                x3gDeviceRestoration["testPackageUninstalledBecauseAbsentAtBaseline"] == true &&
                x3gDeviceRestoration["testPackageUninstallResult"] == "Success" &&
                x3gDeviceRestoration["pluginPackageUninstalledBecauseAbsentAtBaseline"] == true &&
                x3gDeviceRestoration["pluginPackageUninstallResult"] == "Success" &&
                x3gDeviceRestoration["pluginPackageAbsentAfterRestore"] == true &&
                x3gDeviceRestoration["pluginTestPackageAbsentAfterRestore"] == true &&
                x3gDeviceRestoration["hostAndHostTestPackagesAbsentAfterRestore"] == true &&
                x3gDeviceRestoration["nodeOrAutoJsProcessAbsentAfterRestore"] == true &&
                x3gDeviceRestoration["activeInstrumentationAbsentAfterRestore"] == true &&
                x3gDeviceRestoration["nodeRuntimeServiceAbsentAfterRestore"] == true &&
                x3gDeviceRestoration["temporaryBaselineCopyStatus"] ==
                    "not_created_baseline_packages_absent"
        val x3gTemporaryAvdStopped =
            x3gTemporaryAvd["startedByThisRun"] == true &&
                x3gTemporaryAvd["avdName"] == "DEX_R1_API34_X64" &&
                (x3gTemporaryAvd["emulatorProcessId"] as Number).toLong() > 0L &&
                (x3gTemporaryAvd["launchArguments"] as List<*>).map(Any?::toString) == listOf(
                    "-read-only",
                    "-no-snapshot-load",
                    "-no-snapshot-save",
                    "-no-boot-anim",
                    "-no-window",
                    "-no-audio",
                ) &&
                x3gTemporaryAvd["wipeDataUsed"] == false &&
                x3gTemporaryAvd["avdDefinitionDeletedOrModified"] == false &&
                (x3gTemporaryAvd["preexistingOnlineEmulatorCount"] as Number).toInt() == 0 &&
                (x3gTemporaryAvd["adbSerialsBeforeLaunch"] as List<*>).map(Any?::toString) ==
                    listOf("968e9f18", "QV710AF65F") &&
                x3gTemporaryAvd["newSerial"] == "emulator-5554" &&
                x3gTemporaryAvd["newSerialWasUnique"] == true &&
                x3gTemporaryAvd["shutdownCommand"] == "adb -s emulator-5554 emu kill" &&
                (x3gTemporaryAvd["shutdownExitCode"] as Number).toInt() == 0 &&
                x3gTemporaryAvd["shutdownAcknowledged"] == true &&
                x3gTemporaryAvd["serialAbsentAfterShutdown"] == true &&
                (x3gTemporaryAvd["adbSerialsAfterShutdown"] as List<*>).map(Any?::toString) ==
                    listOf("968e9f18", "QV710AF65F") &&
                x3gTemporaryAvd["serialSetRestored"] == true &&
                x3gTemporaryAvd["temporaryAvdStartedAndStopped"] == true
        @Suppress("UNCHECKED_CAST")
        val x3gOwnershipGate = x3gHostRemovalReceipt["ownershipGate"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gAndroidTestCompile =
            x3gHostRemovalReceipt["androidTestCompile"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gRemovals =
            x3gHostRemovalReceipt["removals"] as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val x3gRetainedHostAuthority =
            x3gHostRemovalReceipt["retainedHostAuthority"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gAnnotationAccounting =
            x3gHostRemovalReceipt["annotationAccounting"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val x3gRemainingHostBacklog =
            x3gHostRemovalReceipt["remainingHostBacklog"] as Map<String, Any?>
        val removedCorpusV1Selector = x3gRemovals.getValue("nodeCompatCorpusV1Selector")
        val removedPreloadSelector =
            x3gRemovals.getValue("preloadShapedModuleSourcesSelector")
        val removedCorpusV1Assets = x3gRemovals.getValue("nodeCompatCorpusV1Assets")
        val x3gExpectedSelectors = nodeAndroidConformanceX3gSelectors
        val x3gDirectedSelectorNames = x3gDeviceSelectorResults.map { result ->
            result["name"].toString()
        }
        val x3gExpectedInstrumentationCommand =
            "adb -s emulator-5554 shell am instrument -w -r -e class " +
                "$nodeAndroidConformanceTestClass#${x3gExpectedSelectors[0]}," +
                "$nodeAndroidConformanceTestClass#${x3gExpectedSelectors[1]} " +
                "io.github.supermonster003.autojs6.plugin.nodejs.test/" +
                "androidx.test.runner.AndroidJUnitRunner"
        val x3gCurrentAppSha256 = appApk.nodeAndroidConformanceSha256()
        val x3gCurrentTestSha256 = testApk.nodeAndroidConformanceSha256()
        val x3gEvidenceFreshForCurrentArtifacts =
            (x3gDeviceAppArtifact["bytes"] as Number).toLong() == appApk.length() &&
                x3gDeviceAppArtifact["sha256"] == x3gCurrentAppSha256 &&
                (x3gDeviceTestArtifact["bytes"] as Number).toLong() == testApk.length() &&
                x3gDeviceTestArtifact["sha256"] == x3gCurrentTestSha256
        val x3gDirectedDeviceEvidenceValid =
            x3gDirectedDeviceReportFile.length() ==
                nodeAndroidConformanceX3gDirectedDeviceReportBytes &&
                x3gDirectedDeviceReportFile.nodeAndroidConformanceSha256() ==
                    nodeAndroidConformanceX3gDirectedDeviceReportSha256 &&
                x3gRawDeviceOutputFile.length() ==
                    nodeAndroidConformanceX3gRawDeviceOutputBytes &&
                x3gRawDeviceOutputFile.nodeAndroidConformanceSha256() ==
                    nodeAndroidConformanceX3gRawDeviceOutputSha256 &&
                x3gDirectedDeviceEvidence["schema"] ==
                    "autojs6-node-plugin-x3g-directed-device-run-v1" &&
                x3gDirectedDeviceEvidence["milestone"] == "X3g" &&
                x3gDirectedDeviceEvidence["result"] == "pass" &&
                x3gDirectedDeviceEvidence["evidenceLevel"] == "directed-device" &&
                x3gDeviceSelection["requestedAvdName"] == "DEX_R1_API34_X64" &&
                x3gDeviceSelection["observedSerial"] == "emulator-5554" &&
                x3gDeviceSelection["uniqueNewEmulatorSerial"] == true &&
                x3gDeviceSelection["serialAmbiguous"] == false &&
                x3gDeviceSelection["manufacturer"] == "unknown" &&
                x3gDeviceSelection["model"] == "Android SDK built for x86_64" &&
                (x3gDeviceSelection["apiLevel"] as Number).toInt() == 34 &&
                (x3gDeviceSelection["abis"] as List<*>).map(Any?::toString) ==
                    listOf("x86_64") &&
                x3gDeviceSelection["emulator"] == true &&
                x3gDeviceSelection["bootCompletedBeforeRun"] == true &&
                x3gExecutionPolicy["allDirectedDeviceCommandsUsedExplicitSerial"] == true &&
                x3gExecutionPolicy["adbEnumerationCommandsWereReadOnly"] == true &&
                (x3gExecutionPolicy["physicalDeviceDirectedCommandCount"] as Number).toInt() == 0 &&
                x3gExecutionPolicy["connectedTaskUsed"] == false &&
                x3gExecutionPolicy["genericConnectedTaskUsed"] == false &&
                x3gExecutionPolicy["gradleTaskUsed"] == false &&
                x3gExecutionPolicy["soakRun"] == false &&
                x3gExecutionPolicy["fullNineteenSelectorHarnessRun"] == false &&
                (x3gExecutionPolicy["selectorCount"] as Number).toInt() == 2 &&
                (x3gExecutionPolicy["attemptCount"] as Number).toInt() == 1 &&
                (x3gExecutionPolicy["retryCount"] as Number).toInt() == 0 &&
                x3gExecutionPolicy["sourceChangedAfterFailure"] == false &&
                x3gDevicePreflight["deviceState"] == "device" &&
                x3gDevicePreflight["bootCompleted"] == true &&
                x3gDevicePreflight["avdNameVerified"] == true &&
                x3gDevicePreflight["apiAndAbiVerified"] == true &&
                x3gDevicePreflight["soakDetected"] == false &&
                x3gDevicePreflight["activeInstrumentationDetected"] == false &&
                x3gDevicePreflight["nodeOrAutoJsProcessDetected"] == false &&
                x3gDevicePreflight["nodeRuntimeServiceDetected"] == false &&
                x3gDevicePreflight["nodeRuntimeServiceDump"] == "(nothing)" &&
                x3gDirectedSelectorNames == x3gExpectedSelectors &&
                x3gDirectedSelectorNames.all { selector ->
                    Regex("^[A-Za-z0-9_]+$").matches(selector) && '.' !in selector
                } &&
                x3gDeviceSelectorResults.all { result ->
                    result["result"] == "pass" &&
                        (result["terminalStatusCode"] as Number).toInt() == 0
                } &&
                x3gDeviceInstrumentation["command"] == x3gExpectedInstrumentationCommand &&
                x3gDeviceInstrumentation["className"] == nodeAndroidConformanceTestClass &&
                (x3gDeviceInstrumentation["testsRun"] as Number).toInt() == 2 &&
                (x3gDeviceInstrumentation["testsPassed"] as Number).toInt() == 2 &&
                (x3gDeviceInstrumentation["testsFailed"] as Number).toInt() == 0 &&
                (x3gDeviceInstrumentation["testsIgnored"] as Number).toInt() == 0 &&
                x3gDeviceInstrumentation["runnerSummary"] == "OK (2 tests)" &&
                (x3gDeviceInstrumentation["runnerReportedSeconds"] as Number).toDouble() > 0.0 &&
                (x3gDeviceInstrumentation["instrumentationCode"] as Number).toInt() == -1 &&
                x3gDeviceInstrumentation["instrumentationCodeMeaning"] ==
                    "Android instrumentation success" &&
                (x3gDeviceInstrumentation["adbExitCode"] as Number).toInt() == 0 &&
                x3gDeviceInstrumentation["firstAndOnlyRunPassed"] == true &&
                x3gDeviceInstrumentation["failureEvidence"] == null &&
                x3gEmptyBaselineRestored &&
                x3gTemporaryAvdStopped &&
                x3gEmbeddedRawOutput["path"] ==
                    nodeAndroidConformanceX3gOriginalRawDeviceOutputPath &&
                (x3gEmbeddedRawOutput["bytes"] as Number).toLong() ==
                    nodeAndroidConformanceX3gRawDeviceOutputBytes &&
                x3gEmbeddedRawOutput["sha256"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputSha256 &&
                x3gManagedImmutableCopy["jsonPath"] ==
                    nodeAndroidConformanceX3gDirectedDeviceReportPath &&
                x3gManagedImmutableCopy["rawPath"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputPath &&
                (x3gManagedImmutableCopy["rawBytes"] as Number).toLong() ==
                    nodeAndroidConformanceX3gRawDeviceOutputBytes &&
                x3gManagedImmutableCopy["rawSha256"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputSha256 &&
                x3gRawSelectorNames == x3gExpectedSelectors &&
                Regex("""(?m)^OK \(2 tests\)\r?$""").containsMatchIn(x3gRawDeviceOutput) &&
                Regex("""(?m)^INSTRUMENTATION_CODE: -1\r?$""")
                    .containsMatchIn(x3gRawDeviceOutput) &&
                x3gDeviceArtifacts["aaptIdentityVerifiedBeforeInstall"] == true &&
                x3gDeviceAppArtifact["path"] ==
                    "app/build/outputs/apk/debug/autojs6-plugin-nodejs-runtime-v1.1.0-universal.apk" &&
                x3gDeviceAppArtifact["packageName"] ==
                    "io.github.supermonster003.autojs6.plugin.nodejs" &&
                x3gDeviceAppArtifact["versionName"] == "1.1.0" &&
                (x3gDeviceAppArtifact["versionCode"] as Number).toInt() == 37 &&
                (x3gDeviceAppArtifact["minSdk"] as Number).toInt() == 24 &&
                (x3gDeviceAppArtifact["targetSdk"] as Number).toInt() == 36 &&
                x3gDeviceAppArtifact["nativeAbiMatchedDevice"] == true &&
                x3gDeviceAppArtifact["deviceInstalledSha256Verified"] == true &&
                x3gDeviceTestArtifact["path"] == testApk.nodeAndroidConformanceRelativePath() &&
                x3gDeviceTestArtifact["packageName"] ==
                    "io.github.supermonster003.autojs6.plugin.nodejs.test" &&
                x3gDeviceTestArtifact["targetPackageName"] ==
                    "io.github.supermonster003.autojs6.plugin.nodejs" &&
                x3gDeviceTestArtifact["runner"] ==
                    "androidx.test.runner.AndroidJUnitRunner" &&
                (x3gDeviceTestArtifact["minSdk"] as Number).toInt() == 24 &&
                (x3gDeviceTestArtifact["targetSdk"] as Number).toInt() == 36 &&
                x3gDeviceTestArtifact["deviceInstalledSha256Verified"] == true
        requireCheck(
            "host_removal_receipts_and_x3g_device_prerequisite",
            x3eHostRemovalReceiptValid &&
                x3gHostRemovalReceipt["schema"] == "autojs6-node-host-removal-receipt-v1" &&
                x3gHostRemovalReceipt["repository"] == "AutoJs6" &&
                x3gHostRemovalReceipt["repositoryRole"] == "host" &&
                x3gHostRemovalReceipt["scope"] == "x3g-node-compat-corpus-v1-migration" &&
                x3gPluginPrerequisite["corpusRoot"] ==
                    "app/src/androidTest/assets/node_compat_corpus" &&
                (x3gPluginPrerequisite["corpusFileCount"] as Number).toInt() == 27 &&
                x3gPluginPrerequisite["corpusPathInventorySha256"] ==
                    "0ecb3c94cec23fec250b4a95593d415b94bc85f83b6068cbecf03847016e3af6" &&
                x3gPluginPrerequisite["corpusNormalizedContentSha256"] ==
                    nodeAndroidConformanceCorpusV1ContentSha256 &&
                (x3gPluginPrerequisite["selectors"] as List<*>).map(Any?::toString) ==
                    x3gExpectedSelectors &&
                x3gDirectedDeviceReport["path"] ==
                    nodeAndroidConformanceX3gDirectedDeviceReportPath &&
                x3gDirectedDeviceReport["originalPath"] ==
                    nodeAndroidConformanceX3gOriginalDirectedDeviceReportPath &&
                (x3gDirectedDeviceReport["bytes"] as Number).toLong() ==
                    nodeAndroidConformanceX3gDirectedDeviceReportBytes &&
                x3gDirectedDeviceReport["sha256"] ==
                    nodeAndroidConformanceX3gDirectedDeviceReportSha256 &&
                x3gDirectedDeviceReport["device"] == "DEX_R1_API34_X64" &&
                x3gDirectedDeviceReport["serial"] == "emulator-5554" &&
                (x3gDirectedDeviceReport["apiLevel"] as Number).toInt() == 34 &&
                x3gDirectedDeviceReport["abi"] == "x86_64" &&
                x3gDirectedDeviceReport["emulator"] == true &&
                (x3gDirectedDeviceReport["attemptCount"] as Number).toInt() == 1 &&
                (x3gDirectedDeviceReport["testsRun"] as Number).toInt() == 2 &&
                (x3gDirectedDeviceReport["testsPassed"] as Number).toInt() == 2 &&
                (x3gDirectedDeviceReport["testsFailed"] as Number).toInt() == 0 &&
                (x3gDirectedDeviceReport["testsIgnored"] as Number).toInt() == 0 &&
                x3gDirectedDeviceReport["runnerSummary"] == "OK (2 tests)" &&
                x3gDirectedDeviceReport["baselineRestored"] == true &&
                x3gDirectedDeviceReport["baselineRestored"] ==
                    x3gEmptyBaselineRestored &&
                x3gDirectedDeviceReport["emptyBaseline"] == true &&
                x3gDirectedDeviceReport["temporaryAvdStartedAndStopped"] == true &&
                x3gDirectedDeviceReport["serialAbsentAfterShutdown"] == true &&
                x3gRawDirectedDeviceOutput["path"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputPath &&
                x3gRawDirectedDeviceOutput["originalPath"] ==
                    nodeAndroidConformanceX3gOriginalRawDeviceOutputPath &&
                (x3gRawDirectedDeviceOutput["bytes"] as Number).toLong() ==
                    nodeAndroidConformanceX3gRawDeviceOutputBytes &&
                x3gRawDirectedDeviceOutput["sha256"] ==
                    nodeAndroidConformanceX3gRawDeviceOutputSha256 &&
                x3gDirectedDeviceEvidenceValid &&
                x3gOwnershipGate["task"] == ":app:verifyNodeRuntimeOwnershipGate" &&
                x3gOwnershipGate["ready"] == true &&
                (x3gOwnershipGate["violationCount"] as Number).toInt() == 0 &&
                x3gOwnershipGate["reportSha256"] ==
                    nodeAndroidConformanceX3gHostOwnershipReportSha256 &&
                x3gOwnershipGate["allowlistSha256"] ==
                    nodeAndroidConformanceX3gHostOwnershipAllowlistSha256 &&
                x3gAndroidTestCompile["task"] == ":app:compileAppDebugAndroidTestKotlin" &&
                x3gAndroidTestCompile["result"] == "passed" &&
                x3gAndroidTestCompile["sameInvocationAsOwnershipGate"] == true &&
                removedCorpusV1Selector["symbol"] == "pureJavaScriptCommonJsCorpusPasses" &&
                (removedCorpusV1Selector["remainingMatches"] as Number).toInt() == 0 &&
                (removedCorpusV1Selector["removedTestAnnotations"] as Number).toInt() == 1 &&
                removedPreloadSelector["symbol"] ==
                    "corpusSubsetRunsFromModuleSourcesPreloadShape" &&
                (removedPreloadSelector["remainingMatches"] as Number).toInt() == 0 &&
                (removedPreloadSelector["removedTestAnnotations"] as Number).toInt() == 1 &&
                removedCorpusV1Assets["root"] ==
                    "app/src/androidTest/assets/node_compat_corpus" &&
                (removedCorpusV1Assets["remainingFileCount"] as Number).toInt() == 0 &&
                (removedCorpusV1Assets["removedFileCount"] as Number).toInt() == 27 &&
                removedCorpusV1Assets["removedNormalizedContentSha256"] ==
                    nodeAndroidConformanceCorpusV1ContentSha256 &&
                (removedCorpusV1Assets["removedPaths"] as List<*>).map(Any?::toString).toSet() ==
                    nodeAndroidConformanceCorpusV1Files.toSet() &&
                x3gRetainedHostAuthority["symbol"] ==
                    "moduleConformanceV2PackagedModuleSourcesGraphPasses" &&
                x3gRetainedHostAuthority["retained"] == true &&
                (x3gRetainedHostAuthority["remainingMatches"] as Number).toInt() == 1 &&
                (x3gRetainedHostAuthority["testAnnotationsInFile"] as Number).toInt() == 1 &&
                (x3gRemainingHostBacklog["nodeRelatedSourceFiles"] as Number).toInt() == 134 &&
                (x3gRemainingHostBacklog["testAnnotations"] as Number).toInt() == 751 &&
                (x3gRemainingHostBacklog["corpusFiles"] as Number).toInt() == 493 &&
                x3gRemainingHostBacklog["instrumentationPathSetSha256"] ==
                    nodeAndroidConformanceX3gInstrumentationPathSetSha256 &&
                x3gRemainingHostBacklog["corpusPathSetSha256"] ==
                    nodeAndroidConformanceX3gCorpusPathSetSha256 &&
                (x3gAnnotationAccounting["currentHostNodeTestAnnotations"] as Number).toInt() == 751 &&
                x3gAnnotationAccounting["note"] ==
                    nodeAndroidConformanceX3gAnnotationAccountingNote,
            "The immutable historical X3g receipt and directed D evidence must retain exact selectors, raw output, Host removal, restored empty baseline, and stopped temporary AVD. " +
                "Artifact freshness is reported separately and is not promoted to current X3h evidence.",
        )

        val x3iCurrentAppApk = layout.buildDirectory.file(
            "outputs/apk/debug/autojs6-plugin-nodejs-runtime-v1.1.3-x86_64.apk",
        ).get().asFile
        val x3iCurrentAppSha256 = x3iCurrentAppApk.nodeAndroidConformanceSha256()
        val x3iCurrentTestApkSha256 = testApk.nodeAndroidConformanceSha256()
        val x3iCurrentTestSourceSha256 = testFile.nodeAndroidConformanceSha256()
        val x3iHistoricalAppFreshForCurrentArtifact =
            x3iCurrentAppApk.length() == nodeAndroidConformanceX3iAppBytes &&
                x3iCurrentAppSha256 == nodeAndroidConformanceX3iAppSha256
        val x3iHistoricalTestApkFreshForCurrentArtifact =
            testApk.length() == nodeAndroidConformanceX3iTestApkBytes &&
                x3iCurrentTestApkSha256 == nodeAndroidConformanceX3iTestApkSha256
        val x3iHistoricalTestSourceFreshForCurrentArtifact =
            testFile.length() == nodeAndroidConformanceX3iTestSourceBytes &&
                x3iCurrentTestSourceSha256 == nodeAndroidConformanceX3iTestSourceSha256
        requireCheck(
            "x3i_host_removal_and_directed_device_prerequisite",
            nodeAndroidConformanceX3iEvidenceValid(
                x3iHostRemovalReceiptFile,
                x3iHostRemovalReceipt,
                x3iDirectedDeviceReportFile,
                x3iDirectedDeviceEvidence,
                x3iRawDeviceOutputFile,
                testApk,
                testFile,
            ),
            "X3i promotion requires exact immutable historical Host-removal, directed-device, raw-output, app/test/source artifact identities, restored-baseline, and temporary-AVD cleanup evidence. Current app/test/source freshness is descriptive and is not current execution or a prerequisite replay.",
        )

        val x3jEvidenceValid = nodeAndroidConformanceX3jEvidenceValid(
            x3jHostRemovalReceiptFile,
            x3jHostRemovalReceipt,
            x3jDirectedDeviceReportFile,
            x3jDirectedDeviceEvidence,
            x3jRawDeviceOutputFile,
            x3iCurrentAppApk,
            testApk,
            testFile,
        )
        requireCheck(
            "x3j_host_removal_and_directed_device_prerequisite",
            x3jEvidenceValid,
            "X3j promotion requires the exact one-attempt API 34 x86_64 D JSON/raw, frozen app/test/source artifacts, restored ADB/AVD baseline, and the exact Host removal/compile/ownership receipt.",
        )

        val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        requireCheck(
            "universal_runtime_native_payload",
            supportedAbis.all { abi ->
                "lib/$abi/libnode.so" in appEntries &&
                    "lib/$abi/libautojs6-node.so" in appEntries
            },
            "The universal debug APK must package libnode and the native bridge for all supported ABIs.",
        )
        requireCheck(
            "android_test_apk_dex",
            "AndroidManifest.xml" in testEntries &&
                testEntries.any { Regex("^classes(?:\\d+)?\\.dex$").matches(it) },
            "The Android-test APK must contain a manifest and compiled DEX.",
        )
        requireCheck(
            "android_test_apk_x3i_zlib_selector_semantics",
            nodeAndroidConformanceX3iPackaged(testApk),
            "The Android-test APK DEX must contain the X3i selector, success receipt, and all three exact ZLIB cleanup diagnostic keys.",
        )
        requireCheck(
            "android_test_apk_x3j_crypto_selector_semantics",
            nodeAndroidConformanceX3jPackaged(testApk),
            "The Android-test APK DEX must contain the X3j selector, exact crypto/HMAC receipts, provider boundary, and pending-callback diagnostic.",
        )
        val packagedCorpusEntries = testEntries
            .filter { it.startsWith("assets/node_compat_corpus_v2/") && !it.endsWith("/") }
            .toSet()
        val expectedPackagedCorpusEntries = nodeAndroidConformanceCorpusFiles
            .map { "assets/node_compat_corpus_v2/$it" }
            .toSet()
        requireCheck(
            "android_test_apk_node_compat_corpus_v2",
            packagedCorpusEntries == expectedPackagedCorpusEntries,
            "The Android-test APK must package exactly the reviewed 15-file v2 corpus.",
        )
        val packagedCorpusV1Entries = testEntries
            .filter { it.startsWith("assets/node_compat_corpus/") && !it.endsWith("/") }
            .toSet()
        val expectedPackagedCorpusV1Entries = nodeAndroidConformanceCorpusV1Files
            .map { "assets/node_compat_corpus/$it" }
            .toSet()
        requireCheck(
            "android_test_apk_node_compat_corpus_v1",
            packagedCorpusV1Entries == expectedPackagedCorpusV1Entries,
            "The Android-test APK must package exactly the reviewed 27-file migrated v1 corpus.",
        )

        val reportFile = nodeAndroidConformanceReport.get().asFile
        reportFile.parentFile.mkdirs()
        val report = linkedMapOf<String, Any?>(
            "schema" to nodeAndroidConformanceSchema,
            "schemaVersion" to 1,
            "decision" to "harness_packaged_not_executed",
            "gatePassed" to true,
            "verificationLevel" to "A",
            "testClass" to nodeAndroidConformanceTestClass,
            "selectors" to nodeAndroidConformanceSelectors,
            "selectorCount" to nodeAndroidConformanceSelectors.size,
            "scope" to linkedMapOf(
                "realPluginService" to true,
                "publishedAidl" to true,
                "workspaceArchiveV2" to true,
                "distinctInputOutputPfd" to true,
                "packagedNativeRuntime" to true,
                "materializedWorkspacePlaintextTypescript" to true,
                "nodeCompatCorpusV2" to true,
                "nodeCompatCorpusV1MigratedFromHost" to true,
                "nodeCompatCorpusV1SelectorCount" to 2,
                "nodeCompatCorpusV1DirectedDevicePrerequisitePassed" to true,
                "typeScriptAndroidCaseCount" to
                    nodeAndroidConformanceTypeScriptSelectors.size,
                "pluginProviderV2MissingPlaintextMaterialization" to true,
                "providerV2AndroidSelectorCount" to 3,
                "x3hExactProviderAndroidSelectorCount" to
                    nodeAndroidConformanceX3hGraphSelectors.size,
                "x3hProviderWorkspaceCleanupSelectorCount" to 1,
                "x3hInitialModuleAndRuntimeModuleMapsZero" to true,
                "x3iZlibCallbacksStreamsShadowingSelectorCount" to
                    nodeAndroidConformanceX3iSelectors.size,
                "x3jCryptoSafeExpansionShadowingSelectorCount" to
                    nodeAndroidConformanceX3jSelectors.size,
                "x3jDirectedDevicePrerequisitePassed" to true,
                "x3jHostRemovalVerified" to true,
                "providerMaterializationAndroidHelperCaseCount" to 5,
                "selinuxSafeDirectTargetPublication" to true,
                "hostUnknownComputedDependencyMaterialization" to false,
                "hostDiscoveryTrustPinning" to false,
            ),
            "artifacts" to linkedMapOf(
                "appApk" to linkedMapOf(
                    "path" to appApk.nodeAndroidConformanceRelativePath(),
                    "bytes" to appApk.length(),
                    "sha256" to appApk.nodeAndroidConformanceSha256(),
                ),
                "androidTestApk" to linkedMapOf(
                    "path" to testApk.nodeAndroidConformanceRelativePath(),
                    "bytes" to testApk.length(),
                    "sha256" to testApk.nodeAndroidConformanceSha256(),
                ),
                "testSource" to linkedMapOf(
                    "path" to testFile.nodeAndroidConformanceRelativePath(),
                    "bytes" to testFile.length(),
                    "sha256" to testFile.nodeAndroidConformanceSha256(),
                ),
                "nodeCompatCorpusV2" to linkedMapOf(
                    "path" to "app/$nodeAndroidConformanceCorpusRelativeRoot",
                    "fileCount" to actualCorpusFiles.size,
                    "normalizedContentSha256" to
                        corpusRoot.nodeAndroidConformanceNormalizedCorpusSha256(actualCorpusFiles),
                ),
                "nodeCompatCorpusV1" to linkedMapOf(
                    "path" to "app/$nodeAndroidConformanceCorpusV1RelativeRoot",
                    "fileCount" to actualCorpusV1Files.size,
                    "normalizedContentSha256" to
                        corpusV1Root.nodeAndroidConformanceNormalizedCorpusSha256(actualCorpusV1Files),
                    "migrationState" to nodeAndroidConformanceCorpusV1OwnershipState,
                ),
            ),
            "migration" to linkedMapOf(
                "state" to "migrated-from-host",
                "items" to listOf(
                    linkedMapOf(
                        "source" to "node_compat_corpus_v2",
                        "pluginCopyOwned" to true,
                        "hostRemovalVerified" to true,
                    ),
                    linkedMapOf(
                        "source" to "NodeTypeScriptInstrumentationTest",
                        "pluginCaseCount" to nodeAndroidConformanceTypeScriptSelectors.size,
                        "hostRemovalVerified" to true,
                    ),
                    linkedMapOf(
                        "source" to "node_compat_corpus",
                        "pluginCopyOwned" to true,
                        "pluginSelectorCount" to 2,
                        "hostRemovalVerified" to true,
                        "directedDevicePrerequisitePassed" to true,
                    ),
                    linkedMapOf(
                        "source" to "NodeZlibBufferInstrumentationTest",
                        "pluginSelectorCount" to 1,
                        "hostRemovalVerified" to true,
                        "directedDevicePrerequisitePassed" to true,
                    ),
                    linkedMapOf(
                        "source" to "NodeCryptoExpansionInstrumentationTest",
                        "pluginSelectorCount" to 1,
                        "hostRemovalVerified" to true,
                        "directedDevicePrerequisitePassed" to true,
                    ),
                ),
                "migratedAuthorityClaimed" to true,
                "companionHostEvidence" to linkedMapOf(
                    "x3eReceipt" to x3eHostRemovalReceiptFile.nodeAndroidConformanceRelativePath(),
                    "x3eReceiptSha256" to x3eHostRemovalReceiptFile.nodeAndroidConformanceSha256(),
                    "x3gReceipt" to x3gHostRemovalReceiptFile.nodeAndroidConformanceRelativePath(),
                    "x3gReceiptSha256" to x3gHostRemovalReceiptFile.nodeAndroidConformanceSha256(),
                    "x3iReceipt" to x3iHostRemovalReceiptFile.nodeAndroidConformanceRelativePath(),
                    "x3iReceiptSha256" to x3iHostRemovalReceiptFile.nodeAndroidConformanceSha256(),
                    "x3jReceipt" to x3jHostRemovalReceiptFile.nodeAndroidConformanceRelativePath(),
                    "x3jReceiptSha256" to x3jHostRemovalReceiptFile.nodeAndroidConformanceSha256(),
                    "currentPluginOwnershipReport" to
                        currentOwnershipReportFile.nodeAndroidConformanceRelativePath(),
                    "currentPluginOwnershipReportSha256" to
                        currentOwnershipReportFile.nodeAndroidConformanceSha256(),
                    "x3gHistoricalOwnershipReportSha256" to x3gOwnershipGate["reportSha256"],
                    "x3gHistoricalOwnershipAllowlistSha256" to x3gOwnershipGate["allowlistSha256"],
                    "x3gHistoricalOwnershipGate" to x3gOwnershipGate["task"],
                    "x3gHistoricalAndroidTestCompileTask" to x3gAndroidTestCompile["task"],
                    "x3gHistoricalAndroidInstrumentationFiles" to
                        x3gRemainingHostBacklog["nodeRelatedSourceFiles"],
                    "x3gHistoricalNodeTestAnnotations" to x3gRemainingHostBacklog["testAnnotations"],
                    "x3gHistoricalCorpusFiles" to x3gRemainingHostBacklog["corpusFiles"],
                    "x3gHistoricalInstrumentationPathSetSha256" to
                        x3gRemainingHostBacklog["instrumentationPathSetSha256"],
                    "x3gHistoricalCorpusPathSetSha256" to
                        x3gRemainingHostBacklog["corpusPathSetSha256"],
                    "x3gRetainedHostSelector" to
                        x3gRetainedHostAuthority["symbol"],
                ),
                "independentDirectedDevicePrerequisite" to linkedMapOf(
                    "path" to x3gDirectedDeviceReport["path"],
                    "sha256" to x3gDirectedDeviceReport["sha256"],
                    "rawPath" to x3gRawDirectedDeviceOutput["path"],
                    "rawSha256" to x3gRawDirectedDeviceOutput["sha256"],
                    "device" to x3gDirectedDeviceReport["device"],
                    "serial" to x3gDirectedDeviceReport["serial"],
                    "apiLevel" to x3gDirectedDeviceReport["apiLevel"],
                    "abi" to x3gDirectedDeviceReport["abi"],
                    "emulator" to x3gDirectedDeviceReport["emulator"],
                    "attemptCount" to x3gDirectedDeviceReport["attemptCount"],
                    "testsRun" to x3gDirectedDeviceReport["testsRun"],
                    "testsPassed" to x3gDirectedDeviceReport["testsPassed"],
                    "baselineRestored" to x3gDirectedDeviceReport["baselineRestored"],
                    "emptyBaseline" to x3gDirectedDeviceReport["emptyBaseline"],
                    "temporaryAvdStartedAndStopped" to
                        x3gDirectedDeviceReport["temporaryAvdStartedAndStopped"],
                    "serialAbsentAfterShutdown" to
                        x3gDirectedDeviceReport["serialAbsentAfterShutdown"],
                    "historicalEvidenceFreshForCurrentArtifacts" to
                        x3gEvidenceFreshForCurrentArtifacts,
                    "historicalAppSha256" to x3gDeviceAppArtifact["sha256"],
                    "historicalAndroidTestSha256" to x3gDeviceTestArtifact["sha256"],
                    "currentAppSha256" to x3gCurrentAppSha256,
                    "currentAndroidTestSha256" to x3gCurrentTestSha256,
                    "evidenceBoundary" to
                        "This immutable X3g prerequisite was collected independently. Its artifact freshness is descriptive only and is not current X3h execution.",
                ),
            ),
            "x3h" to linkedMapOf(
                "milestone" to "X3h",
                "state" to "staged-plugin-only",
                "verificationLevel" to "A",
                "selectors" to nodeAndroidConformanceX3hSelectors,
                "selectorCount" to nodeAndroidConformanceX3hSelectors.size,
                "initialModuleSourcesCount" to 0,
                "initialRuntimeModuleSourcesCount" to 0,
                "providerV2ExactBoundedCandidates" to true,
                "providerFirstPolicyMetadata" to true,
                "sharedRequestDeadlineAndBudgets" to true,
                "permissionAndBridgeLimitsUseExactMetadataSnapshot" to true,
                "materializedMetadataUsesVerifiedPlaintextRecord" to true,
                "deviceExecuted" to false,
                "hostPreloadQuarantined" to false,
                "hostOwnershipMigrated" to false,
                "runtimeKitFrozen" to true,
                "releaseNativeRebuildRequired" to false,
                "historicalX3gEvidenceFreshForCurrentArtifacts" to
                    x3gEvidenceFreshForCurrentArtifacts,
                "evidenceBoundary" to
                    "X3h remains Plugin compile/package evidence for these selectors and does not prove Host zero-preload quarantine, Host ownership migration, or Binder/device execution. Runtime Kit 1.1.3 and its rebuilt stripped native payloads are frozen by the dependent release-control-plane gate; four-carrier evidence remains in the dedicated Runtime Kit report.",
            ),
            "x3i" to linkedMapOf(
                "milestone" to "X3i",
                "state" to "migrated-from-host",
                "verificationLevel" to "A with prerequisite directed-device D",
                "semanticSourceHostSelector" to
                    "zlibBufferCallbacksStreamsAndShadowingWork",
                "selectors" to nodeAndroidConformanceX3iSelectors,
                "selectorCount" to nodeAndroidConformanceX3iSelectors.size,
                "realPublishedBinderExecutePackaged" to true,
                "workspaceArchiveV2CommitAssertionPackaged" to true,
                "oneStartedOneTerminalAssertionPackaged" to true,
                "pendingZlibCallbacksExpected" to 0,
                "pendingZlibStreamsExpected" to false,
                "pendingZlibStreamCountExpected" to 0,
                "directedDevicePrerequisitePassed" to true,
                "hostRemovalVerified" to true,
                "migratedAuthorityClaimed" to true,
                "deviceExecuted" to true,
                "currentGateDeviceOrSoakExecuted" to false,
                "historicalDirectedDeviceAppFreshForCurrentArtifact" to
                    x3iHistoricalAppFreshForCurrentArtifact,
                "historicalDirectedDeviceAndroidTestFreshForCurrentArtifact" to
                    x3iHistoricalTestApkFreshForCurrentArtifact,
                "historicalDirectedDeviceTestSourceFreshForCurrentArtifact" to
                    x3iHistoricalTestSourceFreshForCurrentArtifact,
                "currentProductionAppIdentityHardGate" to false,
                "currentAppAndroidTestAndSourceFreshnessDescriptiveOnly" to true,
                "historicalDirectedDeviceArtifactIdentities" to linkedMapOf(
                    "appDebugX86_64" to linkedMapOf(
                        "path" to
                            "app/build/outputs/apk/debug/" +
                            "autojs6-plugin-nodejs-runtime-v1.1.3-x86_64.apk",
                        "bytes" to nodeAndroidConformanceX3iAppBytes,
                        "sha256" to nodeAndroidConformanceX3iAppSha256,
                    ),
                    "androidTest" to linkedMapOf(
                        "path" to
                            "app/build/outputs/apk/androidTest/debug/" +
                            "app-debug-androidTest.apk",
                        "bytes" to nodeAndroidConformanceX3iTestApkBytes,
                        "sha256" to nodeAndroidConformanceX3iTestApkSha256,
                    ),
                    "testSource" to linkedMapOf(
                        "path" to testFile.nodeAndroidConformanceRelativePath(),
                        "bytes" to nodeAndroidConformanceX3iTestSourceBytes,
                        "sha256" to nodeAndroidConformanceX3iTestSourceSha256,
                    ),
                ),
                "currentArtifactIdentities" to linkedMapOf(
                    "appDebugX86_64" to linkedMapOf(
                        "path" to x3iCurrentAppApk.nodeAndroidConformanceRelativePath(),
                        "bytes" to x3iCurrentAppApk.length(),
                        "sha256" to x3iCurrentAppSha256,
                    ),
                    "androidTest" to linkedMapOf(
                        "path" to testApk.nodeAndroidConformanceRelativePath(),
                        "bytes" to testApk.length(),
                        "sha256" to x3iCurrentTestApkSha256,
                    ),
                    "testSource" to linkedMapOf(
                        "path" to testFile.nodeAndroidConformanceRelativePath(),
                        "bytes" to testFile.length(),
                        "sha256" to x3iCurrentTestSourceSha256,
                    ),
                ),
                "hostRemovalReceipt" to linkedMapOf(
                    "path" to nodeAndroidConformanceX3iHostRemovalReceiptPath,
                    "bytes" to nodeAndroidConformanceX3iHostRemovalReceiptBytes,
                    "sha256" to nodeAndroidConformanceX3iHostRemovalReceiptSha256,
                ),
                "directedDeviceEvidence" to linkedMapOf(
                    "path" to nodeAndroidConformanceX3iDirectedDeviceReportPath,
                    "bytes" to nodeAndroidConformanceX3iDirectedDeviceReportBytes,
                    "sha256" to nodeAndroidConformanceX3iDirectedDeviceReportSha256,
                    "rawPath" to nodeAndroidConformanceX3iRawDeviceOutputPath,
                    "rawBytes" to nodeAndroidConformanceX3iRawDeviceOutputBytes,
                    "rawSha256" to nodeAndroidConformanceX3iRawDeviceOutputSha256,
                ),
                "runtimeKitChanged" to false,
                "evidenceBoundary" to
                    "X3i remains migrated Plugin authority because its immutable receipt, D JSON/raw, and their historical app/test/source identities are exact. Current app, Android-test APK, and test-source freshness are reported separately and may be false after later staged selectors; none is a replay of the historical D prerequisite. This current A invocation remains package-only and did not rerun D, connected tests, a matrix, or soak.",
            ),
            "x3j" to linkedMapOf(
                "milestone" to "X3j",
                "state" to "migrated-from-host",
                "verificationLevel" to "A with prerequisite directed-device D",
                "semanticSourceHostPath" to
                    "app/src/androidTest/java/org/autojs/autojs/engine/" +
                    "NodeCryptoExpansionInstrumentationTest.kt",
                "semanticSourceHostBytes" to 4485,
                "semanticSourceHostSha256" to
                    "63c28215f332c9a68bc9709638cef3842edd242dd9ad7a2d58c0b5f2d6874033",
                "semanticSourceHostSelector" to "safeCryptoExpansionAndShadowingWork",
                "selectors" to nodeAndroidConformanceX3jSelectors,
                "selectorCount" to nodeAndroidConformanceX3jSelectors.size,
                "realPublishedBinderExecutePackaged" to true,
                "workspaceArchiveV2CommitAssertionPackaged" to true,
                "oneStartedOneTerminalAssertionPackaged" to true,
                "timeoutMs" to 10_000,
                "pendingCryptoCallbacksExpected" to 0,
                "timedOutExpected" to false,
                "moduleSourceProviderUsed" to false,
                "exactGraphProviderUsed" to false,
                "hostRemovalVerified" to true,
                "migratedAuthorityClaimed" to true,
                "deviceExecuted" to true,
                "currentGateDeviceOrSoakExecuted" to false,
                "evidenceValidated" to x3jEvidenceValid,
                "productionInputIdentityHardGatePassed" to x3jProductionInputsUnchanged,
                "productionSourceChanged" to false,
                "nativeSourceChanged" to false,
                "pluginApiChanged" to false,
                "capabilityCatalogChanged" to false,
                "runtimeKitFrozen" to true,
                "runtimeKitChanged" to false,
                "releaseNativeRebuildRequired" to false,
                "productionApkBytesClaimedUnchanged" to false,
                "directedDeviceArtifactEntriesVerified" to true,
                "hostRemovalReceipt" to linkedMapOf(
                    "path" to nodeAndroidConformanceX3jHostRemovalReceiptPath,
                    "bytes" to nodeAndroidConformanceX3jHostRemovalReceiptBytes,
                    "sha256" to nodeAndroidConformanceX3jHostRemovalReceiptSha256,
                ),
                "directedDeviceEvidence" to linkedMapOf(
                    "path" to nodeAndroidConformanceX3jDirectedDeviceReportPath,
                    "bytes" to nodeAndroidConformanceX3jDirectedDeviceReportBytes,
                    "sha256" to nodeAndroidConformanceX3jDirectedDeviceReportSha256,
                    "rawPath" to nodeAndroidConformanceX3jRawDeviceOutputPath,
                    "rawBytes" to nodeAndroidConformanceX3jRawDeviceOutputBytes,
                    "rawSha256" to nodeAndroidConformanceX3jRawDeviceOutputSha256,
                    "device" to "DEX_R1_API34_X64",
                    "serial" to "emulator-5562",
                    "apiLevel" to 34,
                    "abi" to "x86_64",
                    "attemptCount" to 1,
                    "retryCount" to 0,
                    "testsRun" to 1,
                    "testsPassed" to 1,
                    "runnerReportedSeconds" to 0.55,
                    "runnerSummary" to "OK (1 test)",
                    "baselineRestored" to true,
                    "temporaryAvdStartedAndStopped" to true,
                    "serialAbsentAfterShutdown" to true,
                    "appDebugX86_64" to linkedMapOf(
                        "bytes" to nodeAndroidConformanceX3jAppBytes,
                        "sha256" to nodeAndroidConformanceX3jAppSha256,
                    ),
                    "androidTest" to linkedMapOf(
                        "bytes" to nodeAndroidConformanceX3jTestApkBytes,
                        "sha256" to nodeAndroidConformanceX3jTestApkSha256,
                    ),
                    "testSource" to linkedMapOf(
                        "bytes" to nodeAndroidConformanceX3jTestSourceBytes,
                        "sha256" to nodeAndroidConformanceX3jTestSourceSha256,
                    ),
                ),
                "currentPluginOwnership" to linkedMapOf(
                    "policyBytes" to nodeAndroidConformanceX3jCurrentOwnershipPolicyBytes,
                    "policySha256" to nodeAndroidConformanceX3jCurrentOwnershipPolicySha256,
                    "verifierBytes" to nodeAndroidConformanceX3jCurrentOwnershipVerifierBytes,
                    "verifierSha256" to nodeAndroidConformanceX3jCurrentOwnershipVerifierSha256,
                    "reportBytes" to nodeAndroidConformanceX3jCurrentOwnershipReportBytes,
                    "reportSha256" to nodeAndroidConformanceX3jCurrentOwnershipReportSha256,
                    "candidateCount" to 291,
                    "classifiedCandidateCount" to 291,
                    "violationCount" to 0,
                    "selfTestCount" to 17,
                ),
                "productionInputIdentities" to linkedMapOf(
                    "appMainTree" to x3jAppMainIdentity.nodeAndroidConformanceReport(),
                    "pluginApiTree" to x3jPluginApiIdentity.nodeAndroidConformanceReport(),
                    "buildLogicTree" to x3jBuildLogicIdentity.nodeAndroidConformanceReport(),
                    "gradleTree" to x3jGradleIdentity.nodeAndroidConformanceReport(),
                    "runtimeBuildFiles" to x3jRuntimeBuildInputIdentities.mapValues {
                            (_, identity) -> identity.nodeAndroidConformanceReport()
                    },
                    "versionInputAtConfiguration" to
                        nodeAndroidConformanceX3jVersionInputAtConfiguration
                            .nodeAndroidConformanceReport(),
                    "networkExperimentalAtConfiguration" to
                        nodeAndroidConformanceX3jNetworkExperimentalAtConfiguration,
                    "networkExperimentalExpectedAtConfiguration" to "false",
                    "capabilityCatalog" to
                        x3jCapabilityCatalogIdentity.nodeAndroidConformanceReport(),
                ),
                "runtimeKitIdentity" to linkedMapOf(
                    "manifest" to x3jRuntimeKitManifestIdentity.nodeAndroidConformanceReport(),
                    "packagedAssetManifest" to
                        x3jRuntimeKitAssetIdentity.nodeAndroidConformanceReport(),
                    "lock" to x3jRuntimeKitLockIdentity.nodeAndroidConformanceReport(),
                    "releaseGateTask" to ":app:verifyNodePluginRuntimeKitRelease",
                    "releaseGateRequiredByCurrentA" to true,
                ),
                "evidenceBoundary" to
                    "X3j is migrated Plugin authority for the focused crypto selector because the immutable one-attempt API 34 x86_64 D JSON/raw and exact Host removal/compile/ownership receipt are validated directly. The current A invocation only recompiles and packages the 24-selector harness; it did not rerun X3j, connected tests, the full selector set, a matrix, or soak. Production source/native, public Plugin API, capability catalog, build inputs, and frozen Runtime Kit identities remain separately hard-gated; releaseReady remains false/bootstrap_only.",
            ),
            "checks" to checks,
            "passedChecks" to checks.count { it.value },
            "totalChecks" to checks.size,
            "installedRuntimeExecuted" to false,
            "deviceOrSoakExecuted" to false,
            "deviceEvidenceRequiredForPromotion" to false,
            "evidenceBoundary" to
                "This A-level gate proves that the real-service Android harness compiles and packages. " +
                    "It does not run Binder, PFD, native, device, connected, or soak execution. The " +
                    "X3d plaintext CTS fixture is pre-materialized in workspace-v2. The X3f selector " +
                    "packages a harness-owned provider-v2 PFD for an unknown computed CTS dependency, " +
                    "but this A-level report does not execute it or prove the production Host provider. " +
                    "The 15-file node_compat_corpus_v2 selector and seven granular TypeScript Android " +
                    "cases are plugin-owned after their Host sources were removed and the companion " +
                    "Host ownership/AndroidTest compile gates passed. The 27-file v1 corpus and two X3g " +
                    "selectors are plugin-owned after the exact Host removals passed and the separately " +
                    "collected API 34 x86_64 temporary-AVD directed-device prerequisite passed 2/2 " +
                    "with its empty baseline restored and emulator serial absent after shutdown. " +
                    "That D report is an immutable historical prerequisite referenced by this gate; its " +
                    "artifact freshness is reported separately and is not current X3h evidence. This A-level " +
                    "gate did not execute it and remains package-only. The two X3h graph selectors stage zero-map " +
                    "raw TypeScript/extensionless CJS and package metadata/main/index/bare exact-provider " +
                    "graphs, while the third X3h selector stages serialized provider/workspace cleanup; none " +
                    "of them prove Host preload quarantine or Host ownership migration. The changed " +
                    "native bridge was rebuilt and Runtime Kit 1.1.3 is frozen by the dependent release gate; " +
                    "the dedicated Runtime Kit report separately records all four APK carriers. " +
                    "The one X3i selector owns the migrated ZLIB callback, stream, buffer, error, " +
                    "builtin-shadowing, lifecycle, and workspace assertions after an exact one-attempt " +
                    "API 34 x86_64 directed-device pass and exact Host removal. This A-level gate validates " +
                    "those immutable historical prerequisites; current app/test/source freshness is descriptive " +
                    "and this invocation does not rerun D. " +
                    "The one X3j selector now owns the migrated crypto identity, shadowing, UUID, HMAC, " +
                    "timing-safe, asymmetric-denial, limited-WebCrypto, ten-second timeout, lifecycle, " +
                    "workspace, and zero-pending assertions after one exact API 34 x86_64 D pass and the " +
                    "subsequent exact Host removal/compile/ownership gate. The hard gate directly validates " +
                    "that immutable D JSON/raw and removal receipt, while independently freezing production " +
                    "source/native, public API, capability catalog, build inputs, effective default-off network " +
                    "property, and Runtime Kit identities. This current A invocation remains package-only; it " +
                    "does not rerun X3j, the full 24 selectors, connected tests, a matrix, or soak. " +
                    "The retained packaged moduleSources graph " +
                    "selector and remaining Host Android tests/corpora stay separate Host authority/backlog.",
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        println("Node plugin Android conformance harness: ${report["decision"]}")
        println("Report: $reportFile")
    }
}
