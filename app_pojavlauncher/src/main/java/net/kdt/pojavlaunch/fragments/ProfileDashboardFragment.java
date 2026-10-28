package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.dashboard.AuroraAccountController;
import net.kdt.pojavlaunch.dashboard.RoundedImageView;
import net.kdt.pojavlaunch.dashboard.WorldSaveScanner;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.List;

/**
 * Aurora Glass "Profile" Tab — manages player accounts (Microsoft & Local)
 * and provides direct access to all launcher preferences, Java runtimes, custom controls, and logs.
 */
public class ProfileDashboardFragment extends Fragment implements AuroraAccountController.AccountStateListener {
    public static final String TAG = "ProfileDashboardFragment";

    private RoundedImageView mActiveAvatar;
    private TextView mActiveUsername;
    private TextView mActiveType;
    private TextView mSystemSummary;
    private LinearLayout mAccountsList;

    public ProfileDashboardFragment() {
        super(R.layout.fragment_profile_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mActiveAvatar = view.findViewById(R.id.profile_active_avatar);
        mActiveUsername = view.findViewById(R.id.profile_active_username);
        mActiveType = view.findViewById(R.id.profile_active_type);
        mSystemSummary = view.findViewById(R.id.profile_system_summary);
        mAccountsList = view.findViewById(R.id.profile_accounts_list);

        view.findViewById(R.id.btn_profile_add_microsoft).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class,
                        MicrosoftLoginFragment.TAG, null));

        view.findViewById(R.id.btn_profile_add_local).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LocalLoginFragment.class,
                        LocalLoginFragment.TAG, null));

        view.findViewById(R.id.btn_tool_launcher_settings).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class,
                        LauncherActivity.SETTING_FRAGMENT_TAG, null));

        view.findViewById(R.id.btn_tool_runtime_manager).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceJavaFragment.class,
                        "JAVA_PREF_FRAGMENT", null));

        view.findViewById(R.id.btn_tool_custom_controls).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomControlsActivity.class)));

        view.findViewById(R.id.btn_tool_gamepad_mapper).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                        GamepadMapperFragment.TAG, null));

        View installJarBtn = view.findViewById(R.id.btn_tool_install_jar);
        installJarBtn.setOnClickListener(v -> runInstaller(false));
        installJarBtn.setOnLongClickListener(v -> {
            runInstaller(true);
            return true;
        });

        view.findViewById(R.id.btn_tool_open_dir).setOnClickListener(v -> {
            Tools.switchDemo(Tools.isDemoProfile(v.getContext()));
            if (Tools.isDemoProfile(v.getContext())) {
                Toast.makeText(v.getContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), getCurrentProfileDirectory(), false);
        });

        view.findViewById(R.id.btn_tool_share_logs).setOnClickListener(v -> shareLog(requireContext()));

        mSystemSummary.setText("Allocated Memory: " + LauncherPreferences.PREF_RAM_ALLOCATION
                + " MB • Renderer: " + LauncherPreferences.PREF_RENDERER);

        AuroraAccountController ctrl = getAccountController();
        if (ctrl != null) {
            ctrl.addListener(this);
        }
    }

    @Override
    public void onDestroyView() {
        AuroraAccountController ctrl = getAccountController();
        if (ctrl != null) {
            ctrl.removeListener(this);
        }
        super.onDestroyView();
    }

    @Nullable
    private AuroraAccountController getAccountController() {
        if (getActivity() instanceof LauncherActivity) {
            return ((LauncherActivity) getActivity()).getAccountController();
        }
        return null;
    }

    @Override
    public void onAccountChanged(@Nullable MinecraftAccount account, @NonNull List<String> allAccountNames) {
        if (!isAdded()) return;
        if (account == null) {
            mActiveUsername.setText("Not Signed In");
            mActiveType.setText("Add a Microsoft or Local account below");
            mActiveAvatar.setImageResource(R.drawable.ic_voxel_avatar_default);
        } else {
            mActiveUsername.setText(account.username);
            String typeLabel = account.isMicrosoft ? "Microsoft Account • Online Ready"
                    : (account.isDemo() ? "Demo Account" : "Local Offline Account • Ready");
            mActiveType.setText(typeLabel);
            Bitmap face = account.getSkinFace();
            if (face != null) {
                mActiveAvatar.setImageBitmap(face);
            } else {
                mActiveAvatar.setImageResource(R.drawable.ic_voxel_avatar_default);
            }
        }

        mAccountsList.removeAllViews();
        int dp8 = dp(8);
        int dp10 = dp(10);
        int dp6 = dp(6);

        if (allAccountNames.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No saved accounts yet. Tap + Microsoft Login or + Local Account.");
            empty.setTextColor(getResources().getColor(R.color.aurora_text_muted));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            empty.setPadding(dp6, dp8, dp6, dp8);
            mAccountsList.addView(empty);
            return;
        }

        for (final String name : allAccountNames) {
            boolean isSelected = account != null && name.equals(account.username);
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(isSelected ? R.drawable.bg_aurora_nav_selector : R.drawable.bg_aurora_sub_row);
            row.setSelected(isSelected);
            row.setPadding(dp10, dp8, dp10, dp8);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp6;
            row.setLayoutParams(lp);

            TextView title = new TextView(requireContext());
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            title.setText(isSelected ? "●  " + name + " (Active)" : name);
            title.setTextColor(getResources().getColor(R.color.aurora_text_white));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

            TextView removeBtn = new TextView(requireContext());
            removeBtn.setText("Remove");
            removeBtn.setTextColor(getResources().getColor(R.color.aurora_cyan_link));
            removeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            removeBtn.setPadding(dp8, dp6 / 2, dp6, dp6 / 2);
            removeBtn.setOnClickListener(v -> confirmRemoveAccount(name));

            row.addView(title);
            row.addView(removeBtn);
            row.setOnClickListener(v -> {
                AuroraAccountController ctrl = getAccountController();
                if (ctrl != null) ctrl.selectAccount(name);
            });
            mAccountsList.addView(row);
        }
    }

    @Override
    public void onLoginProgress(int step, int maxSteps) {
        if (!isAdded()) return;
        mActiveType.setText("Signing in... (" + step + "/" + maxSteps + ")");
    }

    private void confirmRemoveAccount(@NonNull final String username) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.warning_remove_account)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (d, w) -> {
                    AuroraAccountController ctrl = getAccountController();
                    if (ctrl != null) ctrl.removeAccount(username);
                })
                .show();
    }

    private void runInstaller(boolean customArgs) {
        if (Tools.isLocalProfile(requireContext()) || Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        if (ProgressKeeper.getTaskCount() == 0) {
            Tools.installMod(requireActivity(), customArgs);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }

    private File getCurrentProfileDirectory() {
        WorldSaveScanner.ensureProfilesInitialized(requireContext());
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
