package net.kdt.pojavlaunch;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.dashboard.AuroraAccountController;
import net.kdt.pojavlaunch.dashboard.AuroraSegmentedHubDialog;
import net.kdt.pojavlaunch.dashboard.RoundedImageView;
import net.kdt.pojavlaunch.dashboard.WorldSaveScanner;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.ProfileDashboardFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.fragments.InstanceManagerFragment;
import net.kdt.pojavlaunch.fragments.WorldsFragment;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.ModloaderInstallTracker;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.utils.interfaces.SimpleTextWatcher;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class LauncherActivity extends BaseActivity implements AuroraAccountController.AccountStateListener {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";
    public static final String JAVA_FRAGMENT_TAG = "JAVA_PREF_FRAGMENT";

    public static final int TAB_HOME = 0;
    public static final int TAB_WORLDS = 1;
    public static final int TAB_MARKETPLACE = 2;
    public static final int TAB_MODS = TAB_MARKETPLACE;
    public static final int TAB_JAVA = 3;
    public static final int TAB_PROFILE = 4;
    public static final int TAB_SETTINGS = TAB_PROFILE;

    public final ActivityResultLauncher<Object> modInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), data -> {
                if (data != null) Tools.launchModInstaller(this, data);
            });

    public final ActivityResultLauncher<Object> worldImportLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("zip"), this::handleImportedWorldZip);

    private AuroraAccountController mAccountController;
    private FragmentContainerView mFragmentView;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;

    private View mSafeRoot;
    private View mBackChip;
    private EditText mSearchInput;
    private ImageButton mSearchClear;
    private View mAvatarContainer;
    private RoundedImageView mAvatarImage;
    private View mAvatarStatusDot;

    private View mTopBar;
    private View mSearchContainer;
    private View mNavHome, mNavCreate, mNavLibrary, mNavExplore, mNavRuntime, mNavInstall, mNavSettings, mNavAccounts;
    private ImageView mNavHomeIcon, mNavCreateIcon, mNavLibraryIcon, mNavExploreIcon;
    private ImageView mNavRuntimeIcon, mNavInstallIcon, mNavSettingsIcon, mNavAccountsIcon;

    private String mCurrentSearchQuery = "";
    @Nullable private String mPendingQuickPlayWorld = null;

    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    syncShellForFragment(f);
                }
            };

    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if (value.equals("true")) onBackPressed();
        return false;
    };

    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        Fragment current = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if (current instanceof SelectAuthFragment || current instanceof MicrosoftLoginFragment) {
            return false;
        }
        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        attemptGameLaunch();
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        if (taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
        Tools.runOnUiThread(this::syncGlobalProgressVisibility);
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return true;
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WorldSaveScanner.ensureProfilesInitialized(this);
        setContentView(R.layout.activity_pojav_launcher);

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() < 1) {
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT")
                    .commit();
        }

        IconCacheJanitor.runJanitor();
        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if (!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if (runnable != null) runnable.run();
                    }
                }
        );

        getWindow().setBackgroundDrawable(null);
        bindViews();
        setupCutoutSafeInsets();
        setupHeaderAndNavigation();

        mAccountController = new AuroraAccountController(this);
        mAccountController.addListener(this);

        checkNotificationPermission();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(mProgressLayout);

        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions), false);

        mInstallTracker = new ModloaderInstallTracker(this);

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
    }

    private void setupCutoutSafeInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        final int basePadH = dp(16);
        final int basePadV = dp(10);
        mSafeRoot.setOnApplyWindowInsetsListener((View v, WindowInsets windowInsets) -> {
            DisplayCutout cutout = windowInsets.getDisplayCutout();
            if (cutout != null) {
                int left = Math.max(basePadH, cutout.getSafeInsetLeft() + dp(8));
                int right = Math.max(basePadH, cutout.getSafeInsetRight() + dp(8));
                int top = Math.max(basePadV, cutout.getSafeInsetTop() + dp(6));
                int bottom = Math.max(basePadV, cutout.getSafeInsetBottom() + dp(6));
                v.setPadding(left, top, right, bottom);
            }
            return windowInsets;
        });
    }

    private void setupHeaderAndNavigation() {
        findViewById(R.id.aurora_brand_logo).setOnClickListener(v -> navigateToTab(TAB_HOME, null));
        mBackChip.setOnClickListener(v -> onBackPressed());

        mNavHome.setOnClickListener(v -> navigateToTab(TAB_HOME, null));
        mNavCreate.setOnClickListener(v -> openCreateInstance());
        mNavLibrary.setOnClickListener(v -> navigateToTab(TAB_WORLDS, null));
        mNavExplore.setOnClickListener(v -> navigateToTab(TAB_MARKETPLACE, null));
        mNavRuntime.setOnClickListener(v -> navigateToTab(TAB_JAVA, null));
        mNavInstall.setOnClickListener(v -> openModInstaller());
        mNavSettings.setOnClickListener(v -> openLauncherSettings());
        mNavAccounts.setOnClickListener(v -> navigateToTab(TAB_PROFILE, null));

        mAvatarContainer.setOnClickListener(v -> openQuickAccountSheet());

        mSearchClear.setOnClickListener(v -> {
            mSearchInput.setText("");
            hideKeyboard(mSearchInput);
            mSearchInput.clearFocus();
        });

        mSearchInput.addTextChangedListener((SimpleTextWatcher) (Editable s) -> {
            mCurrentSearchQuery = s == null ? "" : s.toString().trim();
            mSearchClear.setVisibility(mCurrentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
            Fragment active = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
            if (active instanceof MainMenuFragment) {
                ((MainMenuFragment) active).applySearchFilter(mCurrentSearchQuery);
            } else if (active instanceof InstanceManagerFragment) {
                ((InstanceManagerFragment) active).applySearchQuery(mCurrentSearchQuery);
            } else if (active instanceof WorldsFragment) {
                ((WorldsFragment) active).applySearchQuery(mCurrentSearchQuery);
            }
        });

        mSearchInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard(mSearchInput);
                mSearchInput.clearFocus();
                Fragment active = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
                if (active instanceof SearchModFragment) {
                    ((SearchModFragment) active).applyExternalSearchQuery(mCurrentSearchQuery);
                } else {
                    navigateToTab(TAB_WORLDS, mCurrentSearchQuery);
                }
                return true;
            }
            return false;
        });

        updateNavSelection(TAB_HOME);
    }

    private void openCreateInstance() {
        Tools.backToMainMenu(this);
        Tools.swapFragment(this, ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null);
    }

    private void openLauncherSettings() {
        Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
    }

    private void openModInstaller() {
        if (ProgressKeeper.getTaskCount() > 0) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        Tools.installMod(this, false);
    }

    public void navigateToTab(int tabIndex, @Nullable String optionalQuery) {
        if (optionalQuery != null) {
            mCurrentSearchQuery = optionalQuery.trim();
            if (!mCurrentSearchQuery.equals(mSearchInput.getText().toString().trim())) {
                mSearchInput.setText(mCurrentSearchQuery);
            }
        }
        Tools.backToMainMenu(this);
        getSupportFragmentManager().executePendingTransactions();

        switch (tabIndex) {
            case TAB_HOME: {
                updateNavSelection(TAB_HOME);
                Fragment root = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
                if (root instanceof MainMenuFragment) {
                    ((MainMenuFragment) root).applySearchFilter(mCurrentSearchQuery);
                }
                break;
            }
            case TAB_WORLDS:
                updateNavSelection(TAB_WORLDS);
                Tools.swapFragment(this, InstanceManagerFragment.class, InstanceManagerFragment.TAG, null);
                break;
            case TAB_MARKETPLACE:
                updateNavSelection(TAB_MARKETPLACE);
                Tools.swapFragment(this, SearchModFragment.class, SearchModFragment.TAG, null);
                break;
            case TAB_JAVA:
                updateNavSelection(TAB_JAVA);
                Tools.swapFragment(this, LauncherPreferenceJavaFragment.class, JAVA_FRAGMENT_TAG, null);
                break;
            case TAB_PROFILE:
                updateNavSelection(TAB_PROFILE);
                Tools.swapFragment(this, ProfileDashboardFragment.class, ProfileDashboardFragment.TAG, null);
                break;
        }
    }

    private void syncShellForFragment(@NonNull Fragment f) {
        boolean isHome = f instanceof MainMenuFragment;
        boolean ownsOwnHeader = f instanceof InstanceManagerFragment;
        boolean wantsSearch = f instanceof WorldsFragment;
        configureTopBar(!isHome && !ownsOwnHeader, wantsSearch);

        if (isHome) {
            updateNavSelection(TAB_HOME);
            mBackChip.setVisibility(View.GONE);
        } else if (f instanceof InstanceManagerFragment || f instanceof WorldsFragment) {
            updateNavSelection(TAB_WORLDS);
            mBackChip.setVisibility(View.GONE);
        } else if (f instanceof SearchModFragment) {
            updateNavSelection(TAB_MARKETPLACE);
            mBackChip.setVisibility(View.GONE);
        } else if (f instanceof LauncherPreferenceJavaFragment) {
            updateNavSelection(TAB_JAVA);
            mBackChip.setVisibility(View.VISIBLE);
        } else if (f instanceof LauncherPreferenceFragment) {
            updateNavSelection(-1);
            highlightOnly(mNavSettings, mNavSettingsIcon);
            mBackChip.setVisibility(View.VISIBLE);
        } else if (f instanceof ProfileDashboardFragment) {
            updateNavSelection(TAB_PROFILE);
            mBackChip.setVisibility(View.GONE);
        } else if (f instanceof SelectAuthFragment || f instanceof MicrosoftLoginFragment) {
            updateNavSelection(TAB_PROFILE);
            mBackChip.setVisibility(View.VISIBLE);
        } else {
            mBackChip.setVisibility(View.VISIBLE);
        }
        syncGlobalProgressVisibility();
    }

    /** Shows / hides the utility bar, and toggles the search field inside it. */
    private void configureTopBar(boolean visible, boolean showSearch) {
        if (mTopBar != null) {
            mTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (mSearchContainer != null) {
            mSearchContainer.setVisibility(showSearch ? View.VISIBLE : View.GONE);
        }
    }

    private void syncGlobalProgressVisibility() {
        if (mProgressLayout == null || mFragmentView == null) return;
        Fragment f = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if (f instanceof MainMenuFragment) {
            mProgressLayout.setVisibility(View.GONE);
        } else {
            mProgressLayout.setVisibility(mProgressLayout.hasProcesses() ? View.VISIBLE : View.GONE);
        }
    }

    private void updateNavSelection(int activeTab) {
        applyNavItemState(mNavHome, mNavHomeIcon, activeTab == TAB_HOME);
        applyNavItemState(mNavLibrary, mNavLibraryIcon, activeTab == TAB_WORLDS);
        applyNavItemState(mNavExplore, mNavExploreIcon, activeTab == TAB_MARKETPLACE);
        applyNavItemState(mNavRuntime, mNavRuntimeIcon, activeTab == TAB_JAVA);
        applyNavItemState(mNavAccounts, mNavAccountsIcon, activeTab == TAB_PROFILE);
        applyNavItemState(mNavCreate, mNavCreateIcon, false);
        applyNavItemState(mNavInstall, mNavInstallIcon, false);
        if (activeTab != -1) {
            applyNavItemState(mNavSettings, mNavSettingsIcon, false);
        }
    }

    private void highlightOnly(View container, ImageView icon) {
        applyNavItemState(mNavHome, mNavHomeIcon, false);
        applyNavItemState(mNavLibrary, mNavLibraryIcon, false);
        applyNavItemState(mNavExplore, mNavExploreIcon, false);
        applyNavItemState(mNavRuntime, mNavRuntimeIcon, false);
        applyNavItemState(mNavAccounts, mNavAccountsIcon, false);
        applyNavItemState(container, icon, true);
    }

    private void applyNavItemState(View container, ImageView icon, boolean active) {
        if (container != null) {
            container.setSelected(active);
        }
        if (icon != null) {
            icon.setColorFilter(ContextCompat.getColor(this,
                    active ? R.color.aurora_mint_ink : R.color.aurora_text_white));
        }
    }

    public void openQuickAccountSheet() {
        if (mAccountController == null) return;
        final List<String> saved = mAccountController.getSavedAccountNames();
        final MinecraftAccount active = mAccountController.getSelectedAccount();
        if (saved.isEmpty()) {
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return;
        }

        String badgeLabel = active != null ? active.username : "Accounts";
        Bitmap badgeAvatar = active != null ? active.getSkinFace() : null;

        AuroraSegmentedHubDialog.showActionSheet(
                this,
                badgeLabel,
                "Player Accounts",
                badgeAvatar,
                dialog -> {
                    List<AuroraSegmentedHubDialog.HubCardItem> cards = new ArrayList<>();
                    for (final String accName : saved) {
                        MinecraftAccount loaded = MinecraftAccount.load(accName);
                        Bitmap skin = loaded != null ? loaded.getSkinFace() : null;
                        boolean isCur = active != null && accName.equals(active.username);
                        String typeDesc = (loaded != null && loaded.isMicrosoft)
                                ? "Microsoft Xbox Account" : "Local Offline Account";
                        cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                                R.drawable.ic_voxel_avatar_default,
                                skin,
                                null,
                                accName,
                                isCur ? typeDesc + " • Currently Active" : typeDesc,
                                isCur ? "Active" : "Switch",
                                isCur,
                                true,
                                () -> mAccountController.selectAccount(accName)
                        ));
                    }
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_create_profile,
                            "Add Account",
                            "Sign in with Microsoft or create a Local offline player",
                            "Add",
                            () -> ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true)
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_launcher_settings,
                            "Settings & Accounts Hub",
                            "Manage accounts, Java runtimes, controls & preferences",
                            "Open",
                            () -> navigateToTab(TAB_PROFILE, null)
                    ));
                    return cards;
                }
        );
    }

    private void attemptGameLaunch() {
        if (mProgressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }

        WorldSaveScanner.ensureProfilesInitialized(this);
        String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        if (LauncherProfiles.mainProfileJson == null
                || !LauncherProfiles.mainProfileJson.profiles.containsKey(selectedProfile)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return;
        }
        final MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return;
        }

        final MinecraftAccount selectedAccount = mAccountController != null
                ? mAccountController.getSelectedAccount() : null;
        if (selectedAccount == null) {
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return;
        }

        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        if (MinecraftProfile.LATEST_RELEASE.equals(normalizedVersionId)
                || MinecraftProfile.LATEST_SNAPSHOT.equals(normalizedVersionId)) {
            String installedFallback = findFirstInstalledVersion();
            if (!Tools.isOnline(this) && installedFallback != null) {
                startVersionDownloadAndLaunch(selectedAccount, installedFallback);
                return;
            }
            Toast.makeText(this, R.string.newdl_starting, Toast.LENGTH_SHORT).show();
            new AsyncVersionList().getVersionList(versions -> {
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
                Tools.runOnUiThread(() -> {
                    String resolved = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
                    if (MinecraftProfile.LATEST_RELEASE.equals(resolved)
                            || MinecraftProfile.LATEST_SNAPSHOT.equals(resolved)) {
                        String fallback = findFirstInstalledVersion();
                        if (fallback != null) {
                            startVersionDownloadAndLaunch(selectedAccount, fallback);
                        } else {
                            Toast.makeText(this, R.string.modloader_dl_failed_to_load_list, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        startVersionDownloadAndLaunch(selectedAccount, resolved);
                    }
                });
            }, false);
            return;
        }

        startVersionDownloadAndLaunch(selectedAccount, normalizedVersionId);
    }

    private void startVersionDownloadAndLaunch(@NonNull MinecraftAccount account, @NonNull String normalizedVersionId) {
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);

        if (account.isDemo()) {
            boolean isOlderThan13 = true;
            if (mcVersion != null) {
                try {
                    isOlderThan13 = DateUtils.dateBefore(DateUtils.parseReleaseDate(mcVersion.releaseTime), 2012, 6, 22);
                } catch (ParseException ignored) {
                }
            }
            if (isOlderThan13) {
                Toast.makeText(this, R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }
        }

        String quickPlayWorld = mPendingQuickPlayWorld;
        mPendingQuickPlayWorld = null;

        new MinecraftDownloader().start(
                this,
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId, quickPlayWorld)
        );
    }

    @Nullable
    private String findFirstInstalledVersion() {
        File versionsDir = new File(Tools.DIR_HOME_VERSION);
        File[] children = versionsDir.listFiles();
        if (children == null) return null;
        for (File dir : children) {
            if (dir.isDirectory() && new File(dir, dir.getName() + ".json").exists()) {
                return dir.getName();
            }
        }
        return null;
    }

    public void launchWorldZipImport() {
        if (Tools.isDemoProfile(this)) {
            Toast.makeText(this, R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        worldImportLauncher.launch(null);
    }

    private void handleImportedWorldZip(@Nullable Uri uri) {
        if (uri == null) return;
        WorldSaveScanner.ensureProfilesInitialized(this);
        String key = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(key);
        final File savesDir = new File(prof != null ? Tools.getGameDirPath(prof) : new File(Tools.DIR_GAME_NEW), "saves");

        ProgressLayout.setProgress(ProgressLayout.EXTRACT_SINGLE_FILES, 25, "Importing world archive...");
        PojavApplication.sExecutorService.execute(() -> {
            try {
                final File imported = WorldSaveScanner.importWorldZip(this, uri, savesDir);
                ProgressLayout.clearProgress(ProgressLayout.EXTRACT_SINGLE_FILES);
                Tools.runOnUiThread(() -> {
                    Toast.makeText(this, "Imported world: " + imported.getName(), Toast.LENGTH_LONG).show();
                    Fragment active = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
                    if (active instanceof InstanceManagerFragment) {
                        ((InstanceManagerFragment) active).reload();
                    } else if (active instanceof WorldsFragment) {
                        ((WorldsFragment) active).reloadWorlds();
                    } else if (active instanceof MainMenuFragment) {
                        ((MainMenuFragment) active).refreshDashboardData();
                    }
                });
            } catch (Exception e) {
                ProgressLayout.clearProgress(ProgressLayout.EXTRACT_SINGLE_FILES);
                Tools.showErrorRemote("Failed to import world archive", e);
            }
        });
    }

    @NonNull
    public String getCurrentSearchQuery() {
        return mCurrentSearchQuery;
    }

    public void setPendingQuickPlayWorld(@Nullable String worldFolderName) {
        mPendingQuickPlayWorld = worldFolderName;
    }

    @Nullable
    public AuroraAccountController getAccountController() {
        return mAccountController;
    }

    @Override
    public void onAccountChanged(@Nullable MinecraftAccount account, @NonNull List<String> allAccountNames) {
        if (mAvatarImage == null || mAvatarStatusDot == null) return;
        if (account == null) {
            mAvatarImage.setImageResource(R.drawable.ic_voxel_avatar_default);
            mAvatarStatusDot.setBackgroundResource(R.drawable.bg_aurora_status_dot_offline);
        } else {
            Bitmap face = account.getSkinFace();
            if (face != null) {
                mAvatarImage.setImageBitmap(face);
            } else {
                mAvatarImage.setImageResource(R.drawable.ic_voxel_avatar_default);
            }
            mAvatarStatusDot.setBackgroundResource(R.drawable.bg_aurora_status_dot);
        }
        Fragment active = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
        if (active instanceof MainMenuFragment) {
            ((MainMenuFragment) active).refreshAccountArtwork();
        }
    }

    @Override
    public void onLoginProgress(int step, int maxSteps) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        mInstallTracker.attach();
        if (mAccountController != null) {
            mAccountController.reloadAccounts();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        mInstallTracker.detach();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAccountController != null) {
            mAccountController.removeListener(this);
            mAccountController.destroy();
        }
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ProgressKeeper.removeTaskCountListener(mDoubleLaunchPreventionListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if (fragment != null && fragment.canGoBack()) {
            fragment.goBack();
            return;
        }
        if (getVisibleFragment("ROOT") != null) {
            finish();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if (LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
                checkForNotificationPermission()) {
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(null))
                .setNegativeButton(android.R.string.cancel, (d, w) -> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void hideKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void bindViews() {
        mSafeRoot = findViewById(R.id.aurora_safe_root);
        mFragmentView = findViewById(R.id.container_fragment);
        mProgressLayout = findViewById(R.id.progress_layout);
        mBackChip = findViewById(R.id.aurora_back_chip);
        mTopBar = findViewById(R.id.aurora_top_bar);
        mSearchContainer = findViewById(R.id.aurora_search_container);
        mSearchInput = findViewById(R.id.aurora_search_input);
        mSearchClear = findViewById(R.id.aurora_search_clear);
        mAvatarContainer = findViewById(R.id.aurora_avatar_container);
        mAvatarImage = findViewById(R.id.aurora_avatar_image);
        mAvatarStatusDot = findViewById(R.id.aurora_avatar_status_dot);

        mNavHome = findViewById(R.id.aurora_nav_home);
        mNavCreate = findViewById(R.id.aurora_nav_create);
        mNavLibrary = findViewById(R.id.aurora_nav_library);
        mNavExplore = findViewById(R.id.aurora_nav_explore);
        mNavRuntime = findViewById(R.id.aurora_nav_runtime);
        mNavInstall = findViewById(R.id.aurora_nav_install);
        mNavSettings = findViewById(R.id.aurora_nav_settings);
        mNavAccounts = findViewById(R.id.aurora_nav_accounts);

        mNavHomeIcon = findViewById(R.id.aurora_nav_home_icon);
        mNavCreateIcon = findViewById(R.id.aurora_nav_create_icon);
        mNavLibraryIcon = findViewById(R.id.aurora_nav_library_icon);
        mNavExploreIcon = findViewById(R.id.aurora_nav_explore_icon);
        mNavRuntimeIcon = findViewById(R.id.aurora_nav_runtime_icon);
        mNavInstallIcon = findViewById(R.id.aurora_nav_install_icon);
        mNavSettingsIcon = findViewById(R.id.aurora_nav_settings_icon);
        mNavAccountsIcon = findViewById(R.id.aurora_nav_accounts_icon);
    }
}
