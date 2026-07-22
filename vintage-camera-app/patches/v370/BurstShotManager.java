package kr.co.vintagecolor.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 연속 촬영 원본을 임시 파일로 보관하고 사진 품질과 인물 촬영 상태를 함께 관리합니다.
 */
public final class BurstShotManager {
    public static final class Shot {
        public final int index;
        public final File file;
        public final Bitmap thumbnail;
        public final Date date;
        public final PhotoQualityEngine.Result quality;
        public final PortraitBestShotEngine.Result portrait;
        public int selectionScore;

        Shot(int index, File file, Bitmap thumbnail, Date date,
                PhotoQualityEngine.Result quality,
                PortraitBestShotEngine.Result portrait) {
            this.index = index;
            this.file = file;
            this.thumbnail = thumbnail;
            this.date = date;
            this.quality = quality;
            this.portrait = portrait;
            this.selectionScore = quality == null ? 0 : quality.totalScore;
        }
    }

    private final File directory;
    private final ArrayList<Shot> shots = new ArrayList<Shot>();

    public BurstShotManager(Context context) {
        directory = new File(context.getCacheDir(), "burst_session");
        clearDirectory(directory);
        if (!directory.exists()) directory.mkdirs();
    }

    /** 촬영 원본을 임시 저장하고 일반 사진과 인물사진 품질을 모두 분석합니다. */
    public synchronized Shot add(Bitmap bitmap, Date date) throws Exception {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("촬영 사진이 비어 있습니다.");
        }
        int index = shots.size();
        PhotoQualityEngine.Result quality = PhotoQualityEngine.analyze(bitmap);
        PortraitBestShotEngine.Result portrait = PortraitBestShotEngine.analyze(bitmap);
        Bitmap thumbnail = createThumbnail(bitmap, 640);
        File file = new File(directory,
                String.format(java.util.Locale.US, "burst_%02d.jpg", index + 1));
        FileOutputStream output = new FileOutputStream(file);
        boolean saved;
        try {
            saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output);
            output.flush();
        } finally {
            output.close();
        }
        if (!saved || file.length() <= 0L) {
            if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
            throw new IllegalStateException("연속 촬영 임시 저장에 실패했습니다.");
        }
        Shot shot = new Shot(index, file, thumbnail,
                date == null ? new Date() : date, quality, portrait);
        shots.add(shot);
        return shot;
    }

    public synchronized List<Shot> getShots() {
        return new ArrayList<Shot>(shots);
    }

    /** 자동 모드에서 충분한 후보에 얼굴이 검출됐는지 확인합니다. */
    public synchronized boolean shouldUsePortraitScoring(
            boolean portraitRequested, boolean automaticMode) {
        if (portraitRequested) return countShotsWithFaces() > 0;
        if (!automaticMode || shots.isEmpty()) return false;
        int detected = countShotsWithFaces();
        int required = Math.max(1, (int) Math.ceil(shots.size() * 0.5d));
        return detected >= required;
    }

    /** 사진 품질과 인물 상태를 결합해 최종 BEST 후보를 정합니다. */
    public synchronized Shot getBestShot(boolean usePortraitScoring,
            boolean familyPriority) {
        Shot best = null;
        int maximumFaces = maximumFaceCount();
        for (Shot shot : shots) {
            int qualityScore = shot.quality == null ? 0 : shot.quality.totalScore;
            int score = qualityScore;
            if (usePortraitScoring) {
                int portraitScore = shot.portrait == null ? 0 : shot.portrait.portraitScore;
                if (shot.portrait != null && shot.portrait.hasFaces()) {
                    score = clamp((int) Math.round(
                            qualityScore * 0.55d + portraitScore * 0.45d), 0, 100);
                } else {
                    score = Math.max(0, qualityScore - 22);
                }
                if (familyPriority && maximumFaces > 0) {
                    int faces = shot.portrait == null ? 0 : shot.portrait.faceCount;
                    score -= Math.min(36, Math.max(0, maximumFaces - faces) * 14);
                }
            }
            shot.selectionScore = clamp(score, 0, 100);
            if (best == null || shot.selectionScore > best.selectionScore) best = shot;
        }
        return best;
    }

    public synchronized int countShotsWithFaces() {
        int count = 0;
        for (Shot shot : shots) {
            if (shot.portrait != null && shot.portrait.hasFaces()) count++;
        }
        return count;
    }

    public synchronized int maximumFaceCount() {
        int count = 0;
        for (Shot shot : shots) {
            if (shot.portrait != null) count = Math.max(count, shot.portrait.faceCount);
        }
        return count;
    }

    /** 선택한 임시 원본을 편집 가능한 비트맵으로 다시 읽습니다. */
    public Bitmap load(Shot shot, int maxSide) throws Exception {
        if (shot == null || shot.file == null || !shot.file.exists()) {
            throw new IllegalStateException("선택한 연속 촬영 사진을 찾을 수 없습니다.");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(shot.file.getAbsolutePath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(shot.file.getAbsolutePath(), options);
        if (bitmap == null) throw new IllegalStateException("선택 사진을 해석하지 못했습니다.");
        return limitBitmap(bitmap, maxSide);
    }

    /** 임시 촬영본과 썸네일을 모두 정리합니다. */
    public synchronized void clear() {
        for (Shot shot : shots) {
            if (shot.thumbnail != null && !shot.thumbnail.isRecycled()) {
                shot.thumbnail.recycle();
            }
            if (shot.file != null && shot.file.exists()) shot.file.delete();
        }
        shots.clear();
        clearDirectory(directory);
    }

    private static Bitmap createThumbnail(Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int side = Math.max(width, height);
        float scale = Math.min(1f, maxSide / (float) Math.max(1, side));
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        if (targetWidth == width && targetHeight == height) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private static Bitmap limitBitmap(Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int side = Math.max(width, height);
        if (side <= maxSide) return bitmap;
        float scale = maxSide / (float) side;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        if (scaled != bitmap && !bitmap.isRecycled()) bitmap.recycle();
        return scaled;
    }

    private static void clearDirectory(File directory) {
        if (directory == null || !directory.exists()) return;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) clearDirectory(file);
                file.delete();
            }
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
