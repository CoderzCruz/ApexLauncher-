package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.dashboard.AuroraSegmentedHubDialog;
import net.kdt.pojavlaunch.dashboard.RoundedImageView;
import net.kdt.pojavlaunch.dashboard.WorldSaveScanner;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aurora Glass "Worlds" Tab — manages real Minecraft singleplayer saves (.minecraft/saves)
 * and LauncherProfiles with search filtering, QuickPlay launch, .zip world import, and profile editing.
 */
public class WorldsFragment extends Fragment {
    public static final String TAG = "WorldsFragment";

    private RecyclerView mRecyclerView;
    private View mEmptyState;
    private TextView mHeaderSubtitle;
    private WorldsAdapter mAdapter;
    private final List<WorldSaveScanner.DashboardWorldItem> mAllItems = new ArrayList<>();
    private final List<WorldSaveScanner.DashboardWorldItem> mFilteredItems = new ArrayList<>();
    private String mCurrentQuery = "";

    public WorldsFragment() {
        super(R.layout.fragment_worlds_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mRecyclerView = view.findViewById(R.id.worlds_recycler);
        mEmptyState = view.findViewById(R.id.worlds_empty_state);
        mHeaderSubtitle = view.findViewById(R.id.worlds_header_subtitle);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new WorldsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        view.findViewById(R.id.btn_worlds_new_profile).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));

