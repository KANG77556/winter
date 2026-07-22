package kr.co.vintagecolor.camera;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 촬영된 사진의 기술적인 완성도를 휴대전화 내부에서 분석합니다.
 * 예술적 가치나 사람의 외모를 평가하지 않고 노출, 선명도, 명암과 색 균형만 확인합니다.
 */
public final class PhotoQualityEngine {
    private PhotoQualityEngine() { }

    /** 사진 진단 결과입니다. */
    public static final class Result {
        public final int totalScore;
        public final int exposureScore;
        public final int sharpnessScore;
        public final int contrastScore;
        public final int dynamicRangeScore;
        public final int colorBalanceScore;
        public final String grade;
        public final String primaryAdvice;
        public final String[] advices;
        public final double averageBrightness;
        public final double shadowRatio;
        public final double highlightRatio;

        Result(int totalScore,
               int exposureScore,
               int sharpnessScore,
               int contrastScore,
               int dynamicRangeScore,
               int colorBalanceScore,
               String grade,
               String primaryAdvice,
               String[] advices,
               double averageBrightness,
               double shadowRatio,
               double highlightRatio) {
            this.totalScore = totalScore;
            this.exposureScore = exposureScore;
            this.sharpnessScore = sharpnessScore;
            this.contrastScore = contrastScore;
            this.dynamicRangeScore = dynamicRangeScore;
            this.colorBalanceScore = colorBalanceScore;
            this.grade = grade;
            this.primaryAdvice = primaryAdvice;
            this.advices = advices;
            this.averageBrightness = averageBrightness;
            this.shadowRatio = shadowRatio;
            this.highlightRatio = highlightRatio;
        }

        /** 검토 화면에 표시할 짧은 요약입니다. */
        public String summary() {
            return "사진 진단 " + totalScore + "점 · " + primaryAdvice;
        }

        /** 상세 진단 창에 표시할 설명입니다. */
        public String detailText() {
            StringBuilder value = new StringBuilder();
            value.append("기술 완성도: ").append(totalScore).append("점 · ").append(grade).append("\n\n");
            value.append("노출          ").append(exposureScore).append("점\n");
            value.append("선명도        ").append(sharpnessScore).append("점\n");
            value.append("명암          ").append(contrastScore).append("점\n");
            value.append("계조 범위     ").append(dynamicRangeScore).append("점\n");
            value.append("색 균형       ").append(colorBalanceScore).append("점\n\n");
            value.append("개선 안내\n");
            for (int i = 0; i < advices.length; i++) {
                value.append("• ").append(advices[i]).append("\n");
            }
            value.append("\n이 점수는 사진의 예술성이나 인물을 평가하지 않고 촬영 실패 가능성만 확인합니다.");
            return value.toString();
        }
    }

