package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftLauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Discovers real Minecraft saves (.minecraft/saves/* with NBT level.dat & icon.png)
 * and LauncherProfiles, maintaining genuine Continue Exploring and Featured World state.
 */
public class WorldSaveScanner {
    private static final String PREF_NAME = "apex_aurora_dashboard";
    private static final String KEY_FEATURED_ID = "featured_item_id";
    private static final String KEY_LAST_PLAYED_PREFIX = "played_ts_";
    private static final String KEY_DESC_PREFIX = "world_desc_";
    private static final String KEY_ABYSSAL_MIGRATED = "abyssal_v2_seeded";

    public static class DashboardWorldItem {
        public String id;
        public String title;
        public String subtitle;
        public String description;
        public String profileKey;
        @Nullable public String worldFolderName;
        public String versionId;
        public long lastPlayedMillis;
        @Nullable public File iconFile;
        @DrawableRes public int fallbackArtRes;
        public boolean isSavedWorld;
    }

    private static class NbtWorldMeta {
        String levelName;
        long lastPlayed;
        int gameType = -1;
        boolean hardcore = false;
        String versionName;
    }

    public static SharedPreferences getPrefs(@NonNull Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static String getFeaturedId(@NonNull Context context) {
        return getPrefs(context).getString(KEY_FEATURED_ID, "");
    }

    /**
     * Ensures LauncherProfiles is initialized. On a brand-new installation where
     * launcher_profiles.json does not exist yet, seeds starter adventure profiles
     * matching the Abyssal Bioluminescent Ocean Glass (#02) dashboard so every card is playable.
     */
    public static synchronized void ensureProfilesInitialized(@NonNull Context context) {
        File profilesFile = new File(Tools.GAME_PROFILES_FILE);
        SharedPreferences prefs = getPrefs(context);

        if (!profilesFile.exists()) {
            LauncherProfiles.mainProfileJson = new MinecraftLauncherProfiles();
            LauncherProfiles.mainProfileJson.profiles = new HashMap<>();

            long now = System.currentTimeMillis();
            String atlantisKey = UUID.randomUUID().toString();
            MinecraftProfile atlantis = new MinecraftProfile();
            atlantis.name = "Atlantis Depths";
            atlantis.lastVersionId = MinecraftProfile.LATEST_RELEASE;
            LauncherProfiles.mainProfileJson.profiles.put(atlantisKey, atlantis);

            String coralKey = UUID.randomUUID().toString();
            MinecraftProfile coral = new MinecraftProfile();
            coral.name = "Coral Caverns";
            coral.lastVersionId = MinecraftProfile.LATEST_RELEASE;
            LauncherProfiles.mainProfileJson.profiles.put(coralKey, coral);

            String riftKey = UUID.randomUUID().toString();
            MinecraftProfile rift = new MinecraftProfile();
            rift.name = "Abyssal Rift";
            rift.lastVersionId = MinecraftProfile.LATEST_RELEASE;
            LauncherProfiles.mainProfileJson.profiles.put(riftKey, rift);

            LauncherProfiles.write();

            prefs.edit()
                    .putBoolean(KEY_ABYSSAL_MIGRATED, true)
                    .putString(KEY_FEATURED_ID, "profile:" + atlantisKey)
                    .putString(KEY_DESC_PREFIX + "profile:" + atlantisKey,
                            "Submerge into glowing coral canyons\nand ancient sea-lantern ruins.")
                    .putString(KEY_DESC_PREFIX + "profile:" + coralKey,
                            "Explore vibrant bioluminescent reef tunnels and jellyfish grottos.")
                    .putString(KEY_DESC_PREFIX + "profile:" + riftKey,
                            "Venture into deep prismarine trenches lit by sea-lanterns.")
                    .putLong(KEY_LAST_PLAYED_PREFIX + "profile:" + atlantisKey, now)
                    .putLong(KEY_LAST_PLAYED_PREFIX + "profile:" + coralKey, now - (2L * 3600_000L))
                    .putLong(KEY_LAST_PLAYED_PREFIX + "profile:" + riftKey, now - (24L * 3600_000L))
                    .apply();

            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, atlantisKey)
                    .apply();
        } else {
            LauncherProfiles.load();
            // Upgrade starter profile names cleanly if upgrading from v1 starter defaults
            if (!prefs.getBoolean(KEY_ABYSSAL_MIGRATED, false)
                    && LauncherProfiles.mainProfileJson != null
                    && LauncherProfiles.mainProfileJson.profiles != null) {
                boolean changed = false;
                SharedPreferences.Editor ed = prefs.edit().putBoolean(KEY_ABYSSAL_MIGRATED, true);
                for (Map.Entry<String, MinecraftProfile> e : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                    MinecraftProfile p = e.getValue();
                    if (p == null || p.name == null) continue;
                    if ("Echo Peaks".equals(p.name) || "(Default)".equals(p.name)) {
                        p.name = "Atlantis Depths";
                        ed.putString(KEY_DESC_PREFIX + "profile:" + e.getKey(),
                                "Submerge into glowing coral canyons\nand ancient sea-lantern ruins.");
                        changed = true;
                    } else if ("Skyblock Odyssey".equals(p.name)) {
                        p.name = "Coral Caverns";
                        ed.putString(KEY_DESC_PREFIX + "profile:" + e.getKey(),
                                "Explore vibrant bioluminescent reef tunnels and jellyfish grottos.");
                        changed = true;
                    } else if ("Caves & Cliffs".equals(p.name)) {
                        p.name = "Abyssal Rift";
                        ed.putString(KEY_DESC_PREFIX + "profile:" + e.getKey(),
                                "Venture into deep prismarine trenches lit by sea-lanterns.");
                        changed = true;
                    }
                }
                ed.apply();
                if (changed) {
                    LauncherProfiles.write();
                }
            }
        }

        // Guarantee PREF_KEY_CURRENT_PROFILE always points to an existing profile key
        String currentKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        if (LauncherProfiles.mainProfileJson != null
                && !LauncherProfiles.mainProfileJson.profiles.isEmpty()
                && !LauncherProfiles.mainProfileJson.profiles.containsKey(currentKey)) {
            String firstKey = LauncherProfiles.mainProfileJson.profiles.keySet().iterator().next();
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, firstKey)
                    .apply();
        }
    }

    @NonNull
    public static List<DashboardWorldItem> scanAllItems(@NonNull Context context) {
        ensureProfilesInitialized(context);
        SharedPreferences prefs = getPrefs(context);
        List<DashboardWorldItem> results = new ArrayList<>();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) {
            return results;
        }

        String activeProfileKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");

        Set<String> scannedSavesDirs = new HashSet<>();

        // 1. Scan real Minecraft singleplayer world saves across all profile game directories
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            String profileKey = entry.getKey();
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;

            File gameDir = Tools.getGameDirPath(profile);
            File savesDir = new File(gameDir, "saves");
            String canonicalKey = savesDir.getAbsolutePath();
            if (scannedSavesDirs.contains(canonicalKey)) continue;
            scannedSavesDirs.add(canonicalKey);

            String ownerProfileKey = profileKey;
            MinecraftProfile activeProf = LauncherProfiles.mainProfileJson.profiles.get(activeProfileKey);
            if (activeProf != null && Tools.getGameDirPath(activeProf).getAbsolutePath().equals(gameDir.getAbsolutePath())) {
                ownerProfileKey = activeProfileKey;
                profile = activeProf;
            }

            if (savesDir.exists() && savesDir.isDirectory()) {
                File[] worldFolders = savesDir.listFiles();
                if (worldFolders != null) {
                    for (File wf : worldFolders) {
                        if (!wf.isDirectory()) continue;
                        File levelDat = new File(wf, "level.dat");
                        if (!levelDat.exists() && !new File(wf, "region").exists()) continue;

                        NbtWorldMeta meta = levelDat.exists() ? readLevelDat(levelDat) : null;
                        DashboardWorldItem item = new DashboardWorldItem();
                        item.id = "world:" + wf.getAbsolutePath();
                        item.title = (meta != null && Tools.isValidString(meta.levelName))
                                ? meta.levelName : wf.getName();
                        item.profileKey = ownerProfileKey;
                        item.worldFolderName = wf.getName();
                        item.versionId = (meta != null && Tools.isValidString(meta.versionName))
                                ? meta.versionName : formatVersionLabel(context, profile.lastVersionId);
                        long diskTs = (meta != null && meta.lastPlayed > 0) ? meta.lastPlayed : wf.lastModified();
                        long prefTs = prefs.getLong(KEY_LAST_PLAYED_PREFIX + item.id, 0L);
                        item.lastPlayedMillis = Math.max(diskTs, prefTs);

                        File iconPng = new File(wf, "icon.png");
                        item.iconFile = iconPng.exists() ? iconPng : null;
                        item.fallbackArtRes = pickFallbackArtwork(item.title);
                        item.isSavedWorld = true;

                        String modeLabel = formatGameMode(meta);
                        item.description = prefs.getString(KEY_DESC_PREFIX + item.id,
                                modeLabel + " • Minecraft " + item.versionId);
                        item.subtitle = formatRelativeTime(item.lastPlayedMillis);
                        results.add(item);
                    }
                }
            }
        }

        // 2. Include all Launcher Profiles so every Vanilla/Fabric/Forge/Modpack profile is playable & visible
        int idx = 0;
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            String profileKey = entry.getKey();
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;

            DashboardWorldItem item = new DashboardWorldItem();
            item.id = "profile:" + profileKey;
            String profName = (Tools.isValidString(profile.name) && !"New".equalsIgnoreCase(profile.name))
                    ? profile.name : formatVersionLabel(context, profile.lastVersionId);
            item.title = profName;
            item.profileKey = profileKey;
            item.worldFolderName = null;
            item.versionId = formatVersionLabel(context, profile.lastVersionId);
            long prefTs = prefs.getLong(KEY_LAST_PLAYED_PREFIX + item.id, 0L);
            item.lastPlayedMillis = prefTs > 0 ? prefTs : (System.currentTimeMillis() - (idx + 1L) * 3600_000L);
            item.iconFile = null;
            item.fallbackArtRes = pickFallbackArtwork(item.title);
            item.isSavedWorld = false;

            String defaultDesc = "Submerge into glowing coral canyons\nand ancient sea-lantern ruins.";
            if (!"Atlantis Depths".equalsIgnoreCase(item.title)) {
                defaultDesc = "Instance • Minecraft " + item.versionId;
            }
            item.description = prefs.getString(KEY_DESC_PREFIX + item.id, defaultDesc);
            item.subtitle = formatRelativeTime(item.lastPlayedMillis);
            results.add(item);
            idx++;
        }

        Collections.sort(results, new Comparator<DashboardWorldItem>() {
            @Override
            public int compare(DashboardWorldItem a, DashboardWorldItem b) {
                return Long.compare(b.lastPlayedMillis, a.lastPlayedMillis);
            }
        });

        return results;
    }

    @Nullable
    public static DashboardWorldItem resolveFeaturedItem(@NonNull Context context,
                                                         @NonNull List<DashboardWorldItem> allItems) {
        if (allItems.isEmpty()) return null;
        String activeProfileKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        String featuredId = getPrefs(context).getString(KEY_FEATURED_ID, "");

        for (DashboardWorldItem item : allItems) {
            if (item.id.equals(featuredId) && item.profileKey.equals(activeProfileKey)) {
                return item;
            }
        }
        for (DashboardWorldItem item : allItems) {
            if (!item.isSavedWorld && item.profileKey.equals(activeProfileKey)) {
                return item;
            }
        }
        return allItems.get(0);
    }

    public static void setFeaturedAndSelectProfile(@NonNull Context context, @NonNull DashboardWorldItem item) {
        getPrefs(context).edit().putString(KEY_FEATURED_ID, item.id).apply();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, item.profileKey)
                .apply();
    }

    public static void recordPlayed(@NonNull Context context, @NonNull String itemId) {
        getPrefs(context).edit()
                .putLong(KEY_LAST_PLAYED_PREFIX + itemId, System.currentTimeMillis())
                .apply();
    }

    @DrawableRes
    public static int pickFallbackArtwork(@Nullable String title) {
        if (title == null) return R.drawable.hero_echo_peaks;
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("coral") || lower.contains("cavern") || lower.contains("reef")
                || lower.contains("sky") || lower.contains("island") || lower.contains("odyssey")) {
            return R.drawable.thumb_skyblock_odyssey;
        }
        if (lower.contains("rift") || lower.contains("abyss") || lower.contains("trench")
                || lower.contains("cave") || lower.contains("cliff") || lower.contains("nether")
                || lower.contains("deep") || lower.contains("forge")) {
            return R.drawable.thumb_caves_cliffs;
        }
        return R.drawable.hero_echo_peaks;
    }

    @NonNull
    public static String formatVersionLabel(@NonNull Context context, @Nullable String versionId) {
        if (!Tools.isValidString(versionId)) return "Latest Release";
        if (MinecraftProfile.LATEST_RELEASE.equalsIgnoreCase(versionId)) {
            return context.getString(R.string.profiles_latest_release);
        }
        if (MinecraftProfile.LATEST_SNAPSHOT.equalsIgnoreCase(versionId)) {
            return context.getString(R.string.profiles_latest_snapshot);
        }
        return versionId;
    }

    @NonNull
    public static String formatRelativeTime(long epochMillis) {
        if (epochMillis <= 0) return "Ready to play";
        long diffMs = Math.max(0L, System.currentTimeMillis() - epochMillis);
        long minutes = diffMs / 60_000L;
        if (minutes < 2) return "Last played just now";
        if (minutes < 60) return "Last played " + minutes + "m ago";
        long hours = minutes / 60L;
        if (hours < 24) return "Last played " + hours + "h ago";
        long days = hours / 24L;
        if (days < 30) return "Last played " + days + "d ago";
        long months = days / 30L;
        return "Last played " + months + "mo ago";
    }

    @NonNull
    private static String formatGameMode(@Nullable NbtWorldMeta meta) {
        if (meta == null) return "Singleplayer World";
        if (meta.hardcore) return "Hardcore World";
        switch (meta.gameType) {
            case 0: return "Survival World";
            case 1: return "Creative World";
            case 2: return "Adventure World";
            case 3: return "Spectator World";
            default: return "Singleplayer World";
        }
    }

    @NonNull
    public static File importWorldZip(@NonNull Context context, @NonNull Uri zipUri,
                                      @NonNull File targetSavesDir) throws IOException {
        FileUtils.ensureDirectory(targetSavesDir);
        String displayName = Tools.getFileName(context, zipUri);
        if (displayName == null) displayName = "Imported_World";
        if (displayName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            displayName = displayName.substring(0, displayName.length() - 4);
        }
        displayName = displayName.replaceAll("[^a-zA-Z0-9_\\- .]", "_").trim();
        if (displayName.isEmpty()) displayName = "Imported_World";

        File destWorldDir = new File(targetSavesDir, displayName);
        int suffix = 1;
        while (destWorldDir.exists()) {
            destWorldDir = new File(targetSavesDir, displayName + "_" + suffix);
            suffix++;
        }
        FileUtils.ensureDirectory(destWorldDir);

        InputStream rawIn = context.getContentResolver().openInputStream(zipUri);
        if (rawIn == null) throw new IOException("Unable to open selected world archive");
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            ZipEntry entry;
            String destCanonical = destWorldDir.getCanonicalPath() + File.separator;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File outFile = new File(destWorldDir, name);
                if (!outFile.getCanonicalPath().startsWith(destCanonical)) {
                    throw new IOException("Invalid zip entry path: " + name);
                }
                if (entry.isDirectory()) {
                    FileUtils.ensureDirectory(outFile);
                } else {
                    FileUtils.ensureParentDirectory(outFile);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        IOUtils.copy(zis, fos);
                    }
                }
                zis.closeEntry();
            }
        }

        if (!new File(destWorldDir, "level.dat").exists()) {
            File[] children = destWorldDir.listFiles();
            if (children != null && children.length == 1 && children[0].isDirectory()
                    && new File(children[0], "level.dat").exists()) {
                File nested = children[0];
                File[] nestedFiles = nested.listFiles();
                if (nestedFiles != null) {
                    for (File nf : nestedFiles) {
                        //noinspection ResultOfMethodCallIgnored
                        nf.renameTo(new File(destWorldDir, nf.getName()));
                    }
                }
                //noinspection ResultOfMethodCallIgnored
                nested.delete();
            }
        }

        return destWorldDir;
    }

    @Nullable
    private static NbtWorldMeta readLevelDat(@NonNull File levelDat) {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(levelDat))))) {
            int rootType = in.readByte() & 0xFF;
            if (rootType != 10) return null;
            skipNbtString(in);
            NbtWorldMeta meta = new NbtWorldMeta();
            readCompound(in, meta, "");
            return meta;
        } catch (Exception e) {
            Log.w("WorldSaveScanner", "Could not parse level.dat: " + levelDat.getName());
            return null;
        }
    }

    private static void readCompound(DataInputStream in, NbtWorldMeta meta, String parentName) throws IOException {
        while (true) {
            int tagType = in.readByte() & 0xFF;
            if (tagType == 0) break;
            String name = in.readUTF();
            if (tagType == 10) {
                readCompound(in, meta, name);
            } else if (tagType == 8 && "LevelName".equals(name)) {
                meta.levelName = in.readUTF();
            } else if (tagType == 8 && "Name".equals(name) && "Version".equals(parentName)) {
                meta.versionName = in.readUTF();
            } else if (tagType == 4 && "LastPlayed".equals(name)) {
                meta.lastPlayed = in.readLong();
            } else if (tagType == 3 && "GameType".equals(name)) {
                meta.gameType = in.readInt();
            } else if (tagType == 1 && "hardcore".equals(name)) {
                meta.hardcore = in.readByte() != 0;
            } else {
                skipPayload(in, tagType);
            }
        }
    }

    private static void skipNbtString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        in.skipBytes(len);
    }

    private static void skipPayload(DataInputStream in, int tagType) throws IOException {
        switch (tagType) {
            case 1: in.readByte(); break;
            case 2: in.readShort(); break;
            case 3: in.readInt(); break;
            case 4: in.readLong(); break;
            case 5: in.readFloat(); break;
            case 6: in.readDouble(); break;
            case 7: {
                int len = in.readInt();
                skipFully(in, len);
                break;
            }
            case 8: skipNbtString(in); break;
            case 9: {
                int elemType = in.readByte() & 0xFF;
                int count = in.readInt();
                for (int i = 0; i < count; i++) skipPayload(in, elemType);
                break;
            }
            case 10: {
                while (true) {
                    int nested = in.readByte() & 0xFF;
                    if (nested == 0) break;
                    skipNbtString(in);
                    skipPayload(in, nested);
                }
                break;
            }
            case 11: {
                int len = in.readInt();
                skipFully(in, len * 4L);
                break;
            }
            case 12: {
                int len = in.readInt();
                skipFully(in, len * 8L);
                break;
            }
            default:
                throw new IOException("Unknown NBT tag: " + tagType);
        }
    }

    private static void skipFully(DataInputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) throw new IOException("Unexpected EOF in NBT");
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}
