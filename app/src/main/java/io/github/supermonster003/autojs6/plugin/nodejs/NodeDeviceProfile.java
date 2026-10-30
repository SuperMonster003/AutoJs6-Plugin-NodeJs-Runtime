package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.view.Display;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The synchronous part of Rhino's device and sensors modules in the device-info runtime module (api-coverage P3.1c, Q147 (a) /
 * Q148 (a)): the whole display's short and long side (Rhino's device.width / height, WIDTH / HEIGHT), the Build fields Rhino's
 * Device class exposes (buildId is Build.DISPLAY there), the native page size, the market name, the manufacturers / brands /
 * roms checks and the default sensor of every name Rhino's sensors.register accepts. The checks follow the host's
 * org.autojs.autojs.runtime.api.Device, util.RomUtils and ezy.assist.compat.RomUtil; this process reads the same system
 * properties as AutoJs6, both being ordinary apps.
 *
 * zh-CN: device-info 运行时模块中 Rhino device 与 sensors 模块的同步部分 (api-coverage P3.1c, Q147 (a) / Q148 (a)): 整屏的短边与长边
 * (Rhino 的 device.width / height, WIDTH / HEIGHT), Rhino Device 类给出的 Build 字段 (其 buildId 为 Build.DISPLAY), 原生页大小, 市场名称,
 * manufacturers / brands / roms 判断, 以及 Rhino sensors.register 接受的每个名称的默认传感器. 判断规则同宿主的 Device, util.RomUtils 与
 * ezy.assist.compat.RomUtil; 本进程与 AutoJs6 同为普通应用, 读到的系统属性相同.
 */
final class NodeDeviceProfile {

    private NodeDeviceProfile() {
    }

