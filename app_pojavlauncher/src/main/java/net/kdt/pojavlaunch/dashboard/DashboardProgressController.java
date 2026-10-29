package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.view.View;
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
 * Binds real-time ProgressKeeper download/unpack metrics to the Abyssal Bioluminescent Ocean
 * bottom-right wave card ("Updating World Files • 84%").
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
    private final AbyssalWaveProgressView mWaveView;
    private final TextView mHeaderTextView;
    private final TextView mSubtitleView;
    private final List<KeyBinding> mBindings = new ArrayList<>();

    public DashboardProgressController(@NonNull View cardRoot) {
        mContext = cardRoot.getContext();
        mRootView = cardRoot;
        mWaveView = cardRoot.findViewById(R.id.abyssal_wave_view);
        mHeaderTextView = cardRoot.findViewById(R.id.aurora_progress_header_text);
        mSubtitleView = cardRoot.findViewById(R.id.aurora_progress_subtitle);
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
        if (mWaveView != null) {
            mWaveView.setWaveActive(false);
        }
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
            if (mWaveView != null) {
                mWaveView.setWaveActive(false);
            }
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
            if (clientJar.exists()) {
                double jarMb = clientJar.length() / (1024d * 1024d);
                mHeaderTextView.setText("World Files Ready • 100%");
                mSubtitleView.setText(String.format(Locale.US,
                        "%s verified (%.0f MB client)", displayVer, Math.max(1d, jarMb)));
                if (mWaveView != null) {
                    mWaveView.setProgress(86);
                }
            } else {
                mHeaderTextView.setText("World Files • " + displayVer);
                mSubtitleView.setText("Tap card to pre-sync game files");
                if (mWaveView != null) {
                    mWaveView.setProgress(64);
                }
            }
        });
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        if (taskCount <= 0) {
            refreshIdleState();
        } else {
            mRootView.post(() -> {
                if (mWaveView != null) {
                    mWaveView.setWaveActive(true);
                }
            });
        }
    }

    private class KeyBinding implements ProgressListener {
        final String key;

        KeyBinding(String key) {
            this.key = key;
        }

        @Override
        public void onProgressStarted() {
            mRootView.post(() -> {
                if (mWaveView != null) {
                    mWaveView.setWaveActive(true);
                }
            });
        }

        @Override
        public void onProgressUpdated(final int progress, final int resid, final Object... va) {
            mRootView.post(() -> {
                int clamped = progress < 0 ? 50 : Math.max(5, Math.min(100, progress));
                if (mWaveView != null) {
                    mWaveView.setWaveActive(true);
                    mWaveView.setProgress(clamped);
                }
                String pctSuffix = progress < 0 ? "" : (" • " + Math.max(0, Math.min(100, progress)) + "%");

                if (resid == R.string.newdl_downloading_game_files_size
                        && va != null && va.length >= 3
                        && va[0] instanceof Number && va[1] instanceof Number && va[2] instanceof Number) {
                    mHeaderTextView.setText(mContext.getString(R.string.aurora_updating_world_files) + pctSuffix);
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
                    mHeaderTextView.setText(mContext.getString(R.string.aurora_updating_world_files) + pctSuffix);
                    long done = ((Number) va[0]).longValue();
                    long total = ((Number) va[1]).longValue();
                    double speed = ((Number) va[2]).doubleValue();
                    mSubtitleView.setText(String.format(Locale.US,
                            "%d / %d files • %.1f MB/s", done, total, speed));
                    return;
                }

                mHeaderTextView.setText(resolveKeyTitle(key) + pctSuffix);
                mSubtitleView.setText(formatFallbackDetail(resid, va));
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
