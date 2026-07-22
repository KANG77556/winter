#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit('usage: apply_v350_best_shot.py <project_dir> <patch_dir>')

project = Path(sys.argv[1])
patch_dir = Path(sys.argv[2])
java_dir = project / 'app/src/main/java/kr/co/vintagecolor/camera'
main = java_dir / 'MainActivity.java'
gradle = project / 'app/build.gradle.kts'
changelog = project / 'CHANGELOG_KO.md'
readme = project / 'README_KO.md'

source = patch_dir / 'PhotoQualityEngine.java'
if not source.exists():
    raise SystemExit(f'missing patch file: {source}')
shutil.copyfile(source, java_dir / 'PhotoQualityEngine.java')

text = main.read_text(encoding='utf-8')

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    text = text.replace(old, new, 1)

replace_once(
    '    private Button masterStyleButton;\n    private TextView masterStyleStatusView;\n',
    '    private Button masterStyleButton;\n    private TextView masterStyleStatusView;\n'
    '    private Button qualityButton;\n    private TextView qualityStatusView;\n',
    'quality fields')

replace_once(
    '    private float cameraMaxZoom = 1f;\n',
    '    private float cameraMaxZoom = 1f;\n'
    '    private PhotoQualityEngine.Result photoQualityResult;\n'
    '    private int sessionBestQualityScore = 0;\n',
    'quality state')

replace_once('        title.setText("VCC 3.4.0");',
             '        title.setText("VCC 3.5.0");', 'title version')

replace_once(
    '                ViewGroup.LayoutParams.MATCH_PARENT, dp(210), Gravity.BOTTOM);',
    '                ViewGroup.LayoutParams.MATCH_PARENT, dp(270), Gravity.BOTTOM);',
    'review shade height')
replace_once(
    '                ViewGroup.LayoutParams.MATCH_PARENT, dp(176), Gravity.BOTTOM);',
    '                ViewGroup.LayoutParams.MATCH_PARENT, dp(226), Gravity.BOTTOM);',
    'review controls height')

replace_once(
    '        LinearLayout reviewFilm = new LinearLayout(this);\n'
    '        reviewFilm.setGravity(Gravity.CENTER_VERTICAL);\n',
    '        LinearLayout qualityRow = new LinearLayout(this);\n'
    '        qualityRow.setGravity(Gravity.CENTER_VERTICAL);\n'
    '        qualityStatusView = new TextView(this);\n'
    '        qualityStatusView.setText("촬영 결과 진단 준비");\n'
    '        qualityStatusView.setTextColor(Color.WHITE);\n'
    '        qualityStatusView.setTextSize(12f);\n'
    '        qualityStatusView.setMaxLines(2);\n'
    '        qualityRow.addView(qualityStatusView, new LinearLayout.LayoutParams(0, dp(46), 1f));\n'
    '        qualityButton = smallDarkButton("사진 진단");\n'
    '        qualityRow.addView(qualityButton, new LinearLayout.LayoutParams(dp(104), dp(42)));\n'
    '        reviewControls.addView(qualityRow, new LinearLayout.LayoutParams(\n'
    '                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));\n\n'
    '        LinearLayout reviewFilm = new LinearLayout(this);\n'
    '        reviewFilm.setGravity(Gravity.CENTER_VERTICAL);\n',
    'quality review row')

replace_once(
    '        masterStyleButton.setOnClickListener(new View.OnClickListener() {\n'
    '            @Override public void onClick(View view) { showMasterStyleSuggestions(); }\n'
    '        });\n',
    '        masterStyleButton.setOnClickListener(new View.OnClickListener() {\n'
    '            @Override public void onClick(View view) { showMasterStyleSuggestions(); }\n'
    '        });\n'
    '        qualityButton.setOnClickListener(new View.OnClickListener() {\n'
    '            @Override public void onClick(View view) { showPhotoQualityDetails(); }\n'
    '        });\n',
    'quality click listener')

