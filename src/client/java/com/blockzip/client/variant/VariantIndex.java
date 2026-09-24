package com.blockzip.client.variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WeatheringCopper;

/**
 * 方块变种集合索引。
 *
 * <p>把“同一个方块的各种形态”归成一组：半砖、楼梯，以及随时间变化的状态（铜的
 * 斑驳/锈蚀/氧化，以及打蜡与否）。分组来源按优先级：</p>
 * <ol>
 *   <li>原版氧化链（{@link WeatheringCopper}）与原版打蜡表（{@link HoneycombItem}）</li>
 *   <li>原版方块家族（{@link BlockFamilies}）中的半砖与楼梯</li>
 *   <li>名字启发式，用来兼容其它模组添加的半砖/楼梯/氧化变种</li>
 * </ol>
 */
public final class VariantIndex {
	private static Map<Item, List<Item>> groups;

	private static final String[] WEATHER_PREFIXES = { "exposed_", "weathered_", "oxidized_" };

	/** 0 = 本体，1 = 半砖，2 = 楼梯。 */
	private static int formIndex(Block block) {
		if (block instanceof SlabBlock) {
			return 1;
		}

		if (block instanceof StairBlock) {
			return 2;
		}

		return 0;
	}

	private static boolean isWaxed(Block block) {
		try {
			return HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(block);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 沿氧化链往回数，未氧化为 0。涂蜡方块先换算回未涂蜡的形态，否则查不到链条。 */
	private static int weatherIndex(Block block) {
		int index = 0;
		Block current = unwax(block);

		for (int guard = 0; guard < 8; guard++) {
			Optional<Block> previous;

			try {
				previous = WeatheringCopper.getPrevious(current);
			} catch (Throwable ignored) {
				break;
			}

			if (previous.isEmpty()) {
				break;
			}

			current = previous.get();
			index++;
		}

		return index;
	}

	private static Block unwax(Block block) {
		try {
			Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block);
			return unwaxed != null ? unwaxed : block;
		} catch (Throwable ignored) {
			return block;
		}
	}

	private static final Comparator<Block> VARIANT_ORDER = Comparator
			.comparingInt((Block block) -> isWaxed(block) ? 1 : 0)
			.thenComparingInt(VariantIndex::weatherIndex)
			.thenComparingInt(VariantIndex::formIndex)
			.thenComparing(block -> String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));

	private VariantIndex() {
	}

	/** 提前触发类加载；索引本身等第一次真正用到时再构建。 */
	public static void warmUp() {
	}

	/** 返回该物品所在的变种集合（含它自己）。没有变种时返回空列表。 */
	public static synchronized List<Item> variantsOf(Item item) {
		if (item == null || item == Items.AIR) {
			return List.of();
		}

		if (groups == null) {
			groups = build();
		}

		return groups.getOrDefault(item, List.of());
	}

	public static List<Item> variantsOf(ItemStack stack) {
		return stack == null || stack.isEmpty() ? List.of() : variantsOf(stack.getItem());
	}

	private static Map<Item, List<Item>> build() {
		UnionFind unionFind = new UnionFind();

		// 1) 氧化链：铜块 -> 斑驳的铜块 -> 锈蚀的铜块 -> 氧化的铜块
		// 2) 打蜡：铜块 <-> 涂蜡铜块
		try {
			for (Map.Entry<Block, Block> entry : WeatheringCopper.NEXT_BY_BLOCK.get().entrySet()) {
				unionFind.union(entry.getKey(), entry.getValue());
			}

			for (Map.Entry<Block, Block> entry : HoneycombItem.WAXABLES.get().entrySet()) {
				unionFind.union(entry.getKey(), entry.getValue());
			}
		} catch (Throwable ignored) {
			// 没有这两张表也不影响半砖/楼梯的分组
		}

		// 3) 原版方块家族，只取半砖与楼梯（按钮、门、栅栏等按需求排除）
		try {
			BlockFamilies.getAllFamilies().forEach(family -> {
				Block base = family.getBaseBlock();

				if (base == null) {
					return;
				}

				Block slab = family.get(BlockFamily.Variant.SLAB);
				Block stairs = family.get(BlockFamily.Variant.STAIRS);

				if (slab != null) {
					unionFind.union(base, slab);
				}

				if (stairs != null) {
					unionFind.union(base, stairs);
				}
			});
		} catch (Throwable ignored) {
			// 某个版本没有方块家族表也不影响其它分组方式
		}

		// 4) 名字启发式：覆盖其它模组里的半砖、楼梯、氧化/打蜡方块
		Map<String, List<Block>> sameCore = new HashMap<>();

		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);

