# 3.0.1 설치 호환판

## 수정 이유

Galaxy S24 Ultra에서 기존 `kr.co.vintagecolor.camera` 패키지와 GitHub 디버그 서명이 충돌하여 `앱이 설치되지 않음`이 표시될 수 있었습니다.

## 수정 내용

- applicationId: `kr.co.vintagecolor.camera.installfix`
- versionName: `3.0.1`
- versionCode: `6`
- 앱 표시 이름: `빈티지 컬러 카메라 3`

기존 앱을 삭제하지 않아도 별도 앱으로 설치할 수 있습니다.

## 빌드 결과

GitHub Actions `Vintage Camera 3.0.1 Install Fix APK` 빌드 성공.

APK SHA-256:

`325ea9a286b9379c67c12bb0029cac26a85eb91c0cdc05ea22e8af690bf6b24c`
