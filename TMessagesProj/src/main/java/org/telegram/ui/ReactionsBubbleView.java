package org.telegram.ui;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutParams;
import androidx.recyclerview.widget.RecyclerView.State;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.ChatFull;
import org.telegram.tgnet.TLRPC.InputPeer;
import org.telegram.tgnet.TLRPC.TL_availableReaction;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class ReactionsBubbleView extends FrameLayout {

    private int textColor;
    private int iconColor;
    private int selectorColor;
    private TextView textView;
    private RecyclerListView listView;

    boolean top;
    boolean bottom;

    private int itemHeight = 48;
    private final Theme.ResourcesProvider resourcesProvider;

    private ArrayList<TL_availableReaction> availableReactions;

    private Drawable ovalDrawable;

    private int msg_id;
    private InputPeer inputPeer;
    private ReactionCallback callback;

    public ReactionsBubbleView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItem);
        iconColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon);
        selectorColor = getThemedColor(Theme.key_dialogButtonSelector);


        updateBackground();
        // setPadding   (AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);

        listView = new RecyclerListView(context) {
            @Override
            public void requestLayout() {
                // if (ignoreLayout) {
                //     return;
                // }
                super.requestLayout();
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        listView.setAdapter(new ListAdapter(context));
        listView.setHorizontalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnItemClickListener((view, position) -> {
            TL_availableReaction selectedReaction = availableReactions.get(position);
            // selectedReaction.activate_animation;
            callback.onClick(selectedReaction);

        });

        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // if (Build.VERSION.SDK_INT >= 21) {
        //     setClipToOutline(true);
        //     setOutlineProvider(new ViewOutlineProvider() {
        //         @Override
        //         public void getOutline(View view, Outline outline) {
        //             outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() , AndroidUtilities.dpf2(6));
        //         }
        //     });
        // }
    }

    public void setAvailableReactions(ArrayList<TL_availableReaction> availableReactions) {
        this.availableReactions = availableReactions;
        listView.getAdapter().notifyDataSetChanged();
    }

    public void setCurrentMessage(int msg_id, InputPeer inputPeer) {
        this.msg_id = msg_id;
        this.inputPeer = inputPeer;
    }

    interface ReactionCallback {
        public void onClick(TL_availableReaction reaction);
    }

    public void setReactionClicked(ReactionCallback callback) {
        this.callback = callback;

    }

    // @Override
    // protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    //     super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(itemHeight), View.MeasureSpec.EXACTLY));
    // }

    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }


    public void updateSelectorBackground() {
        updateBackground();
    }

    void updateBackground() {
        // int topBackgroundRadius = top ? 6 : 0;
        // int bottomBackgroundRadius = bottom ? 6 : 0;
        // setBackground(Theme.createRadSelectorDrawable(selectorColor, topBackgroundRadius, bottomBackgroundRadius));
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            if (availableReactions != null && !availableReactions.isEmpty()) {
                return availableReactions.size();
            } else {
                return 0;
            }
            // return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ReactionView(context);
            // if (currentType == TYPE_CREATE) {
            //     view = new ShareDialogCell(context, ShareDialogCell.TYPE_CREATE, null);
            //     view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(100)));
            // } else {
            //     view = new GroupCreateUserCell(context, 2, 0, false, currentType == TYPE_DISPLAY);
            // }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ReactionView) holder.itemView).bind(availableReactions.get(position));
        }
    }



    public class ReactionView extends FrameLayout {

        private BackupImageView imageView;

        public ReactionView(@NonNull Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setAspectFit(true);
            imageView.setLayerNum(1);
            addView(imageView,
                    LayoutHelper.createFrame(40, 40,
                            0,
                            5, 0, 5, 0));
        }

        public void bind(TL_availableReaction reaction) {
            if (reaction.static_icon != null) {
                TLRPC.Document document = reaction.select_animation;
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                // ImageLocation imageLocation = ImageLocation.getForDocument(thumb, reaction.static_icon);
                SvgHelper.SvgDrawable svgThumb =
                        DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);

                imageView.setImage(ImageLocation.getForDocument(document), "50_50", svgThumb, 0, reaction);
                if (imageView.getImageReceiver() != null) {
                    imageView.getImageReceiver().setAutoRepeat(2);
                }
                scheduleAnimationCheck();
            }
        }

        private boolean hasRun = false;

        private void scheduleAnimationCheck() {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Random random = new Random();
                    if (random.nextInt(3) == 0) {
                        imageView.getImageReceiver().startAnimation();
                        hasRun = true;
                    }
                    if (hasRun) {
                        //
                    } else {
                        scheduleAnimationCheck();
                    }
                }
            }, 500);
        }
    }


    public class PaddingItemDecoration extends RecyclerView.ItemDecoration {
        private final int size;

        public PaddingItemDecoration(int size) {
            this.size = size;
        }


        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull State state) {
            // Apply offset only to first item
            if (parent.getChildAdapterPosition(view) == 0) {
                outRect.left += size;
            }
        }
    }
}
