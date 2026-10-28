package net.kdt.pojavlaunch.dashboard;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Aurora Glass Segmented Pill-Tab Control Hub (Mockup #8) and reusable Aurora Glass Action Modal.
 * Replaces stock Android grey AlertDialogs with a landscape-optimized frosted glass hub.
 */
public class AuroraSegmentedHubDialog {

    public static class HubCardItem {
        @DrawableRes public final int iconRes;
        @Nullable public final Bitmap customBitmap;
        @Nullable public final Drawable customDrawable;
        @NonNull public final String title;
        @NonNull public final String subtitle;
        @NonNull public final String actionLabel;
        public final boolean highlighted;
        public final boolean dismissOnClick;
        @NonNull public final Runnable onClick;

        public HubCardItem(
                @DrawableRes int iconRes,
                @NonNull String title,
                @NonNull String subtitle,
                @NonNull String actionLabel,
                @NonNull Runnable onClick) {
            this(iconRes, null, null, title, subtitle, actionLabel, false, true, onClick);
        }

        public HubCardItem(
                @DrawableRes int iconRes,
                @Nullable Bitmap customBitmap,
                @Nullable Drawable customDrawable,
                @NonNull String title,
                @NonNull String subtitle,
                @NonNull String actionLabel,
                boolean highlighted,
                boolean dismissOnClick,
                @NonNull Runnable onClick) {
            this.iconRes = iconRes;
            this.customBitmap = customBitmap;
            this.customDrawable = customDrawable;
            this.title = title;
            this.subtitle = subtitle;
            this.actionLabel = actionLabel;
            this.highlighted = highlighted;
            this.dismissOnClick = dismissOnClick;
            this.onClick = onClick;
        }
    }

    public interface SubListProvider {
        @NonNull List<HubCardItem> buildItems(@NonNull Dialog dialog);
    }

    public interface ControlHubActions {
        @NonNull List<HubCardItem> buildSwitchProfileItems(@NonNull Dialog dialog);
        void onEditActiveProfile();
        void onCreateNewProfile();
        void onOpenTouchControls();
        void onOpenGamepadRemapper();
        void onExecuteModInstaller(boolean customJvmArgs);
        void onOpenGameDirectory();
        void onShareLatestLog();
        void onOpenJavaSettings();
        void onOpenLauncherSettings();
    }

    /**
     * Opens the Mockup #8 3-Tab Segmented Pill Control Hub ('Profile' | 'Mods & Controls' | 'System & Logs').
     */
    public static void showControlHub(
            @NonNull Activity activity,
            @NonNull String activeProfileTitle,
            @Nullable Bitmap playerAvatarBitmap,
            @NonNull ControlHubActions actions) {

        final Dialog dialog = createAuroraDialog(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_aurora_segmented_hub, null, false);
        dialog.setContentView(root);
        applyLandscapeDialogBounds(activity, dialog);

        RoundedImageView avatarView = root.findViewById(R.id.aurora_hub_avatar);
        TextView profileNameView = root.findViewById(R.id.aurora_hub_profile_name);
        TextView backBtn = root.findViewById(R.id.aurora_hub_back_btn);
        View segTrack = root.findViewById(R.id.aurora_hub_seg_track);
        TextView subviewTitle = root.findViewById(R.id.aurora_hub_subview_title);
        TextView tabProfile = root.findViewById(R.id.aurora_hub_tab_profile);
        TextView tabMods = root.findViewById(R.id.aurora_hub_tab_mods);
        TextView tabSystem = root.findViewById(R.id.aurora_hub_tab_system);
        ImageButton closeBtn = root.findViewById(R.id.aurora_hub_close_btn);
        RecyclerView recycler = root.findViewById(R.id.aurora_hub_recycler);

        if (playerAvatarBitmap != null) {
            avatarView.setImageBitmap(playerAvatarBitmap);
        } else {
            avatarView.setImageResource(R.drawable.ic_voxel_avatar_default);
        }
        profileNameView.setText(activeProfileTitle);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        final HubCardAdapter adapter = new HubCardAdapter(activity, dialog);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setAdapter(adapter);

        final int[] currentTab = new int[]{0};

        final Runnable renderCurrentTab = new Runnable() {
            @Override
            public void run() {
                backBtn.setVisibility(View.GONE);
                root.findViewById(R.id.aurora_hub_avatar_wrap).setVisibility(View.VISIBLE);
                profileNameView.setVisibility(View.VISIBLE);
                segTrack.setVisibility(View.VISIBLE);
                subviewTitle.setVisibility(View.GONE);

                styleSegmentTab(activity, tabProfile, currentTab[0] == 0);
                styleSegmentTab(activity, tabMods, currentTab[0] == 1);
                styleSegmentTab(activity, tabSystem, currentTab[0] == 2);

                List<HubCardItem> items = new ArrayList<>();
                if (currentTab[0] == 0) {
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_switch_profile,
                            null,
                            null,
                            "Switch World / Profile",
                            "Choose from saved worlds & instances",
                            "Open",
                            false,
                            false,
                            () -> {
                                // Smoothly transition in-place to the Aurora Glass Profile Selector
                                backBtn.setVisibility(View.VISIBLE);
                                root.findViewById(R.id.aurora_hub_avatar_wrap).setVisibility(View.GONE);
                                profileNameView.setVisibility(View.GONE);
                                segTrack.setVisibility(View.GONE);
                                subviewTitle.setVisibility(View.VISIBLE);
                                subviewTitle.setText("Switch World / Profile");
                                adapter.submitList(actions.buildSwitchProfileItems(dialog));
                                recycler.scrollToPosition(0);
                            }
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_edit_profile,
                            "Edit Active Profile",
                            "Change MC version, Java runtime & renderer",
                            "Open",
                            actions::onEditActiveProfile
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_create_profile,
                            "Create New Profile",
                            "Install Vanilla, Fabric, Forge, Quilt or OptiFine",
                            "Open",
                            actions::onCreateNewProfile
                    ));
                } else if (currentTab[0] == 1) {
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_touch_controls,
                            "Custom Touch Controls",
                            "Design and map on-screen touch buttons & HUD",
                            "Open",
                            actions::onOpenTouchControls
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_gamepad,
                            "Controller / Gamepad Remapper",
                            "Bind Bluetooth & USB controller buttons",
                            "Open",
                            actions::onOpenGamepadRemapper
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_mod_jar,
                            "Execute Mod Installer (.jar)",
                            "Run Forge, NeoForge, Fabric or OptiFine installers",
                            "Run",
                            () -> actions.onExecuteModInstaller(false)
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_custom_jvm,
                            "Execute .jar (Custom JVM Args)",
                            "Launch a standalone Java installer with custom flags",
                            "Run",
                            () -> actions.onExecuteModInstaller(true)
                    ));
                } else {
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_game_folder,
                            "Open Game Directory",
                            "Browse saves, mods, resourcepacks & shaderpacks",
                            "Open",
                            actions::onOpenGameDirectory
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_share_logs,
                            "Share Latest Log File",
                            "Export latestlog.txt for debugging & crash support",
                            "Share",
                            actions::onShareLatestLog
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_edit_profile,
                            "Java Runtimes & Memory",
                            "Manage OpenJDK 8 / 17 / 21 runtimes & RAM allocation",
                            "Open",
                            actions::onOpenJavaSettings
                    ));
                    items.add(new HubCardItem(
                            R.drawable.ic_hub_launcher_settings,
                            "Launcher Settings",
                            "Configure video renderer, controls & app preferences",
                            "Open",
                            actions::onOpenLauncherSettings
                    ));
                }
                adapter.submitList(items);
                recycler.scrollToPosition(0);
            }
        };

        backBtn.setOnClickListener(v -> renderCurrentTab.run());
        tabProfile.setOnClickListener(v -> {
            currentTab[0] = 0;
            renderCurrentTab.run();
        });
        tabMods.setOnClickListener(v -> {
            currentTab[0] = 1;
            renderCurrentTab.run();
        });
        tabSystem.setOnClickListener(v -> {
            currentTab[0] = 2;
            renderCurrentTab.run();
        });

        renderCurrentTab.run();
        showImmersive(activity, dialog);
    }

    /**
     * Opens a standalone Aurora Glass Action Sheet (used for Account Switcher, Profile Switcher, and World Actions).
     */
    public static void showActionSheet(
            @NonNull Activity activity,
            @NonNull String badgeLabel,
            @NonNull String centerTitle,
            @Nullable Bitmap badgeAvatar,
            @NonNull SubListProvider provider) {

        final Dialog dialog = createAuroraDialog(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_aurora_segmented_hub, null, false);
        dialog.setContentView(root);
        applyLandscapeDialogBounds(activity, dialog);

        RoundedImageView avatarView = root.findViewById(R.id.aurora_hub_avatar);
        TextView profileNameView = root.findViewById(R.id.aurora_hub_profile_name);
        View segTrack = root.findViewById(R.id.aurora_hub_seg_track);
        TextView subviewTitle = root.findViewById(R.id.aurora_hub_subview_title);
        ImageButton closeBtn = root.findViewById(R.id.aurora_hub_close_btn);
        RecyclerView recycler = root.findViewById(R.id.aurora_hub_recycler);

        if (badgeAvatar != null) {
            avatarView.setImageBitmap(badgeAvatar);
        } else {
            avatarView.setImageResource(R.drawable.ic_voxel_avatar_default);
        }
        profileNameView.setText(badgeLabel);
        segTrack.setVisibility(View.GONE);
        subviewTitle.setVisibility(View.VISIBLE);
        subviewTitle.setText(centerTitle);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        HubCardAdapter adapter = new HubCardAdapter(activity, dialog);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setAdapter(adapter);
        adapter.submitList(provider.buildItems(dialog));

        showImmersive(activity, dialog);
    }

    private static Dialog createAuroraDialog(@NonNull Context context) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.60f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.setBlurBehindRadius(38);
                window.setAttributes(attrs);
            }
        }
        return dialog;
    }

    private static void applyLandscapeDialogBounds(@NonNull Activity activity, @NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int targetWidth = Math.min(Math.round(dm.widthPixels * 0.80f), Math.round(720 * dm.density));
        int maxHeight = Math.round(dm.heightPixels * 0.88f);
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        View root = dialog.findViewById(R.id.aurora_hub_root);
        if (root instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            ((androidx.constraintlayout.widget.ConstraintLayout) root).setMaxHeight(maxHeight);
        }
    }

    private static void showImmersive(@NonNull Activity activity, @NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        dialog.show();
        if (window != null) {
            window.getDecorView().setSystemUiVisibility(
                    activity.getWindow().getDecorView().getSystemUiVisibility());
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    private static void styleSegmentTab(@NonNull Context context, @NonNull TextView tab, boolean selected) {
        tab.setSelected(selected);
        tab.setTextColor(ContextCompat.getColor(context,
                selected ? R.color.aurora_text_white : R.color.aurora_text_sub));
        Typeface bold = ResourcesCompat.getFont(context, R.font.noto_sans_bold);
        tab.setTypeface(selected && bold != null ? bold : Typeface.DEFAULT,
                selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static class HubCardAdapter extends RecyclerView.Adapter<HubCardAdapter.VH> {
        private final Context mContext;
        private final Dialog mDialog;
        private final List<HubCardItem> mItems = new ArrayList<>();

        HubCardAdapter(@NonNull Context context, @NonNull Dialog dialog) {
            mContext = context;
            mDialog = dialog;
        }

        void submitList(@NonNull List<HubCardItem> items) {
            mItems.clear();
            mItems.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(mContext).inflate(R.layout.item_aurora_hub_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final HubCardItem item = mItems.get(position);
            if (item.customBitmap != null) {
                holder.icon.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                holder.icon.setImageBitmap(item.customBitmap);
            } else if (item.customDrawable != null) {
                holder.icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                holder.icon.setImageDrawable(item.customDrawable);
            } else {
                holder.icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                holder.icon.setImageResource(item.iconRes);
            }

            holder.title.setText(item.title);
            holder.subtitle.setText(item.subtitle);
            holder.openBtn.setText(item.actionLabel);
            holder.openBtn.setBackgroundResource(
                    item.highlighted ? R.drawable.bg_aurora_open_pill_active : R.drawable.bg_aurora_open_pill);

            View.OnClickListener trigger = v -> {
                if (item.dismissOnClick && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                item.onClick.run();
            };
            holder.itemView.setOnClickListener(trigger);
            holder.openBtn.setOnClickListener(trigger);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final RoundedImageView icon;
            final TextView title;
            final TextView subtitle;
            final TextView openBtn;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.aurora_hub_item_icon);
                title = itemView.findViewById(R.id.aurora_hub_item_title);
                subtitle = itemView.findViewById(R.id.aurora_hub_item_subtitle);
                openBtn = itemView.findViewById(R.id.aurora_hub_item_open_btn);
            }
        }
    }
}
