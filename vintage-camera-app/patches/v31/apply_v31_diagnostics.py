from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
patch_dir = Path(sys.argv[2]).resolve()
main = root / "app/src/main/java/kr/co/vintagecolor/camera/MainActivity.java"
build = root / "app/build.gradle.kts"
manifest = root / "app/src/main/AndroidManifest.xml"
target_diag = main.parent / "CameraDiagnostics.java"

text = main.read_text(encoding="utf-8")
text = text.replace("빈티지 컬러 카메라 3.0의 메인 화면입니다.", "빈티지 컬러 카메라 3.1 진단판의 메인 화면입니다.")
text = text.replace("private Button settingsButton;\n", "private Button settingsButton;\n    private Button diagnosticButton;\n")
text = text.replace('title.setText("VINTAGE 3.0");', 'title.setText("VINTAGE 3.1");')

needle = '''        content.addView(heading);\n\n        content.addView(settingLabel("색감 프리셋"));'''
replacement = '''        content.addView(heading);\n\n        diagnosticButton = smallDarkButton("S24 카메라 진단 보고서");\n        LinearLayout.LayoutParams diagnosticParams = new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));\n        diagnosticParams.setMargins(0, dp(4), 0, dp(12));\n        content.addView(diagnosticButton, diagnosticParams);\n\n        content.addView(settingLabel("색감 프리셋"));'''
if needle not in text:
    raise SystemExit("settings insertion point not found")
text = text.replace(needle, replacement)

needle = '''        settingsButton.setOnClickListener(new View.OnClickListener() {\n            @Override public void onClick(View view) { showSettings(true); }\n        });'''
replacement = needle + '''\n        diagnosticButton.setOnClickListener(new View.OnClickListener() {\n            @Override public void onClick(View view) { CameraDiagnostics.show(MainActivity.this); }\n        });'''
if needle not in text:
    raise SystemExit("event insertion point not found")
text = text.replace(needle, replacement)
main.write_text(text, encoding="utf-8")

build_text = build.read_text(encoding="utf-8")
build_text = build_text.replace('applicationId = "kr.co.vintagecolor.camera"', 'applicationId = "kr.co.vintagecolor.camera.s24diag"')
build_text = build_text.replace('versionCode = 5', 'versionCode = 7')
build_text = build_text.replace('versionName = "3.0.0"', 'versionName = "3.1.0"')
build.write_text(build_text, encoding="utf-8")

manifest_text = manifest.read_text(encoding="utf-8")
manifest_text = manifest_text.replace('android:label="빈티지 컬러 카메라"', 'android:label="빈티지 카메라 3.1 진단"')
manifest.write_text(manifest_text, encoding="utf-8")

shutil.copy2(patch_dir / "CameraDiagnostics.java", target_diag)
(root / "S24_DIAGNOSTICS_KO.md").write_text(
    "# S24 카메라 진단판 3.1\n\n"
    "앱의 `조정` 메뉴에서 `S24 카메라 진단 보고서`를 누르면 카메라 ID, 초점거리, 줌 범위, 물리 카메라 ID와 지원 해상도를 확인할 수 있습니다.\n\n"
    "진단 보고서는 사진이나 개인정보를 포함하지 않으며, 사용자가 공유를 눌렀을 때만 텍스트로 전달됩니다.\n",
    encoding="utf-8",
)
