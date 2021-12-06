package org.telegram.ui.Cells;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.StateSet;
import android.view.View;
import android.widget.FrameLayout;
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

public class ReactionChatCell extends FrameLayout {

    private final View parent;
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
    private AvatarDrawable[] reactionAvatarDrawables;
    private boolean[] reactionAvatarImagesVisible;
    private boolean checked;

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
    StaticLayout reactionLayout;
    AnimatedNumberLayout reactionNumberLayout;
    boolean drawReactionNumber;
    int reactionNumberWidth;
    Drawable background;
    Drawable backgroundStroke;
    public Drawable rippleDrawable;
    // int backgroundColorSelected;
    int backgroundColor;
    int textColor;

    public ReactionChatCell(@NonNull Context context, View parent, boolean isOut, boolean belowBackgroud) {
        super(context);
        this.belowBackgroud = belowBackgroud;
        this.isOut = isOut;
        this.parent = parent;
        background = context.getResources().getDrawable(R.drawable.reaction_drawable).mutate();
        backgroundStroke = context.getResources().getDrawable(R.drawable.reaction_drawable_stroke).mutate();
        reactionAvatarImages = new ImageReceiver[3];
        reactionAvatarDrawables = new AvatarDrawable[3];
        reactionAvatarImagesVisible = new boolean[3];
        for (int a = 0; a < reactionAvatarImages.length; a++) {
            reactionAvatarImages[a] = new ImageReceiver(this);
            reactionAvatarImages[a].setRoundRadius(AndroidUtilities.dp(12));
            reactionAvatarDrawables[a] = new AvatarDrawable();
            reactionAvatarDrawables[a].setTextSize(AndroidUtilities.dp(8));
        }
        // reactionNumberLayout = new AnimatedNumberLayout(parent, getPaint());
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

        if (belowBackgroud) {
            backgroundColor = Theme.getColor(Theme.key_statisticChartActiveLine);
            textColor = Theme.getColor(Theme.key_chat_serviceText);
        } else {
            backgroundColor =
                    Theme.getColor(isOut ? Theme.key_chat_outBubbleSelected : Theme.key_chat_inBubbleSelected);
            textColor =
                    Theme.getColor(isOut ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill);
        }
    }

