#!/usr/bin/env python3
from pathlib import Path
import base64
import gzip
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit('usage: apply_v322_gallery.py <project_dir> <patch_dir>')

project = Path(sys.argv[1])
patch_dir = Path(sys.argv[2])
java_dir = project / 'app/src/main/java/kr/co/vintagecolor/camera'
main = java_dir / 'MainActivity.java'
manifest = project / 'app/src/main/AndroidManifest.xml'
gradle = project / 'app/build.gradle.kts'
changelog = project / 'CHANGELOG_KO.md'
readme = project / 'README_KO.md'

gallery_archive = patch_dir / 'PhotoGalleryActivity.java.gz.b64'
zoom_source = patch_dir / 'ZoomImageView.java'
if not gallery_archive.exists() or not zoom_source.exists():
    raise SystemExit('gallery patch source is incomplete')
try:
    gallery_bytes = gzip.decompress(base64.b64decode(gallery_archive.read_text(encoding='utf-8')))
except Exception as error:
    raise SystemExit(f'gallery source decode failed: {error}')
(java_dir / 'PhotoGalleryActivity.java').write_bytes(gallery_bytes)
shutil.copyfile(zoom_source, java_dir / 'ZoomImageView.java')

text = main.read_text(encoding='utf-8')

def replace_once(old: str, new: str, label: str) -> None:
    global text
    if text.count(old) != 1:
        raise SystemExit(f'{label}: expected one match, found {text.count(old)}')
    text = text.replace(old, new, 1)

replace_once(
    'public class MainActivity extends Activity implements CameraController.Listener {\n'
    '    private static final int REQUEST_CAMERA_PERMISSION = 4100;',
    'public class MainActivity extends Activity implements CameraController.Listener {\n'
    '    public static final String EXTRA_EDIT_PHOTO_URI = "extra_edit_photo_uri";\n'
    '    private static final int REQUEST_CAMERA_PERMISSION = 4100;',
    'edit extra constant')
replace_once(
    '    private Button gridButton;\n    private Button settingsButton;',
    '    private Button gridButton;\n    private Button galleryButton;\n    private Button settingsButton;',
    'gallery field')
replace_once(
    '        requestCameraAndStart();\n    }',
    '        requestCameraAndStart();\n        handleIncomingEditIntent(getIntent());\n    }',
    'incoming intent call')
replace_once('        title.setText("VINTAGE 3.1");',
             '        title.setText("VCC 3.2.2");', 'title version')
replace_once(
    '        flashButton = topButton("플래시 끔");\n'
    '        gridButton = topButton("격자");\n'
    '        settingsButton = topButton("조정");\n'
    '        topBar.addView(flashButton, topButtonParams());\n'
    '        topBar.addView(gridButton, topButtonParams());\n'
    '        topBar.addView(settingsButton, topButtonParams());',
    '        flashButton = topButton("플래시 끔");\n'
    '        gridButton = topButton("격자");\n'
    '        galleryButton = topButton("사진 보기");\n'
    '        settingsButton = topButton("조정");\n'
    '        topBar.addView(flashButton, topButtonParams());\n'
    '        topBar.addView(gridButton, topButtonParams());\n'
    '        topBar.addView(galleryButton, topButtonParams());\n'
    '        topBar.addView(settingsButton, topButtonParams());',
    'top gallery button')
replace_once(
    '        settingsButton.setOnClickListener(new View.OnClickListener() {\n'
    '            @Override public void onClick(View view) { showSettings(true); }\n'
    '        });',
    '        galleryButton.setOnClickListener(new View.OnClickListener() {\n'
    '            @Override public void onClick(View view) { openPhotoGallery(); }\n'
    '        });\n'
    '        settingsButton.setOnClickListener(new View.OnClickListener() {\n'
    '            @Override public void onClick(View view) { showSettings(true); }\n'
    '        });',
    'gallery click')

