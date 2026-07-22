package kr.co.vintagecolor.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 실기기 카메라 정보를 수집해 렌즈 매핑과 호환성 분석에 사용하는 진단 도구입니다.
 * 사진이나 개인정보는 수집하지 않으며, 사용자가 공유 버튼을 누를 때만 텍스트로 내보냅니다.
 */
public final class CameraDiagnostics {
    private CameraDiagnostics() { }

    public static void show(final Activity activity) {
        final String report = collect(activity);
        new AlertDialog.Builder(activity)
                .setTitle("카메라 진단 보고서")
                .setMessage(report)
                .setPositiveButton("공유", (dialog, which) -> share(activity, report))
                .setNeutralButton("복사", (dialog, which) -> copy(activity, report))
                .setNegativeButton("닫기", null)
                .show();
    }

    public static String collect(Context context) {
        StringBuilder out = new StringBuilder(4096);
        out.append("Vintage Color Camera 3.1 S24 Diagnostics\n");
        out.append("manufacturer=").append(Build.MANUFACTURER).append('\n');
        out.append("model=").append(Build.MODEL).append('\n');
        out.append("device=").append(Build.DEVICE).append('\n');
        out.append("sdk=").append(Build.VERSION.SDK_INT).append('\n');
        out.append("release=").append(Build.VERSION.RELEASE).append("\n\n");

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return out.append("CameraManager unavailable\n").toString();

        try {
            String[] ids = manager.getCameraIdList();
            out.append("camera_count=").append(ids.length).append("\n\n");
            for (String id : ids) appendCamera(out, manager, id);
        } catch (CameraAccessException error) {
            out.append("camera_error=").append(error.getReason()).append(':')
                    .append(error.getMessage()).append('\n');
        } catch (SecurityException error) {
            out.append("permission_error=").append(error.getMessage()).append('\n');
        } catch (Throwable error) {
            out.append("unexpected_error=").append(error.getClass().getSimpleName())
                    .append(':').append(error.getMessage()).append('\n');
        }
        return out.toString();
    }

    private static void appendCamera(StringBuilder out, CameraManager manager, String id)
            throws CameraAccessException {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        out.append("[camera ").append(id).append("]\n");
        out.append("facing=").append(facing(c.get(CameraCharacteristics.LENS_FACING))).append('\n');
        out.append("hardware=").append(hardware(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))).append('\n');
        out.append("orientation=").append(value(c.get(CameraCharacteristics.SENSOR_ORIENTATION))).append('\n');
        out.append("flash=").append(value(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))).append('\n');
        out.append("focal_lengths_mm=").append(array(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))).append('\n');
        out.append("apertures=").append(array(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES))).append('\n');
        out.append("min_focus_diopters=").append(value(c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE))).append('\n');
        out.append("max_digital_zoom=").append(value(c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))).append('\n');

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> zoomRange = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            out.append("zoom_ratio_range=").append(zoomRange == null ? "null" :
                    String.format(Locale.US, "%.3f..%.3f", zoomRange.getLower(), zoomRange.getUpper())).append('\n');
        }

        Rect active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Size pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        out.append("active_array=").append(active == null ? "null" : active.width() + "x" + active.height()).append('\n');
        out.append("pixel_array=").append(pixels == null ? "null" : pixels.getWidth() + "x" + pixels.getHeight()).append('\n');

        Range<Integer> ae = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        Rational aeStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        out.append("ae_compensation=").append(ae == null ? "null" : ae.toString())
                .append(" step=").append(aeStep == null ? "null" : aeStep.toString()).append('\n');

        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        out.append("capabilities=").append(caps == null ? "null" : Arrays.toString(caps)).append('\n');
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Set<String> physicalIds = c.getPhysicalCameraIds();
            out.append("physical_ids=").append(physicalIds == null ? "null" : physicalIds.toString()).append('\n');
        }

        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            out.append("jpeg_sizes=").append(topSizes(map.getOutputSizes(android.graphics.ImageFormat.JPEG), 8)).append('\n');
            out.append("preview_sizes=").append(topSizes(map.getOutputSizes(android.graphics.SurfaceTexture.class), 8)).append('\n');
        }
        out.append('\n');
    }

    private static String facing(Integer value) {
        if (value == null) return "unknown";
        if (value == CameraCharacteristics.LENS_FACING_BACK) return "back";
        if (value == CameraCharacteristics.LENS_FACING_FRONT) return "front";
        if (value == CameraCharacteristics.LENS_FACING_EXTERNAL) return "external";
        return String.valueOf(value);
    }

    private static String hardware(Integer value) {
        if (value == null) return "unknown";
        switch (value) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "legacy";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "limited";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "full";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "level_3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "external";
            default: return String.valueOf(value);
        }
    }

    private static String array(float[] values) {
        return values == null ? "null" : Arrays.toString(values);
    }

    private static String value(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String topSizes(Size[] values, int limit) {
        if (values == null || values.length == 0) return "[]";
        Size[] sorted = values.clone();
        Arrays.sort(sorted, (a, b) -> Long.compare(
                (long) b.getWidth() * b.getHeight(), (long) a.getWidth() * a.getHeight()));
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < Math.min(limit, sorted.length); i++) {
            if (i > 0) out.append(", ");
            out.append(sorted[i].getWidth()).append('x').append(sorted[i].getHeight());
        }
        if (sorted.length > limit) out.append(", ...");
        return out.append(']').toString();
    }

    private static void copy(Context context, String report) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Vintage camera diagnostics", report));
            Toast.makeText(context, "진단 보고서를 복사했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void share(Context context, String report) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Vintage Camera S24 진단 보고서");
        send.putExtra(Intent.EXTRA_TEXT, report);
        context.startActivity(Intent.createChooser(send, "진단 보고서 공유"));
    }
}
