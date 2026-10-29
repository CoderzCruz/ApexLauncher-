package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Binds real ProgressKeeper download / install / unpack metrics to the
 * "Tasks" card of the Your Space dashboard.
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
    private final View mCardRoot;
    private final TextView mTitleView;
    private final TextView mStatusView;
    private final List<KeyBinding> mBindings = new ArrayList<>();

    public DashboardProgressController(@NonNull View cardRoot) {
        mContext = cardRoot.getContext();
        mCardRoot = cardRoot;
        mTitleView = cardRoot.findViewById(R.id.tasks_title);
        mStatusView = cardRoot.findViewById(R.id.tasks_status);
    }

    public void attach() {
        for (String key : OBSERVED_KEYS) {
            KeyBinding binding = new KeyBinding(key);
            mBindings.add(binding);
            ProgressKeeper.addListener(key, binding);
        }
        ProgressKeeper.addTaskCountListener(this, true);
        refreshIdleState();
    }

    public void detach() {
        for (KeyBinding binding : mBindings) {
            ProgressKeeper.removeListener(binding.key, binding);
        }
        mBindings.clear();
        ProgressKeeper.removeTaskCountListener(this);
    }

    public void refreshIdleState() {
        mCardRoot.post(() -> {
            if (ProgressKeeper.getTaskCount() > 0) return;
            if (mTitleView != null) {
                mTitleView.setText(R.string.aurora_tasks_title);
            }
            if (mStatusView != null) {
                mStatusView.setText(R.string.aurora_tasks_idle);
            }
        });
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        if (taskCount <= 0) {
            refreshIdleState();
        } else {
            mCardRoot.post(() -> {
                if (mTitleView != null) {
                    mTitleView.setText(R.string.aurora_tasks_title);
                }
                if (mStatusView != null && ProgressKeeper.getTaskCount() > 0) {
                    mStatusView.setText(R.string.aurora_tasks_title);
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
            mCardRoot.post(() -> {
                if (mTitleView != null) {
                    mTitleView.setText(R.string.aurora_tasks_title);
                }
                if (mStatusView != null) {
                    mStatusView.setText(resolveKeyTitle(key) + "...");
                }
            });
        }

        @Override
        public void onProgressUpdated(final int progress, final int resid, final Object... va) {
            mCardRoot.post(() -> {
                String pct = progress < 0 ? "" : (" • " + Math.max(0, Math.min(100, progress)) + "%");
                if (mTitleView != null) {
                    mTitleView.setText(R.string.aurora_tasks_title);
                }
                if (mStatusView == null) return;

                if (resid == R.string.newdl_downloading_game_files_size
                        && va != null && va.length >= 3
                        && va[0] instanceof Number && va[1] instanceof Number && va[2] instanceof Number) {
                    double dlMb = ((Number) va[0]).doubleValue();
                    double totalMb = ((Number) va[1]).doubleValue();
                    double speed = ((Number) va[2]).doubleValue();
                    mStatusView.setText(String.format(Locale.US,
                            "Minecraft files %.0f/%.0f MB • %.1f MB/s%s",
                            dlMb, totalMb, speed, pct));
                    return;
                }

                if (resid == R.string.newdl_downloading_game_files
                        && va != null && va.length >= 3
                        && va[0] instanceof Number && va[1] instanceof Number && va[2] instanceof Number) {
                    long done = ((Number) va[0]).longValue();
                    long total = ((Number) va[1]).longValue();
                    double speed = ((Number) va[2]).doubleValue();
                    mStatusView.setText(String.format(Locale.US,
                            "%d/%d files • %.1f MB/s%s", done, total, speed, pct));
                    return;
                }

                mStatusView.setText(resolveKeyTitle(key) + pct);
            });
        }

        @Override
        public void onProgressEnded() {
            refreshIdleState();
        }

        private String resolveKeyTitle(String progressKey) {
            if (ProgressLayout.DOWNLOAD_MINECRAFT.equals(progressKey)) {
                return "Downloading Minecraft";
            }
            if (ProgressLayout.INSTALL_MODPACK.equals(progressKey)) {
                return "Installing modpack";
            }
            if (ProgressLayout.UNPACK_RUNTIME.equals(progressKey)) {
                return "Unpacking Java runtime";
            }
            if (ProgressLayout.EXTRACT_SINGLE_FILES.equals(progressKey)) {
                return "Extracting files";
            }
            if (ProgressLayout.EXTRACT_COMPONENTS.equals(progressKey)) {
                return "Extracting components";
            }
            if (ProgressLayout.AUTHENTICATE_MICROSOFT.equals(progressKey)) {
                return "Signing in";
            }
            if (ProgressLayout.DOWNLOAD_VERSION_LIST.equals(progressKey)) {
                return "Syncing versions";
            }
            return "Working";
        }
    }
}