replace_once(
    '        if (masterStyleStatusView != null) {\n'
    '            masterStyleStatusView.setText(MasterStyleEngine.summary(limited));\n'
    '        }\n',
    '        if (masterStyleStatusView != null) {\n'
    '            masterStyleStatusView.setText(MasterStyleEngine.summary(limited));\n'
    '        }\n'
    '        photoQualityResult = PhotoQualityEngine.analyze(limited);\n'
    '        if (qualityStatusView != null) {\n'
    '            int previousBest = sessionBestQualityScore;\n'
    '            if (photoQualityResult.totalScore > sessionBestQualityScore) {\n'
    '                sessionBestQualityScore = photoQualityResult.totalScore;\n'
    '            }\n'
    '            String suffix;\n'
    '            if (photoQualityResult.totalScore >= previousBest && photoQualityResult.totalScore > 0) {\n'
    '                suffix = " · 이번 촬영 최고";\n'
    '            } else {\n'
    '                suffix = " · 최고 " + sessionBestQualityScore + "점";\n'
    '            }\n'
    '            qualityStatusView.setText(photoQualityResult.summary() + suffix);\n'
    '        }\n',
    'quality analysis update')

replace_once(
    '    /** 사진을 기기 안에서 분석해 어울리는 마스터 룩 세 가지를 제안합니다. */\n'
    '    private void showMasterStyleSuggestions() {\n',
    '    /** 촬영 결과의 노출, 선명도와 계조를 점수와 문장으로 설명합니다. */\n'
    '    private void showPhotoQualityDetails() {\n'
    '        if (sourceBitmap == null || sourceBitmap.isRecycled()) {\n'
    '            Toast.makeText(this, "먼저 사진을 촬영하거나 불러오세요.", Toast.LENGTH_SHORT).show();\n'
    '            return;\n'
    '        }\n'
    '        photoQualityResult = PhotoQualityEngine.analyze(sourceBitmap);\n'
    '        AlertDialog dialog = new AlertDialog.Builder(this)\n'
    '                .setTitle("촬영 결과 진단 · " + photoQualityResult.totalScore + "점")\n'
    '                .setMessage(photoQualityResult.detailText())\n'
    '                .setPositiveButton("마스터 룩 추천", (value, which) -> showMasterStyleSuggestions())\n'
    '                .setNeutralButton("다시 촬영", (value, which) -> closeReview())\n'
    '                .setNegativeButton("닫기", null)\n'
    '                .create();\n'
    '        dialog.show();\n'
    '    }\n\n'
    '    /** 사진을 기기 안에서 분석해 어울리는 마스터 룩 세 가지를 제안합니다. */\n'
    '    private void showMasterStyleSuggestions() {\n',
    'quality detail method')

main.write_text(text, encoding='utf-8')

g = gradle.read_text(encoding='utf-8')
if 'versionCode = 12' not in g or 'versionName = "3.4.0"' not in g:
    raise SystemExit('expected 3.4.0 version not found')
g = g.replace('versionCode = 12', 'versionCode = 13', 1)
g = g.replace('versionName = "3.4.0"', 'versionName = "3.5.0"', 1)
gradle.write_text(g, encoding='utf-8')

entry = '''\n## 3.5.0\n\n- 촬영 결과 기술 진단 기능 추가\n- 노출, 선명도, 명암, 계조 범위, 색 균형 점수 제공\n- 주요 개선 방법을 최대 4개 문장으로 안내\n- 현재 실행 중 가장 높은 점수의 사진을 베스트샷으로 표시\n- 진단 화면에서 다시 촬영 또는 마스터 룩 추천으로 바로 이동\n- 예술성이나 인물 외모가 아닌 촬영 실패 가능성만 기기 내부 분석\n'''
changelog.write_text(changelog.read_text(encoding='utf-8').rstrip() + '\n' + entry,
                     encoding='utf-8')
readme.write_text(readme.read_text(encoding='utf-8').rstrip() + '''\n\n## 촬영 결과 진단\n\n사진 검토 화면의 `사진 진단` 버튼을 누르면 노출, 선명도, 명암, 계조 범위와 색 균형을 점수로 확인할 수 있습니다. 점수는 사진의 예술성이나 인물을 평가하지 않고 흔들림, 과다 노출과 같은 기술적 실패 가능성만 안내합니다. 앱을 실행한 동안 가장 높은 점수의 사진은 `이번 촬영 최고`로 표시됩니다.\n''',
                  encoding='utf-8')

print('Applied Vintage Color Camera 3.5 best-shot analysis')
