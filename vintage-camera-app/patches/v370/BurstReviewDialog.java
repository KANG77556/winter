package kr.co.vintagecolor.camera;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * 연속 촬영 후보의 일반 품질과 인물 촬영 상태를 함께 비교하는 화면입니다.
 */
public final class BurstReviewDialog {
    public interface Listener {
        void onUseShot(BurstShotManager.Shot shot);
        void onRetake();
        void onCancel();
    }

    private final Context context;
    private final List<BurstShotManager.Shot> shots;
    private final BurstShotManager.Shot bestShot;
    private final boolean portraitScoring;
    private final boolean familyPriority;
    private final Listener listener;
    private Dialog dialog;
    private ImageView preview;
    private TextView information;
    private BurstShotManager.Shot selected;

    public BurstReviewDialog(Context context, List<BurstShotManager.Shot> shots,
            BurstShotManager.Shot bestShot, boolean portraitScoring,
            boolean familyPriority, Listener listener) {
        this.context = context;
        this.shots = shots;
        this.bestShot = bestShot;
        this.portraitScoring = portraitScoring;
        this.familyPriority = familyPriority;
        this.listener = listener;
        this.selected = bestShot != null ? bestShot : (shots.isEmpty() ? null : shots.get(0));
    }

    public void show() {
        if (shots == null || shots.isEmpty()) return;
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildContent());
        dialog.setCancelable(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams attributes = new WindowManager.LayoutParams();
            attributes.copyFrom(window.getAttributes());
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(attributes);
        }
        updateSelection(selected);
        dialog.show();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(round(Color.rgb(24, 23, 22), 22));

        TextView title = new TextView(context);
        title.setText(portraitScoring
                ? (familyPriority ? "가족사진 인물 베스트샷" : "인물사진 베스트샷")
                : "연속 촬영 베스트샷");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView guide = new TextView(context);
        guide.setText(portraitScoring
                ? "사진 품질과 얼굴 위치·선명도를 함께 비교했습니다."
                : "최고 점수 사진이 자동 선택됐습니다. 아래 후보를 눌러 비교하세요.");
        guide.setTextColor(Color.LTGRAY);
        guide.setTextSize(13f);
        guide.setGravity(Gravity.CENTER);
        root.addView(guide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.BLACK);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));

        information = new TextView(context);
        information.setTextColor(Color.WHITE);
        information.setTextSize(13f);
        information.setGravity(Gravity.CENTER);
        information.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(information, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout thumbnails = new LinearLayout(context);
        thumbnails.setOrientation(LinearLayout.HORIZONTAL);
        thumbnails.setPadding(dp(2), dp(4), dp(2), dp(4));
        scroll.addView(thumbnails, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (final BurstShotManager.Shot shot : shots) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), dp(4), dp(4), dp(4));
            item.setBackground(round(Color.rgb(49, 47, 44), 12));

            ImageView image = new ImageView(context);
            image.setImageBitmap(shot.thumbnail);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            item.addView(image, new LinearLayout.LayoutParams(dp(92), dp(92)));

            TextView label = new TextView(context);
            boolean best = bestShot != null && bestShot.index == shot.index;
            String faceText = portraitScoring && shot.portrait != null
                    ? " · 얼굴 " + shot.portrait.faceCount : "";
            label.setText("#" + (shot.index + 1) + "  " + shot.selectionScore + "점"
                    + faceText + (best ? "  BEST" : ""));
            label.setTextColor(best ? Color.rgb(255, 205, 91) : Color.WHITE);
            label.setTextSize(10f);
            label.setGravity(Gravity.CENTER);
            item.addView(label, new LinearLayout.LayoutParams(dp(128), dp(38)));

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { updateSelection(shot); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(136), dp(142));
            params.setMargins(dp(4), 0, dp(4), 0);
            thumbnails.addView(item, params);
        }
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(152)));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        Button use = actionButton("선택 사진 사용", Color.rgb(42, 116, 81));
        Button retake = actionButton("다시 촬영", Color.rgb(103, 75, 48));
        Button cancel = actionButton("모두 취소", Color.rgb(77, 72, 70));
        buttons.addView(use, weightedButtonParams());
        buttons.addView(retake, weightedButtonParams());
        buttons.addView(cancel, weightedButtonParams());
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        use.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (selected != null && listener != null) listener.onUseShot(selected);
                dismiss();
            }
        });
        retake.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (listener != null) listener.onRetake();
                dismiss();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (listener != null) listener.onCancel();
                dismiss();
            }
        });
        return root;
    }

    private void updateSelection(BurstShotManager.Shot shot) {
        if (shot == null) return;
        selected = shot;
        if (preview != null) preview.setImageBitmap(shot.thumbnail);
        if (information != null) {
            boolean best = bestShot != null && bestShot.index == shot.index;
            StringBuilder value = new StringBuilder();
            value.append("#").append(shot.index + 1)
                    .append(" · 선택 ").append(shot.selectionScore).append("점")
                    .append(" · 사진 품질 ").append(shot.quality.totalScore).append("점");
            if (portraitScoring && shot.portrait != null) {
                value.append("\n").append(shot.portrait.shortSummary())
                        .append(" · 얼굴 선명도 ").append(shot.portrait.faceSharpnessScore).append("점");
                value.append("\n").append(shot.portrait.primaryAdvice);
            } else {
                value.append("\n").append(shot.quality.primaryAdvice);
            }
            if (best) value.append(" · 자동 선택된 최고 사진");
            information.setText(value.toString());
        }
    }

    private Button actionButton(String text, int color) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setBackground(round(color, 12));
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), dp(5), dp(3), dp(5));
        return params;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }
}
