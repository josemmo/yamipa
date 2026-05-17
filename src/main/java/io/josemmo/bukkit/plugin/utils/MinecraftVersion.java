package io.josemmo.bukkit.plugin.utils;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final @NotNull Pattern VERSION_PATTERN = Pattern.compile("([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?.*");

    public static final @NotNull MinecraftVersion V26_1 = new MinecraftVersion("26.1");
    public static final @NotNull MinecraftVersion V1_21_9 = new MinecraftVersion("1.21.9");
    public static final @NotNull MinecraftVersion V1_21_6 = new MinecraftVersion("1.21.6");
    public static final @NotNull MinecraftVersion V1_20_5 = new MinecraftVersion("1.20.5");
    public static final @NotNull MinecraftVersion V1_19_4 = new MinecraftVersion("1.19.4");
    public static final @NotNull MinecraftVersion V1_19_3 = new MinecraftVersion("1.19.3");
    public static final @NotNull MinecraftVersion V1_19 = new MinecraftVersion("1.19");
    public static final @NotNull MinecraftVersion V1_17_1 = new MinecraftVersion("1.17.1");
    public static final @NotNull MinecraftVersion V1_17 = new MinecraftVersion("1.17");
    /** Current Minecraft version running in this server */
    public static final @NotNull MinecraftVersion CURRENT = new MinecraftVersion(Bukkit.getVersion());

    /** Major number (e.g., <code>1</code> for "1.21.11") */
    private final int major;
    /** Minor number (e.g., <code>21</code> for "1.21.11") */
    private final int minor;
    /** Patch number or <code>0</code> if omitted (e.g., <code>11</code> for "1.21.11") */
    private final int patch;

    private MinecraftVersion(@NotNull String rawVersion) {
        // Extract Minecraft version
        // e.g., "1.21.11-69-94d0c97 (MC: 1.21.11)" becomes "1.21.11)"
        String version = rawVersion.contains(" (MC: ") ?
            rawVersion.substring(rawVersion.lastIndexOf(" (MC: ")+6) :
            rawVersion;

        // Extract version parts
        Matcher match = VERSION_PATTERN.matcher(version);
        if (!match.matches()) {
            throw new IllegalArgumentException("Unexpected Minecraft version: " + version);
        }
        major = Integer.parseInt(match.group(1));
        minor = Integer.parseInt(match.group(2));
        String rawPatch = match.group(3);
        patch = (rawPatch == null) ? 0 : Integer.parseInt(rawPatch);
    }

    public boolean isAtLeast(@NotNull MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (o instanceof MinecraftVersion) {
            MinecraftVersion other = (MinecraftVersion) o;
            return other.major == this.major && other.minor == this.minor && other.patch == this.patch;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public @NotNull String toString() {
        return major + "." + minor + "." + patch;
    }
}
