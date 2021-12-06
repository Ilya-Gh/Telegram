package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.Switch;

public class ReactionsCell extends FrameLayout {

    private TextView textView;
    private TextView imageView;
    private RadialProgressView progressView;
    private Switch checkBox;
    private boolean needDivider;
    private TLRPC.TL_availableReaction reaction;

    public ReactionsCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(textView,
                LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                        Gravity.START | Gravity.CENTER_VERTICAL,
                        71, 0, 46, 0));

        imageView = new TextView(context);
        // imageView.setAspectFit(true);
        // imageView.setLayerNum(1);
        imageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        imageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        imageView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        imageView.setLines(1);
        imageView.setMaxLines(1);
        imageView.setSingleLine(true);
        addView(imageView,
                LayoutHelper.createFrame(40, 40,
                        (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                        LocaleController.isRTL ? 0 : 13, 0, LocaleController.isRTL ? 13 : 0, 0));

        checkBox = new Switch(context);
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhite);
        addView(checkBox, LayoutHelper.createFrame(37, 20,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    public void setReaction(TLRPC.TL_availableReaction reaction, boolean checked, boolean divider) {
        needDivider = divider;
        this.reaction = reaction;

        checkBox.setChecked(checked, false);
        imageView.setVisibility(VISIBLE);
        if (progressView != null) {
            progressView.setVisibility(INVISIBLE);
        }

        textView.setTranslationY(0);
        textView.setText(reaction.title);

        imageView.setText(Emoji.replaceEmoji(reaction.reaction, Theme.chat_msgTextPaintOneEmoji.getFontMetricsInt(),
                AndroidUtilities.dp(40), false));

        // if (reaction.static_icon != null) {
        // TLRPC.Document document = reaction.static_icon;
        // TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
        // ImageLocation imageLocation = ImageLocation.getForDocument(thumb, reaction.static_icon);
        // SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
        //
        //
        // // imageView.setImage(imageLocation, "70_70", "webp", svgThumb, reaction);
        // if (MessageObject.isAnimatedStickerDocument(document, true)) {
        //     if (svgThumb != null) {
        //         imageView.setImage(ImageLocation.getForDocument(document), "70_70", svgThumb, 0, reaction);
        //     } else {
        //         imageView.setImage(ImageLocation.getForDocument(document), "70_70", imageLocation, null, 0, reaction);
        //     }
        // } else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
        //     imageView.setImage(imageLocation, "70_70", "tgs", svgThumb, reaction);
        // } else {
        //     imageView.setImage(imageLocation, "70_70", "webp", svgThumb, reaction);
        // }

        // } else {
        //     imageView.setImageDrawable(null);
        // }
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
