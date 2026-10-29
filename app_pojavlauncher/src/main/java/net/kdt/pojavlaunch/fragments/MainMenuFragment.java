package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.dashboard.AuroraAccountController;
import net.kdt.pojavlaunch.dashboard.AuroraPlayerSkinView;
import net.kdt.pojavlaunch.dashboard.AuroraSegmentedHubDialog;
import net.kdt.pojavlaunch.dashboard.DashboardProgressController;
import net.kdt.pojavlaunch.dashboard.WorldSaveScanner;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Your Space" home dashboard — quick actions, the active world card with a mint
 * Play bar, live background-task status and the player column on the right.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private TextView mQuickLibrarySub;

    private TextView mActiveWorldSub;
    private ImageView mActiveWorldIcon;
    private TextView mActiveWorldTitle;
    private TextView mActiveWorldMeta;

    private TextView mTasksStatus;
    private View mTasksCard;

    private AuroraPlayerSkinView mPlayerSkinView;
    private TextView mPlayerInstanceTitle;
    private TextView mPlayerInstanceMeta;

    private DashboardProgressController mTaskController;

    private WorldSaveScanner.DashboardWorldItem mActiveItem;
    private String mSearchFilter = "";

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String refreshedProfile = (String) ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        if (Tools.isValidString(refreshedProfile)
                && !ProfileEditorFragment.DELETED_PROFILE.equals(refreshedProfile)) {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, refreshedProfile)
                    .apply();
        }

        mQuickLibrarySub = view.findViewById(R.id.quick_library_sub);

        mActiveWorldSub = view.findViewById(R.id.active_world_sub);
        mActiveWorldIcon = view.findViewById(R.id.active_world_icon);
        mActiveWorldTitle = view.findViewById(R.id.active_world_title);
        mActiveWorldMeta = view.findViewById(R.id.active_world_meta);

        mTasksCard = view.findViewById(R.id.tasks_card);
        mTasksStatus = view.findViewById(R.id.tasks_status);

        mPlayerSkinView = view.findViewById(R.id.player_skin_view);
        mPlayerInstanceTitle = view.findViewById(R.id.player_instance_title);
        mPlayerInstanceMeta = view.findViewById(R.id.player_instance_meta);

        View playButton = view.findViewById(R.id.play_button);
        View playerPlayButton = view.findViewById(R.id.player_play_button);
        View playerInstancePill = view.findViewById(R.id.player_instance_pill);
        View activeWorldCard = view.findViewById(R.id.aurora_active_world_card);
        ImageButton hubBtn = view.findViewById(R.id.aurora_hero_more_btn);

        // ---- Quick actions ----
        view.findViewById(R.id.quick_create_card).setOnClickListener(v -> openCreateInstance());
        view.findViewById(R.id.quick_explore_card).setOnClickListener(v ->
                navigateToTab(LauncherActivity.TAB_MARKETPLACE));
        view.findViewById(R.id.quick_library_card).setOnClickListener(v ->
                navigateToTab(LauncherActivity.TAB_WORLDS));

        // ---- Active world card ----
        playButton.setOnClickListener(v -> launchActiveWorld());
        playerPlayButton.setOnClickListener(v -> launchActiveWorld());
        hubBtn.setOnClickListener(v -> showHeroQuickActionsDialog());
        activeWorldCard.setOnClickListener(v -> showProfileSelectorDialog());
        playerInstancePill.setOnClickListener(v -> showProfileSelectorDialog());

        // ---- Player column ----
        view.findViewById(R.id.btn_add_account).setOnClickListener(v -> openAccountChooser());
        if (mPlayerSkinView != null) {
            mPlayerSkinView.setOnClickListener(v -> openAccountChooser());
        }

        // ---- Tasks card ----
        mTasksCard.setOnClickListener(v -> {
            if (ProgressKeeper.hasOngoingTasks()) {
                Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), R.string.aurora_tasks_idle, Toast.LENGTH_SHORT).show();
            }
        });

        mTaskController = new DashboardProgressController(mTasksCard);
        mTaskController.attach();

        if (getActivity() instanceof LauncherActivity) {
            mSearchFilter = ((LauncherActivity) getActivity()).getCurrentSearchQuery();
        }
        refreshDashboardData();
        refreshAccountArtwork();
    }

    @Override
    public void onResume() {
        super.onResume();
        String refreshedProfile = (String) ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        if (Tools.isValidString(refreshedProfile)
                && !ProfileEditorFragment.DELETED_PROFILE.equals(refreshedProfile)) {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, refreshedProfile)
                    .apply();
        }
        refreshDashboardData();
        refreshAccountArtwork();
        if (mTaskController != null) {
            mTaskController.refreshIdleState();
        }
    }

    @Override
    public void onDestroyView() {
        if (mTaskController != null) {
            mTaskController.detach();
        }
        super.onDestroyView();
    }

    public void applySearchFilter(@Nullable String query) {
        mSearchFilter = query == null ? "" : query.trim();
        if (isAdded()) {
            refreshDashboardData();
        }
    }

    private void navigateToTab(int tab) {
        if (getActivity() instanceof LauncherActivity) {
            ((LauncherActivity) getActivity()).navigateToTab(tab, null);
        }
    }

    private void openCreateInstance() {
        if (!isAdded()) return;
        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                ProfileTypeSelectFragment.TAG, null);
    }

    public void refreshAccountArtwork() {
        if (!isAdded()) return;
        Bitmap face = null;
        boolean online = false;
        if (getActivity() instanceof LauncherActivity) {
            AuroraAccountController ctrl = ((LauncherActivity) getActivity()).getAccountController();
            if (ctrl != null) {
                MinecraftAccount acc = ctrl.getSelectedAccount();
                if (acc != null) {
                    online = true;
                    face = acc.getSkinFace();
                }
            }
        }
        if (mPlayerSkinView != null) {
            mPlayerSkinView.setFaceBitmap(face);
            mPlayerSkinView.setOnline(online);
        }
    }

    public void refreshDashboardData() {
        if (!isAdded()) return;

        // ---- Library / instance counter ----
        int instanceCount = 0;
        if (LauncherProfiles.mainProfileJson != null
                && LauncherProfiles.mainProfileJson.profiles != null) {
            instanceCount = LauncherProfiles.mainProfileJson.profiles.size();
        }
        if (mQuickLibrarySub != null) {
            mQuickLibrarySub.setText(getString(R.string.aurora_quick_library_sub, instanceCount));
        }

        List<WorldSaveScanner.DashboardWorldItem> all = WorldSaveScanner.scanAllItems(requireContext());
        if (all.isEmpty()) {
            if (mActiveWorldTitle != null) {
                mActiveWorldTitle.setText(getString(R.string.aurora_default_world_title));
            }
            return;
        }

        List<WorldSaveScanner.DashboardWorldItem> pool = new ArrayList<>();
        String q = mSearchFilter.toLowerCase(Locale.ROOT);
        for (WorldSaveScanner.DashboardWorldItem item : all) {
            if (q.isEmpty()
                    || item.title.toLowerCase(Locale.ROOT).contains(q)
                    || item.versionId.toLowerCase(Locale.ROOT).contains(q)) {
                pool.add(item);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(all);
        }

        mActiveItem = q.isEmpty()
                ? WorldSaveScanner.resolveFeaturedItem(requireContext(), pool)
                : pool.get(0);
        if (mActiveItem == null) mActiveItem = pool.get(0);

        bindActiveWorld(mActiveItem);
    }

    private void bindActiveWorld(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        String versionLabel = Tools.isValidString(item.versionId)
                ? item.versionId : getString(R.string.aurora_default_world_title);
        String loader = resolveLoaderName(item.versionId);
        String meta = loader + " · Minecraft " + versionLabel;

        if (mActiveWorldTitle != null) {
            mActiveWorldTitle.setText(versionLabel);
        }
        if (mActiveWorldMeta != null) {
            mActiveWorldMeta.setText(meta);
        }
        if (mActiveWorldSub != null) {
            mActiveWorldSub.setText(getString(R.string.aurora_active_world_sub) + " · " + item.title);
        }
        if (mPlayerInstanceTitle != null) {
            mPlayerInstanceTitle.setText(versionLabel);
        }
        if (mPlayerInstanceMeta != null) {
            mPlayerInstanceMeta.setText("Minecraft " + versionLabel);
        }
        loadActiveWorldIcon(item);
    }

    private void loadActiveWorldIcon(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        if (mActiveWorldIcon == null) return;
        if (item.iconFile != null && item.iconFile.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(item.iconFile.getAbsolutePath());
            if (bmp != null) {
                mActiveWorldIcon.setImageBitmap(bmp);
                return;
            }
        }
        mActiveWorldIcon.setImageResource(R.drawable.ic_grass_block);
    }

    @NonNull
    private String resolveLoaderName(@Nullable String versionId) {
        if (versionId == null) return "Vanilla";
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge")) return "NeoForge";
        if (lower.contains("fabric")) return "Fabric";
        if (lower.contains("quilt")) return "Quilt";
        if (lower.contains("forge")) return "Forge";
        if (lower.contains("optifine")) return "OptiFine";
        return "Vanilla";
    }

    private void launchActiveWorld() {
        if (!isAdded() || mActiveItem == null) {
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
            return;
        }
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), mActiveItem);
        WorldSaveScanner.recordPlayed(requireContext(), mActiveItem.id);
        if (getActivity() instanceof LauncherActivity) {
            ((LauncherActivity) getActivity()).setPendingQuickPlayWorld(mActiveItem.worldFolderName);
        }
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void openAccountChooser() {
        if (!isAdded()) return;
        if (getActivity() instanceof LauncherActivity) {
            ((LauncherActivity) getActivity()).openQuickAccountSheet();
        }
    }

    @Nullable
    private Bitmap resolveActiveAvatarBitmap() {
        if (getActivity() instanceof LauncherActivity) {
            AuroraAccountController ctrl = ((LauncherActivity) getActivity()).getAccountController();
            if (ctrl != null) {
                MinecraftAccount acc = ctrl.getSelectedAccount();
                if (acc != null) {
                    return acc.getSkinFace();
                }
            }
        }
        return null;
    }

    @NonNull
    private List<AuroraSegmentedHubDialog.HubCardItem> buildProfileSwitchCards() {
        final List<WorldSaveScanner.DashboardWorldItem> items = WorldSaveScanner.scanAllItems(requireContext());
        final String activeProfileKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        final String activeFeaturedId = mActiveItem != null
                ? mActiveItem.id : WorldSaveScanner.getFeaturedId(requireContext());

        List<AuroraSegmentedHubDialog.HubCardItem> cards = new ArrayList<>();
        for (final WorldSaveScanner.DashboardWorldItem it : items) {
            Bitmap thumb = null;
            if (it.iconFile != null && it.iconFile.exists()) {
                thumb = BitmapFactory.decodeFile(it.iconFile.getAbsolutePath());
            }
            boolean isCurrent = (!activeFeaturedId.isEmpty() && activeFeaturedId.equals(it.id))
                    || (activeFeaturedId.isEmpty() && !it.isSavedWorld && it.profileKey.equals(activeProfileKey));
            String kind = it.isSavedWorld ? "Saved World" : "Instance";
            String subtitle = kind + " • Minecraft " + it.versionId + " • " + it.subtitle;
            cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                    it.isSavedWorld ? R.drawable.ic_hub_game_folder : R.drawable.ic_hub_switch_profile,
                    thumb,
                    null,
                    it.title,
                    subtitle,
                    isCurrent ? "Active" : "Select",
                    isCurrent,
                    true,
                    () -> selectActiveWorld(it)
            ));
        }

        cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                R.drawable.ic_hub_create_profile,
                "Create New Profile",
                "Install Vanilla, Fabric, Forge, Quilt or OptiFine",
                "Create",
                () -> Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null)
        ));
        return cards;
    }

    private void selectActiveWorld(@Nullable WorldSaveScanner.DashboardWorldItem item) {
        if (item == null || !isAdded()) return;
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
        refreshDashboardData();
        if (mTaskController != null) {
            mTaskController.refreshIdleState();
        }
    }

    private void showProfileSelectorDialog() {
        if (!isAdded()) return;
        String activeTitle = mActiveItem != null
                ? mActiveItem.title : getString(R.string.aurora_default_world_title);
        AuroraSegmentedHubDialog.showActionSheet(
                requireActivity(),
                activeTitle,
                "Switch World / Profile",
                resolveActiveAvatarBitmap(),
                dialog -> buildProfileSwitchCards()
        );
    }

    private void showHeroQuickActionsDialog() {
        if (!isAdded()) return;
        String activeTitle = mActiveItem != null
                ? mActiveItem.title : getString(R.string.aurora_default_world_title);
        AuroraSegmentedHubDialog.showControlHub(
                requireActivity(),
                activeTitle,
                resolveActiveAvatarBitmap(),
                new AuroraSegmentedHubDialog.ControlHubActions() {
                    @NonNull
                    @Override
                    public List<AuroraSegmentedHubDialog.HubCardItem> buildSwitchProfileItems(
                            @NonNull android.app.Dialog dialog) {
                        return buildProfileSwitchCards();
                    }

                    @Override
                    public void onEditActiveProfile() {
                        Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                                ProfileEditorFragment.TAG, null);
                    }

                    @Override
                    public void onCreateNewProfile() {
                        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                                ProfileTypeSelectFragment.TAG, null);
                    }

                    @Override
                    public void onOpenTouchControls() {
                        startActivity(new Intent(requireContext(), CustomControlsActivity.class));
                    }

                    @Override
                    public void onOpenGamepadRemapper() {
                        Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                                GamepadMapperFragment.TAG, null);
                    }

                    @Override
                    public void onExecuteModInstaller(boolean customJvmArgs) {
                        runInstallerWithConfirmation(customJvmArgs);
                    }

                    @Override
                    public void onOpenGameDirectory() {
                        openGameFolder();
                    }

                    @Override
                    public void onShareLatestLog() {
                        shareLog(requireContext());
                    }

                    @Override
                    public void onOpenJavaSettings() {
                        Tools.swapFragment(requireActivity(), LauncherPreferenceJavaFragment.class,
                                LauncherActivity.JAVA_FRAGMENT_TAG, null);
                    }

                    @Override
                    public void onOpenLauncherSettings() {
                        Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class,
                                LauncherActivity.SETTING_FRAGMENT_TAG, null);
                    }
                }
        );
    }

    private void openGameFolder() {
        Tools.switchDemo(Tools.isDemoProfile(requireContext()));
        if (Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        openPath(requireContext(), getCurrentProfileDirectory(), false);
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

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (Tools.isLocalProfile(requireContext()) || Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        if (ProgressKeeper.getTaskCount() == 0) {
            Tools.installMod(requireActivity(), isCustomArgs);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }
}
