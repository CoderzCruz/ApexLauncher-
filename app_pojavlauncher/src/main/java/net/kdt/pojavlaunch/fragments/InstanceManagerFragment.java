package net.kdt.pojavlaunch.fragments;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.dashboard.AuroraSegmentedHubDialog;
import net.kdt.pojavlaunch.dashboard.WorldSaveScanner;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.interfaces.SimpleTextWatcher;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Instance Management" (Mint Glass mockup #01).
 *
 * A complete instance manager: searchable, filterable instance library on the left,
 * and a live detail panel on the right with Overview / Mods / Resource Packs / Worlds /
 * Settings tabs and the mint PLAY bar that launches the selected instance.
 */
public class InstanceManagerFragment extends Fragment {
    public static final String TAG = "InstanceManagerFragment";

    private static final String PREFS_NAME = "apex_instance_manager";
    private static final String KEY_PINNED = "pinned_profiles";

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_MODS = 1;
    private static final int TAB_PACKS = 2;
    private static final int TAB_WORLDS = 3;
    private static final int TAB_SETTINGS = 4;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_MODPACKS = 1;
    private static final int FILTER_VANILLA = 2;
    private static final int FILTER_PINNED = 3;

    private EditText mSearch;
    private RecyclerView mRecycler;
    private TextView mEmptyState;
    private TextView mCountText;
    private TextView mInstalledCount;

    private ImageView mDetailIcon;
    private TextView mDetailTitle;
    private TextView mPillLoader;
    private TextView mPillVersion;
    private ImageButton mPinButton;
    private ImageButton mMoreButton;
    private ImageButton mFolderButton;
    private ImageButton mSortButton;
    private LinearLayout mTabBody;
    private View mPlayBar;
    private View mInstallButton;

    private TextView[] mChips;
    private TextView[] mTabs;

    private final List<WorldSaveScanner.DashboardWorldItem> mInstances = new ArrayList<>();
    private final List<WorldSaveScanner.DashboardWorldItem> mVisible = new ArrayList<>();
    private InstanceAdapter mAdapter;

    @Nullable
    private WorldSaveScanner.DashboardWorldItem mSelected;
    private int mFilter = FILTER_ALL;
    private int mActiveTab = TAB_OVERVIEW;
    private String mQuery = "";
    private boolean mSortByName = false;

