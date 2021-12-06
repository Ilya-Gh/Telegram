package org.telegram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.TL_reactionCount;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedNumberLayout;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.w3c.dom.Text;

public class ReactionsFilterView extends FrameLayout {

    private boolean belowBackgroud;
    public static TextPaint reactionChatPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public static TextPaint reactionChatOutPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public static TextPaint reactionChatPaintBelow = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public static Paint backgrounDrawable = new Paint(TextPaint.ANTI_ALIAS_FLAG);
    public static Paint backgrounTextDrawable = new Paint(TextPaint.ANTI_ALIAS_FLAG);

    static {
        reactionChatPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reactionChatPaint.setTextSize(AndroidUtilities.dp(14));

        reactionChatPaintBelow.setTextSize(AndroidUtilities.dp(14));
        reactionChatPaintBelow.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        reactionChatOutPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reactionChatOutPaint.setTextSize(AndroidUtilities.dp(14));

        reactionChatPaint.setColor(Theme.getColor(Theme.key_chat_inAudioSeekbarFill));
        reactionChatPaintBelow.setColor(Theme.getColor(Theme.key_chat_serviceText));
        reactionChatOutPaint.setColor(Theme.getColor(Theme.key_chat_outAudioSeekbarFill));
    }

    private boolean isOut;
    public TL_reactionCount reactionCount;
    public ImageReceiver[] reactionAvatarImages;

    private TextPaint getPaint() {
        if (belowBackgroud) {
            return reactionChatPaintBelow;
        } else {
            if (isOut) {
                return reactionChatOutPaint;
            } else {
                return reactionChatPaint;
            }
        }
    }

    int reactionWidth;
    public int totalWidth;
    ImageView reactionImage;
    TextView reactionLayout;
    TextView reactionNumberLayout;
    boolean drawReactionNumber;
    int reactionNumberWidth;
    Drawable background;
    Drawable backgroundStroke;
    public Drawable rippleDrawable;
    // int backgroundColorSelected;
    int backgroundColor;
    int textColor;

    public ReactionsFilterView(@NonNull Context context) {
        super(context);
        this.belowBackgroud = false;
        this.isOut = true;
        background = context.getResources().getDrawable(R.drawable.reaction_drawable).mutate();
        backgroundStroke = context.getResources().getDrawable(R.drawable.reaction_drawable_stroke_filled).mutate();
        ColorStateList colorStateList = new ColorStateList(
                new int[][] { StateSet.WILD_CARD },
                new int[] {
                        Theme.getColor(
                                isOut ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText)
                                & 0x88ffffff
                }
        );
        if (Build.VERSION.SDK_INT >= 21) {
            rippleDrawable = new RippleDrawable(colorStateList, null, background);
        }

        backgroundColor = Theme.getColor(Theme.key_chat_inBubbleSelected);
        textColor = Theme.getColor(Theme.key_chat_inAudioSeekbarFill);

        reactionLayout = new TextView(context);
        reactionLayout.setTextSize(14);
        reactionLayout.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reactionLayout.setTextColor(textColor);

        addView(reactionLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 8, 4, 0, 4));

        reactionImage = new ImageView(context);
        // reactionImage.setTextSize(AndroidUtilities.dp(16));
        // reactionImage.setTextColor(textColor);

        addView(reactionImage, LayoutHelper.createFrame(AndroidUtilities.dp(7), AndroidUtilities.dp(6),
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 6, 4, 0, 4));

        reactionImage.setVisibility(GONE);

