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
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.dashboard.AuroraAccountController;
import net.kdt.pojavlaunch.dashboard.AuroraSegmentedHubDialog;
import net.kdt.pojavlaunch.dashboard.DashboardProgressController;
import net.kdt.pojavlaunch.dashboard.RoundedImageView;
import net.kdt.pojavlaunch.dashboard.WorldSaveScanner;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Abyssal Bioluminescent Ocean Glass (#02) Home Dashboard Fragment.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private RoundedImageView mHeroImage;
    private TextView mHeroBadge;
    private TextView mHeroTitle;
    private TextView mHeroSubtitle;
    private TextView mHeroVersionChip;

    private TextView mHardwareSummary;

    private View mRecentRow1;
    private RoundedImageView mRecentThumb1;
    private TextView mRecentTitle1;
    private TextView mRecentSub1;
    private ImageButton mRecentPlay1;

    private View mRecentRow2;
    private RoundedImageView mRecentThumb2;
    private TextView mRecentTitle2;
    private TextView mRecentSub2;
    private ImageButton mRecentPlay2;

    private DashboardProgressController mProgressController;

    private WorldSaveScanner.DashboardWorldItem mFeaturedItem;
    private WorldSaveScanner.DashboardWorldItem mRecentItem1;
    private WorldSaveScanner.DashboardWorldItem mRecentItem2;
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

        mHeroImage = view.findViewById(R.id.aurora_hero_image);
        mHeroBadge = view.findViewById(R.id.aurora_hero_badge);
        mHeroTitle = view.findViewById(R.id.aurora_hero_title);
        mHeroSubtitle = view.findViewById(R.id.aurora_hero_subtitle);
        mHeroVersionChip = view.findViewById(R.id.aurora_hero_version_chip);

        mHardwareSummary = view.findViewById(R.id.abyssal_hardware_summary);
        View statAccount = view.findViewById(R.id.abyssal_stat_account);
        View statJava = view.findViewById(R.id.abyssal_stat_java);
        View statRam = view.findViewById(R.id.abyssal_stat_ram);

        mRecentRow1 = view.findViewById(R.id.aurora_recent_row_1);
        mRecentThumb1 = view.findViewById(R.id.aurora_recent_thumb_1);
        mRecentTitle1 = view.findViewById(R.id.aurora_recent_title_1);
        mRecentSub1 = view.findViewById(R.id.aurora_recent_sub_1);
        mRecentPlay1 = view.findViewById(R.id.aurora_recent_play_1);

        mRecentRow2 = view.findViewById(R.id.aurora_recent_row_2);
        mRecentThumb2 = view.findViewById(R.id.aurora_recent_thumb_2);
        mRecentTitle2 = view.findViewById(R.id.aurora_recent_title_2);
        mRecentSub2 = view.findViewById(R.id.aurora_recent_sub_2);
        mRecentPlay2 = view.findViewById(R.id.aurora_recent_play_2);

        View playButton = view.findViewById(R.id.play_button);
        ImageButton moreBtn = view.findViewById(R.id.aurora_hero_more_btn);
        View viewAllBtn = view.findViewById(R.id.aurora_view_all_btn);
        View progressCard = view.findViewById(R.id.aurora_progress_card);

        playButton.setOnClickListener(v -> launchDashboardItem(mFeaturedItem));
        moreBtn.setOnClickListener(v -> showHeroQuickActionsDialog());
        mHeroVersionChip.setOnClickListener(v -> showProfileSelectorDialog());

        statAccount.setOnClickListener(v -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).openQuickAccountSheet();
            }
        });
        statJava.setOnClickListener(v -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).navigateToTab(LauncherActivity.TAB_JAVA, null);
            }
        });
        statRam.setOnClickListener(v -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).navigateToTab(LauncherActivity.TAB_JAVA, null);
            }
        });

        viewAllBtn.setOnClickListener(v -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).navigateToTab(LauncherActivity.TAB_WORLDS, null);
            }
        });

        mRecentRow1.setOnClickListener(v -> selectIntoHero(mRecentItem1));
        mRecentPlay1.setOnClickListener(v -> launchDashboardItem(mRecentItem1));

        mRecentRow2.setOnClickListener(v -> selectIntoHero(mRecentItem2));
        mRecentPlay2.setOnClickListener(v -> launchDashboardItem(mRecentItem2));

        progressCard.setOnClickListener(v -> triggerFileSyncOrStatus());

        mProgressController = new DashboardProgressController(progressCard);
        mProgressController.attach();

        if (getActivity() instanceof LauncherActivity) {
            mSearchFilter = ((LauncherActivity) getActivity()).getCurrentSearchQuery();
        }
        refreshDashboardData();
        refreshHardwareTelemetry();
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
        refreshHardwareTelemetry();
        if (mProgressController != null) {
            mProgressController.refreshIdleState();
        }
    }

    @Override
    public void onDestroyView() {
        if (mProgressController != null) {
            mProgressController.detach();
        }
        super.onDestroyView();
    }

    public void applySearchFilter(@Nullable String query) {
        mSearchFilter = query == null ? "" : query.trim();
        if (isAdded()) {
            refreshDashboardData();
        }
    }

    public void refreshHardwareTelemetry() {
        if (!isAdded() || mHardwareSummary == null) return;

        // 1. Player Status
        String playerStatus = "Sign In";
        if (getActivity() instanceof LauncherActivity) {
            AuroraAccountController ctrl = ((LauncherActivity) getActivity()).getAccountController();
            if (ctrl != null) {
                MinecraftAccount acc = ctrl.getSelectedAccount();
                if (acc != null && Tools.isValidString(acc.username)) {
                    playerStatus = acc.username + " Online";
                }
            }
        }

        // 2. Active OpenJDK Runtime
        String jdkLabel = resolveOpenJdkLabel();

        // 3. Allocated RAM
        int ramMb = LauncherPreferences.PREF_RAM_ALLOCATION;
        String ramLabel;
        if (ramMb >= 1024) {
            if (ramMb % 1024 == 0) {
                ramLabel = (ramMb / 1024) + " GB RAM";
            } else {
                ramLabel = String.format(Locale.US, "%.1f GB RAM", ramMb / 1024f);
            }
        } else {
            ramLabel = ramMb + " MB RAM";
        }

        mHardwareSummary.setText(playerStatus + " • " + jdkLabel + " • " + ramLabel);
    }

    @NonNull
    private String resolveOpenJdkLabel() {
        try {
            List<Runtime> runtimes = MultiRTUtils.getRuntimes();
            if (runtimes != null && !runtimes.isEmpty()) {
                String preferred = LauncherPreferences.PREF_DEFAULT_RUNTIME;
                WorldSaveScanner.ensureProfilesInitialized(requireContext());
                String key = LauncherPreferences.DEFAULT_PREF.getString(
                        LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                MinecraftProfile prof = LauncherProfiles.mainProfileJson != null
                        ? LauncherProfiles.mainProfileJson.profiles.get(key) : null;
                if (prof != null && Tools.isValidString(prof.javaDir)
                        && prof.javaDir.startsWith(Tools.LAUNCHERPROFILES_RTPREFIX)) {
                    preferred = prof.javaDir.substring(Tools.LAUNCHERPROFILES_RTPREFIX.length());
                }

                Runtime matched = null;
                int highestVer = 0;
                for (Runtime rt : runtimes) {
                    if (rt.javaVersion > highestVer) {
                        highestVer = rt.javaVersion;
                    }
                    if (Tools.isValidString(preferred) && preferred.equals(rt.name)) {
                        matched = rt;
                    }
                }
                if (matched != null && matched.javaVersion > 0) {
                    return "OpenJDK " + matched.javaVersion;
                }
                if (highestVer > 0) {
                    return "OpenJDK " + highestVer;
                }
            }
        } catch (Exception ignored) {
        }
        return "OpenJDK 21";
    }

    public void refreshDashboardData() {
        if (!isAdded()) return;
        List<WorldSaveScanner.DashboardWorldItem> all = WorldSaveScanner.scanAllItems(requireContext());
        if (all.isEmpty()) return;

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

        mFeaturedItem = q.isEmpty()
                ? WorldSaveScanner.resolveFeaturedItem(requireContext(), pool)
                : pool.get(0);
        if (mFeaturedItem == null) mFeaturedItem = pool.get(0);

        bindHero(mFeaturedItem);

        List<WorldSaveScanner.DashboardWorldItem> recents = new ArrayList<>();
        for (WorldSaveScanner.DashboardWorldItem candidate : pool) {
            if (!candidate.id.equals(mFeaturedItem.id)) {
                recents.add(candidate);
            }
        }
        if (recents.isEmpty()) {
            recents.add(mFeaturedItem);
        }

        mRecentItem1 = recents.get(0);
        mRecentItem2 = recents.size() > 1 ? recents.get(1) : null;

        bindRecentRow(mRecentRow1, mRecentThumb1, mRecentTitle1, mRecentSub1, mRecentItem1);
        bindRecentRow(mRecentRow2, mRecentThumb2, mRecentTitle2, mRecentSub2, mRecentItem2);
    }

    private void bindHero(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        String verLower = item.versionId.toLowerCase(Locale.ROOT);
        String loaderPrefix = "IRIS SHADERS";
        if (verLower.contains("fabric")) loaderPrefix = "FABRIC";
        else if (verLower.contains("quilt")) loaderPrefix = "QUILT";
        else if (verLower.contains("neoforge")) loaderPrefix = "NEOFORGE";
        else if (verLower.contains("forge")) loaderPrefix = "FORGE";
        else if (verLower.contains("optifine")) loaderPrefix = "OPTIFINE";

        String shortVer = item.versionId;
        if (shortVer.length() > 18) {
            shortVer = shortVer.substring(0, 18);
        }
        mHeroBadge.setText((loaderPrefix + " • " + shortVer).toUpperCase(Locale.ROOT));
        mHeroTitle.setText(item.title);
        mHeroSubtitle.setText(item.description);
        mHeroVersionChip.setText(item.versionId + " ▾");
        loadArtworkInto(mHeroImage, item);
    }

    private void bindRecentRow(@NonNull View row, @NonNull RoundedImageView thumb,
                               @NonNull TextView title, @NonNull TextView sub,
                               @Nullable WorldSaveScanner.DashboardWorldItem item) {
        if (item == null) {
            row.setVisibility(View.INVISIBLE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        title.setText(item.title);
        sub.setText(item.subtitle);
        loadArtworkInto(thumb, item);
    }

    private void loadArtworkInto(@NonNull ImageView target, @NonNull WorldSaveScanner.DashboardWorldItem item) {
        if (item.iconFile != null && item.iconFile.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(item.iconFile.getAbsolutePath());
            if (bmp != null) {
                target.setImageBitmap(bmp);
                return;
            }
        }
        target.setImageResource(item.fallbackArtRes);
    }

    private void selectIntoHero(@Nullable WorldSaveScanner.DashboardWorldItem item) {
        if (item == null || !isAdded()) return;
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
        refreshDashboardData();
        refreshHardwareTelemetry();
        if (mProgressController != null) {
            mProgressController.refreshIdleState();
        }
    }

    private void launchDashboardItem(@Nullable WorldSaveScanner.DashboardWorldItem item) {
        if (!isAdded()) return;
        if (item != null) {
            WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
            WorldSaveScanner.recordPlayed(requireContext(), item.id);
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).setPendingQuickPlayWorld(item.worldFolderName);
            }
        }
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void triggerFileSyncOrStatus() {
        if (ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Tools.isLocalProfile(requireContext())) {
            Toast.makeText(requireContext(),
                    "Sign in with a Microsoft account to download official game files, or tap PLAY for installed versions.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        WorldSaveScanner.ensureProfilesInitialized(requireContext());
        String key = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(key);
        if (prof == null || !Tools.isValidString(prof.lastVersionId)) return;

        final String normVer = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        if (MinecraftProfile.LATEST_RELEASE.equals(normVer) || MinecraftProfile.LATEST_SNAPSHOT.equals(normVer)) {
            Toast.makeText(requireContext(), "Fetching version manifest, try again in a moment...", Toast.LENGTH_SHORT).show();
            return;
        }
        JMinecraftVersionList.Version mcVer = AsyncMinecraftDownloader.getListedVersion(normVer);
        Toast.makeText(requireContext(), "Syncing files for Minecraft " + normVer + "...", Toast.LENGTH_SHORT).show();
        new MinecraftDownloader().start(
                requireActivity(),
                mcVer,
                normVer,
                new AsyncMinecraftDownloader.DoneListener() {
                    @Override
                    public void onDownloadDone() {
                        Tools.runOnUiThread(() -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Minecraft " + normVer + " files verified & ready!",
                                        Toast.LENGTH_SHORT).show();
                                if (mProgressController != null) {
                                    mProgressController.refreshIdleState();
                                }
                            }
                        });
                    }

                    @Override
                    public void onDownloadFailed(Throwable throwable) {
                        Tools.showErrorRemote(getString(R.string.mc_download_failed), throwable);
                    }
                }
        );
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
        final String activeFeaturedId = mFeaturedItem != null
                ? mFeaturedItem.id : WorldSaveScanner.getFeaturedId(requireContext());

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
                    () -> selectIntoHero(it)
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

    private void showProfileSelectorDialog() {
        String activeTitle = mFeaturedItem != null ? mFeaturedItem.title : getString(R.string.app_short_name);
        AuroraSegmentedHubDialog.showActionSheet(
                requireActivity(),
                activeTitle,
                "Switch World / Profile",
                resolveActiveAvatarBitmap(),
                dialog -> buildProfileSwitchCards()
        );
    }

    private void showHeroQuickActionsDialog() {
        String activeTitle = mFeaturedItem != null ? mFeaturedItem.title : getString(R.string.app_short_name);
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
