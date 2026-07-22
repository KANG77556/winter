#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit('usage: apply_v370_portrait_best.py <project_dir> <patch_dir>')

project = Path(sys.argv[1])
patch_dir = Path(sys.argv[2])
java_dir = project / 'app/src/main/java/kr/co/vintagecolor/camera'
main_file = java_dir / 'MainActivity.java'
gradle_file = project / 'app/build.gradle.kts'
changelog_file = project / 'CHANGELOG_KO.md'
readme_file = project / 'README_KO.md'

for name in ('PortraitBestShotEngine.java', 'BurstShotManager.java', 'BurstReviewDialog.java'):
    source = patch_dir / name
    if not source.exists():
        raise SystemExit(f'missing patch file: {source}')
    shutil.copyfile(source, java_dir / name)

text = main_file.read_text(encoding='utf-8')

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    text = text.replace(old, new, 1)

replace_once('title.setText("VCC 3.6.0");',
             'title.setText("VCC 3.7.0");', 'title version')

old_finish = '''        new BurstReviewDialog(this, burstShotManager.getShots(), burstShotManager.getBestShot(),
                new BurstReviewDialog.Listener() {
                    @Override public void onUseShot(final BurstShotManager.Shot shot) {
                        loadSelectedBurstShot(shot);
                    }
                    @Override public void onRetake() {
                        clearBurstSession();
                        statusView.setText("연속 촬영을 다시 시작할 수 있습니다.");
                    }
                    @Override public void onCancel() {
                        clearBurstSession();
                        statusView.setText("연속 촬영을 취소했습니다.");
                    }
                }).show();'''
new_finish = '''        int sceneMode = coachModeSpinner == null ? 0 : coachModeSpinner.getSelectedItemPosition();
        boolean portraitRequested = sceneMode == 1 || sceneMode == 2;
        boolean automaticPortrait = sceneMode == 0;
        boolean familyPriority = sceneMode == 2;
        boolean portraitScoring = burstShotManager.shouldUsePortraitScoring(
                portraitRequested, automaticPortrait);
        BurstShotManager.Shot bestShot = burstShotManager.getBestShot(
                portraitScoring, familyPriority);
        statusView.setText(portraitScoring
                ? "인물 베스트샷 분석 완료" : "연속 촬영 분석 완료");
        new BurstReviewDialog(this, burstShotManager.getShots(), bestShot,
                portraitScoring, familyPriority,
                new BurstReviewDialog.Listener() {
                    @Override public void onUseShot(final BurstShotManager.Shot shot) {
                        loadSelectedBurstShot(shot);
                    }
                    @Override public void onRetake() {
                        clearBurstSession();
                        statusView.setText("연속 촬영을 다시 시작할 수 있습니다.");
                    }
                    @Override public void onCancel() {
                        clearBurstSession();
                        statusView.setText("연속 촬영을 취소했습니다.");
                    }
                }).show();'''
replace_once(old_finish, new_finish, 'burst review portrait selection')
main_file.write_text(text, encoding='utf-8')

gradle = gradle_file.read_text(encoding='utf-8')
if gradle.count('versionCode = 14') != 1 or gradle.count('versionName = "3.6.0"') != 1:
    raise SystemExit('expected 3.6.0 version not found')
gradle = gradle.replace('versionCode = 14', 'versionCode = 15', 1)
gradle = gradle.replace('versionName = "3.6.0"', 'versionName = "3.7.0"', 1)
gradle_file.write_text(gradle, encoding='utf-8')

entry = '''
## 3.7.0

- 인물·아이·가족사진 베스트샷 강화
- Android 내장 얼굴 검출을 이용한 기기 내부 분석
- 얼굴 위치·크기·화면 가장자리 잘림·얼굴 부분 선명도 분석
- 인물 모드에서는 일반 사진 품질과 인물 촬영 점수를 결합해 BEST 선택
- 아이·가족 모드에서는 감지된 얼굴 수가 많은 후보를 우선 고려
- 자동 모드는 연속 촬영 후보 절반 이상에서 얼굴이 검출될 때 인물 기준 자동 적용
- 얼굴 미검출 시 기존 사진 품질 점수로 안전하게 대체
- 얼굴 신원·이름·나이·외모는 분석하거나 저장하지 않음
'''
changelog_file.write_text(
    changelog_file.read_text(encoding='utf-8').rstrip() + '\n' + entry,
    encoding='utf-8')

readme_add = '''

## 인물·아이 베스트샷 3.7

연속 촬영 후보에서 정면에 가까운 얼굴이 검출되면 사진 품질과 함께 얼굴 위치, 크기, 잘림 안전성, 얼굴 부분 선명도를 비교합니다. `인물` 또는 `아이·가족` 촬영 코치 모드를 선택하면 인물 기준이 우선 적용되며, 자동 모드는 후보 절반 이상에서 얼굴이 확인될 때만 인물 기준으로 전환합니다. 얼굴 신원과 외모는 판단하거나 저장하지 않습니다.
'''
readme_file.write_text(
    readme_file.read_text(encoding='utf-8').rstrip() + readme_add,
    encoding='utf-8')

print('Applied Vintage Color Camera 3.7.0 portrait best-shot')
