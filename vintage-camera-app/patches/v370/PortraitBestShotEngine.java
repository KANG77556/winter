package kr.co.vintagecolor.camera;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.FaceDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * 사진 안의 얼굴 위치와 기술적인 촬영 상태를 기기 내부에서 분석합니다.
 * 사람의 신원, 이름, 나이 또는 외모를 판단하지 않습니다.
 */
public final class PortraitBestShotEngine {
    private static final int MAX_FACES = 8;
    private static final int MAX_ANALYSIS_SIDE = 900;

    private PortraitBestShotEngine() { }

    /** 인물사진 분석 결과입니다. */
    public static final class Result {
        public final int faceCount;
        public final int portraitScore;
        public final int faceSharpnessScore;
        public final int compositionScore;
        public final int edgeSafetyScore;
        public final int faceSizeScore;
        public final int poseScore;
        public final int confidenceScore;
        public final String primaryAdvice;
        public final String[] advices;

        Result(int faceCount,
               int portraitScore,
               int faceSharpnessScore,
               int compositionScore,
               int edgeSafetyScore,
               int faceSizeScore,
               int poseScore,
               int confidenceScore,
               String primaryAdvice,
               String[] advices) {
            this.faceCount = faceCount;
            this.portraitScore = portraitScore;
            this.faceSharpnessScore = faceSharpnessScore;
            this.compositionScore = compositionScore;
            this.edgeSafetyScore = edgeSafetyScore;
            this.faceSizeScore = faceSizeScore;
            this.poseScore = poseScore;
            this.confidenceScore = confidenceScore;
            this.primaryAdvice = primaryAdvice;
            this.advices = advices;
        }

        public boolean hasFaces() {
            return faceCount > 0;
        }

        public String shortSummary() {
            if (!hasFaces()) return "얼굴 미감지";
            return "얼굴 " + faceCount + "명 · 인물 " + portraitScore + "점";
        }

        public String detailText() {
            if (!hasFaces()) {
                return "정면에 가까운 얼굴을 찾지 못했습니다. 일반 사진 품질 점수로 베스트샷을 선택합니다.";
            }
            StringBuilder value = new StringBuilder();
            value.append("인물 촬영 안정성: ").append(portraitScore).append("점\n\n");
            value.append("감지 얼굴       ").append(faceCount).append("명\n");
            value.append("얼굴 선명도     ").append(faceSharpnessScore).append("점\n");
            value.append("화면 배치       ").append(compositionScore).append("점\n");
            value.append("얼굴 잘림 안전  ").append(edgeSafetyScore).append("점\n");
            value.append("얼굴 크기       ").append(faceSizeScore).append("점\n");
            value.append("기울기·회전     ").append(poseScore).append("점\n\n");
            value.append("안내\n");
            for (String advice : advices) value.append("• ").append(advice).append("\n");
            value.append("\n얼굴의 신원이나 외모를 평가하지 않고 위치와 촬영 선명도만 확인합니다.");
            return value.toString();
        }
    }

