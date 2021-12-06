package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.TL_availableReaction;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class ReactionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private ContextProgressView progressView;
    private LinearLayoutManager layoutManager;

    private Runnable queryRunnable;

    private int reqId;

    private TLRPC.ChatFull info;
    private long chatId;

    private int enableToggleRow;
    private int subtitleRow;
    private int headerRow;
    private int reactionsShadowRow;
    private int reactionsEndRow;
    private int reactionsStartRow;
    private int rowCount;

    private boolean reactionsEnabled = true;

    private ArrayList<TL_availableReaction> availableReaction;

    public ReactionsActivity(long id) {
        super();
        chatId = id;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        TLRPC.TL_messages_setChatAvailableReactions request = new TLRPC.TL_messages_setChatAvailableReactions();
        request.peer = getMessagesController().getInputPeer(-chatId);
        request.available_reactions = info.available_reactions;
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    AlertsCreator.processError(currentAccount, error, ReactionsActivity.this, request);
                });
            } else {
                getMessagesController().putChatFull(info);
            }
        });
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ReactionsActivityTitle", R.string.ReactionsActivityTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        progressView = new ContextProgressView(context, 1);
        progressView.setAlpha(0.0f);
        progressView.setScaleX(0.1f);
        progressView.setScaleY(0.1f);
        progressView.setVisibility(View.INVISIBLE);

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect, boolean immediate,
                    boolean focusedChildVisible) {
                return false;
            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {

            if (position == 0) {
                // Disable all reactions and hide
                TextCheckCell cell = (TextCheckCell) view;
                boolean isEnabled = !cell.isChecked();
                reactionsEnabled = isEnabled;

                if (reactionsEnabled) {
                    ArrayList<String> available = new ArrayList<>();
                    for (int i = 0; i < availableReaction.size(); i++) {
                        available.add(availableReaction.get(i).reaction);
                    }
                    info.available_reactions = available;
                } else {
                    info.available_reactions = new ArrayList<>();
                }
                cell.setChecked(isEnabled);
                cell.setBackgroundColorAnimated(isEnabled,
                        Theme.getColor(isEnabled ? Theme.key_statisticChartLine_lightblue
                                : Theme.key_windowBackgroundUnchecked));
                updateRows();
            } else {
                int row = position - 3;
                TLRPC.TL_availableReaction reaction = availableReaction.get(row);
                boolean isChecked = !((ReactionsCell) view).isChecked();
                ((ReactionsCell) view).setChecked(isChecked, true);

                if (isChecked) {
                    info.available_reactions.add(reaction.reaction);
                } else {
                    info.available_reactions.remove(reaction.reaction);
                }
            }
        });
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                info = chatFull;
                updateRows();
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull, ArrayList<TL_availableReaction> reactions) {
        info = chatFull;
        this.availableReaction = reactions;
        if (info != null && info.available_reactions != null) {
            reactionsEnabled = !info.available_reactions.isEmpty();
            updateRows();
        }
    }

    private void updateRows() {
        rowCount = 0;
        enableToggleRow = rowCount++;
        subtitleRow = rowCount++;

        ArrayList<TL_availableReaction> availableReactions = ReactionsController.availableReactions;
        if (!availableReactions.isEmpty()) {
            headerRow = rowCount++;
            reactionsStartRow = rowCount;
            reactionsEndRow = rowCount + availableReactions.size();
            rowCount += availableReactions.size();
            reactionsShadowRow = rowCount++;
        }
        // TODO Update data here
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return reactionsEnabled ? rowCount : 2;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    int row = position - 3;
                    ReactionsCell cell = (ReactionsCell) holder.itemView;

                    TLRPC.TL_availableReaction reaction = availableReaction.get(row);
                    boolean isChecked = false;
                    if (info != null && info.available_reactions != null) {
                        for (int j = 0; j < info.available_reactions.size(); j++) {
                            if (reaction.reaction.equals(info.available_reactions.get(j))) {
                                isChecked = true;
                            }
                        }
                    }
                    cell.setReaction(reaction, isChecked, true);
                    break;
                }
                case 1: {
                    break;
                }
                case 2: {
                    ((TextInfoPrivacyCell) holder.itemView).setText(
                            LocaleController.getString("ReactionsEnableSubtitle", R.string.ReactionsEnableSubtitle));
                    break;
                }
                case 3: {
                    ((HeaderCell) holder.itemView).setText(
                            LocaleController.getString("ReactionAvailable", R.string.ReactionAvailable));
                    break;
                }
                case 4: {
                    holder.itemView.setBackgroundDrawable(
                            Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom,
                                    Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 1 || type == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    boolean isEnabled = !info.available_reactions.isEmpty();
                    TextCheckCell cell = (TextCheckCell) view;
                    cell.setTextAndCheck(
                            LocaleController.getString("ReactionsEnable", R.string.ReactionsEnable), isEnabled, true);
                    cell.setColors(Theme.key_wallet_whiteText, Theme.key_switchTrackBlue,
                            Theme.key_switchTrackBlueChecked, Theme.key_windowBackgroundWhite,
                            Theme.key_windowBackgroundWhite);
                    cell.setBackgroundColor(Theme.getColor(isEnabled ? Theme.key_statisticChartLine_lightblue
                            : Theme.key_windowBackgroundUnchecked));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom,
                            Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom,
                            Theme.key_windowBackgroundGrayShadow));
                    break;
                case 0:
                default:
                    view = new ReactionsCell(mContext);
                    view.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enableToggleRow) {
                return 1;
            } else if (position == subtitleRow) {
                return 2;
            } else if (position == headerRow) {
                return 3;
            } else if (position == reactionsShadowRow) {
                return 4;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR,
                new Class[] { StickerSetCell.class, TextSettingsCell.class }, null, null, null,
                Theme.key_windowBackgroundWhite));
        themeDescriptions.add(
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null,
                        Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null,
                Theme.key_actionBarDefault));
        themeDescriptions.add(
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null,
                        Theme.key_actionBarDefault));
        themeDescriptions.add(
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null,
                        Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null,
                        Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null,
                        Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null,
                Theme.key_listSelector));

        themeDescriptions.add(
                new ThemeDescription(listView, 0, new Class[] { View.class }, Theme.dividerPaint, null, null,
                        Theme.key_divider));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER,
                new Class[] { TextInfoPrivacyCell.class }, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[] { TextInfoPrivacyCell.class },
                new String[] { "textView" }, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR,
                new Class[] { TextInfoPrivacyCell.class }, new String[] { "textView" }, null, null, null,
                Theme.key_windowBackgroundWhiteLinkText));

        themeDescriptions.add(
                new ThemeDescription(listView, 0, new Class[] { TextSettingsCell.class }, new String[] { "textView" },
                        null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[] { TextSettingsCell.class },
                new String[] { "valueTextView" }, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER,
                new Class[] { ShadowSectionCell.class }, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(
                new ThemeDescription(listView, 0, new Class[] { StickerSetCell.class }, new String[] { "textView" },
                        null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[] { StickerSetCell.class },
                new String[] { "valueTextView" }, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView,
                ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE,
                new Class[] { StickerSetCell.class }, new String[] { "optionsButton" }, null, null, null,
                Theme.key_stickers_menuSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[] { StickerSetCell.class },
                new String[] { "optionsButton" }, null, null, null, Theme.key_stickers_menu));

        return themeDescriptions;
    }
}
