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
 * 연속 촬영 원본을 임시 파일로 보관하고 분석 결과와 미리보기를 관리합니다.
 * 고해상도 비트맵 여러 장을 메모리에 계속 보관하지 않아 메모리 부족을 줄입니다.
 */
public final class BurstShotManager {
    public static final class Shot {
        public final int index;
        public final File file;
        public final Bitmap thumbnail;
        public final Date date;
        public final PhotoQualityEngine.Result quality;

        Shot(int index, File file, Bitmap thumbnail, Date date,
                PhotoQualityEngine.Result quality) {
            this.index = index;
            this.file = file;
            this.thumbnail = thumbnail;
            this.date = date;
            this.quality = quality;
        }
    }

    private final File directory;
    private final ArrayList<Shot> shots = new ArrayList<Shot>();

    public BurstShotManager(Context context) {
        directory = new File(context.getCacheDir(), "burst_session");
        clearDirectory(directory);
        if (!directory.exists()) directory.mkdirs();
    }

    /** 촬영 원본을 임시 JPEG로 저장하고 화면용 축소본과 진단 결과를 생성합니다. */
    public synchronized Shot add(Bitmap bitmap, Date date) throws Exception {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("촬영 사진이 비어 있습니다.");
        }
        int index = shots.size();
        PhotoQualityEngine.Result quality = PhotoQualityEngine.analyze(bitmap);
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
                date == null ? new Date() : date, quality);
        shots.add(shot);
        return shot;
    }

    public synchronized List<Shot> getShots() {
        return new ArrayList<Shot>(shots);
    }

    public synchronized Shot getBestShot() {
        Shot best = null;
        for (Shot shot : shots) {
            if (best == null || shot.quality.totalScore > best.quality.totalScore) {
                best = shot;
            }
        }
        return best;
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
}
