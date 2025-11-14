/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.data

import org.connectbot.util.Colors

/**
 * Built-in color scheme presets for terminal emulation.
 * Each preset defines all 256 colors in the terminal palette.
 */
object ColorSchemePresets {

    /**
     * Solarized Dark - Popular low-contrast color scheme
     * Official colors from https://github.com/altercation/solarized
     */
    val solarizedDark = PresetScheme(
        name = "Solarized Dark",
        description = "Precision colors for machines and people",
        defaultFg = 12, // base0 (#839496)
        defaultBg = 8,  // base03 (#002b36)
        colors = mapOf(
            // ANSI colors (0-15) - Official Solarized Dark mapping
            0 to 0xff073642.toInt(),  // black: base02
            1 to 0xffdc322f.toInt(),  // red
            2 to 0xff859900.toInt(),  // green
            3 to 0xffb58900.toInt(),  // yellow
            4 to 0xff268bd2.toInt(),  // blue
            5 to 0xffd33682.toInt(),  // magenta
            6 to 0xff2aa198.toInt(),  // cyan
            7 to 0xffeee8d5.toInt(),  // white: base2
            8 to 0xff002b36.toInt(),  // bright black: base03
            9 to 0xffcb4b16.toInt(),  // bright red: orange
            10 to 0xff586e75.toInt(), // bright green: base01
            11 to 0xff657b83.toInt(), // bright yellow: base00
            12 to 0xff839496.toInt(), // bright blue: base0
            13 to 0xff6c71c4.toInt(), // bright magenta: violet
            14 to 0xff93a1a1.toInt(), // bright cyan: base1
            15 to 0xfffdf6e3.toInt()  // bright white: base3
        )
    )

    /**
     * Solarized Light - Light variant of Solarized
     * Official colors from https://github.com/altercation/solarized
     */
    val solarizedLight = PresetScheme(
        name = "Solarized Light",
        description = "Solarized with light background",
        defaultFg = 10, // base1 (#586e75)
        defaultBg = 15, // base3 (#fdf6e3)
        colors = mapOf(
            // ANSI colors (0-15) - Official Solarized Light mapping
            // Note: Light variant swaps dark/light base colors
            0 to 0xff073642.toInt(),  // black: base02
            1 to 0xffdc322f.toInt(),  // red
            2 to 0xff859900.toInt(),  // green
            3 to 0xffb58900.toInt(),  // yellow
            4 to 0xff268bd2.toInt(),  // blue
            5 to 0xffd33682.toInt(),  // magenta
            6 to 0xff2aa198.toInt(),  // cyan
            7 to 0xffeee8d5.toInt(),  // white: base2
            8 to 0xff002b36.toInt(),  // bright black: base03
            9 to 0xffcb4b16.toInt(),  // bright red: orange
            10 to 0xff586e75.toInt(), // bright green: base01
            11 to 0xff657b83.toInt(), // bright yellow: base00
            12 to 0xff839496.toInt(), // bright blue: base0
            13 to 0xff6c71c4.toInt(), // bright magenta: violet
            14 to 0xff93a1a1.toInt(), // bright cyan: base1
            15 to 0xfffdf6e3.toInt()  // bright white: base3
        )
    )

    /**
     * Dracula - Dark theme with vibrant colors
     */
    val dracula = PresetScheme(
        name = "Dracula",
        description = "A dark theme for the terminal",
        defaultFg = 7,
        defaultBg = 0,
        colors = mapOf(
            0 to 0xff282a36.toInt(),  // background
            1 to 0xffff5555.toInt(),  // red
            2 to 0xff50fa7b.toInt(),  // green
            3 to 0xfff1fa8c.toInt(),  // yellow
            4 to 0xffbd93f9.toInt(),  // purple
            5 to 0xffff79c6.toInt(),  // pink
            6 to 0xff8be9fd.toInt(),  // cyan
            7 to 0xfff8f8f2.toInt(),  // foreground
            8 to 0xff6272a4.toInt(),  // selection
            9 to 0xffff6e6e.toInt(),  // bright red
            10 to 0xff69ff94.toInt(), // bright green
            11 to 0xfffffa65.toInt(), // bright yellow
            12 to 0xffd6acff.toInt(), // bright purple
            13 to 0xffff92df.toInt(), // bright pink
            14 to 0xffa4ffff.toInt(), // bright cyan
            15 to 0xffffffff.toInt()  // bright white
        )
    )

    /**
     * Nord - Arctic, north-bluish color palette
     */
    val nord = PresetScheme(
        name = "Nord",
        description = "An arctic, north-bluish color palette",
        defaultFg = 7,
        defaultBg = 0,
        colors = mapOf(
            0 to 0xff2e3440.toInt(),  // polar night
            1 to 0xffbf616a.toInt(),  // red
            2 to 0xffa3be8c.toInt(),  // green
            3 to 0xffebcb8b.toInt(),  // yellow
            4 to 0xff81a1c1.toInt(),  // blue
            5 to 0xffb48ead.toInt(),  // purple
            6 to 0xff88c0d0.toInt(),  // cyan
            7 to 0xffe5e9f0.toInt(),  // snow storm
            8 to 0xff4c566a.toInt(),  // polar night bright
            9 to 0xffbf616a.toInt(),  // bright red
            10 to 0xffa3be8c.toInt(), // bright green
            11 to 0xffebcb8b.toInt(), // bright yellow
            12 to 0xff81a1c1.toInt(), // bright blue
            13 to 0xffb48ead.toInt(), // bright purple
            14 to 0xff8fbcbb.toInt(), // bright cyan
            15 to 0xffeceff4.toInt()  // bright white
        )
    )

