package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SharedMediaLayout;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;
import org.telegram.ui.Components.TextStyleSpan;

import static java.util.Calendar.DATE;

public class MediaCalendarActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint bluePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    TextView pickerBottomLayout;
    private View shadow;

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;

    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;
    Callback callback;
    HistoryCallback historyCallback;

    Date periodStartDate;
    Date periodEndDate;

    // SparseArray<RectF> selectedRegion = new SparseArray<>();
    HashMap<Date, RectF> selectedRegion = new HashMap<Date, RectF>();

    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private int photosVideosTypeFilter;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;

    private boolean clearHistory;
    private boolean selectRange;

    public MediaCalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate) {
        super(args);
        this.photosVideosTypeFilter = photosVideosTypeFilter;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    public MediaCalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate, boolean clearHistory) {
        this(args, photosVideosTypeFilter, selectedDate);
        this.clearHistory = clearHistory;
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public boolean onBackPressed() {
        if (selectRange) {
            selectRange = false;
            pickerBottomLayout.setText(LocaleController.getString("Selectdays", R.string.Selectdays));
            pickerBottomLayout.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
            actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);

            periodEndDate = null;
            periodStartDate = null;
            listView.getAdapter().notifyDataSetChanged();
        } else {
            finishFragment();
        }
        return false;
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 0));

        final String[] daysOfWeek = new String[] {
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(),
                        getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    onBackPressed();
                }
            }
        });

        fragmentView = contentView;

        if (clearHistory) {
            FrameLayout.LayoutParams frameLayoutParams;
            frameLayoutParams =
                    new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(),
                            Gravity.BOTTOM | Gravity.LEFT);
            frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
            shadow = new View(context);
            shadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
            contentView.addView(shadow, frameLayoutParams);

            pickerBottomLayout = new TextView(context);
            pickerBottomLayout.setBackgroundDrawable(
                    Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground),
                            getThemedColor(Theme.key_listSelector)));
            pickerBottomLayout.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
            pickerBottomLayout.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pickerBottomLayout.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            pickerBottomLayout.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            pickerBottomLayout.setAllCaps(true);
            pickerBottomLayout.setGravity(Gravity.CENTER);
            pickerBottomLayout.setText(LocaleController.getString("Selectdays", R.string.Selectdays));
            pickerBottomLayout.setOnClickListener(v -> {
                if (!selectRange) {
                    pickerBottomLayout.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory));
                    pickerBottomLayout.setTextColor(getThemedColor(Theme.key_dialogTextRed2));
                    actionBar.setTitle(LocaleController.getString("Selectdays", R.string.Selectdays));
                    //TODO animate??
                    actionBar.setBackButtonImage(R.drawable.ic_close_white);
                    selectRange = !selectRange;
                } else {
                    if (periodStartDate == null) {
                        Toast.makeText(context, "Please select history range", Toast.LENGTH_SHORT).show();
                    } else {

                        AlertDialog.Builder builder = new AlertDialog.Builder(context, getResourceProvider());
                        builder.setTitle(LocaleController.getString("AreYouSureDeleteHistory",
                                R.string.AreYouSureDeleteHistory));
                        if (periodEndDate == null) {
                            builder.setSubtitle(LocaleController.getString("AreYouSureDeleteHistoryMessageDay",
                                    R.string.AreYouSureDeleteHistoryMessageDay));
                        } else {
                            long daysSelected =
                                    TimeUnit.DAYS.convert(periodEndDate.getTime() - periodStartDate.getTime(),
                                            TimeUnit.MILLISECONDS) + 1;

                            builder.setSubtitle(makeSectionOfTextBold(
                                    LocaleController.formatString("AreYouSureDeleteHistoryMessageDays",
                                            R.string.AreYouSureDeleteHistoryMessageDays, daysSelected),
                                    LocaleController.formatString("AreYouSureDeleteHistoryMessageDaysSpan",
                                            R.string.AreYouSureDeleteHistoryMessageDaysSpan, daysSelected)));
                        }

                        CheckBoxCell[] cell = new CheckBoxCell[1];
                        cell[0] = new CheckBoxCell(context, 1, getResourceProvider());
                        cell[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                        cell[0].setText(LocaleController.formatString("DeleteMessagesOptionAlso",
                                R.string.DeleteMessagesOptionAlso,
                                UserObject.getFirstName(getMessagesController().getUser(dialogId))), "", false, false);
                        FrameLayout frameLayout = new FrameLayout(context);
                        final boolean[] deleteForAll = new boolean[1];
                        cell[0].setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0,
                                LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                        frameLayout.addView(cell[0],
                                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT,
                                        0, 0, 0, 0));
                        cell[0].setOnClickListener(v2 -> {
                            CheckBoxCell cell1 = (CheckBoxCell) v2;
                            deleteForAll[0] = !deleteForAll[0];
                            cell1.setChecked(deleteForAll[0], true);
                        });
                        builder.setView(frameLayout);
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete),
                                (dialog, which) -> {
                                    if (periodEndDate == null) {
                                        getMessagesController().clearHistory(dialogId, null, !deleteForAll[0],
                                                (int) periodStartDate.getTime() / 1000, (int) periodStartDate.getTime() / 1000);
                                    } else {
                                        getMessagesController().clearHistory(dialogId, null, !deleteForAll[0],
                                                periodStartDate.getTime()  / 1000 , periodEndDate.getTime()  / 1000 );
                                    }
                                    // CLEAR history here;
                                    historyCallback.onHistoryCleared();
                                    onBackPressed();
                                });

                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                        }
                    }
                }
            });
            contentView.addView(pickerBottomLayout,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        }

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }

        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        return fragmentView;
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    public static SpannableStringBuilder makeSectionOfTextBold(String text, String textToBold) {

        SpannableStringBuilder builder = new SpannableStringBuilder();

        if (textToBold.length() > 0 && !textToBold.trim().equals("")) {

            String testText = text.toLowerCase();
            String testTextToBold = textToBold.toLowerCase();
            int startingIndex = testText.indexOf(testTextToBold);
            int endingIndex = startingIndex + testTextToBold.length();

            if (startingIndex < 0 || endingIndex < 0) {
                return builder.append(text);
            } else if (startingIndex >= 0 && endingIndex >= 0) {
                builder.append(text);
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
                builder.setSpan(new TextStyleSpan(run), startingIndex, endingIndex, 0);
            }
        } else {
            return builder.append(text);
        }

        return builder;
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        }

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }
                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        }
        ;
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        Calendar calendar = Calendar.getInstance();

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();
        SparseArray<RectF> drawRegionByDays = new SparseArray<>();

        SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;
        float animationProgress = 1f;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(currentYear, currentMonthInYear, 0);

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44",
                                            messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44",
                                            ImageLocation.getForDocument(thumb, document), "b", (String) null,
                                            messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto
                                && messageObject.messageOwner.media.photo != null
                                && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb =
                                    FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject =
                                    FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false,
                                            currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount)
                                    .canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject,
                                                    messageObject.photoThumbsObject), "44_44", null, null,
                                            messageObject.strippedThumb,
                                            currentPhotoObject != null ? currentPhotoObject.size : 0, null,
                                            messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject,
                                                    messageObject.photoThumbsObject), "44_44",
                                            ImageLocation.getForObject(currentPhotoObjectThumb,
                                                    messageObject.photoThumbsObject), "b",
                                            currentPhotoObject != null ? currentPhotoObject.size : 0, null,
                                            messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb,
                                            messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime = (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        boolean pressed;
        float pressedX;
        float pressedY;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressed) {
                    if (clearHistory) {

                        for (int i = 0; i < drawRegionByDays.size(); i++) {
                            if (drawRegionByDays.valueAt(i).contains(pressedX, pressedY)) {
                                if (selectRange) {


                                    if (periodStartDate == null) {
                                        calendar.set(currentYear, currentMonthInYear, i + 1);
                                        periodStartDate = calendar.getTime();

                                        if (periodStartDate.after(new Date())) {
                                            periodStartDate = null;
                                            return pressed;
                                        }



                                        // selectedRegion.put(i, drawRegionByDays.valueAt(i));

                                        actionBar.setTitle(
                                                LocaleController.getString("OneDaySelected", R.string.OneDaySelected));

                                        invalidate();
                                    } else {
                                        calendar.set(currentYear, currentMonthInYear, i + 1);
                                        periodEndDate = calendar.getTime();

                                        if (periodEndDate.equals(periodStartDate)) {
                                            periodEndDate = null;
                                            actionBar.setTitle(LocaleController.getString("OneDaySelected",
                                                    R.string.OneDaySelected));
                                            invalidate();
                                        } else {
                                            if (periodEndDate.after(new Date())) {
                                                periodEndDate = null;
                                                return pressed;
                                            }


                                            if (periodStartDate.after(periodEndDate)) {
                                                Date temp = periodEndDate;
                                                periodEndDate = periodStartDate;
                                                periodStartDate = temp;
                                            }

                                            long daysSelected = TimeUnit.DAYS.convert(
                                                    periodEndDate.getTime() - periodStartDate.getTime(),
                                                    TimeUnit.MILLISECONDS) + 1;
                                            actionBar.setTitle(LocaleController.formatString("ManyDaysSelected",
                                                    R.string.ManyDaysSelected, daysSelected));
                                            listView.getAdapter().notifyDataSetChanged();
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        for (int i = 0; i < imagesByDays.size(); i++) {
                            if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
                                if (callback != null) {
                                    PeriodDay periodDay = messagesByDays.valueAt(i);
                                    callback.onDateSelected(periodDay.messageObject.getId(), periodDay.startOffset);
                                    finishFragment();
                                    break;
                                }
                            }
                        }
                    }
                }
                pressed = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
            }
            return pressed;
        }
        

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);
                int imageW = AndroidUtilities.dp(44);
                int imageH = AndroidUtilities.dp(44);
                float imageX = cx - AndroidUtilities.dp(44) / 2f;
                float imageY = cy - AndroidUtilities.dp(44) / 2f;

                // drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                RectF rectF = new RectF();
                rectF.set(imageX, imageY, imageX + imageW, imageY + imageH);
                drawRegionByDays.put(i, rectF);

                calendar.set(DATE, i + 1);
                Date currentDate = calendar.getTime();

                bluePaint.setColor(0xFFedf6fb);

                if (periodStartDate != null && currentDate.getTime() == periodStartDate.getTime() && (
                        periodStartDate.equals(periodEndDate)
                                || periodEndDate == null)) {
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, bluePaint);
                } else if (periodEndDate != null && periodStartDate != null) {
                    if (currentDate.getTime() < periodEndDate.getTime()
                            && currentDate.getTime() > periodStartDate.getTime()) {
                        // if (selectedRegion.get(calendar.getTime()) != null) {
                        RectF toDraw = new RectF(rectF.left - AndroidUtilities.dp(4f), rectF.top,
                                rectF.right + AndroidUtilities.dp(4), rectF.bottom);
                        canvas.drawRect(toDraw, bluePaint);
                    }
                    if (currentDate.getTime() == periodEndDate.getTime()
                            || currentDate.getTime() == periodStartDate.getTime()) {
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, bluePaint);
                        RectF toDraw;
                        if (currentDate.getTime() == periodStartDate.getTime()) {
                            toDraw = new RectF(cx, rectF.top, rectF.right + AndroidUtilities.dp(4), rectF.bottom);
                        } else {
                            toDraw = new RectF(rectF.left - AndroidUtilities.dp(4f), rectF.top, cx, rectF.bottom);
                        }
                        canvas.drawRect(toDraw, bluePaint);
                    }
                }
                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s, cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        imagesByDays.get(i)
                                .setImageCoords(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f,
                                        AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK,
                                (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }
                } else {
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setHistoryClearCallback(HistoryCallback callback) {
        this.historyCallback = callback;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    public interface HistoryCallback {
        void onHistoryCleared();
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate =
                new ThemeDescription.ThemeDescriptionDelegate() {
                    @Override
                    public void didSetColor() {
                        updateColors();
                    }
                };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);

        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }
}
