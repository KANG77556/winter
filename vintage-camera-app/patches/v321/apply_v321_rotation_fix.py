#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_v321_rotation_fix.py <project_dir>')

project = Path(sys.argv[1])
java = project / 'app/src/main/java/kr/co/vintagecolor/camera/CameraController.java'
gradle = project / 'app/build.gradle.kts'
manifest = project / 'app/src/main/AndroidManifest.xml'
changelog = project / 'CHANGELOG_KO.md'

text = java.read_text(encoding='utf-8')

# EXIF 읽기 및 바이트 스트림 import 추가
text = text.replace(
    'import android.media.Image;\nimport android.media.ImageReader;\n',
    'import android.media.ExifInterface;\nimport android.media.Image;\nimport android.media.ImageReader;\n'
)
text = text.replace(
    'import java.nio.ByteBuffer;\n',
    'import java.io.ByteArrayInputStream;\nimport java.nio.ByteBuffer;\n'
)

old_decode = '''                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);\n                    if (bitmap == null) throw new IllegalStateException("JPEG 해석 실패");\n                    final Bitmap result = limitBitmap(bitmap, 4200);\n                    final Date date = new Date();'''
new_decode = '''                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);\n                    if (bitmap == null) throw new IllegalStateException("JPEG 해석 실패");\n\n                    // Camera2는 회전값을 JPEG EXIF에만 기록하는 기기가 많습니다.\n                    // 필터 처리 전에 픽셀을 실제 정방향으로 회전해 저장 결과가 눕지 않게 합니다.\n                    Bitmap normalized = normalizeCapturedBitmap(data, bitmap, calculateJpegOrientation());\n                    if (normalized != bitmap && !bitmap.isRecycled()) bitmap.recycle();\n                    final Bitmap result = limitBitmap(normalized, 4200);\n                    final Date date = new Date();'''
if old_decode not in text:
    raise SystemExit('decode block not found')
text = text.replace(old_decode, new_decode)

anchor = '''    private int calculateJpegOrientation() {\n        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();\n        int displayDegrees;\n        if (rotation == Surface.ROTATION_90) displayDegrees = 90;\n        else if (rotation == Surface.ROTATION_180) displayDegrees = 180;\n        else if (rotation == Surface.ROTATION_270) displayDegrees = 270;\n        else displayDegrees = 0;\n        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {\n            return (sensorOrientation + displayDegrees + 360) % 360;\n        }\n        return (sensorOrientation - displayDegrees + 360) % 360;\n    }\n'''
addition = anchor + '''\n    /**\n     * JPEG EXIF 방향을 실제 비트맵 픽셀에 반영합니다.\n     * 일부 제조사 카메라는 픽셀은 가로 상태로 두고 EXIF에만 90도 회전을 기록하므로\n     * BitmapFactory로 바로 읽으면 필터 저장 결과가 옆으로 눕는 문제가 발생합니다.\n     */\n    private Bitmap normalizeCapturedBitmap(byte[] jpegData, Bitmap bitmap, int fallbackDegrees) {\n        int orientation = ExifInterface.ORIENTATION_UNDEFINED;\n        try {\n            ByteArrayInputStream input = new ByteArrayInputStream(jpegData);\n            ExifInterface exif = new ExifInterface(input);\n            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,\n                    ExifInterface.ORIENTATION_UNDEFINED);\n            input.close();\n        } catch (Exception ignored) { }\n\n        Matrix matrix = new Matrix();\n        boolean changed = false;\n\n        switch (orientation) {\n            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:\n                matrix.postScale(-1f, 1f);\n                changed = true;\n                break;\n            case ExifInterface.ORIENTATION_ROTATE_180:\n                matrix.postRotate(180f);\n                changed = true;\n                break;\n            case ExifInterface.ORIENTATION_FLIP_VERTICAL:\n                matrix.postScale(1f, -1f);\n                changed = true;\n                break;\n            case ExifInterface.ORIENTATION_TRANSPOSE:\n                matrix.postRotate(90f);\n                matrix.postScale(-1f, 1f);\n                changed = true;\n                break;\n            case ExifInterface.ORIENTATION_ROTATE_90:\n                // 이미 세로 픽셀로 저장된 특이 기기는 중복 회전하지 않습니다.\n                if (bitmap.getWidth() > bitmap.getHeight()) {\n                    matrix.postRotate(90f);\n                    changed = true;\n                }\n                break;\n            case ExifInterface.ORIENTATION_TRANSVERSE:\n                matrix.postRotate(270f);\n                matrix.postScale(-1f, 1f);\n                changed = true;\n                break;\n            case ExifInterface.ORIENTATION_ROTATE_270:\n                if (bitmap.getWidth() > bitmap.getHeight()) {\n                    matrix.postRotate(270f);\n                    changed = true;\n                }\n                break;\n            case ExifInterface.ORIENTATION_NORMAL:\n            case ExifInterface.ORIENTATION_UNDEFINED:\n            default:\n                // EXIF가 누락된 기기는 촬영 요청 방향과 가로·세로 비율로 안전하게 보정합니다.\n                int degrees = ((fallbackDegrees % 360) + 360) % 360;\n                if ((degrees == 90 || degrees == 270) && bitmap.getWidth() > bitmap.getHeight()) {\n                    matrix.postRotate(degrees);\n                    changed = true;\n                } else if (degrees == 180) {\n                    matrix.postRotate(180f);\n                    changed = true;\n                }\n                break;\n        }\n\n        if (!changed) return bitmap;\n        try {\n            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),\n                    matrix, true);\n        } catch (OutOfMemoryError error) {\n            // 고해상도 회전에 실패하면 촬영 자체가 사라지지 않도록 원본을 반환합니다.\n            return bitmap;\n        }\n    }\n'''
if anchor not in text:
    raise SystemExit('orientation anchor not found')
text = text.replace(anchor, addition)
java.write_text(text, encoding='utf-8')

# 기존 설치판과 충돌하지 않도록 별도 패키지로 생성하고, 이후 이 패키지를 안정 업데이트 계열로 사용합니다.
g = gradle.read_text(encoding='utf-8')
g = g.replace('applicationId = "kr.co.vintagecolor.camera.s24final"',
              'applicationId = "kr.co.vintagecolor.camera.s24stable"')
g = g.replace('versionCode = 8', 'versionCode = 9')
g = g.replace('versionName = "3.2.0"', 'versionName = "3.2.1"')
gradle.write_text(g, encoding='utf-8')

m = manifest.read_text(encoding='utf-8')
m = m.replace('android:label="빈티지 컬러 카메라 S24"',
              'android:label="빈티지 컬러 카메라 S24 안정판"')
manifest.write_text(m, encoding='utf-8')

entry = '''\n## 3.2.1\n\n- Camera2 JPEG EXIF 방향을 필터 처리 전에 실제 픽셀에 적용\n- 세로 촬영 사진이 옆으로 회전되어 저장되는 오류 수정\n- EXIF가 누락된 기기를 위한 센서 방향 기반 보정 추가\n- 고해상도 회전 중 메모리 부족 시 원본 보존 처리\n- 안정 업데이트용 별도 패키지 `kr.co.vintagecolor.camera.s24stable` 적용\n'''
old = changelog.read_text(encoding='utf-8') if changelog.exists() else '# 변경 내역\n'
changelog.write_text(old.rstrip() + '\n' + entry, encoding='utf-8')

print('Applied Vintage Color Camera 3.2.1 rotation fix')
