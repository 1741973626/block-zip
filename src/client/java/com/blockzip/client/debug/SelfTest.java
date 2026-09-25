package com.blockzip.client.debug;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.blockzip.client.BlockZipClient;
import com.blockzip.client.mixin.AbstractContainerScreenAccessor;
import com.blockzip.client.ui.VariantPanel;
import com.blockzip.client.variant.VariantIndex;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * 诊断用自检：只在设置了环境变量 {@code BLOCKZIP_SELFTEST=1} 时运行。
 *
 * <p>进入世界后按顺序跑一遍：分组结果 → 世界里“按住+滚轮+松开取出” → 界面里“展开+滚轮选择”，
 * 每步都把结果打进日志，最后把面板留在屏幕上方便截图。</p>
 *
 * <p>1.21.1 适配：按键/鼠标事件都是 GLFW 整数（没有 26.3 的 KeyEvent/MouseButtonEvent），
 * 鼠标位置改成用 {@code GLFW.glfwSetCursorPos} 真的移动光标
 * （1.21.1 的 {@code MouseHandler#onMove} 是私有的，外部调不到）。</p>
 */
public final class SelfTest {
	private static final String TAG = "[BLOCKZIP-SELFTEST]";
	/** 反引号键的 GLFW 扫描码，模拟真实按键时用。 */
	private static final int GRAVE_SCANCODE = 0x29;
	private static boolean finished;
	private static boolean enabled;
	private static int phaseTicks;
	private static int phase;
	private static int sinceGive;

	private SelfTest() {
	}

	public static void tick(Minecraft minecraft) {
		if (finished) {
			return;
		}

		if (!enabled) {
			if (System.getenv("BLOCKZIP_SELFTEST") == null) {
				finished = true;
				return;
			}

			enabled = true;
		}

		if (minecraft.player == null || minecraft.gameMode == null) {
			return;
		}

		phaseTicks++;

		switch (phase) {
			case 0 -> waitForItem(minecraft);
			case 1 -> openWorldPanel(minecraft);
			case 2 -> repeatKeyPresses();
			case 3 -> scrollInWorld();
			case 4 -> releaseKey();
			case 5 -> afterRelease(minecraft);
			case 6 -> moveCursorOverSlot(minecraft);
			case 7 -> noOpenWhenMouseOffSlot(minecraft);
			case 8 -> openScreenPanel(minecraft);
			case 9 -> keepScreenPanel(minecraft);
			case 10 -> leaveWorldPanelOpen(minecraft);
			default -> finished = true;
		}
	}

	/** 第 0 步：用正规途径（本地 + 发包）把铜块放进快捷栏，并等服务器确认。 */
	private static void waitForItem(Minecraft minecraft) {
		Item copper = Items.COPPER_BLOCK;
		var inventory = minecraft.player.getInventory();
		int hotbar = inventory.selected;

		if (inventory.getSelected().getItem() != copper) {
			if (sinceGive >= 20) {
				sinceGive = 0;
				inventory.setItem(hotbar, new ItemStack(copper));
				minecraft.gameMode.handleCreativeModeItemAdd(new ItemStack(copper), 36 + hotbar);
				inventory.player.inventoryMenu.broadcastChanges();
			}

			sinceGive++;
			return;
		}

		report();
		log("player creative = " + minecraft.player.isCreative() + ", gameType = " + minecraft.gameMode.getPlayerMode());
		minecraft.player.getAbilities().instabuild = true;
		next();
	}

	private static void openWorldPanel(Minecraft minecraft) {
		if (phaseTicks < 10) {
			return;
		}

		// 加载界面（Loading terrain / Receiving level 等）还在时先等着，
		// 真实玩家也不会在这种时候按键
		if (minecraft.screen != null) {
			if (phaseTicks % 40 == 0) {
				log("waiting for screen to close: " + minecraft.screen);
			}

			return;
		}

		// 模拟“按住波浪键”：真实按键时 consumeClick 触发打开，此时 isDown 为 true
		BlockZipClient.OPEN_VARIANTS.setDown(true);
		VariantPanel.get().toggleForHeldItem(minecraft);
		log("world panel open = " + VariantPanel.get().isOpen() + " (expect true), held = "
				+ minecraft.player.getInventory().getSelected()
				+ ", initial selected = " + VariantPanel.get().selectedItemName()
				+ " (expect 就是手上那个 = copper_block)");
		next();
	}

	/** 回归测试：长按波浪键时系统会连续发 keydown，原版每个都 click 一次，面板不能因此反复开关。 */
	private static void repeatKeyPresses() {
		if (phaseTicks < 5) {
			return;
		}

		// 1.21.1：InputConstants.getKey(keyCode, scanCode)
		InputConstants.Key key = InputConstants.getKey(InputConstants.KEY_GRAVE, GRAVE_SCANCODE);

		for (int i = 0; i < 6; i++) {
			KeyMapping.set(key, true);
			KeyMapping.click(key);
		}

		log("simulated 6 key repeats, skipped clicks handled by edge detection");
		next(20);
	}

	private static void scrollInWorld() {
		if (phaseTicks < 0) {
			return;
		}

		log("after repeats: panel open = " + VariantPanel.get().isOpen() + " (expect true, 否则就是长按闪烁)");
		boolean first = VariantPanel.get().onWorldScroll(-1.0);
		boolean second = VariantPanel.get().onWorldScroll(-1.0);
		log("world wheel handled = " + (first && second) + ", selected = " + VariantPanel.get().selectedItemName()
				+ " (expect minecraft:weathered_copper)");
		next();
	}

	private static void releaseKey() {
		if (phaseTicks < 20) {
			return;
		}

		log("before release, held = " + Minecraft.getInstance().player.getInventory().getSelected());
		// 松开按键：生产代码里的 tickInWorld 应该确认选择、取出变种并收起面板
		BlockZipClient.OPEN_VARIANTS.setDown(false);
		next();
	}

	private static void afterRelease(Minecraft minecraft) {
		if (phaseTicks < 25) {
			return;
		}

		log("after release: panelOpen = " + VariantPanel.get().isOpen()
				+ " (expect false), held = " + minecraft.player.getInventory().getSelected()
				+ " (expect minecraft:weathered_copper)");
		log("opening creative inventory screen");
		minecraft.setScreen(new CreativeModeInventoryScreen(minecraft.player,
				minecraft.player.level().enabledFeatures(), false));
		next();
	}

	/** 把鼠标坐标直接喂给原版，让 hoveredSlot 落在装着方块的那个快捷栏格子上。 */
	private static void moveCursorOverSlot(Minecraft minecraft) {
		if (phaseTicks < 30) {
			return;
		}

		Screen screen = minecraft.screen;

		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			next();
			return;
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
		Slot target = null;
		int hotbar = minecraft.player.getInventory().selected;

		for (Slot slot : containerScreen.getMenu().slots) {
			if (slot.container == minecraft.player.getInventory() && slot.getContainerSlot() == hotbar) {
				target = slot;
				break;
			}
		}

		if (target != null) {
			int guiX = accessor.getLeftPos() + target.x + 8;
			int guiY = accessor.getTopPos() + target.y + 8;
			moveCursor(minecraft, guiX, guiY);
			log("screen mode: cursor -> gui(" + guiX + "," + guiY + ")");
		}

		next();
	}

	/** 鼠标不在任何格子上时，按住波浪键不应该展开任何东西（不能因为手上有集合就展开）。 */
	private static void noOpenWhenMouseOffSlot(Minecraft minecraft) {
		if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen)) {
			next();
			return;
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;

		if (phaseTicks == 5) {
			// 挪到界面外的左上角，确保鼠标底下没有格子
			moveCursor(minecraft, 4, 4);
			return;
		}

		if (phaseTicks == 40) {
			// 按住波浪键：鼠标不在格子上 → 什么都不该展开（哪怕手上拿着一整个集合）
			VariantPanel.get().onKeyPressed(containerScreen, InputConstants.KEY_GRAVE, GRAVE_SCANCODE);
			log("mouse off slot: hoveredSlot="
					+ (accessor.getHoveredSlot() == null ? "null" : accessor.getHoveredSlot().getItem())
					+ ", panelOpen=" + VariantPanel.get().isOpen() + " (expect false)");
			// 松开（真实玩家也会松手），否则下一步的按下会被“长按去重”正确忽略
			VariantPanel.get().onKeyReleased(InputConstants.KEY_GRAVE, GRAVE_SCANCODE);
			return;
		}

		if (phaseTicks < 60) {
			return;
		}

		// 找一个**真的装着有变种方块**的格子，不再假设快捷栏里是铜（存档里的物品会漂移）
		Slot target = null;

		for (Slot slot : containerScreen.getMenu().slots) {
			if (slot.container == minecraft.player.getInventory() && slot.hasItem()
					&& VariantIndex.variantsOf(slot.getItem()).size() > 1) {
				target = slot;
				break;
			}
		}

		if (target == null) {
			log("screen mode: 背包里没有带变种的方块，跳过这一段");
			next(30);
			return;
		}

		log("screen mode: 把鼠标移到 containerSlot=" + target.getContainerSlot()
				+ " 的 " + target.getItem().getItem());
		moveCursor(minecraft, accessor.getLeftPos() + target.x + 8, accessor.getTopPos() + target.y + 8);

		next(30);
	}

	private static void openScreenPanel(Minecraft minecraft) {
		if (phaseTicks < 30) {
			return;
		}

		Screen screen = minecraft.screen;

		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
			// 直接喂一个波浪键事件，走的就是玩家按键时的那条逻辑
			boolean handled = VariantPanel.get().onKeyPressed(containerScreen, InputConstants.KEY_GRAVE, GRAVE_SCANCODE);
			Slot hoveredNow = accessor.getHoveredSlot();
			String hoveredName = hoveredNow != null && hoveredNow.hasItem()
					? String.valueOf(hoveredNow.getItem().getItem()) : "<none>";
			log("screen initial selected = " + VariantPanel.get().selectedItemName()
					+ " (鼠标下那一格 = " + hoveredName + "，两者应一致)");
			// 再喂一次一模一样的事件 = 模拟长按自动重复，面板不应该被关掉
			boolean repeatHandled = VariantPanel.get().onKeyPressed(containerScreen, InputConstants.KEY_GRAVE, GRAVE_SCANCODE);
			boolean stillOpen = VariantPanel.get().isOpen();
			boolean scrolled = VariantPanel.get().onMouseScrolled(containerScreen, 0.0, 0.0, -1.0);
			log("screen mode: handled=" + handled + " repeatHandled=" + repeatHandled
					+ " stillOpenAfterRepeat=" + stillOpen + " (expect true)"
					+ " wheelHandled=" + scrolled + " selected=" + VariantPanel.get().selectedItemName()
					+ " hoveredSlot=" + (accessor.getHoveredSlot() == null ? "null" : accessor.getHoveredSlot().getItem()));
		} else {
			log("screen mode: unexpected screen " + screen);
		}

		next();
	}

	private static void keepScreenPanel(Minecraft minecraft) {
		if (phaseTicks < 20) {
			return;
		}

		if (phaseTicks == 20) {
			var window = minecraft.getWindow();
			log("screen panel rect = " + VariantPanel.get().debugRect() + " (给外部点击测试定位用)");
			log("gui: scale=" + window.getGuiScale() + " window=" + window.getWidth() + "x" + window.getHeight()
					+ " gui=" + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
			log("screen panel still open = " + VariantPanel.get().isOpen() + " (expect true)");
			return;
		}

		if (phaseTicks == 60) {
			// 走真实界面方法：containerScreen.mouseClicked(...) → 我们的 Mixin 应该把点击吃掉，
			// 既选中面板里的那一项，又不让点击漏到下面的格子上（这就是“面板透明”那个 bug）
			Screen screen = minecraft.screen;

			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				String[] rect = VariantPanel.get().debugRect().split("[ ,x]+");
				int px = Integer.parseInt(rect[0]);
				int py = Integer.parseInt(rect[1]);
				int pw = Integer.parseInt(rect[2]);
				double gx = px + pw / 2.0;
				double gy = py + 16 + 18 + 9;          // HEADER + 第二格中心
				// 1.21.1：mouseClicked/mouseReleased 就是 (mouseX, mouseY, button)
				boolean consumed = containerScreen.mouseClicked(gx, gy, 0);
				String afterPress = containerScreen.getMenu().getCarried().toString();
				// 真实的鼠标操作是“按下 + 松开”，原版松开时会对下面的格子再做一次动作，
				// 所以这一步必须也走一遍，否则测不出“点出来的东西又被放回去/丢出去”
				boolean releaseConsumed = containerScreen.mouseReleased(gx, gy, 0);
				log("real mouseClicked consumed = " + consumed + " (expect true), selected = "
						+ VariantPanel.get().selectedItemName()
						+ ", panelOpen = " + VariantPanel.get().isOpen()
						+ ", carriedAfterPress = " + afterPress + " (expect 1 个 exposed_copper，默认只拿一个)"
						+ ", releaseConsumed = " + releaseConsumed + " (expect true)"
						+ ", carriedAfterRelease = " + containerScreen.getMenu().getCarried()
						+ " (expect 仍在鼠标上 = 拿得住)");

				// 再点几下（含 button 1/3，模拟不同机器左右键编号），每次 +1，绝不会变少
				for (int b : new int[]{1, 3, 0}) {
					containerScreen.mouseClicked(gx, gy, b);
					containerScreen.mouseReleased(gx, gy, b);
				}

				log("repeat clicks (button 1/3/0): carried = " + containerScreen.getMenu().getCarried()
						+ " (expect 4 个 = 每点一次 +1)");

				// Shift 点击 = 补满一整组。1.21.1 的 mouseClicked 没有修饰键参数，
				// Shift 由 Screen#hasShiftDown() 现场读键盘，伪造不了，
				// 所以这里直接调面板自己的入口把 fullStack=true 传进去，测的仍是同一段逻辑。
				boolean shiftConsumed = VariantPanel.get().onMouseClicked(containerScreen, gx, gy, 3, true);
				boolean shiftRelease = VariantPanel.get().onMouseReleased(containerScreen, 3);
				log("shift click (fullStack=true): consumed=" + shiftConsumed
						+ " releaseConsumed=" + shiftRelease
						+ " carried = " + containerScreen.getMenu().getCarried()
						+ " (expect 64 个 = Shift 拿一整组)");
			}

			return;
		}

		if (phaseTicks < 400) {
			return;
		}

		// 界面里也要“松开按键才取出”，和世界里的手感一致
		boolean released = VariantPanel.get().onKeyReleased(InputConstants.KEY_GRAVE, GRAVE_SCANCODE);
		log("screen release handled = " + released + ", panelOpen = " + VariantPanel.get().isOpen() + " (expect false)");
		log("==== end ====");
		next(30);
	}

	/** 收尾：关掉界面回到世界，把面板再撑开一会儿，方便截图确认外观与提示文字。 */
	private static void leaveWorldPanelOpen(Minecraft minecraft) {
		if (phaseTicks < 30) {
			return;
		}

		log("hotbar after screen-mode release = " + minecraft.player.getInventory().getSelected()
				+ " (expect minecraft:exposed_copper)");
		minecraft.setScreen(null);
		BlockZipClient.OPEN_VARIANTS.setDown(true);
		VariantPanel.get().toggleForHeldItem(minecraft);
		log("final world panel open = " + VariantPanel.get().isOpen()
				+ ", held = " + minecraft.player.getInventory().getSelected());
		next(440);
	}

	/** 按 GUI 坐标移动真实光标：GLFW 会把光标位置回调排进主线程，MouseHandler 随之更新 xpos/ypos。 */
	private static void moveCursor(Minecraft minecraft, int guiX, int guiY) {
		var window = minecraft.getWindow();
		double pixelX = guiX * (double) window.getWidth() / window.getGuiScaledWidth();
		double pixelY = guiY * (double) window.getHeight() / window.getGuiScaledHeight();
		GLFW.glfwSetCursorPos(window.getWindow(), pixelX, pixelY);
	}

	private static void next() {
		next(0);
	}

	private static void next(int wait) {
		phase++;
		phaseTicks = -wait;
	}

	private static void report() {
		log("==== begin ====");
		log("accessor mixin applied = " + AbstractContainerScreenAccessor.class.isAssignableFrom(AbstractContainerScreen.class));

		// 1.21.1 的 Items.COPPER_BLOCK 等就是“未氧化”的那一个，没有 26.3 的 asList() 链式类型
		check("stone_bricks", Items.STONE_BRICKS, 3);
		check("copper_block", Items.COPPER_BLOCK, 8);
		check("cut_copper", Items.CUT_COPPER, 24);
		check("cut_copper_stairs", Items.CUT_COPPER_STAIRS, 24);
		check("oak_planks", Items.OAK_PLANKS, 3);
		check("dirt", Items.DIRT, 0);

		Set<List<Item>> distinct = new HashSet<>();

		for (Block block : BuiltInRegistries.BLOCK) {
			List<Item> variants = VariantIndex.variantsOf(block.asItem());

			if (variants.size() >= 2) {
				distinct.add(variants);
			}
		}

		log("total variant groups = " + distinct.size());
	}

	private static void check(String name, Item item, int expected) {
		List<Item> variants = VariantIndex.variantsOf(item);
		StringBuilder names = new StringBuilder();

		for (Item variant : variants) {
			names.append(variant.getDescriptionId().replace("block.", "")).append(' ');
		}

		log(name + " -> " + variants.size() + " (expected " + expected + ") [" + names.toString().trim() + "]");
	}

	private static void log(String message) {
		System.out.println(TAG + " " + message);
	}
}
