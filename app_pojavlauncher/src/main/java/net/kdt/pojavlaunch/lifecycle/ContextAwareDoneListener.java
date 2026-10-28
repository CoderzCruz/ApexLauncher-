package net.kdt.pojavlaunch.lifecycle;

import static net.kdt.pojavlaunch.MainActivity.INTENT_MINECRAFT_VERSION;
import static net.kdt.pojavlaunch.MainActivity.INTENT_QUICK_PLAY_WORLD;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;

public class ContextAwareDoneListener implements AsyncMinecraftDownloader.DoneListener, ContextExecutorTask {
    private final String mErrorString;
    private final String mNormalizedVersionid;
    @Nullable private final String mQuickPlayWorldFolder;

    public ContextAwareDoneListener(Context baseContext, String versionId) {
        this(baseContext, versionId, null);
    }

    public ContextAwareDoneListener(Context baseContext, String versionId, @Nullable String quickPlayWorldFolder) {
        this.mErrorString = baseContext.getString(R.string.mc_download_failed);
        this.mNormalizedVersionid = versionId;
        this.mQuickPlayWorldFolder = quickPlayWorldFolder;
    }

    private Intent createGameStartIntent(Context context) {
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.putExtra(INTENT_MINECRAFT_VERSION, mNormalizedVersionid);
        if (Tools.isValidString(mQuickPlayWorldFolder)) {
            mainIntent.putExtra(INTENT_QUICK_PLAY_WORLD, mQuickPlayWorldFolder);
        }
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return mainIntent;
    }

    @Override
    public void onDownloadDone() {
        ProgressKeeper.waitUntilDone(()->ContextExecutor.execute(this));
    }

    @Override
    public void onDownloadFailed(Throwable throwable) {
        Tools.showErrorRemote(mErrorString, throwable);
    }

    @Override
    public void executeWithActivity(Activity activity) {
        try {
            Intent gameStartIntent = createGameStartIntent(activity);
            activity.startActivity(gameStartIntent);
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid()); //You should kill yourself, NOW!
        } catch (Throwable e) {
            Tools.showError(activity.getBaseContext(), e);
        }
    }

    @Override
    public void executeWithApplication(Context context) {
        Intent gameStartIntent = createGameStartIntent(context);
        NotificationUtils.sendBasicNotification(context,
                R.string.notif_download_finished,
                R.string.notif_download_finished_desc,
                gameStartIntent,
                NotificationUtils.PENDINGINTENT_CODE_GAME_START,
                NotificationUtils.NOTIFICATION_ID_GAME_START
        );
    }
}