old_picker = '''    private void openImagePicker() {\n        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        intent.addCategory(Intent.CATEGORY_OPENABLE);\n        intent.setType("image/*");\n        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);\n        startActivityForResult(intent, REQUEST_PICK_IMAGE);\n    }\n\n    @Override\n    protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n        super.onActivityResult(requestCode, resultCode, data);\n        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {\n            final Uri uri = data.getData();\n            try {\n                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);\n            } catch (Exception ignored) { }\n            statusView.setText("사진 불러오는 중...");\n            worker.execute(new Runnable() {\n                @Override public void run() {\n                    try {\n                        final Bitmap bitmap = decodeUri(uri, MAX_IMAGE_SIDE);\n                        mainHandler.post(new Runnable() {\n                            @Override public void run() {\n                                setSourceBitmap(bitmap, new Date());\n                                showReview();\n                            }\n                        });\n                    } catch (final Exception error) {\n                        mainHandler.post(new Runnable() {\n                            @Override public void run() {\n                                Toast.makeText(MainActivity.this,\n                                        "사진을 열지 못했습니다: " + safeMessage(error), Toast.LENGTH_LONG).show();\n                            }\n                        });\n                    }\n                }\n            });\n        }\n    }\n'''
new_picker = '''    private void openImagePicker() {\n        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        intent.addCategory(Intent.CATEGORY_OPENABLE);\n        intent.setType("image/*");\n        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);\n        startActivityForResult(intent, REQUEST_PICK_IMAGE);\n    }\n\n    private void openPhotoGallery() {\n        startActivity(new Intent(this, PhotoGalleryActivity.class));\n    }\n\n    private void handleIncomingEditIntent(Intent intent) {\n        if (intent == null || !intent.getBooleanExtra(EXTRA_EDIT_PHOTO_URI, false)\n                || intent.getData() == null) return;\n        Uri uri = intent.getData();\n        intent.removeExtra(EXTRA_EDIT_PHOTO_URI);\n        loadImageForEditing(uri);\n    }\n\n    @Override protected void onNewIntent(Intent intent) {\n        super.onNewIntent(intent);\n        setIntent(intent);\n        handleIncomingEditIntent(intent);\n    }\n\n    private void loadImageForEditing(final Uri uri) {\n        if (uri == null) return;\n        statusView.setText("사진 불러오는 중...");\n        worker.execute(new Runnable() {\n            @Override public void run() {\n                try {\n                    final Bitmap bitmap = decodeUri(uri, MAX_IMAGE_SIDE);\n                    mainHandler.post(new Runnable() {\n                        @Override public void run() {\n                            if (destroyed) {\n                                recycleBitmap(bitmap);\n                                return;\n                            }\n                            setSourceBitmap(bitmap, new Date());\n                            showReview();\n                        }\n                    });\n                } catch (final Exception error) {\n                    mainHandler.post(new Runnable() {\n                        @Override public void run() {\n                            Toast.makeText(MainActivity.this,\n                                    "사진을 열지 못했습니다: " + safeMessage(error), Toast.LENGTH_LONG).show();\n                        }\n                    });\n                }\n            }\n        });\n    }\n\n    @Override\n    protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n        super.onActivityResult(requestCode, resultCode, data);\n        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK\n                && data != null && data.getData() != null) {\n            Uri uri = data.getData();\n            try {\n                getContentResolver().takePersistableUriPermission(uri,\n                        Intent.FLAG_GRANT_READ_URI_PERMISSION);\n            } catch (Exception ignored) { }\n            loadImageForEditing(uri);\n        }\n    }\n'''
replace_once(old_picker, new_picker, 'photo picker refactor')
replace_once('new LinearLayout.LayoutParams(dp(76), dp(42))',
             'new LinearLayout.LayoutParams(dp(62), dp(42))', 'compact top buttons')
main.write_text(text, encoding='utf-8')

m = manifest.read_text(encoding='utf-8')
activity_anchor = '''        <activity\n            android:name=".MainActivity"'''
if activity_anchor not in m:
    raise SystemExit('manifest activity anchor not found')
m = m.replace(activity_anchor, '''        <activity\n            android:name=".PhotoGalleryActivity"\n            android:exported="false"\n            android:screenOrientation="portrait" />\n        <activity\n            android:name=".MainActivity"''', 1)
manifest.write_text(m, encoding='utf-8')

g = gradle.read_text(encoding='utf-8')
if 'versionCode = 9' not in g or 'versionName = "3.2.1"' not in g:
    raise SystemExit('expected 3.2.1 version not found')
g = g.replace('versionCode = 9', 'versionCode = 10', 1)
g = g.replace('versionName = "3.2.1"', 'versionName = "3.2.2"', 1)
gradle.write_text(g, encoding='utf-8')

entry = '''\n## 3.2.2\n\n- 앱 내부 사진 갤러리 추가\n- Pictures/VintageColorCamera 저장 사진을 3열 썸네일로 표시\n- 전체 화면 사진 보기와 확대·축소·두 번 누르기 지원\n- 좌우 넘기기, 이전·다음 이동 지원\n- 사진 공유·삭제·필터 재편집 기능 추가\n- 카메라 상단에 `사진 보기` 버튼 추가\n'''
changelog.write_text(changelog.read_text(encoding='utf-8').rstrip() + '\n' + entry,
                     encoding='utf-8')
readme.write_text(readme.read_text(encoding='utf-8').rstrip() + '''\n\n## 앱 내부 사진 보기\n\n카메라 화면 상단의 `사진 보기`를 누르면 앱에서 저장한 사진을 썸네일과 전체 화면으로 볼 수 있습니다. 확대·축소, 좌우 이동, 공유, 삭제, 필터 재편집을 지원합니다.\n''', encoding='utf-8')
print('Applied Vintage Color Camera 3.2.2 photo gallery')