    public InstanceManagerFragment() {
        super(R.layout.fragment_instance_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mSearch = view.findViewById(R.id.inst_search);
        mRecycler = view.findViewById(R.id.inst_recycler);
        mEmptyState = view.findViewById(R.id.inst_empty);
        mCountText = view.findViewById(R.id.inst_count_text);
        mInstalledCount = view.findViewById(R.id.inst_installed_count);

        mDetailIcon = view.findViewById(R.id.inst_detail_icon);
        mDetailTitle = view.findViewById(R.id.inst_detail_title);
        mPillLoader = view.findViewById(R.id.inst_pill_loader);
        mPillVersion = view.findViewById(R.id.inst_pill_version);
        mPinButton = view.findViewById(R.id.inst_pin_btn);
        mMoreButton = view.findViewById(R.id.inst_more_btn);
        mFolderButton = view.findViewById(R.id.inst_folder_btn);
        mSortButton = view.findViewById(R.id.inst_sort_btn);
        mTabBody = view.findViewById(R.id.inst_tab_body);
        mPlayBar = view.findViewById(R.id.inst_play_bar);
        mInstallButton = view.findViewById(R.id.inst_install_btn);

        mChips = new TextView[]{
                view.findViewById(R.id.inst_chip_all),
                view.findViewById(R.id.inst_chip_modpacks),
                view.findViewById(R.id.inst_chip_vanilla),
                view.findViewById(R.id.inst_chip_pinned)
        };
        mTabs = new TextView[]{
                view.findViewById(R.id.inst_tab_overview),
                view.findViewById(R.id.inst_tab_mods),
                view.findViewById(R.id.inst_tab_packs),
                view.findViewById(R.id.inst_tab_worlds),
                view.findViewById(R.id.inst_tab_settings)
        };

        mRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new InstanceAdapter();
        mRecycler.setAdapter(mAdapter);

        for (int i = 0; i < mChips.length; i++) {
            final int filter = i;
            mChips[i].setOnClickListener(v -> setFilter(filter));
        }
        for (int i = 0; i < mTabs.length; i++) {
            final int tab = i;
            mTabs[i].setOnClickListener(v -> selectTab(tab));
        }

        mSearch.addTextChangedListener((SimpleTextWatcher) (Editable s) -> {
            mQuery = s == null ? "" : s.toString().trim();
            rebuild();
        });

        mInstallButton.setOnClickListener(v -> openCreateInstance());
        mFolderButton.setOnClickListener(v -> openCurrentInstanceFolder());
        mSortButton.setOnClickListener(v -> showSortDialog());
        mPinButton.setOnClickListener(v -> togglePin());
        mMoreButton.setOnClickListener(v -> {
            if (mSelected != null) showInstanceOptions(mSelected);
        });
        mPlayBar.setOnClickListener(v -> launchSelected());

        if (requireActivity() instanceof LauncherActivity) {
            mQuery = ((LauncherActivity) requireActivity()).getCurrentSearchQuery();
            if (!mQuery.isEmpty()) {
                mSearch.setText(mQuery);
                mSearch.setSelection(mSearch.getText().length());
            }
        }

        selectTab(TAB_OVERVIEW);
        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    // ------------------------------------------------------------------ data

    public void reload() {
        if (!isAdded() || mAdapter == null) return;
        String keepId = mSelected != null ? mSelected.id : null;

        mInstances.clear();
        for (WorldSaveScanner.DashboardWorldItem item : WorldSaveScanner.scanAllItems(requireContext())) {
            if (!item.isSavedWorld) mInstances.add(item);
        }

        if (keepId != null) {
            for (WorldSaveScanner.DashboardWorldItem item : mInstances) {
                if (item.id.equals(keepId)) {
                    mSelected = item;
                    break;
                }
            }
        }
        if (mSelected == null) {
            WorldSaveScanner.DashboardWorldItem featured =
                    WorldSaveScanner.resolveFeaturedItem(requireContext(), mInstances);
            mSelected = featured;
        }
        rebuild();
    }

    public void applySearchQuery(@Nullable String query) {
        mQuery = query == null ? "" : query.trim();
        if (mSearch != null && !mQuery.equals(mSearch.getText().toString().trim())) {
            mSearch.setText(mQuery);
            mSearch.setSelection(mSearch.getText().length());
        } else {
            rebuild();
        }
    }

    private void setFilter(int filter) {
        mFilter = filter;
        for (int i = 0; i < mChips.length; i++) {
            boolean active = i == filter;
            mChips[i].setBackgroundResource(active ? R.drawable.bg_inst_chip_on : R.drawable.bg_inst_chip_off);
            mChips[i].setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.aurora_mint_ink : R.color.aurora_text_white));
        }
        rebuild();
    }

    private void rebuild() {
        if (mAdapter == null || !isAdded()) return;

        mVisible.clear();
        String q = mQuery.toLowerCase(Locale.ROOT);
        for (WorldSaveScanner.DashboardWorldItem item : mInstances) {
            if (!matchesFilter(item)) continue;
            if (!q.isEmpty()) {
                String haystack = (displayName(item) + " " + item.versionId + " "
                        + loaderOf(item.versionId) + " " + safe(item.title)).toLowerCase(Locale.ROOT);
                if (!haystack.contains(q)) continue;
            }
            mVisible.add(item);
        }

        if (mSortByName) {
            Collections.sort(mVisible, new Comparator<WorldSaveScanner.DashboardWorldItem>() {
                @Override
                public int compare(WorldSaveScanner.DashboardWorldItem a, WorldSaveScanner.DashboardWorldItem b) {
                    return displayName(a).compareToIgnoreCase(displayName(b));
                }
            });
        }

        if (mSelected == null || !containsId(mVisible, mSelected.id)) {
            mSelected = mVisible.isEmpty() ? null : mVisible.get(0);
        }

        int total = mInstances.size();
        String countLabel = getString(R.string.aurora_inst_installed, total);
        if (mInstalledCount != null) mInstalledCount.setText(countLabel);
        if (mCountText != null) mCountText.setText(String.valueOf(total));

        boolean empty = mVisible.isEmpty();
        mEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        mRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);