    /**
     * Android 내장 얼굴 검출기를 사용해 최대 8개의 얼굴을 확인합니다.
     * 검출기가 처리할 수 있도록 폭이 짝수인 RGB_565 축소본을 생성합니다.
     */
    public static Result analyze(Bitmap source) {
        if (source == null || source.isRecycled()) return emptyResult();

        Bitmap analysis = null;
        Bitmap rgb565 = null;
        try {
            analysis = createAnalysisBitmap(source);
            if (analysis == null) return emptyResult();
            rgb565 = analysis.copy(Bitmap.Config.RGB_565, false);
            if (rgb565 == null) return emptyResult();

            int width = rgb565.getWidth();
            int height = rgb565.getHeight();
            FaceDetector detector = new FaceDetector(width, height, MAX_FACES);
            FaceDetector.Face[] faces = new FaceDetector.Face[MAX_FACES];
            int detected = detector.findFaces(rgb565, faces);
            if (detected <= 0) return emptyResult();

            double sharpnessSum = 0d;
            double compositionSum = 0d;
            double edgeSum = 0d;
            double sizeSum = 0d;
            double poseSum = 0d;
            double confidenceSum = 0d;
            int valid = 0;
            ArrayList<Rect> faceRects = new ArrayList<Rect>();

            for (int i = 0; i < Math.min(detected, faces.length); i++) {
                FaceDetector.Face face = faces[i];
                if (face == null) continue;
                PointF midpoint = new PointF();
                face.getMidPoint(midpoint);
                float eyeDistance = face.eyesDistance();
                if (eyeDistance <= 2f) continue;

                Rect rect = approximateFaceRect(midpoint, eyeDistance, width, height);
                if (rect.width() < 8 || rect.height() < 8) continue;
                faceRects.add(rect);
                valid++;

                double x = midpoint.x / Math.max(1f, width);
                double y = midpoint.y / Math.max(1f, height);
                double nearestHorizontal = Math.min(Math.abs(x - 0.333d),
                        Math.min(Math.abs(x - 0.5d), Math.abs(x - 0.667d)));
                int horizontalScore = clamp((int) Math.round(100d - nearestHorizontal * 300d), 0, 100);
                int verticalScore = clamp((int) Math.round(100d - Math.abs(y - 0.36d) * 240d), 0, 100);
                compositionSum += horizontalScore * 0.55d + verticalScore * 0.45d;

                double minMargin = Math.min(rect.left / (double) width,
                        Math.min((width - rect.right) / (double) width,
                                Math.min(rect.top / (double) height,
                                        (height - rect.bottom) / (double) height)));
                edgeSum += clamp((int) Math.round((minMargin + 0.015d) * 950d), 0, 100);

                double eyeRatio = eyeDistance / Math.max(1d, Math.min(width, height));
                sizeSum += scoreRange(eyeRatio, 0.055d, 0.19d, 0.12d);

                double yaw = Math.abs(face.pose(FaceDetector.Face.EULER_Y));
                double roll = Math.abs(face.pose(FaceDetector.Face.EULER_Z));
                poseSum += clamp((int) Math.round(100d - yaw * 1.8d - roll * 2.1d), 0, 100);
                confidenceSum += clamp((int) Math.round(face.confidence() * 100d), 0, 100);
                sharpnessSum += localSharpnessScore(analysis, rect);
            }

            if (valid <= 0) return emptyResult();

            int sharpness = clamp((int) Math.round(sharpnessSum / valid), 0, 100);
            int composition = clamp((int) Math.round(compositionSum / valid), 0, 100);
            int edge = clamp((int) Math.round(edgeSum / valid), 0, 100);
            int size = clamp((int) Math.round(sizeSum / valid), 0, 100);
            int pose = clamp((int) Math.round(poseSum / valid), 0, 100);
            int confidence = clamp((int) Math.round(confidenceSum / valid), 0, 100);

            if (valid > 1) {
                composition = clamp((int) Math.round(
                        composition * 0.72d + groupBalanceScore(faceRects, width, height) * 0.28d), 0, 100);
            }

            int total = clamp((int) Math.round(
                    sharpness * 0.31d
                            + composition * 0.19d
                            + edge * 0.20d
                            + size * 0.12d
                            + pose * 0.11d
                            + confidence * 0.07d), 0, 100);

            List<String> advice = new ArrayList<String>();
            if (sharpness < 58) advice.add("얼굴 부분이 부드럽습니다. 휴대전화를 고정하고 얼굴을 눌러 초점을 맞추세요.");
            if (edge < 62) advice.add("얼굴이 화면 가장자리에 가깝습니다. 카메라를 조금 뒤로 빼거나 구도를 옮기세요.");
            if (size < 55) advice.add("얼굴 크기가 적절하지 않습니다. 가까이 이동하거나 3× 렌즈를 활용해보세요.");
            if (composition < 58) advice.add("눈 위치가 너무 높거나 낮습니다. 얼굴을 화면 위쪽 3분할 부근에 맞춰보세요.");
            if (pose < 52) advice.add("얼굴이나 휴대전화 기울기가 큽니다. 정면에 가깝게 다시 맞춰보세요.");
            if (advice.isEmpty()) advice.add("얼굴 위치와 선명도가 안정적입니다. 표정이 가장 자연스러운 후보를 선택하세요.");
            while (advice.size() > 3) advice.remove(advice.size() - 1);

            String primary = advice.get(0);
            if (primary.length() > 28) primary = primary.substring(0, 28) + "…";
            return new Result(valid, total, sharpness, composition, edge, size, pose,
                    confidence, primary, advice.toArray(new String[0]));
        } catch (Throwable ignored) {
            return emptyResult();
        } finally {
            if (rgb565 != null && !rgb565.isRecycled()) rgb565.recycle();
            if (analysis != null && analysis != source && !analysis.isRecycled()) analysis.recycle();
        }
    }