			if (id == null) {
				continue;
			}

			String path = id.getPath();
			Block stem = null;

			if (path.endsWith("_slab")) {
				stem = lookup(id.getNamespace(), path.substring(0, path.length() - 5));
			} else if (path.endsWith("_stairs")) {
				stem = lookup(id.getNamespace(), path.substring(0, path.length() - 7));
			}

			if (stem == null && path.endsWith("_slab")) {
				stem = lookup(id.getNamespace(), path.substring(0, path.length() - 5) + "s");
			} else if (stem == null && path.endsWith("_stairs")) {
				stem = lookup(id.getNamespace(), path.substring(0, path.length() - 7) + "s");
			}

			if (stem != null) {
				unionFind.union(stem, block);
			}

			String core = coreName(path);
			sameCore.computeIfAbsent(id.getNamespace() + ":" + core, key -> new ArrayList<>()).add(block);
		}

		for (List<Block> blocks : sameCore.values()) {
			if (blocks.size() < 2) {
				continue;
			}

			Block first = blocks.get(0);

			for (Block block : blocks) {
				unionFind.union(first, block);
			}
		}

		// 归组并按“本体 -> 半砖 -> 楼梯 / 未氧化 -> 氧化 / 未打蜡 -> 打蜡”排序
		Map<Block, List<Block>> classes = new LinkedHashMap<>();

		for (Block block : unionFind.elements()) {
			classes.computeIfAbsent(unionFind.find(block), key -> new ArrayList<>()).add(block);
		}

		Map<Item, List<Item>> result = new HashMap<>();

		for (List<Block> members : classes.values()) {
			if (members.size() < 2) {
				continue;
			}

			members.sort(VARIANT_ORDER);
			List<Item> items = new ArrayList<>(members.size());

			for (Block block : members) {
				Item item = block.asItem();

				if (item != Items.AIR) {
					items.add(item);
				}
			}

			if (items.size() < 2) {
				continue;
			}

			List<Item> immutable = List.copyOf(items);

			for (Item item : items) {
				result.put(item, immutable);
			}
		}

		return result;
	}

	/**
	 * 把方块 id 归一成“同一个方块”的名字：去掉 waxed_ / exposed_ / weathered_ / oxidized_ 前缀，
	 * 再去掉结尾的 _block，于是 copper_block、exposed_copper、waxed_weathered_copper 都归到 copper。
	 */
	private static String coreName(String path) {
		String stripped = path;

		if (stripped.startsWith("waxed_")) {
			stripped = stripped.substring(6);
		}

		for (String prefix : WEATHER_PREFIXES) {
			if (stripped.startsWith(prefix)) {
				stripped = stripped.substring(prefix.length());
				break;
			}
		}

		if (stripped.endsWith("_block") && stripped.length() > "_block".length()) {
			stripped = stripped.substring(0, stripped.length() - "_block".length());
		}

		return stripped;
	}

	/**
	 * 按 id 找方块。注意 {@code DefaultedRegistry#getValue} 对不存在的 id 会返回默认值（空气），
	 * 所以必须再核对一次 id，否则所有半砖/楼梯都会被并到同一组。
	 */
	private static Block lookup(String namespace, String path) {
		Identifier id = Identifier.tryParse(namespace + ":" + path);

		if (id == null) {
			return null;
		}

		Block block = BuiltInRegistries.BLOCK.getValue(id);
		return block != null && id.equals(BuiltInRegistries.BLOCK.getKey(block)) ? block : null;
	}

	private static final class UnionFind {
		private final Map<Block, Block> parent = new HashMap<>();

		void union(Block first, Block second) {
			Block rootFirst = find(first);
			Block rootSecond = find(second);

			if (!rootFirst.equals(rootSecond)) {
				this.parent.put(rootSecond, rootFirst);
			}
		}

		Block find(Block block) {
			Block current = this.parent.computeIfAbsent(block, key -> key);

			if (!current.equals(block)) {
				current = find(current);
				this.parent.put(block, current);
			}

			return current;
		}

		Iterable<Block> elements() {
			return new ArrayList<>(this.parent.keySet());
		}
	}
}
