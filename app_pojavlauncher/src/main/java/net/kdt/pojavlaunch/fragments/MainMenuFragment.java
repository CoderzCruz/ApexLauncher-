package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private volatile TextView mTaskStatus;
    private volatile View mTaskCard;
    @Nullable private TextView mProfileDirectory;
    @Nullable private TextView mProfileRuntime;

    // ProgressKeeper may notify us from a worker thread. Never retain a destroyed view in a post.
    private final TaskCountListener mTaskCountListener = taskCount -> {
        TextView status = mTaskStatus;
        View card = mTaskCard;
        if (status == null || card == null) return;
        status.post(() -> {
            if (mTaskStatus != status || mTaskCard != card) return;
            status.setText(taskCount == 0
                    ? status.getContext().getString(R.string.launcher_tasks_idle)
                    : status.getContext().getString(R.string.progresslayout_tasks_in_progress, taskCount));
            card.setEnabled(taskCount > 0);
        });
    };

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View newsButton = view.findViewById(R.id.news_button);
        View discordButton = view.findViewById(R.id.discord_button);
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View installJarButton = view.findViewById(R.id.install_jar_button);
        View shareLogsButton = view.findViewById(R.id.share_logs_button);
        View openDirectoryButton = view.findViewById(R.id.open_files_button);
        View editProfileButton = view.findViewById(R.id.edit_profile_button);
        View playButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mProfileDirectory = view.findViewById(R.id.launcher_profile_dir_value);
        mProfileRuntime = view.findViewById(R.id.launcher_profile_runtime_value);
        mTaskStatus = view.findViewById(R.id.launcher_task_status);
        mTaskCard = view.findViewById(R.id.launcher_tasks_card);

        view.findViewById(R.id.create_profile_button).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));
        view.findViewById(R.id.explore_modpacks_button).setOnClickListener(v -> openModpacks());
        view.findViewById(R.id.quick_settings_button).setOnClickListener(v ->
                requireActivity().findViewById(R.id.setting_button).performClick());
        view.findViewById(R.id.gamepad_mapping_button).setOnClickListener(v -> openGamepadMapper());

        newsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        discordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        customControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        installJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        installJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
        editProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        shareLogsButton.setOnClickListener(v -> shareLog(requireContext()));
        openDirectoryButton.setOnClickListener(v -> {
            Tools.switchDemo(Tools.isDemoProfile(v.getContext()));
            if (Tools.isDemoProfile(v.getContext())) {
                Toast.makeText(v.getContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), getCurrentProfileDirectory(), false);
        });

        // Keep the original long-press shortcut in addition to the visible gamepad tool.
        newsButton.setOnLongClickListener(v -> {
            openGamepadMapper();
            return true;
        });
        mTaskCard.setOnClickListener(v -> {
            if (ProgressKeeper.hasOngoingTasks()) {
                requireActivity().findViewById(R.id.progress_layout).performClick();
            }
        });
        mVersionSpinner.setOnProfileChangedListener(this::updateProfileDetails);
        ProgressKeeper.addTaskCountListener(mTaskCountListener);
        updateProfileDetails();
    }

    private void openModpacks() {
        // Match the restrictions used by ProfileTypeSelectFragment.
        if (Tools.isLocalProfile(requireContext()) || Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
    }

    private void openGamepadMapper() {
        Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    /** The wide layout shows real profile preferences, not sample values from the mockup. */
    private void updateProfileDetails() {
        if (mProfileDirectory == null || mProfileRuntime == null || LauncherProfiles.mainProfileJson == null) return;
        String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        if (profile == null) {
            mProfileDirectory.setText("");
            mProfileRuntime.setText(R.string.global_default);
            return;
        }
        // The default directory changes with the account's demo state; show the
        // profile preference rather than a potentially stale resolved path.
        mProfileDirectory.setText(profile.gameDir == null
                ? getString(R.string.global_default)
                : Tools.getGameDirPath(profile).getAbsolutePath());
        String runtime = Tools.getRuntimeName(profile.javaDir);
        if (!Tools.isValidString(runtime)) runtime = LauncherPreferences.PREF_DEFAULT_RUNTIME;
        mProfileRuntime.setText(Tools.isValidString(runtime) ? runtime : getString(R.string.global_default));
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        updateProfileDetails();
    }

    @Override
    public void onDestroyView() {
        ProgressKeeper.removeTaskCountListener(mTaskCountListener);
        mVersionSpinner.setOnProfileChangedListener(null);
        mTaskStatus = null;
        mTaskCard = null;
        mProfileDirectory = null;
        mProfileRuntime = null;
        super.onDestroyView();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        // Avoid using custom installers to install a version for local or demo accounts.
        if (Tools.isLocalProfile(requireContext()) || Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