    /** Rhino's sensors table (org.autojs.autojs.runtime.api.Sensors); other names resolve to a Sensor.TYPE_ constant. */
    @SuppressWarnings("deprecation") // TYPE_ORIENTATION is in Rhino's table.
    private static Map<String, Integer> rhinoSensorTypes() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("accelerometer", Sensor.TYPE_ACCELEROMETER);
        types.put("ambient_temperature", Sensor.TYPE_AMBIENT_TEMPERATURE);
        types.put("gravity", Sensor.TYPE_GRAVITY);
        types.put("gyroscope", Sensor.TYPE_GYROSCOPE);
        types.put("light", Sensor.TYPE_LIGHT);
        types.put("linear_acceleration", Sensor.TYPE_LINEAR_ACCELERATION);
        types.put("magnetic_field", Sensor.TYPE_MAGNETIC_FIELD);
        types.put("orientation", Sensor.TYPE_ORIENTATION);
        types.put("pressure", Sensor.TYPE_PRESSURE);
        types.put("proximity", Sensor.TYPE_PROXIMITY);
        types.put("relative_humidity", Sensor.TYPE_RELATIVE_HUMIDITY);
        types.put("temperature", Sensor.TYPE_AMBIENT_TEMPERATURE);
        for (Field field : Sensor.class.getFields()) {
            String name = field.getName();
            if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers()) || !name.startsWith("TYPE_") || name.equals("TYPE_ALL")) {
                continue;
            }
            String key = name.substring("TYPE_".length()).toLowerCase(Locale.ROOT);
            if (types.containsKey(key)) {
                continue;
            }
            try {
                types.put(key, field.getInt(null));
            } catch (IllegalAccessException ignored) {
                /* Not a readable constant. */
            }
        }
        return types;
    }

    /** Adds the fields to the device-info object of NodeRuntimeModuleInjector. */
    static void put(Context context, JSONObject info, DisplayMetrics window) throws JSONException {
        DisplayMetrics real = new DisplayMetrics();
        try {
            DisplayManager manager = context.getSystemService(DisplayManager.class);
            Display display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                display.getRealMetrics(real);
            }
        } catch (RuntimeException ignored) {
            /* The window metrics below. */
        }
        DisplayMetrics source = real.widthPixels > 0 && real.heightPixels > 0 ? real : window;
        info.put("width", source == null ? 0 : Math.max(0, Math.min(source.widthPixels, source.heightPixels)))
                .put("height", source == null ? 0 : Math.max(0, Math.max(source.widthPixels, source.heightPixels)));
        info.put("buildDisplay", Build.DISPLAY)
                .put("manufacturer", Build.MANUFACTURER)
                .put("device", Build.DEVICE)
                .put("incremental", Build.VERSION.INCREMENTAL)
                .put("baseOS", Build.VERSION.BASE_OS)
                .put("securityPatch", Build.VERSION.SECURITY_PATCH)
                .put("codename", Build.VERSION.CODENAME)
                .put("pageSize", Os.sysconf(OsConstants._SC_PAGESIZE))
                .put("marketName", marketName())
                .put("manufacturers", manufacturers())
                .put("brands", brands())
                .put("roms", roms())
                .put("sensors", sensors(context));
    }

    /** Rhino's Device.getMarketName. */
    private static String marketName() {
        for (String key : new String[]{"ro.product.marketname", "ro.semc.product.name", "ro.config.marketing_name", "ro.product.model"}) {
            String value = property(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return Build.MODEL;
    }

    private static boolean isXperia() {
        String value = property("ro.semc.product.name");
        return value != null && value.toLowerCase(Locale.ROOT).contains("xperia");
    }

    private static boolean isRedmi() {
        String value = property("ro.product.model");
        return value != null && value.toLowerCase(Locale.ROOT).contains("redmi");
    }

    private static boolean isMiMix() {
        return Build.MODEL.toLowerCase(Locale.ROOT).contains("mi mix");
    }

    /** Rhino's RomUtils.Brand. */
    private static JSONObject brands() throws JSONException {
        return new JSONObject().put("isXperia", isXperia()).put("isRedmi", isRedmi()).put("isMiMix", isMiMix());
    }

    private static boolean manufacturer(String name) {
        return name.trim().equalsIgnoreCase(Build.MANUFACTURER);
    }

    /** Rhino's Device.Manufacturers. */
    private static JSONObject manufacturers() throws JSONException {
        boolean nova = manufacturer("nova"), honor = manufacturer("honor");
        return new JSONObject()
                .put("isHtc", manufacturer("htc"))
                .put("isLG", manufacturer("lg"))
                .put("isOnePlus", manufacturer("oneplus"))
                .put("isSamsung", manufacturer("samsung"))
                .put("isZte", manufacturer("zte"))
                .put("isLetv", manufacturer("letv"))
                .put("isHuawei", manufacturer("huawei") || nova || honor)
                .put("isNova", nova)
                .put("isHonor", honor)
                .put("isXiaomi", manufacturer("xiaomi") || isRedmi() || isMiMix())
                .put("isOppo", manufacturer("oppo"))
                .put("isSony", manufacturer("sony") || isXperia())
                .put("isVivo", manufacturer("vivo"))
                .put("isLenovo", manufacturer("lenovo"))
                .put("isCoolpad", manufacturer("yulong"))
                .put("isSmartisan", manufacturer("smartisan"))
                .put("isMeizu", manufacturer("meizu"));
    }

    private static boolean hasProperty(String key) {
        String value = property(key);
        return value != null && !value.isEmpty();
    }

    /** Rhino's RomUtils (isQiku: ezy.assist.compat.RomUtil, the manufacturer when no known ROM property or Flyme display is found). */
    private static JSONObject roms() throws JSONException {
        boolean miui = hasProperty("ro.miui.ui.version.name"), emui = hasProperty("ro.build.version.emui");
        boolean oppo = hasProperty("ro.build.version.opporom"), smartisan = hasProperty("ro.smartisan.version");
        boolean vivo = hasProperty("ro.vivo.os.version"), flyme = Build.DISPLAY.toLowerCase(Locale.ROOT).contains("flyme");
        String manufacturer = Build.MANUFACTURER.toUpperCase(Locale.ROOT);
        boolean qiku = !(miui || emui || oppo || vivo || smartisan || flyme) && (manufacturer.equals("QIKU") || manufacturer.equals("360"));
        return new JSONObject()
                .put("isMiui", miui)
                .put("isEmui", emui)
                .put("isOppo", oppo)
                .put("isSmartisan", smartisan)
                .put("isVivo", vivo)
                .put("isGionee", hasProperty("ro.gn.sv.version"))
                .put("isLenovo", hasProperty("ro.lenovo.lvp.version"))
                .put("isFlyme", flyme)
                .put("isQiku", qiku);
    }

    /** The default sensor of each name, as the bean properties of Rhino's android.hardware.Sensor. */
    private static JSONObject sensors(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        SensorManager manager = context.getSystemService(SensorManager.class);
        if (manager == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : rhinoSensorTypes().entrySet()) {
            Sensor sensor;
            try {
                sensor = manager.getDefaultSensor(entry.getValue());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (sensor == null) {
                continue;
            }
            JSONObject item = new JSONObject()
                    .put("name", sensor.getName())
                    .put("vendor", sensor.getVendor())
                    .put("version", sensor.getVersion())
                    .put("type", sensor.getType())
                    .put("stringType", sensor.getStringType())
                    .put("maximumRange", (double) sensor.getMaximumRange())
                    .put("resolution", (double) sensor.getResolution())
                    .put("power", (double) sensor.getPower())
                    .put("minDelay", sensor.getMinDelay())
                    .put("maxDelay", sensor.getMaxDelay())
                    .put("reportingMode", sensor.getReportingMode())
                    .put("wakeUpSensor", sensor.isWakeUpSensor())
                    .put("fifoMaxEventCount", sensor.getFifoMaxEventCount())
                    .put("fifoReservedEventCount", sensor.getFifoReservedEventCount());
            result.put(entry.getKey(), item);
        }
        return result;
    }

    /** android.os.SystemProperties.get, else the getprop command (as the host's SystemProperties). */
    private static String property(String key) {
        try {
            Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            Object value = get.invoke(null, key);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            /* The command below. */
        }
        try {
            Process process = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            } finally {
                process.destroy();
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
