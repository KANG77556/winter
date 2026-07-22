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
 * 연속 촬영 후보를 한 화면에서 비교하고 사용할 사진을 선택하는 대화상자입니다.
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
    private final Listener listener;
    private Dialog dialog;
    private ImageView preview;
    private TextView information;
    private BurstShotManager.Shot selected;

    public BurstReviewDialog(Context context, List<BurstShotManager.Shot> shots,
            BurstShotManager.Shot bestShot, Listener listener) {
        this.context = context;
        this.shots = shots;
        this.bestShot = bestShot;
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
        title.setText("연속 촬영 베스트샷");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView guide = new TextView(context);
        guide.setText("최고 점수 사진이 자동 선택됐습니다. 아래 후보를 눌러 비교하세요.");
        guide.setTextColor(Color.LTGRAY);
        guide.setTextSize(13f);
        guide.setGravity(Gravity.CENTER);
        root.addView(guide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.BLACK);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));

        information = new TextView(context);
        information.setTextColor(Color.WHITE);
        information.setTextSize(14f);
        information.setGravity(Gravity.CENTER);
        information.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(information, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

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
            label.setText("#" + (shot.index + 1) + "  " + shot.quality.totalScore + "점"
                    + (best ? "  BEST" : ""));
            label.setTextColor(best ? Color.rgb(255, 205, 91) : Color.WHITE);
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            item.addView(label, new LinearLayout.LayoutParams(dp(112), dp(34)));

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { updateSelection(shot); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(120), dp(138));
            params.setMargins(dp(4), 0, dp(4), 0);
            thumbnails.addView(item, params);
        }
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(148)));

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
            information.setText("#" + (shot.index + 1) + " · " + shot.quality.summary()
                    + (best ? " · 자동 선택된 최고 사진" : "")
                    + "\n" + shot.quality.primaryAdvice);
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
