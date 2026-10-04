package com.tac5studios.elementsnexus.holograms;

import java.util.ArrayList;
import java.util.List;

/** One hologram as saved in the store ("holograms" collection, key = id). */
public class Hologram {

    public static class Line {
        /** & colors work. */
        public String text = "";
        /** Animation: these texts take turns. Empty = no animation. */
        public List<String> frames = new ArrayList<>();
        /** Ticks between frames (20 = 1 second). */
        public int interval = 20;
    }

    public String world = "minecraft:overworld";
    public double x, y, z;
    public List<Line> lines = new ArrayList<>();
    /** Text size. 1 = normal. */
    public float scale = 1f;
    /** Blocks between lines (before scale). */
    public float spacing = 0.3f;
    public boolean shadow = true;
    /** Background color as ARGB. 0 = none. */
    public int background = 0;
    /** Wrap text after this many pixels. */
    public int width = 400;
    /** center, fixed, vertical or horizontal. */
    public String facing = "center";
    /** Visible through blocks. */
    public boolean seeThrough = false;
}