    public void setData(TL_reactionCount reactionCount, ArrayList<Long> user_ids, boolean animateReactions) {
        this.reactionCount = reactionCount;
        reactionWidth =
                totalWidth = (int) Math.ceil(getPaint().measureText(reactionCount.reaction));

        reactionLayout = new StaticLayout(
                Emoji.replaceEmoji(reactionCount.reaction, getPaint().getFontMetricsInt(),
                        AndroidUtilities.dp(16), false)
                , getPaint(),
                reactionWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        if (user_ids == null || user_ids.isEmpty()) {
            drawReactionNumber = true;
            if (reactionNumberLayout == null) {
                reactionNumberLayout = new AnimatedNumberLayout(parent, getPaint());
                //false
                reactionNumberLayout.setNumber(reactionCount.count, false);
            } else {
                //animateReactions
                reactionNumberLayout.setNumber(reactionCount.count, animateReactions);
            }
            reactionNumberWidth = reactionNumberLayout.getWidth();
            totalWidth += AndroidUtilities.dp(10) + reactionNumberWidth + AndroidUtilities.dp(4) + AndroidUtilities.dp(10);

        } else {
            drawReactionNumber = false;
          

            int size = user_ids.size();
            for (int a = 0; a < reactionAvatarImages.length; a++) {
                if (a < size) {
                    reactionAvatarImages[a].setImageCoords(0, 0, AndroidUtilities.dp(20), AndroidUtilities.dp(20));
                    long id = user_ids.get(a);
                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    if (DialogObject.isUserDialog(id)) {
                        user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(id);
                    } else if (DialogObject.isChatDialog(id)) {
                        chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-id);
                    }
                    if (user != null) {
                        reactionAvatarDrawables[a].setInfo(user);
                        reactionAvatarImages[a].setForUserOrChat(user, reactionAvatarDrawables[a]);
                    } else if (chat != null) {
                        reactionAvatarDrawables[a].setInfo(chat);
                        reactionAvatarImages[a].setForUserOrChat(chat, reactionAvatarDrawables[a]);
                    } else {
                        reactionAvatarDrawables[a].setInfo(id, "", "");
                    }
                    reactionAvatarImagesVisible[a] = true;
                    totalWidth += AndroidUtilities.dp(12);
                    // avatarsOffset += a == 0 ? 2 : 17;
                } else if (size != 0) {
                    reactionAvatarImages[a].setImageBitmap((Drawable) null);
                    reactionAvatarImagesVisible[a] = false;
                }
            }
            totalWidth += AndroidUtilities.dp(10) + AndroidUtilities.dp(4) + AndroidUtilities.dp(10);
            // TODO show avatarts
        }


        background.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        backgroundStroke.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.MULTIPLY));
    }


    public void draw(Canvas canvas, int selectorDrawableMaskType) {
        super.draw(canvas);
        canvas.save();
        if (reactionCount.chosen) {
            backgroundStroke.setBounds(0, 0, totalWidth + AndroidUtilities.dp(2),
                    AndroidUtilities.dp(30) + AndroidUtilities.dp(2));
            backgroundStroke.draw(canvas);
            background.setBounds(AndroidUtilities.dp(2), AndroidUtilities.dp(2), totalWidth, AndroidUtilities.dp(30));
        } else {
            background.setBounds(0, 0, totalWidth, AndroidUtilities.dp(30));
        }

        background.draw(canvas);
        if (rippleDrawable != null && selectorDrawableMaskType == 2) {
            rippleDrawable.setBounds(0, 0, totalWidth, AndroidUtilities.dp(30));
            rippleDrawable.draw(canvas);
        }
        canvas.translate(AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        reactionLayout.draw(canvas);
        canvas.translate(reactionWidth, 0);
        canvas.translate(AndroidUtilities.dp(4), 0);
        if (drawReactionNumber) {
            int prevAlpha = getPaint().getAlpha();
            reactionNumberLayout.draw(canvas);
            getPaint().setAlpha(prevAlpha);
        } else {
            if (belowBackgroud) {
                backgrounDrawable.setColor(backgroundColor);
                backgrounTextDrawable.setColor(textColor);
            } else  {
                backgrounDrawable.setColor(backgroundColor | 0xff000000);
                backgrounTextDrawable.setColor(textColor);
            }
            boolean drawnAvatars = false;
            int avatarsOffset = 2;
            if (reactionAvatarImages != null) {
                int toAdd = AndroidUtilities.dp(12);
                // int ax = x + getExtraTextX();
                int ax = 0;
                int y = 0;
                for (int a = reactionAvatarImages.length - 1; a >= 0; a--) {
                    if (reactionAvatarImagesVisible[a] && !reactionAvatarImages[a].hasImageSet()) {
                        canvas.drawCircle(reactionAvatarImages[a].getCenterX() + toAdd * a,
                                reactionAvatarImages[a].getCenterY() - AndroidUtilities.dp(2),
                                AndroidUtilities.dp(12), backgrounDrawable);
                        canvas.drawCircle(reactionAvatarImages[a].getCenterX() + toAdd * a,
                                reactionAvatarImages[a].getCenterY() - AndroidUtilities.dp(2),
                                AndroidUtilities.dp(10), backgrounTextDrawable);
                    }
                }
                for (int a = reactionAvatarImages.length - 1; a >= 0; a--) {
                    if (!reactionAvatarImagesVisible[a] || !reactionAvatarImages[a].hasImageSet()) {
                        continue;
                    }
                    reactionAvatarImages[a].setImageX(ax + toAdd * a);
                    reactionAvatarImages[a].setImageY(y - AndroidUtilities.dp(2));
                    if (a != reactionAvatarImages.length - 1) {
                        canvas.drawCircle(reactionAvatarImages[a].getCenterX(), reactionAvatarImages[a].getCenterY(),
                                AndroidUtilities.dp(12), backgrounDrawable);
                    }
                    reactionAvatarImages[a].draw(canvas);
                    drawnAvatars = true;
                    if (a != 0) {
                        avatarsOffset += 17;
                    }
                }
            }
        }
        canvas.restore();
    }

    public void setChecked(boolean checked) {
        this.checked = checked;

        if (checked) {
            if (belowBackgroud) {
                backgroundColor = Theme.getColor(Theme.key_statisticChartActiveLine);
            } else {
                backgroundColor =
                        Theme.getColor(isOut ? Theme.key_chat_outAudioSeekbar : Theme.key_chat_inAudioSeekbar)
                                & 0x88ffffff;
            }
        } else {
            if (belowBackgroud) {
                backgroundColor = Theme.getColor(Theme.key_statisticChartActiveLine);
            } else {
                backgroundColor =
                        Theme.getColor(isOut ? Theme.key_chat_outBubbleSelected : Theme.key_chat_inBubbleSelected);
            }
        }
    }
}
