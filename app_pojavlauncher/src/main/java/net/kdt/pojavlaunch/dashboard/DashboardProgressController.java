package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Binds real-time ProgressKeeper download/unpack metrics to the Aurora Glass
 * bottom-right status card ("Updating World Files • 68% • 128 MB / 188 MB • 4.1 MB/s").
 */
public class DashboardProgressController implements TaskCountListener {

    private static final String[] OBSERVED_KEYS = new String[]{
            ProgressLayout.DOWNLOAD_MINECRAFT,
            ProgressLayout.INSTALL_MODPACK,
            ProgressLayout.UNPACK_RUNTIME,
            ProgressLayout.EXTRACT_SINGLE_FILES,
            ProgressLayout.EXTRACT_COMPONENTS,
            ProgressLayout.AUTHENTICATE_MICROSOFT,
            ProgressLayout.DOWNLOAD_VERSION_LIST
    };

    private final Context mContext;
    private final View mRootView;
    private final ImageView mIconView;
    private final TextView mTitleView;
    private final TextView mPercentView;
    private final TextView mSubtitleView;
    private final ProgressBar mProgressBar;
    private final List<KeyBinding> mBindings = new ArrayList<>();

    public DashboardProgressController(@NonNull View cardRoot) {
        mContext = cardRoot.getContext();
        mRootView = cardRoot;
        mIconView = cardRoot.findViewById(R.id.aurora_progress_icon);
        mTitleView = cardRoot.findViewById(R.id.aurora_progress_title);
        mPercentView = cardRoot.findViewById(R.id.aurora_progress_percent);
        mSubtitleView = cardRoot.findViewById(R.id.aurora_progress_subtitle);
        mProgressBar = cardRoot.findViewById(R.id.aurora_progress_bar);
    }

    public void attach() {
        for (String key : OBSERVED_KEYS) {
            KeyBinding binding = new KeyBinding(key);
            mBindings.add(binding);
            ProgressKeeper.addListener(key, binding);
        }
        ProgressKeeper.addTaskCountListener(this, true);
    }

    public void detach() {
        for (KeyBinding binding : mBindings) {
            ProgressKeeper.removeListener(binding.key, binding);
        }
        mBindings.clear();
        ProgressKeeper.removeTaskCountListener(this);
    }

    public void refreshIdleState() {
        if (ProgressKeeper.getTaskCount() > 0) return;
        mRootView.post(() -> {
            if (ProgressKeeper.getTaskCount() > 0) return;
            WorldSaveScanner.ensureProfilesInitialized(mContext);
            String currentKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile prof = (LauncherProfiles.mainProfileJson != null)
                    ? LauncherProfiles.mainProfileJson.profiles.get(currentKey) : null;

            String rawVer = (prof != null && Tools.isValidString(prof.lastVersionId))
                    ? prof.lastVersionId : MinecraftProfile.LATEST_RELEASE;
            String normVer = AsyncMinecraftDownloader.normalizeVersionId(rawVer);
            String displayVer = WorldSaveScanner.formatVersionLabel(mContext, normVer);

            File clientJar = new File(Tools.DIR_HOME_VERSION, normVer + File.separator + normVer + ".jar");
            int ramMb = LauncherPreferences.PREF_RAM_ALLOCATION;

            if (clientJar.exists()) {
                double jarMb = clientJar.length() / (1024d * 1024d);
                mIconView.setImageResource(R.drawable.ic_aurora_check);
                mTitleView.setText(R.string.aurora_world_files_ready);
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress(100);
                mPercentView.setText("100%");
                mSubtitleView.setText(String.format(Locale.US,
                        "%s (%.0f MB client) • %d MB RAM", displayVer, Math.max(1d, jarMb), ramMb));
            } else {
                mIconView.setImageResource(R.drawable.ic_aurora_download);
                mTitleView.setText("Sync Game Files • " + displayVer);
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress(0);
                mPercentView.setText("0%");
                mSubtitleView.setText(String.format(Locale.US,
                        "Tap ↓ to pre-download • %d MB RAM", ramMb));
            }
        });
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        if (taskCount <= 0) {
            refreshIdleState();
        }
    }

    private class KeyBinding implements ProgressListener {
        final String key;

        KeyBinding(String key) {
            this.key = key;
        }

        @Override
        public void onProgressStarted() {
            mRootView.post(() -> mIconView.setImageResource(R.drawable.ic_aurora_download));
        }

        @Override
        public void onProgressUpdated(final int progress, final int resid, final Object... va) {
            mRootView.post(() -> {
                mIconView.setImageResource(R.drawable.ic_aurora_download);
                int clamped = Math.max(0, Math.min(100, progress));
                if (progress < 0) {
                    mProgressBar.setIndeterminate(true);
                    mPercentView.setText("...");
                } else {
                    mProgressBar.setIndeterminate(false);
                    mProgressBar.setProgress(clamped);
                    mPercentView.setText(clamped + "%");
                }

                if (resid == R.string.newdl_downloading_game_files_size
                        && va != null && va.length >= 3
                        && va[0] instanceof Number && va[1] instanceof Number && va[2] instanceof Number) {
                    mTitleView.setText(R.string.aurora_updating_world_files);
                    double dlMb = ((Number) va[0]).doubleValue();
                    double totalMb = ((Number) va[1]).doubleValue();
                    double speed = ((Number) va[2]).doubleValue();
                    mSubtitleView.setText(String.format(Locale.US,
                            "%.0f MB / %.0f MB • %.1f MB/s", dlMb, totalMb, speed));
                    return;
                }

                if (resid == R.string.newdl_downloading_game_files
                        && va != null && va.length >= 3
                        && va[0] instanceof Number && va[1] instanceof Number && va[2] instanceof Number) {
                    mTitleView.setText(R.string.aurora_updating_world_files);
                    long done = ((Number) va[0]).longValue();
                    long total = ((Number) va[1]).longValue();
                    double speed = ((Number) va[2]).doubleValue();
                    mSubtitleView.setText(String.format(Locale.US,
                            "%d / %d files • %.1f MB/s", done, total, speed));
                    return;
                }

                mTitleView.setText(resolveKeyTitle(key));
                String detail = formatFallbackDetail(resid, va);
                mSubtitleView.setText(detail);
            });
        }

        @Override
        public void onProgressEnded() {
            refreshIdleState();
        }

        private String resolveKeyTitle(String progressKey) {
            if (ProgressLayout.DOWNLOAD_MINECRAFT.equals(progressKey)) {
                return mContext.getString(R.string.aurora_updating_world_files);
            }
            if (ProgressLayout.INSTALL_MODPACK.equals(progressKey)) {
                return "Installing Modpack";
            }
            if (ProgressLayout.UNPACK_RUNTIME.equals(progressKey)) {
                return "Unpacking Java Runtime";
            }
            if (ProgressLayout.AUTHENTICATE_MICROSOFT.equals(progressKey)) {
                return "Signing In";
            }
            if (ProgressLayout.DOWNLOAD_VERSION_LIST.equals(progressKey)) {
                return "Syncing Versions";
            }
            return mContext.getString(R.string.aurora_updating_world_files);
        }

        private String formatFallbackDetail(int resid, Object... va) {
            try {
                if (resid != -1) {
                    return mContext.getString(resid, va);
                }
                if (va != null && va.length > 0 && va[0] != null) {
                    return String.valueOf(va[0]);
                }
            } catch (Exception ignored) {
            }
            return "Working...";
        }
    }
}
