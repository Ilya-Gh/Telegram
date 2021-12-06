package org.telegram.ui;

import android.os.SystemClock;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.InputPeer;
import org.telegram.tgnet.TLRPC.TL_availableReaction;
import org.telegram.tgnet.TLRPC.TL_messages_availableReactions;
import org.telegram.tgnet.TLRPC.TL_messages_availableReactionsNotModified;
import org.telegram.tgnet.TLRPC.TL_updateMessageReactions;

public class ReactionsController {

    // TODO Crash if null
    public static ArrayList<TL_availableReaction> availableReactions;
    private static int reactionsHash = 0;
    private static long lastReactionReloadTimeMs;

    public static void requestReaction() {
        requestReaction(result -> {});
    }

    public static void requestReaction(final ResultCallback<ArrayList<TL_availableReaction>> callback) {

        // if (reactionsHash == 0 || lastReactionReloadTimeMs == 0) {
        //     //TODO init
        // }
        if (SystemClock.elapsedRealtime() - lastReactionReloadTimeMs < 3600000 && availableReactions != null) {
            callback.onComplete(availableReactions);
            return;
        }
        TLRPC.TL_messages_getAvailableReactions req = new TLRPC.TL_messages_getAvailableReactions();
        req.hash = reactionsHash;
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                if (response instanceof TL_messages_availableReactions) {
                    // TODO save to share pref
                    AndroidUtilities.runOnUIThread(() -> {
                        reactionsHash = ((TL_messages_availableReactions) response).hash;
                        availableReactions = ((TL_messages_availableReactions) response).reactions;
                        lastReactionReloadTimeMs = SystemClock.elapsedRealtime();
                        for (TL_availableReaction availableReaction : availableReactions) {
                            Emoji.preloadEmoji(availableReaction.reaction);
                        }
                        callback.onComplete(availableReactions);
                    });
                } else if (response instanceof TL_messages_availableReactionsNotModified) {
                    // TODO read from shared pref here
                    AndroidUtilities.runOnUIThread(() -> {
                        callback.onComplete(availableReactions);
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        lastReactionReloadTimeMs = SystemClock.elapsedRealtime();
                        callback.onError(error);
                    });
                }
            }
        });
    }
}