    private static Bitmap createAnalysisBitmap(Bitmap source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= 1 || sourceHeight <= 1) return null;
        float scale = Math.min(1f, MAX_ANALYSIS_SIDE
                / (float) Math.max(sourceWidth, sourceHeight));
        int width = Math.max(2, Math.round(sourceWidth * scale));
        int height = Math.max(2, Math.round(sourceHeight * scale));
        if ((width & 1) != 0) width--;
        width = Math.max(2, width);
        if (width == sourceWidth && height == sourceHeight) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    private static Rect approximateFaceRect(PointF midpoint, float eyeDistance,
            int width, int height) {
        int left = Math.round(midpoint.x - eyeDistance * 1.72f);
        int right = Math.round(midpoint.x + eyeDistance * 1.72f);
        int top = Math.round(midpoint.y - eyeDistance * 1.38f);
        int bottom = Math.round(midpoint.y + eyeDistance * 2.35f);
        return new Rect(clamp(left, 0, width - 1), clamp(top, 0, height - 1),
                clamp(right, 1, width), clamp(bottom, 1, height));
    }

    private static int localSharpnessScore(Bitmap bitmap, Rect rect) {
        int stepX = Math.max(1, rect.width() / 42);
        int stepY = Math.max(1, rect.height() / 42);
        double sum = 0d;
        int count = 0;
        for (int y = rect.top + stepY; y < rect.bottom - stepY; y += stepY) {
            for (int x = rect.left + stepX; x < rect.right - stepX; x += stepX) {
                double center = luminance(bitmap.getPixel(x, y));
                double right = luminance(bitmap.getPixel(Math.min(rect.right - 1, x + stepX), y));
                double below = luminance(bitmap.getPixel(x, Math.min(rect.bottom - 1, y + stepY)));
                sum += Math.abs(center - right) + Math.abs(center - below);
                count += 2;
            }
        }
        double gradient = count == 0 ? 0d : sum / count;
        return clamp((int) Math.round((gradient - 0.010d) * 1550d), 0, 100);
    }

    private static int groupBalanceScore(List<Rect> rects, int width, int height) {
        if (rects == null || rects.size() <= 1) return 100;
        double centerX = 0d;
        double centerY = 0d;
        double sizeSum = 0d;
        double sizeSquare = 0d;
        for (Rect rect : rects) {
            centerX += rect.exactCenterX() / width;
            centerY += rect.exactCenterY() / height;
            double area = rect.width() * rect.height() / (double) Math.max(1, width * height);
            sizeSum += area;
            sizeSquare += area * area;
        }
        centerX /= rects.size();
        centerY /= rects.size();
        double meanSize = sizeSum / rects.size();
        double variance = Math.max(0d, sizeSquare / rects.size() - meanSize * meanSize);
        double positionPenalty = Math.abs(centerX - 0.5d) * 120d
                + Math.abs(centerY - 0.42d) * 90d;
        double sizePenalty = meanSize <= 0d ? 60d : Math.sqrt(variance) / meanSize * 38d;
        return clamp((int) Math.round(100d - positionPenalty - sizePenalty), 0, 100);
    }

    private static int scoreRange(double value, double minimum, double maximum,
            double ideal) {
        if (value >= minimum && value <= maximum) {
            return clamp((int) Math.round(100d - Math.abs(value - ideal) * 360d), 72, 100);
        }
        double distance = value < minimum ? minimum - value : value - maximum;
        return clamp((int) Math.round(72d - distance * 850d), 0, 72);
    }

    private static double luminance(int color) {
        return (0.2126d * Color.red(color)
                + 0.7152d * Color.green(color)
                + 0.0722d * Color.blue(color)) / 255d;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Result emptyResult() {
        return new Result(0, 0, 0, 0, 0, 0, 0, 0,
                "얼굴을 찾지 못했습니다.",
                new String[] {"일반 사진 품질 점수로 후보를 비교합니다."});
    }
}