        reactionNumberLayout = new TextView(context);
        reactionNumberLayout.setTextSize(14);
        reactionNumberLayout.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reactionNumberLayout.setTextColor(textColor);
        addView(reactionNumberLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 30, 4, 8, 4));



        
        // TypedValue outValue = new TypedValue();
        // context.getTheme().resolveAttribute(
        //         android.R.attr.selectableItemBackground, outValue, true);
        // Drawable foregroundDrawable = context.getResources().getDrawable(outValue.resourceId);
        // foregroundDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, Mode.ADD));
        // setForeground(foregroundDrawable);


        // if (Build.VERSION.SDK_INT >= 21) {
        //     TypedValue outValue = new TypedValue();
        //     context.getTheme().resolveAttribute(
        //             android.R.attr.selectableItemBackground, outValue, true);
        //     Drawable foregroundDrawable = context.getResources().getDrawable(outValue.resourceId);
        //     foregroundDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, Mode.SRC_ATOP));
        //     setForeground(foregroundDrawable);
        // }
    }

    public void setData(TL_reactionCount reactionCount, boolean selected) {
        this.reactionCount = reactionCount;
        reactionWidth =
                totalWidth = (int) Math.ceil(getPaint().measureText(reactionCount.reaction));

        reactionLayout.setText(Emoji.replaceEmoji(reactionCount.reaction, getPaint().getFontMetricsInt(),
                AndroidUtilities.dp(14), false));

        drawReactionNumber = true;
        reactionNumberLayout.setText("" + reactionCount.count);
        reactionNumberWidth = reactionNumberLayout.getWidth();
        totalWidth +=
                AndroidUtilities.dp(10) + reactionNumberWidth + AndroidUtilities.dp(4) + AndroidUtilities.dp(10);

        background.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));

        // if (selected) {
        //     backgroundStroke.setBounds(0, 0, totalWidth + AndroidUtilities.dp(2),
        //             AndroidUtilities.dp(30) + AndroidUtilities.dp(2));
        //     // backgroundStroke.draw(canvas);
        //     // addView(backgroundStroke, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        //     background.setBounds(AndroidUtilities.dp(2), AndroidUtilities.dp(2), totalWidth, AndroidUtilities.dp(30));
        // } else {
        //     background.setBounds(0, 0, totalWidth, AndroidUtilities.dp(30));
        // }
        setBackground(background);
    }

    public void setDataAll(int total, boolean selected) {
        Drawable drawable = getContext().getResources().getDrawable(R.drawable.msg_reactions_filled).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.MULTIPLY));
        reactionImage.setImageDrawable(drawable);
        reactionLayout.setVisibility(GONE);
        reactionImage.setVisibility(VISIBLE);
        drawReactionNumber = true;
        reactionNumberLayout.setText("" + total);
        reactionNumberWidth = reactionNumberLayout.getWidth();
        totalWidth +=
                AndroidUtilities.dp(10) + reactionNumberWidth + AndroidUtilities.dp(4) + AndroidUtilities.dp(10);

        background.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));

        setBackground(background);
    }

    public void setChoosen(boolean isChoosen) {
        if (isChoosen) {
            setBackground(backgroundStroke);
            backgroundStroke.setBounds(background.getBounds());
        } else {
            setBackground(background);
        }

        // if (isChoosen) {
        //     backgroundStroke.setBounds(0, 0, totalWidth + AndroidUtilities.dp(2),
        //             AndroidUtilities.dp(30) + AndroidUtilities.dp(2));
        //     // backgroundStroke.draw(canvas);
        //     // addView(backgroundStroke, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        //     background.setBounds(AndroidUtilities.dp(2), AndroidUtilities.dp(2), totalWidth, AndroidUtilities.dp(30));
        // } else {
        //     background.setBounds(0, 0, totalWidth, AndroidUtilities.dp(30));
        // }
    }

    // public void draw(Canvas canvas, int selectorDrawableMaskType) {
    //     super.draw(canvas);
    //     canvas.save();
    //
    //
    //     background.draw(canvas);
    //     if (rippleDrawable != null && selectorDrawableMaskType == 2) {
    //         rippleDrawable.setBounds(0, 0, totalWidth, AndroidUtilities.dp(30));
    //         rippleDrawable.draw(canvas);
    //     }
    //     canvas.translate(AndroidUtilities.dp(10), AndroidUtilities.dp(8));
    //     reactionLayout.draw(canvas);
    //     canvas.translate(reactionWidth, 0);
    //     canvas.translate(AndroidUtilities.dp(4), 0);
    //     if (drawReactionNumber) {
    //         int prevAlpha = getPaint().getAlpha();
    //         reactionNumberLayout.draw(canvas);
    //         getPaint().setAlpha(prevAlpha);
    //     }
    //     canvas.restore();
    // }
}
