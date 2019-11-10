/*
 * Colormatic
 * Copyright (C) 2019  Thalia Nero
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.kvverti.colormatic.colormap;

import io.github.kvverti.colormatic.properties.ColormapProperties;
import io.github.kvverti.colormatic.properties.ColormapProperties.ColumnBounds;
import io.github.kvverti.colormatic.properties.HexColor;

import java.util.Random;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.level.ColorResolver;

public class BiomeColormap {

    private static final Random GRID_RANDOM = new Random(47L);

    private final ColormapProperties properties;
    private final NativeImage colormap;
    private transient final int defaultColor;

    public BiomeColormap(ColormapProperties props, NativeImage image) {
        properties = props;
        colormap = image;
        HexColor col = props.getColor();
        if(col != null) {
            defaultColor = col.get();
        } else {
            defaultColor = computeDefaultColor(props);
        }
    }

    private final int computeDefaultColor(ColormapProperties props) {
        switch(props.getFormat()) {
            case VANILLA:
                return colormap.getPixelRgba(128, 128);
            case GRID:
                try {
                    int x = props.getColumn(Biomes.PLAINS).column;
                    int y = MathHelper.clamp(63 - props.getOffset(), 0, colormap.getHeight() - 1);
                    return colormap.getPixelRgba(x, y);
                } catch(IllegalArgumentException e) {
                    return 0xffffffff;
                }
            case FIXED:
                return 0xffffffff;
        }
        throw new AssertionError();
    }

    public ColormapProperties getProperties() {
        return properties;
    }

    /**
     * Returns a color given by the custom colormap for the given biome
     * temperature and humidity.
     */
    private int getColor(double temp, double rain) {
        rain *= temp;
        int x = (int)((1.0D - temp) * 255.0D);
        int y = (int)((1.0D - rain) * 255.0D);
        if(x >= colormap.getWidth() || y >= colormap.getHeight()) {
            return 0xffff00ff;
        }
        return colormap.getPixelRgba(x, y);
    }

    /**
     * Returns a color given by the custom colormap for the given biome.
     */
    public int getColor(Biome biome) {
        return getColor(biome, null);
    }

    /**
     * Returns a color given by the custom colormap for the given biome and
     * BlockPos.
     */
    public int getColor(Biome biome, BlockPos pos) {
        switch(properties.getFormat()) {
            case VANILLA:
                double temp = pos == null ? biome.getTemperature() : biome.getTemperature(pos);
                temp = MathHelper.clamp(temp, 0.0f, 1.0f);
                double rain = MathHelper.clamp(biome.getRainfall(), 0.0F, 1.0F);
                return getColor(temp, rain);
            case GRID:
                ColumnBounds cb = properties.getColumn(biome);
                int x;
                int y;
                if(pos != null) {
                    double frac = Biome.FOLIAGE_NOISE.sample(pos.getX() * 0.0225, pos.getZ() * 0.0225, false);
                    frac = (frac + 1.0) / 2; // normalize
                    x = cb.column + (int)(frac * cb.count);
                    y = pos.getY() - properties.getOffset();
                    int variance = properties.getVariance();
                    GRID_RANDOM.setSeed(pos.getX() * 31L + pos.getZ());
                    y += GRID_RANDOM.nextInt(variance * 2 + 1) - variance;
                } else {
                    x = cb.column;
                    y = 63 - properties.getOffset();
                }
                x %= colormap.getWidth();
                y = MathHelper.clamp(y, 0, colormap.getHeight() - 1);
                return colormap.getPixelRgba(x, y);
            case FIXED:
                return getDefaultColor();
        }
        throw new AssertionError();
    }

    /**
     * Returns the default color given by the custom colormap.
     */
    public int getDefaultColor() {
        return defaultColor;
    }

    public static class SingleColormaticResolver implements ColorResolver {

        final BlockPos.Mutable pos = new BlockPos.Mutable();
        // must be set before calling getColor()!
        BiomeColormap colormap = null;

        @Override
        public synchronized int getColor(Biome biome, double x, double z) {
            pos.setX((int)x);
            pos.setZ((int)z);
            return colormap.getColor(biome, pos);
        }
    }

    public static final SingleColormaticResolver colormaticResolver = new SingleColormaticResolver();

    /**
     * Retrieves the biome coloring for the given block position, taking into
     * account the client's biome blend options If either `world` or `pos` is
     * null, this returns the colormap's default color.
     */
    public static int getBiomeColor(BlockRenderView world, BlockPos pos, BiomeColormap colormap) {
        if(world == null || pos == null) {
            return colormap.getDefaultColor();
        }
        colormaticResolver.pos.setY(pos.getY());
        colormaticResolver.colormap = colormap;
        return world.method_23752(pos, colormaticResolver);
        // int r = 0;
        // int g = 0;
        // int b = 0;
        // int radius = MinecraftClient.getInstance().options.biomeBlendRadius;
        // Iterable<BlockPos> coll = BlockPos.iterate(
        //     pos.getX() - radius, pos.getY(), pos.getZ() - radius,
        //     pos.getX() + radius, pos.getY(), pos.getZ() + radius);
        // for(BlockPos curpos : coll) {
        //     int color = colormap.getColor(world.getBiome(curpos), curpos);
        //     r += (color & 0xff0000) >> 16;
        //     g += (color & 0x00ff00) >> 8;
        //     b += (color & 0x0000ff);
        // }
        // int posCount = (radius * 2 + 1) * (radius * 2 + 1);
        // return ((r / posCount & 255) << 16) | ((g / posCount & 255) << 8) | (b / posCount & 255);
    }
}