        view.findViewById(R.id.btn_worlds_import_zip).setOnClickListener(v -> {
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).launchWorldZipImport();
            }
        });

        view.findViewById(R.id.btn_worlds_open_folder).setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                Toast.makeText(v.getContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }
            File savesDir = getActiveSavesDir();
            //noinspection ResultOfMethodCallIgnored
            savesDir.mkdirs();
            Tools.openPath(v.getContext(), savesDir, false);
        });

        view.findViewById(R.id.btn_worlds_search_marketplace).setOnClickListener(v -> {
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).navigateToTab(LauncherActivity.TAB_MARKETPLACE, mCurrentQuery);
            }
        });

        if (requireActivity() instanceof LauncherActivity) {
            mCurrentQuery = ((LauncherActivity) requireActivity()).getCurrentSearchQuery();
        }
        reloadWorlds();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadWorlds();
    }

    public void applySearchQuery(@Nullable String query) {
        mCurrentQuery = query == null ? "" : query.trim();
        filterItems();
    }

    public void reloadWorlds() {
        if (!isAdded()) return;
        mAllItems.clear();
        mAllItems.addAll(WorldSaveScanner.scanAllItems(requireContext()));
        filterItems();
    }

    private void filterItems() {
        if (mAdapter == null) return;
        mFilteredItems.clear();
        String q = mCurrentQuery.toLowerCase(Locale.ROOT);
        int worldCount = 0;
        int profileCount = 0;

        for (WorldSaveScanner.DashboardWorldItem item : mAllItems) {
            if (q.isEmpty()
                    || item.title.toLowerCase(Locale.ROOT).contains(q)
                    || item.versionId.toLowerCase(Locale.ROOT).contains(q)
                    || item.description.toLowerCase(Locale.ROOT).contains(q)) {
                mFilteredItems.add(item);
                if (item.isSavedWorld) worldCount++;
                else profileCount++;
            }
        }

        mHeaderSubtitle.setText(worldCount + " saved worlds • " + profileCount + " profiles");
        mEmptyState.setVisibility(mFilteredItems.isEmpty() ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(mFilteredItems.isEmpty() ? View.GONE : View.VISIBLE);
        mAdapter.notifyDataSetChanged();
    }

    private File getActiveSavesDir() {
        WorldSaveScanner.ensureProfilesInitialized(requireContext());
        String key = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(key);
        File gameDir = prof != null ? Tools.getGameDirPath(prof) : new File(Tools.DIR_GAME_NEW);
        return new File(gameDir, "saves");
    }

    private void launchItem(@NonNull WorldSaveScanner.DashboardWorldItem item) {
        WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
        WorldSaveScanner.recordPlayed(requireContext(), item.id);
        if (requireActivity() instanceof LauncherActivity) {
            ((LauncherActivity) requireActivity()).setPendingQuickPlayWorld(item.worldFolderName);
        }
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void showItemOptions(@NonNull final WorldSaveScanner.DashboardWorldItem item) {
        Bitmap thumb = null;
        if (item.iconFile != null && item.iconFile.exists()) {
            thumb = BitmapFactory.decodeFile(item.iconFile.getAbsolutePath());
        }
        AuroraSegmentedHubDialog.showActionSheet(
                requireActivity(),
                item.title,
                item.isSavedWorld ? "World Options" : "Instance Options",
                thumb,
                dialog -> {
                    List<AuroraSegmentedHubDialog.HubCardItem> cards = new ArrayList<>();
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_switch_profile,
                            "Set as Featured on Home",
                            "Pin \"" + item.title + "\" to your Home dashboard hero card",
                            "Pin",
                            () -> {
                                WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
                                Toast.makeText(requireContext(), "Pinned \"" + item.title + "\" to Home", Toast.LENGTH_SHORT).show();
                                if (requireActivity() instanceof LauncherActivity) {
                                    ((LauncherActivity) requireActivity()).navigateToTab(LauncherActivity.TAB_HOME, null);
                                }
                            }
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_game_folder,
                            "Play Now",
                            "Launch Minecraft " + item.versionId + " immediately",
                            "Play",
                            () -> launchItem(item)
                    ));
                    cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                            R.drawable.ic_hub_edit_profile,
                            "Edit Profile (" + item.versionId + ")",
                            "Change MC version, Java runtime & video renderer",
                            "Edit",
                            () -> {
                                LauncherPreferences.DEFAULT_PREF.edit()
                                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, item.profileKey)
                                        .apply();
                                Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                                        ProfileEditorFragment.TAG, null);
                            }
                    ));
                    if (item.isSavedWorld && item.worldFolderName != null) {
                        cards.add(new AuroraSegmentedHubDialog.HubCardItem(
                                R.drawable.ic_hub_launcher_settings,
                                "Delete Saved World",
                                "Permanently remove \"" + item.title + "\" from saves/",
                                "Delete",
                                () -> confirmDeleteWorld(item)
                        ));
                    }
                    return cards;
                }
        );
    }

    private void confirmDeleteWorld(@NonNull final WorldSaveScanner.DashboardWorldItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete \"" + item.title + "\"?")
                .setMessage("This will permanently remove this world folder from saves.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (d, w) -> {
                    if (item.id.startsWith("world:")) {
                        File wf = new File(item.id.substring("world:".length()));
                        try {
                            FileUtils.deleteDirectory(wf);
                        } catch (IOException e) {
                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        reloadWorlds();
                    }
                })
                .show();
    }

    private class WorldsAdapter extends RecyclerView.Adapter<WorldsAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_aurora_world_card, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final WorldSaveScanner.DashboardWorldItem item = mFilteredItems.get(position);
            h.title.setText(item.title);
            h.badge.setText(item.isSavedWorld ? "WORLD" : "PROFILE");
            h.meta.setText(item.description.replace('\n', ' ') + " • " + item.subtitle);

            if (item.iconFile != null && item.iconFile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(item.iconFile.getAbsolutePath());
                if (bmp != null) {
                    h.thumb.setImageBitmap(bmp);
                } else {
                    h.thumb.setImageResource(item.fallbackArtRes);
                }
            } else {
                h.thumb.setImageResource(item.fallbackArtRes);
            }

            h.itemView.setOnClickListener(v -> {
                WorldSaveScanner.setFeaturedAndSelectProfile(requireContext(), item);
                Toast.makeText(requireContext(), "Selected " + item.title, Toast.LENGTH_SHORT).show();
                if (requireActivity() instanceof LauncherActivity) {
                    ((LauncherActivity) requireActivity()).navigateToTab(LauncherActivity.TAB_HOME, null);
                }
            });
            h.optionsBtn.setOnClickListener(v -> showItemOptions(item));
            h.playBtn.setOnClickListener(v -> launchItem(item));
        }

        @Override
        public int getItemCount() {
            return mFilteredItems.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final RoundedImageView thumb;
            final TextView title;
            final TextView badge;
            final TextView meta;
            final ImageButton optionsBtn;
            final ImageButton playBtn;

            Holder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.item_world_thumb);
                title = itemView.findViewById(R.id.item_world_title);
                badge = itemView.findViewById(R.id.item_world_badge);
                meta = itemView.findViewById(R.id.item_world_meta);
                optionsBtn = itemView.findViewById(R.id.item_world_options);
                playBtn = itemView.findViewById(R.id.item_world_play);
            }
        }
    }
}
