package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.exoplayer2.util.Log;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.TL_messageUserReaction;
import org.telegram.tgnet.TLRPC.TL_reactionCount;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ReactionChatCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PollVotesAlert;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import static org.telegram.tgnet.ConnectionsManager.getInstance;

public class MessageSeenView extends FrameLayout {

    private final MessageObject messageObject;
    private String reactionFilter;
    private TL_reactionCount reactionCount;
    ArrayList<Long> peerIds = new ArrayList<>();
    // reacted users ??

    public ArrayList<TLRPC.User> users = new ArrayList<>();
    public ArrayList<TLRPC.User> reactedUsers = new ArrayList<>();
    public HashMap<Long, String> reactions = new HashMap<>();
    public HashMap<Long, TLRPC.User> userIdToUser = new HashMap<>();
    public ArrayList<TL_messageUserReaction> userReactions = new ArrayList<>();
    AvatarsImageView avatarsImageView;
    TextView titleView;
    ImageView iconView;
    int totalNumberOfReactions;
    TextView iconTextView;
    int currentAccount;
    boolean isVoice;
    boolean hasReactions;


    FlickerLoadingView flickerLoadingView;
    private RecyclerListView recycler;
    private String firstRequestNextOffter;

    public MessageSeenView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat) {
        this(context, currentAccount, messageObject, chat, false);
    }

    public MessageSeenView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat,
            boolean ignoreSeen) {
        super(context);
        this.currentAccount = currentAccount;

        if (messageObject.hasReactions()) {
            for (TL_reactionCount result : messageObject.messageOwner.reactions.results) {
                totalNumberOfReactions += result.count;
            }
        }
        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());
        hasReactions = (messageObject.hasReactions());
        this.messageObject = messageObject;
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setMinWidth(AndroidUtilities.dp(150));
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

        TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
        req.msg_id = messageObject.getId();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

        iconView = new ImageView(context);
        iconTextView = new TextView(context);
        iconTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        iconTextView.setLines(1);
        iconTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        addView(iconTextView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable;
        if (hasReactions) {
            drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions)
                    .mutate();
        } else {
            drawable =
                    ContextCompat.getDrawable(context, isVoice ? R.drawable.msg_played : R.drawable.msg_seen).mutate();
        }
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon),
                PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);
        long fromId = 0;
        if (messageObject.messageOwner.from_id != null) {
            fromId = messageObject.messageOwner.from_id.user_id;
        }
        long finalFromId = fromId;

        if (!ignoreSeen) {
            getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                FileLog.e("MessageSeenView request completed");
                if (error == null) {
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    ArrayList<Long> unknownUsers = new ArrayList<>();
                    HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
                    ArrayList<Long> allPeers = new ArrayList<>();
                    for (int i = 0, n = vector.objects.size(); i < n; i++) {
                        Object object = vector.objects.get(i);
                        if (object instanceof Long) {
                            Long peerId = (Long) object;
                            if (finalFromId == peerId) {
                                continue;
                            }
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                            allPeers.add(peerId);
                            if (true || user == null) {
                                unknownUsers.add(peerId);
                            } else {
                                usersLocal.put(peerId, user);
                            }
                        }
                    }

                    if (unknownUsers.isEmpty()) {
                        for (int i = 0; i < allPeers.size(); i++) {
                            peerIds.add(allPeers.get(i));
                            users.add(usersLocal.get(allPeers.get(i)));
                        }
                        requestReactions("", false);
                        if (!messageObject.hasReactions()) {
                            updateView();
                        }
                    } else {
                        if (ChatObject.isChannel(chat)) {
                            TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                            // TODO set limit here
                            usersReq.limit = 100;
                            usersReq.offset = 0;
                            usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                            usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
                            getInstance(currentAccount).sendRequest(usersReq,
                                    (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response1 != null) {
                                            TLRPC.TL_channels_channelParticipants users =
                                                    (TLRPC.TL_channels_channelParticipants) response1;
                                            for (int i = 0; i < users.users.size(); i++) {
                                                TLRPC.User user = users.users.get(i);
                                                MessagesController.getInstance(currentAccount).putUser(user, false);
                                                usersLocal.put(user.id, user);
                                            }
                                            for (int i = 0; i < allPeers.size(); i++) {
                                                peerIds.add(allPeers.get(i));
                                                this.users.add(usersLocal.get(allPeers.get(i)));
                                            }
                                            requestReactions("", false);
                                            if (!messageObject.hasReactions()) {
                                                updateView();
                                            }
                                        }
                                    }));
                        } else {
                            TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                            usersReq.chat_id = chat.id;
                            getInstance(currentAccount).sendRequest(usersReq,
                                    (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response1 != null) {
                                            TLRPC.TL_messages_chatFull chatFull =
                                                    (TLRPC.TL_messages_chatFull) response1;
                                            for (int i = 0; i < chatFull.users.size(); i++) {
                                                TLRPC.User user = chatFull.users.get(i);
                                                MessagesController.getInstance(currentAccount).putUser(user, false);
                                                usersLocal.put(user.id, user);
                                            }
                                            for (int i = 0; i < allPeers.size(); i++) {
                                                peerIds.add(allPeers.get(i));
                                                this.users.add(usersLocal.get(allPeers.get(i)));
                                            }
                                        }
                                        requestReactions("", false);
                                        if (!messageObject.hasReactions()) {
                                            updateView();
                                        }
                                    }));
                        }
                    }
                } else {
                    updateView();
                }
            }));
        } else {
            if (messageObject.hasReactions()) {
                requestReactions("", false);
            }
        }

        setBackground(
                Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4),
                        AndroidUtilities.dp(4)));
        setEnabled(false);
    }

    private void requestReactions(String offset, boolean hasOffset) {
        if (!messageObject.hasReactions()) {
            
            return;
        }

        TLRPC.TL_messages_getMessageReactionsList getReactionListReq =
                new TLRPC.TL_messages_getMessageReactionsList();
        getReactionListReq.id = messageObject.getId();
        getReactionListReq.peer =
                MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

        int N = 100; // TODO 50 if no limit

        // usersToDiplay.addAll(us)
        getReactionListReq.limit = 100;
        if (hasOffset) {
            getReactionListReq.offset = offset;
            getReactionListReq.flags |= 2;
        }

        if (reactionFilter != null) {
            getReactionListReq.reaction = reactionFilter;
            getReactionListReq.flags |= 1;
        }

        // getReactionListReq.reaction
        getInstance(currentAccount)
                .sendRequest(getReactionListReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                    // queries.remove(reqIds[num]);
                    if (response2 != null) {
                        TLRPC.TL_messages_messageReactionsList res =
                                (TLRPC.TL_messages_messageReactionsList) response2;
                        // parentFragment.getMessagesController().putUsers(res.users, false);
                        // if (res)
                        reactedUsers.addAll(res.users);
                        userReactions.addAll(res.reactions);
                        for (TL_messageUserReaction reaction : res.reactions) {
                            reactions.put(reaction.user_id, reaction.reaction);
                        }

                        for (User user : res.users) {
                            if (!userIdToUser.containsKey(user.id)) {
                                userIdToUser.put(user.id, user);
                            }
                        }

                        if (reactedUsers.size() > 0 && users.size() > 0) {
                            for (User reactedUser : reactedUsers) {
                                boolean contains = false;
                                User userTORemove = null;
                                for (User user : users) {
                                    if (user != null && reactedUser != null && user.id == reactedUser.id) {
                                        contains = true;
                                        userTORemove = user;
                                    }
                                }
                                if (contains) {
                                    users.remove(userTORemove);
                                }
                            }
                        }
                        this.firstRequestNextOffter = res.next_offset;
                        if (reactionFilter != null) {
                            recycler.getAdapter().notifyDataSetChanged();
                        }
                        updateView();
                    }
                }));
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void updateView() {
        if (!messageObject.hasReactions()) {
            if (recycler != null) {
                recycler.getAdapter().notifyDataSetChanged();
            }
        }
        setEnabled((totalNumberOfReactions + users.size()) > 0);
        for (int i = 0; i < 3; i++) {
            if (hasReactions) {
                if (i < reactedUsers.size()) {
                    avatarsImageView.setObject(i, currentAccount, reactedUsers.get(i));
                } else {
                    avatarsImageView.setObject(i, currentAccount, null);
                }
            } else {
                if (i < users.size()) {
                    avatarsImageView.setObject(i, currentAccount, users.get(i));
                } else {
                    avatarsImageView.setObject(i, currentAccount, null);
                }
            }
        }

        if (hasReactions) {
            if (totalNumberOfReactions == 1) {
                iconView.setVisibility(GONE);
                iconTextView.setText(Emoji.replaceEmoji(reactions.get(reactedUsers.get(0).id
                        ), Theme.chat_msgTextPaint.getFontMetricsInt(),
                        AndroidUtilities.dp(24), false));

                avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
            } else if (reactedUsers.size() == 2) {
                avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
            } else {
                avatarsImageView.setTranslationX(0);
            }
        } else {
            if (users.size() == 1) {
                // iconView.setVisibility(GONE);
                // iconTextView.setText(Emoji.replaceEmoji(reactions.get(users.get(0).id
                //         ), Theme.chat_msgTextPaint.getFontMetricsInt(),
                //         AndroidUtilities.dp(24), false));

                avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
            } else if (users.size() == 2) {
                avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
            } else {
                avatarsImageView.setTranslationX(0);
            }
        }

        avatarsImageView.commitTransition(false);

        if (totalNumberOfReactions == 0 && peerIds.size() == 1 && users.get(0) != null) {
            titleView.setText(ContactsController.formatName(users.get(0).first_name, users.get(0).last_name));
        } else {
            if (totalNumberOfReactions == 1 && reactedUsers.size() == 1 && peerIds.size() == 1) {
                titleView.setText(
                        ContactsController.formatName(reactedUsers.get(0).first_name, reactedUsers.get(0).last_name));
            } else if (totalNumberOfReactions >= 1 && hasReactions) {
                if (totalNumberOfReactions < peerIds.size()) {
                    titleView.setText(LocaleController.formatString("Reacted", R.string.Reacted, totalNumberOfReactions,
                            peerIds.size()));
                } else {
                    titleView.setText(
                            LocaleController.formatString("Reactions", R.string.Reactions, totalNumberOfReactions));
                }
            } else {
                titleView.setText(
                        LocaleController.formatPluralString(isVoice ? "MessagePlayed" : "MessageSeen", peerIds.size()));
            }
        }

        titleView.animate().alpha(1f).setDuration(220).start();
        avatarsImageView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate()
                .alpha(0f)
                .setDuration(220)
                .setListener(new HideViewAfterAnimation(flickerLoadingView))
                .start();
    }

    private int currentViewPagerPage;
    private int currentPage;
    private float pageOffset;

    interface SeenClickListener {
        public void onClick(User user);
    }

    public ViewPager createListViewWithFilters(HorizontalScrollView scrollView, SeenClickListener listener) {
        // LinearLayout
        ViewPager viewPager = new ViewPager(getContext());
        viewPager.setAdapter(new PagerAdapter() {

            @Override
            public Object instantiateItem(ViewGroup collection, int position) {
                View view;
                if (position != 0) {
                    view = createListView(messageObject.messageOwner.reactions.results.get(position - 1), null);
                } else {
                    view = createReactionListView();
                }
                ((RecyclerListView) view).setOnItemClickListener((view1, position2) -> {
                    if (((UserCell) view1).user != null) {
                        listener.onClick(((UserCell) view1).user);
                    }
                });
                collection.addView(view,
                        LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }

            @Override
            public int getCount() {
                return messageObject.messageOwner.reactions.results.size() + 1;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
        });
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        // frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                 currentPage = position;
                pageOffset = positionOffset;
                updateTitlesLayout();
            }

            @Override
            public void onPageSelected(int i) {
                ((ReactionsFilterView) ((LinearLayout) scrollView.getChildAt(0)).getChildAt(i)).setChoosen(true);
                ((ReactionsFilterView) ((LinearLayout) scrollView.getChildAt(0)).getChildAt(
                        currentViewPagerPage)).setChoosen(false);
                currentViewPagerPage = i;
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        for (int i = 0; i < messageObject.messageOwner.reactions.results.size() + 1; i++) {
            final int finalI = i;
            ((LinearLayout) scrollView.getChildAt(0)).getChildAt(i).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (finalI != currentViewPagerPage) {
                        ((ReactionsFilterView) ((LinearLayout) scrollView.getChildAt(0)).getChildAt(finalI)).setChoosen(
                                true);
                        ((ReactionsFilterView) ((LinearLayout) scrollView.getChildAt(0)).getChildAt(
                                currentViewPagerPage)).setChoosen(false);
                        viewPager.setCurrentItem(finalI);
                    }
                }
            });
        }

        return viewPager;
    }

    private void updateTitlesLayout() {

    }

    public RecyclerListView createListView(TL_reactionCount reactionCount,  SeenClickListener listener) {

        String reactionFilter = reactionCount.reaction;
        int numberOfUsersForFilter = reactionCount.count;
        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        // this.recycler = recyclerListView;
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }
                if (p == users.size() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });

        if (listener != null) {
            recyclerListView.setOnItemClickListener((view1, position2) -> {
                if (((UserCell) view1).user != null) {
                    listener.onClick(((UserCell) view1).user);
                }
            });
        }

        ArrayList<User> usersToUse = new ArrayList<>();
        ArrayList<TL_messageUserReaction> userReactions = new ArrayList<>();

        for (TL_messageUserReaction userReaction : this.userReactions) {
            if (userReaction.reaction.equals(reactionFilter)) {
                userReactions.add(userReaction);
            }
        }

        for (User user : reactedUsers) {
            if (reactions.get(user.id).equals(reactionFilter)) {
                usersToUse.add(user);
            }
        }

        for (TL_messageUserReaction userReaction : this.userReactions) {
            long userId = userReaction.user_id;
            for (User reactedUser : reactedUsers) {
                if (reactedUser.id == userId) {
                    reactions.put(userId, userReaction.reaction);
                    usersToUse.add(reactedUser);
                }
            }
        }

        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(
                        new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                UserCell cell = (UserCell) holder.itemView;

                if (position < userReactions.size()) {
                    User user = userIdToUser.get(userReactions.get(position).user_id);
                    cell.setUser(user, userReactions.get(position).reaction);
                } else {
                    cell.setLoading();
                    requestMoreReaction(reactionFilter);
                }
            }



            @Override
            public int getItemCount() {
                return numberOfUsersForFilter;
            }


            private String nextOffset;
            private boolean isFirstRequest = true;
            private boolean requestInProgress = false;

            private void requestMoreReaction(String reactionFilter) {
                if (!messageObject.hasReactions()) {
                    return;
                }

                if (requestInProgress) {
                    return;
                }
                // Log.e("MessageSeenVIew", "Requesting " + reactionFilter + " offset" + nextOffset + " in progres" + requestInProgress);
                requestInProgress = true;
                
                if (isFirstRequest) {
                    usersToUse.clear();
                    userReactions.clear();
                    isFirstRequest = false;
                }


                TLRPC.TL_messages_getMessageReactionsList getReactionListReq =
                        new TLRPC.TL_messages_getMessageReactionsList();
                getReactionListReq.id = messageObject.getId();
                getReactionListReq.peer =
                        MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

               // TODO 50 if no limit
                getReactionListReq.limit = 50;
                if (nextOffset != null) {
                    getReactionListReq.offset = nextOffset;
                    getReactionListReq.flags |= 2;
                }

                if (reactionFilter != null) {
                    getReactionListReq.reaction = reactionFilter;
                    getReactionListReq.flags |= 1;
                }


                // getReactionListReq.reaction
                getInstance(currentAccount)
                        .sendRequest(getReactionListReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            // queries.remove(reqIds[num]);
                            if (response2 != null) {
                                TLRPC.TL_messages_messageReactionsList res =
                                        (TLRPC.TL_messages_messageReactionsList) response2;
                                // parentFragment.getMessagesController().putUsers(res.users, false);
                                // if (res)
                                reactedUsers.addAll(res.users);
                                userReactions.addAll(res.reactions);
                                for (TL_messageUserReaction reaction : res.reactions) {
                                    reactions.put(reaction.user_id, reaction.reaction);
                                }

                                for (User user : res.users) {
                                    if (!userIdToUser.containsKey(user.id)) {
                                        userIdToUser.put(user.id, user);
                                    }
                                }

                                for (TL_messageUserReaction userReaction : res.reactions) {
                                    long userId = userReaction.user_id;
                                    for (User reactedUser : reactedUsers) {
                                        if (reactedUser.id == userId) {
                                            reactions.put(userId, userReaction.reaction);
                                            usersToUse.add(reactedUser);
                                        }
                                    }
                                }
                                nextOffset = res.next_offset;
                                requestInProgress = false;

                                if (reactionFilter != null) {
                                    notifyDataSetChanged();
                                }
                            }
                        }));
            }
        });
        recyclerListView.getAdapter().notifyDataSetChanged();
        return recyclerListView;
    }

    public RecyclerListView createListView( SeenClickListener listener) {

        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        this.recycler = recyclerListView;
        recyclerListView.setOnItemClickListener((view1, position2) -> {
            if (((UserCell) view1).user != null) {
                listener.onClick(((UserCell) view1).user);
            }
        });
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }
                if (p == users.size() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });
        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                UserCell cell = (UserCell) holder.itemView;
                if (position < totalNumberOfReactions) {
                    User user = userIdToUser.get(userReactions.get(position).user_id);
                    cell.setUser(user, userReactions.get(position).reaction);
                } else {
                    User user = users.get(position - totalNumberOfReactions);
                    cell.setUser(user);
                }
            }

            @Override
            public int getItemCount() {
                return totalNumberOfReactions + users.size();
            }
        });
        return recyclerListView;
    }


    private RecyclerListView createReactionListView() {

        RecyclerListView recyclerListView = new RecyclerListView(getContext());

        ArrayList<User> usersToUse = new ArrayList<>();

        for (User user : reactedUsers) {

            usersToUse.add(user);
        }
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }
                if (p == users.size() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });
        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(
                        new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (position < userReactions.size()) {
                    UserCell cell = (UserCell) holder.itemView;

                    User user = userIdToUser.get(userReactions.get(position).user_id);
                    cell.setUser(user, userReactions.get(position).reaction);
                    //
                    // if (position < usersToUse.size()) {
                    //     UserCell cell = (UserCell) holder.itemView;
                    //     cell.setUser(usersToUse.get(position), reactionFilter);
                    // }  else {
                    //     UserCell cell = (UserCell) holder.itemView;
                    //     cell.setLoading();
                    //     requestMoreReaction(reactionFilter);
                    // }

                } else if (position >= reactedUsers.size() && position < totalNumberOfReactions) {
                    UserCell cell = (UserCell) holder.itemView;
                    cell.setLoading();
                    requestMoreReaction();
                } else if (position - totalNumberOfReactions >= 0
                        && position - totalNumberOfReactions < users.size()
                        && users.size() > 0) {
                    int index = position - totalNumberOfReactions;
                    UserCell cell = (UserCell) holder.itemView;
                    cell.setUser(users.get(index));
                }
            }

            boolean requestInProgress = false;
            private String nextOffset = firstRequestNextOffter;

            private void requestMoreReaction() {
                if (!messageObject.hasReactions()) {
                    return;
                }

                if (requestInProgress) {
                    return;
                }
                // Log.e("MessageSeenVIew",
                //         "Requesting " + reactionFilter + " offset " + nextOffset + " in progres" + requestInProgress);

                TLRPC.TL_messages_getMessageReactionsList getReactionListReq =
                        new TLRPC.TL_messages_getMessageReactionsList();
                getReactionListReq.id = messageObject.getId();
                getReactionListReq.peer =
                        MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

                getReactionListReq.limit = 100;
                if (nextOffset != null) {
                    getReactionListReq.offset = nextOffset;
                    getReactionListReq.flags |= 2;
                }
                requestInProgress = true;
                // getReactionListReq.reaction
                getInstance(currentAccount)
                        .sendRequest(getReactionListReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            // queries.remove(reqIds[num]);
                            if (response2 != null) {
                                TLRPC.TL_messages_messageReactionsList res =
                                        (TLRPC.TL_messages_messageReactionsList) response2;
                                // parentFragment.getMessagesController().putUsers(res.users, false);
                                // if (res)
                                reactedUsers.addAll(res.users);
                                userReactions.addAll(res.reactions);

                                for (TL_messageUserReaction reaction : res.reactions) {
                                    reactions.put(reaction.user_id, reaction.reaction);
                                }

                                for (User user : res.users) {
                                    if (!userIdToUser.containsKey(user.id)) {
                                        userIdToUser.put(user.id, user);
                                    }
                                }

                                if (reactedUsers.size() > 0 && users.size() > 0) {
                                    for (User reactedUser : reactedUsers) {
                                        boolean contains = false;
                                        User userTORemove = null;
                                        for (User user : users) {
                                            if (user != null && reactedUser != null && user.id == reactedUser.id) {
                                                contains = true;
                                                userTORemove = user;
                                            }
                                        }
                                        if (contains) {
                                            users.remove(userTORemove);
                                        }
                                    }
                                }
                                nextOffset = res.next_offset;
                                requestInProgress = false;
                                notifyDataSetChanged();
                            }
                        }));
            }

            @Override
            public int getItemCount() {
                return totalNumberOfReactions + users.size();
            }
        });
        return recyclerListView;
    }

    private static class UserCell extends FrameLayout {

        BackupImageView avatarImageView;
        TextView nameView;
        TextView reactionView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        FlickerLoadingView flickerLoadingView;
        public User user;

        public UserCell(Context context) {
            super(context);
            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL, 13, 0, 0, 0));
            avatarImageView.setRoundRadius(AndroidUtilities.dp(16));
            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setMinWidth(AndroidUtilities.dp(200));

            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL, 59, 0, 53, 0));

            nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

            reactionView = new TextView(context);
            reactionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            reactionView.setLines(1);
            reactionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            addView(reactionView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 13, 0));

            flickerLoadingView = new FlickerLoadingView(context);
            flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
            flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_REACTION_TYPE);
            flickerLoadingView.setIsSingleCell(false);
            addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        // boolean ignoreLayout;
        //
        // @Override
        // public void requestLayout() {
        //     if (ignoreLayout) {
        //         return;
        //     }
        //     super.requestLayout();
        // }
        //
        // @Override
        // protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //     if (flickerLoadingView.getVisibility() == View.VISIBLE) {
        //         ignoreLayout = true;
        //         flickerLoadingView.setVisibility(View.GONE);
        //         super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //         flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
        //         flickerLoadingView.setVisibility(View.VISIBLE);
        //         ignoreLayout = false;
        //         super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //     } else {
        //         super.onMeasure(widthMeasureSpec,
        //                 MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), View.MeasureSpec.EXACTLY));
        //     }
        // }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), View.MeasureSpec.EXACTLY));
        }


        public void setUser(TLRPC.User user) {
            if (user != null) {
                this.user  = user;
                flickerLoadingView.setVisibility(GONE);
                nameView.setVisibility(VISIBLE);

                avatarDrawable.setInfo(user);
                ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
                reactionView.setVisibility(GONE);
            }
        }

        public void setUser(TLRPC.User user, String reaction) {
            if (user != null) {
                this.user  = user;
                flickerLoadingView.setVisibility(GONE);
                avatarDrawable.setInfo(user);
                nameView.setVisibility(VISIBLE);
                ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
                reactionView.setText(Emoji.replaceEmoji(reaction, Theme.chat_msgTextPaint.getFontMetricsInt(),
                        AndroidUtilities.dp(16), false));
                reactionView.setVisibility(VISIBLE);
            }
        }

        public void setLoading() {
            flickerLoadingView.setVisibility(VISIBLE);
            nameView.setVisibility(GONE);
            reactionView.setVisibility(GONE);
            // avatarImageView.getImageReceiver(GONE);
            // avatarDrawable.setVisible(GONE);
        }
    }
}