    /**
     * 고해상도 전체 픽셀을 처리하지 않고 최대 약 96×96개의 표본으로 빠르게 진단합니다.
     */
    public static Result analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return emptyResult();

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / 96);
        int stepY = Math.max(1, height / 96);
        int[] histogram = new int[256];

        long count = 0L;
        double sumLum = 0d;
        double sumLum2 = 0d;
        double sumGradient = 0d;
        double sumR = 0d;
        double sumG = 0d;
        double sumB = 0d;
        int shadows = 0;
        int highlights = 0;
        int gradientCount = 0;

        for (int y = stepY; y < height - stepY; y += stepY) {
            for (int x = stepX; x < width - stepX; x += stepX) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                double lum = luminance(r, g, b);
                int lumByte = clamp((int) Math.round(lum * 255d), 0, 255);
                histogram[lumByte]++;
                sumLum += lum;
                sumLum2 += lum * lum;
                sumR += r;
                sumG += g;
                sumB += b;
                if (lum < 0.08d) shadows++;
                if (lum > 0.94d) highlights++;
                count++;

                int right = bitmap.getPixel(Math.min(width - 1, x + stepX), y);
                int below = bitmap.getPixel(x, Math.min(height - 1, y + stepY));
                double rightLum = luminance(Color.red(right), Color.green(right), Color.blue(right));
                double belowLum = luminance(Color.red(below), Color.green(below), Color.blue(below));
                sumGradient += Math.abs(lum - rightLum) + Math.abs(lum - belowLum);
                gradientCount += 2;
            }
        }

        if (count == 0L) return emptyResult();

        double average = sumLum / count;
        double variance = Math.max(0d, sumLum2 / count - average * average);
        double contrast = Math.sqrt(variance);
        double sharpness = gradientCount == 0 ? 0d : sumGradient / gradientCount;
        double shadowRatio = shadows / (double) count;
        double highlightRatio = highlights / (double) count;
        double low = histogramPercentile(histogram, count, 0.05d) / 255d;
        double high = histogramPercentile(histogram, count, 0.95d) / 255d;
        double range = Math.max(0d, high - low);

        double avgR = sumR / count;
        double avgG = sumG / count;
        double avgB = sumB / count;
        double channelAverage = (avgR + avgG + avgB) / 3d;
        double cast = channelAverage <= 1d ? 0d
                : (Math.abs(avgR - channelAverage) + Math.abs(avgG - channelAverage)
                + Math.abs(avgB - channelAverage)) / (channelAverage * 3d);

        int exposureScore = scoreExposure(average, shadowRatio, highlightRatio);
        int sharpnessScore = clamp((int) Math.round((sharpness - 0.012d) * 1450d), 0, 100);
        int contrastScore = scoreTarget(contrast, 0.20d, 0.17d);
        int dynamicRangeScore = clamp((int) Math.round((range - 0.28d) * 150d), 0, 100);
        int colorBalanceScore = clamp((int) Math.round(100d - Math.max(0d, cast - 0.08d) * 180d), 0, 100);

        int total = clamp((int) Math.round(
                exposureScore * 0.27d
                        + sharpnessScore * 0.31d
                        + contrastScore * 0.14d
                        + dynamicRangeScore * 0.18d
                        + colorBalanceScore * 0.10d), 0, 100);

        List<String> advice = new ArrayList<>();
        if (sharpnessScore < 48) {
            advice.add("사진이 부드럽습니다. 휴대전화를 고정하고 주제를 눌러 초점을 맞추세요.");
        } else if (sharpnessScore < 68) {
            advice.add("조금 더 안정적으로 잡으면 세부 묘사가 좋아집니다.");
        }
        if (average < 0.28d || shadowRatio > 0.38d) {
            advice.add("어두운 영역이 많습니다. 밝은 쪽으로 이동하거나 노출을 조금 올려보세요.");
        } else if (average > 0.74d || highlightRatio > 0.16d) {
            advice.add("밝은 부분이 날아갈 수 있습니다. 노출을 낮추거나 빛의 방향을 바꿔보세요.");
        }
        if (highlightRatio > 0.10d) {
            advice.add("하이라이트가 많습니다. 화면의 가장 밝은 부분을 눌러 노출을 맞춰보세요.");
        }
        if (shadowRatio > 0.28d) {
            advice.add("검은 영역의 세부가 사라질 수 있습니다. 그림자 쪽에 빛을 보충해보세요.");
        }
        if (contrastScore < 48) {
            advice.add("명암이 평평합니다. 옆에서 들어오는 빛이나 단순한 배경을 활용해보세요.");
        }
        if (dynamicRangeScore < 48 && exposureScore >= 55) {
            advice.add("밝기 범위가 좁습니다. 주제와 배경 사이에 밝기 차이를 만들어보세요.");
        }
        if (colorBalanceScore < 58) {
            advice.add("색 쏠림이 강합니다. 서로 다른 색의 조명이 섞이지 않게 촬영해보세요.");
        }
        if (advice.isEmpty()) {
            advice.add("노출과 선명도가 안정적입니다. 지금 결과를 기준으로 구도와 표정을 선택하세요.");
        }
        while (advice.size() > 4) advice.remove(advice.size() - 1);

        String grade;
        if (total >= 88) grade = "매우 안정적";
        else if (total >= 76) grade = "좋음";
        else if (total >= 62) grade = "보통";
        else grade = "재촬영 권장";

        String primary = advice.get(0);
        if (primary.length() > 24) primary = primary.substring(0, 24) + "…";

        return new Result(total, exposureScore, sharpnessScore, contrastScore,
                dynamicRangeScore, colorBalanceScore, grade, primary,
                advice.toArray(new String[0]), average, shadowRatio, highlightRatio);
    }

    /** 짧은 상태 문구를 만듭니다. */
    public static String summary(Bitmap bitmap) {
        return analyze(bitmap).summary();
    }

    private static int scoreExposure(double average, double shadowRatio, double highlightRatio) {
        double centerPenalty = Math.abs(average - 0.50d) * 145d;
        double clipPenalty = shadowRatio * 70d + highlightRatio * 105d;
        return clamp((int) Math.round(100d - centerPenalty - clipPenalty), 0, 100);
    }

    private static int scoreTarget(double value, double target, double tolerance) {
        return clamp((int) Math.round(100d - Math.abs(value - target) / tolerance * 100d), 0, 100);
    }

    private static int histogramPercentile(int[] histogram, long total, double percentile) {
        long target = Math.max(1L, Math.round(total * percentile));
        long sum = 0L;
        for (int i = 0; i < histogram.length; i++) {
            sum += histogram[i];
            if (sum >= target) return i;
        }
        return 255;
    }

    private static double luminance(int r, int g, int b) {
        return (0.2126d * r + 0.7152d * g + 0.0722d * b) / 255d;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Result emptyResult() {
        return new Result(0, 0, 0, 0, 0, 0,
                "분석 불가", "사진을 다시 불러오세요",
                new String[] {"사진 데이터를 읽지 못했습니다."}, 0d, 0d, 0d);
    }
}