        mAdapter.notifyDataSetChanged();
        bindDetail();
    }

    private boolean containsId(@NonNull List<WorldSaveScanner.DashboardWorldItem> list, @Nullable String id) {
        if (id == null) return false;
        for (WorldSaveScanner.DashboardWorldItem item : list) {
            if (id.equals(item.id)) return true;
        }
        return false;
    }

    private boolean matchesFilter(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        String loader = loaderOf(item.versionId);
        switch (mFilter) {
            case FILTER_MODPACKS:
                return !"Vanilla".equals(loader);
            case FILTER_VANILLA:
                return "Vanilla".equals(loader);
            case FILTER_PINNED:
                return getPinnedIds().contains(item.profileKey);
            case FILTER_ALL:
            default:
                return true;
        }
    }

    // --------------------------------------------------------------- detail

    private void bindDetail() {
        boolean has = mSelected != null;

        if (mDetailTitle != null) {
            mDetailTitle.setText(has ? displayName(mSelected) : getString(R.string.aurora_inst_title));
        }
        if (mPillLoader != null) {
            mPillLoader.setText(has ? loaderOf(mSelected.versionId) : getString(R.string.aurora_inst_chip_vanilla));
        }
        if (mPillVersion != null) {
            mPillVersion.setText(has ? "Minecraft " + safeVersion(mSelected.versionId) : "—");
        }
        if (mDetailIcon != null) {
            if (has && mSelected.iconFile != null && mSelected.iconFile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(mSelected.iconFile.getAbsolutePath());
                if (bmp != null) {
                    mDetailIcon.setImageBitmap(bmp);
                } else {
                    mDetailIcon.setImageResource(R.drawable.ic_grass_block);
                }
            } else {
                mDetailIcon.setImageResource(R.drawable.ic_grass_block);
            }
        }
        updatePinIcon();
        if (mPlayBar != null) {
            mPlayBar.setAlpha(has ? 1f : 0.45f);
            mPlayBar.setEnabled(has);
        }
        renderTab();
    }

    private void updatePinIcon() {
        if (mPinButton == null) return;
        boolean pinned = mSelected != null && getPinnedIds().contains(mSelected.profileKey);
        mPinButton.setImageResource(pinned
                ? R.drawable.ic_inst_heart_filled
                : R.drawable.ic_inst_heart_outline);
        mPinButton.setAlpha(pinned ? 1f : 0.85f);
    }

    private void selectTab(int tab) {
        mActiveTab = tab;
        if (mTabs != null) {
            for (int i = 0; i < mTabs.length; i++) {
                boolean active = i == tab;
                mTabs[i].setBackgroundResource(active ? R.drawable.bg_inst_tab_on : R.drawable.bg_inst_tab_off);
                mTabs[i].setTextColor(ContextCompat.getColor(requireContext(),
                        active ? R.color.aurora_text_white : R.color.aurora_text_sub));
            }
        }
        renderTab();
    }

    private void renderTab() {
        if (mTabBody == null || !isAdded()) return;
        mTabBody.removeAllViews();

        if (mSelected == null) {
            mTabBody.addView(makeLabel(getString(R.string.aurora_inst_empty),
                    12f, false, R.color.aurora_text_muted));
            return;
        }

        File gameDir = gameDirOf(mSelected);
        switch (mActiveTab) {
            case TAB_MODS:
                renderFileTab(gameDir, "mods", R.string.aurora_inst_mods_header, true);
                break;
            case TAB_PACKS:
                renderFileTab(gameDir, "resourcepacks", R.string.aurora_inst_packs_header, true);
                break;
            case TAB_WORLDS:
                renderWorldsTab(gameDir);
                break;
            case TAB_SETTINGS:
                renderSettingsTab(gameDir);
                break;
            case TAB_OVERVIEW:
            default:
                renderOverviewTab(gameDir);
                break;
        }
    }

    private void renderOverviewTab(@NonNull File gameDir) {
        int mods = countEntries(new File(gameDir, "mods"));
        int packs = countEntries(new File(gameDir, "resourcepacks"));
        int shaders = countEntries(new File(gameDir, "shaderpacks"));
        int worlds = countWorlds(new File(gameDir, "saves"));

        HorizontalScrollView chipsScroll = new HorizontalScrollView(requireContext());
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(requireContext());
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(chips, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chips.addView(makeChip(getString(R.string.aurora_inst_healthy), true, null));
        chips.addView(makeChip(loaderOf(mSelected.versionId), false, null));
        chips.addView(makeChip("Minecraft " + safeVersion(mSelected.versionId), false, null));
        chips.addView(makeChip(safeVersion(mSelected.versionId), false, null));
        mTabBody.addView(chipsScroll);

        LinearLayout firstRow = makeTileRow();
        addTile(firstRow, R.drawable.ic_abyssal_mods_cube, String.valueOf(mods),
                getString(R.string.aurora_inst_tile_mods), () -> selectTab(TAB_MODS));
        addTile(firstRow, R.drawable.ic_inst_packs, String.valueOf(packs),
                getString(R.string.aurora_inst_tile_packs), () -> selectTab(TAB_PACKS));
        mTabBody.addView(firstRow);

        LinearLayout secondRow = makeTileRow();
        addTile(secondRow, R.drawable.ic_inst_shaders, String.valueOf(shaders),
                getString(R.string.aurora_inst_tile_shaders), () -> openFolder(new File(gameDir, "shaderpacks")));
        addTile(secondRow, R.drawable.ic_aurora_worlds, String.valueOf(worlds),
                getString(R.string.aurora_inst_tile_worlds), () -> selectTab(TAB_WORLDS));
        mTabBody.addView(secondRow);

        addInfoLine(getString(R.string.aurora_inst_last_played), safe(mSelected.subtitle));
        addInfoLine(getString(R.string.aurora_inst_game_folder), gameDir.getAbsolutePath());
        addInfoLine(getString(R.string.aurora_inst_settings_java), javaLabel(mSelected));
    }

    private void renderFileTab(@NonNull File gameDir, @NonNull String folderName,
                               int headerFormat, boolean jarsOnly) {
        File dir = new File(gameDir, folderName);
        List<String> names = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (jarsOnly) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (!n.endsWith(".jar") && !n.endsWith(".jar.disabled") && !f.isDirectory()) continue;
                }
                names.add(f.getName());
            }
        }
        Collections.sort(names);

        mTabBody.addView(makeLabel(getString(headerFormat, names.size()),
                12.5f, true, R.color.aurora_text_white));

        if (names.isEmpty()) {
            mTabBody.addView(makeLabel(getString(R.string.aurora_inst_empty_folder),
                    11.5f, false, R.color.aurora_text_muted));
        } else {
            int limit = Math.min(names.size(), 60);
            for (int i = 0; i < limit; i++) {
                mTabBody.addView(makeListRow(names.get(i), null, null));
            }
        }

        mTabBody.addView(makeActionButton(getString(R.string.aurora_inst_open_folder),
                () -> openFolder(dir)));
    }

    private void renderWorldsTab(@NonNull File gameDir) {
        List<WorldSaveScanner.DashboardWorldItem> worlds = new ArrayList<>();
        for (WorldSaveScanner.DashboardWorldItem item : WorldSaveScanner.scanAllItems(requireContext())) {
            if (item.isSavedWorld && mSelected != null
                    && item.profileKey.equals(mSelected.profileKey)) {
                worlds.add(item);
            }
        }

        mTabBody.addView(makeLabel(getString(R.string.aurora_inst_worlds_header, worlds.size()),
                12.5f, true, R.color.aurora_text_white));

        if (worlds.isEmpty()) {
            mTabBody.addView(makeLabel(getString(R.string.aurora_inst_empty_folder),
                    11.5f, false, R.color.aurora_text_muted));
        } else {
            for (final WorldSaveScanner.DashboardWorldItem world : worlds) {
                mTabBody.addView(makeListRow(world.title,
                        getString(R.string.aurora_inst_tap_launch),
                        () -> launchWorld(world)));
            }
        }

        mTabBody.addView(makeActionButton(getString(R.string.aurora_inst_import_world), () -> {
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).launchWorldZipImport();
            }
        }));
    }

    private void renderSettingsTab(@NonNull File gameDir) {
        MinecraftProfile profile = profileOf(mSelected);
        mTabBody.addView(makeSettingRow(getString(R.string.aurora_inst_settings_version),
                safeVersion(mSelected.versionId)));
        mTabBody.addView(makeSettingRow(getString(R.string.aurora_inst_settings_ram),
                LauncherPreferences.PREF_RAM_ALLOCATION + " MB"));
        mTabBody.addView(makeSettingRow(getString(R.string.aurora_inst_settings_renderer),
                profile != null && Tools.isValidString(profile.pojavRendererName)
                        ? profile.pojavRendererName : "Default"));
        mTabBody.addView(makeSettingRow(getString(R.string.aurora_inst_settings_java),
                javaLabel(mSelected)));

        mTabBody.addView(makeActionButton(getString(R.string.aurora_inst_edit_profile), () -> {
            if (mSelected == null) return;
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, mSelected.profileKey)
                    .apply();
            Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                    ProfileEditorFragment.TAG, null);
        }));
        mTabBody.addView(makeActionButton(getString(R.string.aurora_inst_open_folder),
                () -> openFolder(gameDir)));
        mTabBody.addView(makeActionButton(getString(R.string.aurora_inst_share_logs),
                () -> Tools.shareLog(requireContext())));
    }

    // ----------------------------------------------------------------- views

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView makeLabel(String text, float sizeSp, boolean bold, int colorRes) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    private TextView makeChip(String text, boolean active, @Nullable Runnable onClick) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setBackgroundResource(active ? R.drawable.bg_inst_chip_on : R.drawable.bg_inst_chip_off);
        tv.setTextColor(ContextCompat.getColor(requireContext(),
                active ? R.color.aurora_mint_ink : R.color.aurora_text_white));
        tv.setPadding(dp(11), dp(4), dp(11), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        if (onClick != null) {
            tv.setClickable(true);
            tv.setOnClickListener(v -> onClick.run());
        }
        return tv;
    }

    private LinearLayout makeTileRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private void addTile(@NonNull LinearLayout row, @DrawableRes int iconRes, String count,
                         String label, @Nullable Runnable onClick) {
        LinearLayout tile = new LinearLayout(requireContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackgroundResource(R.drawable.bg_inst_tile);
        tile.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        tile.setLayoutParams(lp);
        if (onClick != null) {
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(v -> onClick.run());
        }

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aurora_mint_primary));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        icon.setLayoutParams(iconLp);
        top.addView(icon);

        TextView countView = new TextView(requireContext());
        countView.setText(count);
        countView.setTextSize(14f);
        countView.setTextColor(ContextCompat.getColor(requireContext(), R.color.aurora_text_white));
        countView.setTypeface(countView.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.leftMargin = dp(8);
        countView.setLayoutParams(countLp);
        top.addView(countView);

        tile.addView(top);
        tile.addView(makeLabel(label, 10.5f, false, R.color.aurora_text_muted));
        row.addView(tile);
    }

    private void addInfoLine(String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);

        TextView key = makeLabel(label, 11f, false, R.color.aurora_text_muted);
        row.addView(key);

        TextView val = makeLabel(value, 11f, false, R.color.aurora_text_sub);
        val.setSingleLine(true);
        val.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valLp.leftMargin = dp(8);
        val.setLayoutParams(valLp);
        row.addView(val);

        mTabBody.addView(row);
    }

    private TextView makeSettingRow(String label, String value) {
        TextView tv = new TextView(requireContext());
        tv.setText(label + "   ·   " + value);
        tv.setTextSize(11.5f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.aurora_text_sub));
        tv.setBackgroundResource(R.drawable.bg_inst_row);
        tv.setPadding(dp(11), dp(9), dp(11), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makeListRow(String title, @Nullable String subtitle, @Nullable Runnable onClick) {
        TextView tv = new TextView(requireContext());
        tv.setText(subtitle == null ? title : title + "\n" + subtitle);
        tv.setTextSize(11.5f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.aurora_text_white));
        tv.setBackgroundResource(R.drawable.bg_inst_row);
        tv.setPadding(dp(11), dp(8), dp(11), dp(8));
        tv.setSingleLine(subtitle == null);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        if (onClick != null) {
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(v -> onClick.run());
        }
        return tv;
    }

    private TextView makeActionButton(String text, @NonNull Runnable onClick) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_inst_chip_off);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.aurora_mint_bright));
        tv.setPadding(dp(12), dp(9), dp(12), dp(9));
        tv.setClickable(true);
        tv.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    // ---------------------------------------------------------------- actions

    private void selectInstance(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        mSelected = item;
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
        mAdapter.notifyDataSetChanged();
        bindDetail();
    }

    private void launchSelected() {
        if (mSelected == null || !isAdded()) return;
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), mSelected);
        WorldSaveScanner.recordPlayed(requireContext(), mSelected.id);
        if (requireActivity() instanceof LauncherActivity) {
            ((LauncherActivity) requireActivity()).setPendingQuickPlayWorld(mSelected.worldFolderName);
        }
        Toast.makeText(requireContext(),
                getString(R.string.aurora_inst_selected_toast, displayName(mSelected)),
                Toast.LENGTH_SHORT).show();
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void launchWorld(@NonNull WorldSaveScanner.DashboardWorldItem world) {
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), world);
        WorldSaveScanner.recordPlayed(requireContext(), world.id);
        if (requireActivity() instanceof LauncherActivity) {
            ((LauncherActivity) requireActivity()).setPendingQuickPlayWorld(world.worldFolderName);
        }
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void togglePin() {
        if (mSelected == null || !isAdded()) return;
        Set<String> pinned = new HashSet<>(getPinnedIds());
        if (pinned.contains(mSelected.profileKey)) {
            pinned.remove(mSelected.profileKey);
        } else {
            pinned.add(mSelected.profileKey);
        }
        requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PINNED, pinned).apply();
        updatePinIcon();
        rebuild();
    }

    private Set<String> getPinnedIds() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_PINNED, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private void showSortDialog() {
        final String[] options = {"Most recent first", "Name (A – Z)"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Sort instances")
                .setItems(options, (dialog, which) -> {
                    mSortByName = which == 1;
                    rebuild();
                })
                .show();
    }

    private void openCreateInstance() {
        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                ProfileTypeSelectFragment.TAG, null);
    }

    private void openCurrentInstanceFolder() {
        if (mSelected == null) return;
        openInstanceFolder(mSelected);
    }

    private void openInstanceFolder(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        if (Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        File dir = gameDirOf(item);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        Tools.openPath(requireContext(), dir, false);
    }

    private void openFolder(@NonNull File dir) {
        if (Tools.isDemoProfile(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        Tools.openPath(requireContext(), dir, false);
    }

    private void showInstanceOptions(@NonNull final WorldSaveScanner.DashboardWorldItem item) {
        Bitmap thumb = null;
        if (item.iconFile != null && item.iconFile.exists()) {
            thumb = BitmapFactory.decodeFile(item.iconFile.getAbsolutePath());
        }
        AuroraSegmentedHubDialog.showActionSheet(
                requireActivity(),
                displayName(item),
                getString(R.string.aurora_inst_options),
                thumb,
                dialog -> {
                    List<AuroraSegmentedHubDialog.HubCardItem> cards = new ArrayList<>();
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_play_solid,
                            getString(R.string.aurora_inst_quick_play),
                            loaderOf(item.versionId) + " · Minecraft " + safeVersion(item.versionId),
                            getString(R.string.aurora_inst_play),
                            this::launchSelected
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_switch_profile,
                            getString(R.string.aurora_inst_set_current),
                            getString(R.string.aurora_inst_caption),
                            "Select",
                            () -> selectInstance(item)
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_edit_profile,
                            getString(R.string.aurora_inst_edit_profile),
                            "Change version, Java runtime and renderer",
                            "Edit",
                            () -> {
                                LauncherPreferences.DEFAULT_PREF.edit()
                                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, item.profileKey)
                                        .apply();
                                Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                                        ProfileEditorFragment.TAG, null);
                            }
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_game_folder,
                            getString(R.string.aurora_inst_open_folder),
                            gameDirOf(item).getAbsolutePath(),
                            "Open",
                            () -> openInstanceFolder(item)
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_share_logs,
                            getString(R.string.aurora_inst_share_logs),
                            "Share the latest game log",
                            "Share",
                            () -> Tools.shareLog(requireContext())
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_menu_delete_forever,
                            getString(R.string.aurora_inst_delete),
                            "Removes this instance entry (game files stay on disk)",
                            "Delete",
                            () -> confirmDelete(item)
                    ));
                    return cards;
                }
        );
    }

    private void confirmDelete(@NonNull final WorldSaveScanner.DashboardWorldItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete \"" + displayName(item) + "\"?")
                .setMessage("This removes the instance from your launcher. "
                        + "Mods, worlds and configs stay on disk.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (dialog, which) -> {
                    if (LauncherProfiles.mainProfileJson != null
                            && LauncherProfiles.mainProfileJson.profiles != null) {
                        LauncherProfiles.mainProfileJson.profiles.remove(item.profileKey);
                        LauncherProfiles.write();

                        String current = LauncherPreferences.DEFAULT_PREF.getString(
                                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                        if (!LauncherProfiles.mainProfileJson.profiles.containsKey(current)
                                && !LauncherProfiles.mainProfileJson.profiles.isEmpty()) {
                            String next = LauncherProfiles.mainProfileJson.profiles.keySet().iterator().next();
                            LauncherPreferences.DEFAULT_PREF.edit()
                                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, next)
                                    .apply();
                        }
                    }
                    mSelected = null;
                    Toast.makeText(requireContext(), R.string.aurora_inst_deleted, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .show();
    }

    // ----------------------------------------------------------------- utils

    @NonNull
    private String displayName(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        String version = safeVersion(item.versionId);
        if (Tools.isValidString(item.title) && !"New".equalsIgnoreCase(item.title)) {
            return item.title;
        }
        return version;
    }

    @NonNull
    private String safeVersion(@Nullable String versionId) {
        return Tools.isValidString(versionId) ? versionId : "latest-release";
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NonNull
    private String loaderOf(@Nullable String versionId) {
        if (versionId == null) return "Vanilla";
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge")) return "NeoForge";
        if (lower.contains("fabric")) return "Fabric";
        if (lower.contains("quilt")) return "Quilt";
        if (lower.contains("forge")) return "Forge";
        if (lower.contains("optifine")) return "OptiFine";
        return "Vanilla";
    }

    @Nullable
    private MinecraftProfile profileOf(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        if (LauncherProfiles.mainProfileJson == null
                || LauncherProfiles.mainProfileJson.profiles == null) return null;
        return LauncherProfiles.mainProfileJson.profiles.get(item.profileKey);
    }

    @NonNull
    private File gameDirOf(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        MinecraftProfile profile = profileOf(item);
        return profile != null ? Tools.getGameDirPath(profile) : new File(Tools.DIR_GAME_NEW);
    }

    @NonNull
    private String javaLabel(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        MinecraftProfile profile = profileOf(item);
        if (profile == null || !Tools.isValidString(profile.javaDir)) return "Auto";
        File java = new File(profile.javaDir);
        return java.getName().isEmpty() ? profile.javaDir : java.getName();
    }

    private int countEntries(@Nullable File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    private int countWorlds(@Nullable File savesDir) {
        if (savesDir == null || !savesDir.isDirectory()) return 0;
        File[] dirs = savesDir.listFiles();
        if (dirs == null) return 0;
        int count = 0;
        for (File world : dirs) {
            if (!world.isDirectory()) continue;
            if (new File(world, "level.dat").exists() || new File(world, "region").exists()) {
                count++;
            }
        }
        return count;
    }

    // --------------------------------------------------------------- adapter

    private class InstanceAdapter extends RecyclerView.Adapter<InstanceAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_instance_row, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final WorldSaveScanner.DashboardWorldItem item = mVisible.get(position);
            boolean selected = mSelected != null && mSelected.id.equals(item.id);

            holder.root.setBackgroundResource(selected
                    ? R.drawable.bg_inst_row_selected
                    : R.drawable.bg_inst_row);
            holder.title.setText(displayName(item));
            holder.meta.setText(loaderOf(item.versionId) + " · " + safeVersion(item.versionId));

            String currentKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            holder.dot.setVisibility(item.profileKey.equals(currentKey) ? View.VISIBLE : View.INVISIBLE);

            if (item.iconFile != null && item.iconFile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(item.iconFile.getAbsolutePath());
                if (bmp != null) {
                    holder.icon.setImageBitmap(bmp);
                } else {
                    holder.icon.setImageResource(R.drawable.ic_grass_block);
                }
            } else {
                holder.icon.setImageResource(R.drawable.ic_grass_block);
            }

            holder.root.setOnClickListener(v -> selectInstance(item));
            holder.root.setOnLongClickListener(v -> {
                showInstanceOptions(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mVisible.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final View root;
            final ImageView icon;
            final TextView title;
            final TextView meta;
            final View dot;

            Holder(@NonNull View itemView) {
                super(itemView);
                root = itemView;
                icon = itemView.findViewById(R.id.inst_row_icon);
                title = itemView.findViewById(R.id.inst_row_title);
                meta = itemView.findViewById(R.id.inst_row_meta);
                dot = itemView.findViewById(R.id.inst_row_dot);
            }
        }
    }
}
