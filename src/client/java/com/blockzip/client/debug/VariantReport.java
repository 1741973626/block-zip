package com.blockzip.client.debug;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.blockzip.client.variant.VariantIndex;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * 无界面自检：直接启动 Minecraft 的注册表，然后打印变种集合的分组结果。
 * 用 {@code gradlew variantReport} 运行，不需要开游戏窗口。
 */
public final class VariantReport {
	private VariantReport() {
	}

	public static void main(String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		System.out.println("==== Block_zip variant report ====");
		check("stone_bricks", Items.STONE_BRICKS, 3);
		check("stone", Items.STONE, 3);
		check("oak_planks", Items.OAK_PLANKS, 3);
		check("copper_block", Items.COPPER_BLOCK.asList().get(0), 8);
		check("cut_copper", Items.CUT_COPPER.asList().get(0), 24);
		check("cut_copper_stairs", Items.CUT_COPPER_STAIRS.asList().get(0), 24);
		check("copper_grate", Items.COPPER_GRATE.asList().get(0), 8);
		check("dirt", Items.DIRT, 0);
		check("diamond_sword", Items.DIAMOND_SWORD, 0);

		Set<List<Item>> groups = new HashSet<>();
		int singleton = 0;

		for (Block block : BuiltInRegistries.BLOCK) {
			List<Item> variants = VariantIndex.variantsOf(block.asItem());

			if (variants.size() >= 2) {
				groups.add(variants);
			} else {
				singleton++;
			}
		}

		System.out.println("groups (>=2 variants) = " + groups.size());
		System.out.println("blocks without a group = " + singleton);

		int threes = 0;
		int big = 0;

		for (List<Item> group : groups) {
			if (group.size() == 3) {
				threes++;
			}

			if (group.size() > 3) {
				big++;
			}
		}

		System.out.println("groups of exactly 3 = " + threes + ", groups bigger than 3 = " + big);

		System.out.println("==== sample: stone_bricks ====");
		dump(Items.STONE_BRICKS);
		System.out.println("==== sample: copper_block ====");
		dump(Items.COPPER_BLOCK.asList().get(0));
		System.out.println("==== done ====");
	}

	private static void dump(Item item) {
		for (Item variant : VariantIndex.variantsOf(item)) {
			System.out.println("  " + variant.getDescriptionId());
		}
	}

	private static void check(String name, Item item, int expected) {
		List<Item> variants = VariantIndex.variantsOf(item);
		String status = variants.size() == expected ? "OK " : "BAD";
		System.out.println("[" + status + "] " + name + " -> " + variants.size() + " (expected " + expected + ")");
	}
}