    /**
     * Gruvbox Dark - Retro groove color scheme
     */
    val gruvboxDark = PresetScheme(
        name = "Gruvbox Dark",
        description = "Retro groove color scheme",
        defaultFg = 15,
        defaultBg = 0,
        colors = mapOf(
            0 to 0xff282828.toInt(),  // dark0
            1 to 0xffcc241d.toInt(),  // red
            2 to 0xff98971a.toInt(),  // green
            3 to 0xffd79921.toInt(),  // yellow
            4 to 0xff458588.toInt(),  // blue
            5 to 0xffb16286.toInt(),  // purple
            6 to 0xff689d6a.toInt(),  // aqua
            7 to 0xffa89984.toInt(),  // gray
            8 to 0xff928374.toInt(),  // dark gray
            9 to 0xfffb4934.toInt(),  // bright red
            10 to 0xffb8bb26.toInt(), // bright green
            11 to 0xfffabd2f.toInt(), // bright yellow
            12 to 0xff83a598.toInt(), // bright blue
            13 to 0xffd3869b.toInt(), // bright purple
            14 to 0xff8ec07c.toInt(), // bright aqua
            15 to 0xffebdbb2.toInt()  // light
        )
    )

    /**
     * Monokai - Sublime Text default color scheme
     */
    val monokai = PresetScheme(
        name = "Monokai",
        description = "Sublime Text inspired theme",
        defaultFg = 7,
        defaultBg = 0,
        colors = mapOf(
            0 to 0xff272822.toInt(),  // background
            1 to 0xfff92672.toInt(),  // red
            2 to 0xffa6e22e.toInt(),  // green
            3 to 0xfff4bf75.toInt(),  // yellow
            4 to 0xff66d9ef.toInt(),  // blue
            5 to 0xffae81ff.toInt(),  // purple
            6 to 0xffa1efe4.toInt(),  // cyan
            7 to 0xfff8f8f2.toInt(),  // foreground
            8 to 0xff75715e.toInt(),  // comment
            9 to 0xfffd971f.toInt(),  // orange
            10 to 0xffa6e22e.toInt(), // bright green
            11 to 0xffe6db74.toInt(), // bright yellow
            12 to 0xff66d9ef.toInt(), // bright blue
            13 to 0xffae81ff.toInt(), // bright purple
            14 to 0xffa1efe4.toInt(), // bright cyan
            15 to 0xfff9f8f5.toInt()  // bright white
        )
    )

    /**
     * Tomorrow Night - Clean, comfortable theme
     */
    val tomorrowNight = PresetScheme(
        name = "Tomorrow Night",
        description = "A clean and comfortable dark theme",
        defaultFg = 7,
        defaultBg = 0,
        colors = mapOf(
            0 to 0xff1d1f21.toInt(),  // background
            1 to 0xffcc6666.toInt(),  // red
            2 to 0xffb5bd68.toInt(),  // green
            3 to 0xfff0c674.toInt(),  // yellow
            4 to 0xff81a2be.toInt(),  // blue
            5 to 0xffb294bb.toInt(),  // purple
            6 to 0xff8abeb7.toInt(),  // cyan
            7 to 0xffc5c8c6.toInt(),  // foreground
            8 to 0xff969896.toInt(),  // bright black
            9 to 0xffcc6666.toInt(),  // bright red
            10 to 0xffb5bd68.toInt(), // bright green
            11 to 0xfff0c674.toInt(), // bright yellow
            12 to 0xff81a2be.toInt(), // bright blue
            13 to 0xffb294bb.toInt(), // bright purple
            14 to 0xff8abeb7.toInt(), // bright cyan
            15 to 0xffffffff.toInt()  // bright white
        )
    )

    /**
     * List of all built-in color scheme presets.
     */
    val builtInSchemes = listOf(
        solarizedDark,
        solarizedLight,
        dracula,
        nord,
        gruvboxDark,
        monokai,
        tomorrowNight
    )

    /**
     * Represents a preset color scheme definition.
     */
    data class PresetScheme(
        val name: String,
        val description: String,
        val defaultFg: Int,
        val defaultBg: Int,
        val colors: Map<Int, Int>
    ) {
        /**
         * Get the full 256-color palette for this scheme.
         * Colors not defined in the preset use the standard Colors.defaults.
         */
        fun getFullPalette(): IntArray {
            val palette = Colors.defaults.clone()
            colors.forEach { (index, value) ->
                if (index in 0..255) {
                    palette[index] = value
                }
            }
            return palette
        }
    }
}
