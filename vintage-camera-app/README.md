# Vintage Color Camera 3.0

Galaxy S24 Ultra를 우선 대상으로 구성한 Camera2 기반 빈티지 카메라 앱입니다.

## 포함 기능

- 실시간 카메라 미리보기
- 0.6x, 1x, 3x, 5x 줌 버튼
- 전면/후면 카메라 전환
- 터치 초점
- 노출 보정
- 플래시 끔/자동/켬
- 격자선
- 12개 빈티지 색감 프리셋
- 필름 입자, 비네트, 빛샘, 날짜 스탬프
- 촬영 후 저장, 공유, 원본 비교

## 소스 보관 방식

GitHub 연결 도구의 바이너리 파일 전송 제한 때문에 원본 소스 ZIP을 Base64 조각 8개로 나누어 보관합니다.

- `source-parts/part_00.b64` ~ `part_07.b64`
- `reconstruct-source.sh`: 조각을 원본 ZIP으로 복원
- 원본 ZIP SHA-256: `473bc705bc0a7e8889d60620a56f00b1d20f23d6077c1c7fb4a3b5476248dc18`

GitHub Actions는 소스를 자동 복원하고 무결성을 확인한 뒤 APK를 빌드합니다.

## APK 받는 방법

1. 저장소의 `Actions` 탭을 엽니다.
2. `Vintage Camera 3.0 APK` 워크플로를 선택합니다.
3. 완료된 실행을 엽니다.
4. `VintageColorCamera-v3-debug-apk` 아티팩트를 내려받습니다.

## 로컬 복원

Linux 또는 Git Bash에서 다음 명령을 실행합니다.

```bash
bash vintage-camera-app/reconstruct-source.sh
```

그 후 생성된 ZIP의 압축을 풀어 Android Studio에서 열 수 있습니다.

## 주의

실제 카메라 렌즈 전환 범위는 기기 제조사의 Camera2 공개 정책에 따라 달라질 수 있습니다. 0.6x, 3x, 5x 버튼은 기기가 지원하는 확대 범위 안에서 자동 활성화됩니다.
