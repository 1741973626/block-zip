package com.blockzip.client.ui;

import java.util.List;

import com.blockzip.client.BlockZipClient;
import com.blockzip.client.mixin.AbstractContainerScreenAccessor;
import com.blockzip.client.variant.VariantIndex;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 竖向变种面板：把一个方块的所有变种排成一列显示，滚轮/方向键切换，点击或回车取出。
 *
 * <p>两种使用场景共用一个面板：</p>
 * <ul>
 *   <li>打开物品栏 / 创造模式物品栏 / 容器时——鼠标指的格子就是“集合”的来源，面板就贴在格子旁边；</li>
 *   <li>在世界里——手持方块按波浪键，面板出现在快捷栏上方，方向键 + 回车切换。</li>
 * </ul>
 */
public final class VariantPanel {
	private static final VariantPanel INSTANCE = new VariantPanel();

	/** 尺寸/配色全部照原版容器来：18×18 槽位 + #C6C6C6 面板 + 原版高亮贴图。 */
	private static final int CELL = 18;
	private static final int ICON = 16;
	private static final int PAD = 2;
	/** 标题行高度：要留得下选中格那圈 24×23 的外框。 */
	private static final int HEADER = 16;
	private static final int PANEL_OUTLINE = 0xFF000000;
	private static final int PANEL_FILL = 0xFFC6C6C6;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_DARK = 0xFF555555;
	private static final int TITLE_COLOR = 0xFF404040;
	private static final int UNAVAILABLE = 0x70101010;

	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
	private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
	/** 原版快捷栏选中格的加粗外框（1px 白亮边 + 2px 深描边），24×23。 */
	private static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
	private static final int FRAME_W = 24;
	private static final int FRAME_H = 23;
	/** 切换选中项后外框亮起的时长（毫秒）。 */
	private static final long PULSE_MS = 220L;

	private boolean open;
	private List<Item> items = List.of();
	private Item source;
	/** 方块集合的名字，画在栏顶当容器标题。 */
	private Component title = Component.empty();
	private int selected;
	private int scroll;
	/** 世界里“按住按键 + 滚轮选、松开取出”用：中途有没有改过选中项。 */
	private boolean selectionChanged;
	/** 界面里按键当前是否处于按下状态，用来忽略长按产生的重复事件（否则面板会闪）。 */
	private boolean openKeyHeld;
	/** 最近一次切换选中项的时刻，用来给选中格外框做一下亮起。 */
	private long selectionPulseAt;
	/** 面板吃掉了某次鼠标按下时置位，紧接着的那次松开也要吃掉，否则原版会把刚拿到鼠标上的物品又放回格子。 */
	private boolean swallowNextRelease;
	private AbstractContainerScreen<?> screen;
	private Slot anchorSlot;
	private int hotbarIndex = -1;
	private int x;
	private int y;
	private int width;
	private int height;

	// 世界里没有界面可接收按键事件，只能每 tick 轮询，这里记录上一 tick 的按下状态
	private boolean upDown;
	private boolean downDown;
	private boolean enterDown;
	private final boolean[] numberDown = new boolean[9];

	private VariantPanel() {
	}

	public static VariantPanel get() {
		return INSTANCE;
	}

	public boolean isOpen() {
		return this.open;
	}

	/** 这个 GUI 坐标是不是落在面板上（给“面板盖住分类栏/滚动条”那类判定用）。 */
	public boolean containsGui(double guiX, double guiY) {
		return this.open && inside(guiX, guiY);
	}

	/** 诊断用：当前选中项的 id。 */
	public String selectedItemName() {
		if (this.items.isEmpty() || this.selected < 0 || this.selected >= this.items.size()) {
			return "<none>";
		}

		return this.items.get(this.selected).getDescriptionId();
	}

	/** 诊断用：面板当前位置与大小。 */
	public String debugRect() {
		return this.x + "," + this.y + " " + this.width + "x" + this.height;
	}

	// ------------------------------------------------------------------ 开关

	/** 诊断日志：只在设置 BLOCKZIP_SELFTEST=1 时输出，平时保持游戏日志干净。 */
	public static void debug(String message) {
		if (System.getenv("BLOCKZIP_SELFTEST") != null) {
			System.out.println("[BlockZip] " + message);
		}
	}

	/** 关掉面板并清空状态。 */
	public void close() {
		if (this.open) {
			debug("panel closed");
		}

		this.open = false;
		this.items = List.of();
		this.source = null;
		this.title = Component.empty();
		this.anchorSlot = null;
		this.screen = null;
		this.hotbarIndex = -1;
		this.selected = 0;
		this.scroll = 0;
		this.selectionChanged = false;
		this.swallowNextRelease = false;
		this.upDown = false;
		this.downDown = false;
		this.enterDown = false;
	}

	private void openWith(ItemStack stack, Slot slot, int hotbar) {
		List<Item> variants = VariantIndex.variantsOf(stack);

		if (variants.size() < 2) {
			close();
			return;
		}

		this.items = variants;
		this.source = stack.getItem();
		// 栏顶标题用集合里第一个（本体形态）的名字，比如铜块、石砖
		this.title = new ItemStack(variants.get(0)).getHoverName();
		// 默认框选“展开前手上/鼠标上那一个”，而不是最上面那个：
		// 这样按住不滚、直接松开就是原样不动，符合直觉
		int current = variants.indexOf(stack.getItem());
		this.selected = current >= 0 ? current : 0;
		this.scroll = 0;
		this.selectionChanged = false;
		this.anchorSlot = slot;
		this.hotbarIndex = hotbar;
		this.open = true;
		// 把默认选中的那一项滚进可见范围
		move(0);
		debug("panel opened source=" + this.source + " items=" + this.items.size()
				+ " selected=" + this.selected + " anchor=" + (slot == null ? "none" : slot.index)
				+ " screen=" + (this.screen == null ? "world" : "screen"));
	}

	private void toggleForScreen(AbstractContainerScreen<?> containerScreen) {
		if (this.open) {
			close();
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return;
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
		Slot hovered = accessor.getHoveredSlot();

		// 界面里**只认鼠标底下那一格**：
		// 鼠标没停在一个有东西的格子上就什么都不展开（不能因为手上拿着橡木就展开橡木集合），
		// 停下了就看那一格的东西有没有变种，没有也不展开。
		if (hovered == null || !hovered.hasItem()) {
			return;
		}

		this.screen = containerScreen;
		openWith(hovered.getItem(), hovered, player.getInventory().getSelectedSlot());
	}

	/** 在容器菜单里找玩家快捷栏第 index 格的 Slot。 */
	private static Slot findHotbarSlot(AbstractContainerMenu menu, LocalPlayer player, int index) {
		for (Slot slot : menu.slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == index) {
				return slot;
			}
		}

		return null;
	}

	/** 世界里（没开界面）按波浪键时调用。 */
	public void toggleForHeldItem(Minecraft minecraft) {
		if (this.open) {
			close();
			return;
		}

		if (minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}

		this.screen = null;
		openWith(minecraft.player.getInventory().getSelectedItem(), null,
				minecraft.player.getInventory().getSelectedSlot());
	}

	// ------------------------------------------------------------------ 界面里的输入

	public boolean onKeyPressed(AbstractContainerScreen<?> containerScreen, KeyEvent event) {
		// 打开键：和世界里一样是“按住”——按下时展开，中途的自动重复忽略，松开才取出。
		if (BlockZipClient.OPEN_VARIANTS.matches(event)) {
			if (!this.openKeyHeld) {
				this.openKeyHeld = true;

				if (!this.open) {
					toggleForScreen(containerScreen);
				}
			}

			return true;
		}

		if (!this.open) {
			return false;
		}

		this.screen = containerScreen;

		if (event.key() == InputConstants.KEY_ESCAPE) {
			// Esc = 取消，不取出
			close();
			return true;
		}

		switch (event.key()) {
			case InputConstants.KEY_UP -> {
				move(-1);
				return true;
			}
			case InputConstants.KEY_DOWN -> {
				move(1);
				return true;
			}
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				apply(this.selected, false, false);
				return true;
			}
			default -> {
			}
		}

		int number = numberKey(event.key());

		if (number >= 0) {
			if (number < this.items.size()) {
				this.selected = number;
				apply(number, false, false);
			}

			return true;
		}

		return false;
	}

	/** 松开按键：清掉“按住中”标记，并把选中的变种取出来（没滚过就什么都不做）。 */
	public boolean onKeyReleased(KeyEvent event) {
		if (BlockZipClient.OPEN_VARIANTS.matches(event)) {
			this.openKeyHeld = false;
			releaseConfirm();
			return true;
		}

		return false;
	}

	/** 松手时的确认：只有中途改过选中项才真的取出。 */
	private void releaseConfirm() {
		boolean confirm = this.selectionChanged;
		int choice = this.selected;

		if (confirm) {
			apply(choice, false, false);
		}

		close();
	}

	/**
	 * 把变种“贴”到鼠标上。全程只动本地 {@code carried}，<b>不发任何包</b>
	 * （发 slotNum &lt; 0 的包会被服务端当成丢到世界）。
	 *
	 * <p>数量规则和原版创造物品栏一致：点一下只拿 <b>一个</b>，按住 <b>Shift</b> 拿一整组。
	 * 鼠标上已经拿着同一种时只会往上加、<b>绝不会减少</b>——按鼠标键区分“+1/−1”在
	 * 不同机器上会错乱（实测有人左键上报成 button=1、右键上报成 button=3）。</p>
	 */
	private static void grabToCursor(AbstractContainerMenu menu, ItemStack stack, boolean fullStack) {
		ItemStack carried = menu.getCarried();
		int amount = fullStack ? stack.getMaxStackSize() : 1;

		if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, stack)) {
			int next = fullStack ? carried.getMaxStackSize() : carried.getCount() + 1;
			carried.setCount(Math.min(carried.getMaxStackSize(), next));
			debug("cursor grown to " + carried + " (fullStack=" + fullStack + ")");
			return;
		}

		menu.setCarried(stack.copyWithCount(amount));
		debug("grabbed to cursor: " + amount + " x " + stack.getItem()
				+ " (fullStack=" + fullStack + ")");
	}

	/** 松开鼠标：只有紧跟在“被面板吃掉的按下”后面那一次才吃掉。 */
	public boolean onMouseReleased(AbstractContainerScreen<?> containerScreen, MouseButtonEvent event) {
		if (this.swallowNextRelease) {
			this.swallowNextRelease = false;
			debug("swallowed release button=" + event.button()
					+ " carried=" + containerScreen.getMenu().getCarried());
			return true;
		}

		return false;
	}

	/** 界面被关掉时调用。 */
	public void onScreenClosed() {
		this.openKeyHeld = false;
		close();
	}

	public boolean onMouseClicked(AbstractContainerScreen<?> containerScreen, MouseButtonEvent event) {
		if (!this.open || this.screen != containerScreen) {
			return false;
		}

		layoutForScreen(containerScreen);
		double mouseX = event.x();
		double mouseY = event.y();

		if (inside(mouseX, mouseY)) {
			int index = indexAt(mouseX, mouseY);
			// 任何鼠标键都当作“取这个变种”：不同机器/鼠标上报的 button 编号完全不一样
			// （实测有的机器左键=1、右键=3），按编号区分只会出 bug

			if (index >= 0) {
				this.selected = index;
				// Shift = 一次拿一整组，否则只拿一个（和原版创造物品栏一致）
				apply(index, true, event.hasShiftDown());
				// 已经取出来了，松手时不用再取一次
				this.selectionChanged = false;
			}

			debug("panel click button=" + event.button() + " index=" + index
					+ " carried=" + containerScreen.getMenu().getCarried());

			// 面板范围内的任何鼠标键都吃掉，别让原版对着下面的格子动手；
			// 配套的松开也要吃掉，否则原版 mouseReleased 会再对下面的格子做一次动作
			// （把东西放进背包，或者当成“点在外面”直接丢到世界里）。
			this.swallowNextRelease = true;
			return true;
		}

		// 点面板外面：收起面板，这一次点击照样交给原版处理
		debug("panel click outside at=" + (int) mouseX + "," + (int) mouseY
				+ " panel=" + this.x + "," + this.y + " " + this.width + "x" + this.height);
		close();
		return false;
	}

	public boolean onMouseScrolled(AbstractContainerScreen<?> containerScreen, double mouseX, double mouseY, double scrollY) {
		if (System.getenv("BLOCKZIP_SELFTEST") != null) {
			System.out.println("[BLOCKZIP-SELFTEST] mouseScrolled open=" + this.open
					+ " sameScreen=" + (this.screen == containerScreen)
					+ " at=" + (int) mouseX + "," + (int) mouseY + " scroll=" + scrollY);
		}

		if (!this.open || this.screen != containerScreen) {
			return false;
		}

		// 面板展开期间滚轮只用来切换选中项，不再滚视图、也不传给原版
		if (scrollY != 0.0) {
			move(scrollY > 0.0 ? -1 : 1);
		}

		return true;
	}

	/** 世界里没有界面可以收滚轮事件，由 MouseHandlerMixin 调过来。 */
	public boolean onWorldScroll(double scrollY) {
		if (!this.open || this.screen != null) {
			return false;
		}

		if (scrollY != 0.0) {
			move(scrollY > 0.0 ? -1 : 1);
		}

		return true;
	}

	/**
	 * 世界里的交互：按住按键展开 → 滚轮（或 ↑↓）选择 → 松开按键取出。
	 * 没滚动过就直接松开 = 什么都不做，不会误操作。
	 */
	public void tickInWorld(Minecraft minecraft) {
		if (!this.open || this.screen != null) {
			return;
		}

		if (minecraft.gui.screen() != null || minecraft.player == null) {
			close();
			return;
		}

		ItemStack held = minecraft.player.getInventory().getSelectedItem();

		if (held.isEmpty() || held.getItem() != this.source) {
			close();
			return;
		}

		this.hotbarIndex = minecraft.player.getInventory().getSelectedSlot();
		layoutForHud(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());

		// 松开按键：确认选择
		if (!BlockZipClient.OPEN_VARIANTS.isDown()) {
			releaseConfirm();
			return;
		}

		boolean up = InputConstants.isKeyDown(InputConstants.KEY_UP);
		boolean down = InputConstants.isKeyDown(InputConstants.KEY_DOWN);
		boolean enter = InputConstants.isKeyDown(InputConstants.KEY_RETURN)
				|| InputConstants.isKeyDown(InputConstants.KEY_NUMPADENTER);

		if (up && !this.upDown) {
			move(-1);
		}

		if (down && !this.downDown) {
			move(1);
		}

		if (enter && !this.enterDown) {
			apply(this.selected, false, false);
			close();
			return;
		}

		this.upDown = up;
		this.downDown = down;
		this.enterDown = enter;

		for (int i = 0; i < this.numberDown.length; i++) {
			boolean pressed = InputConstants.isKeyDown(InputConstants.KEY_1 + i);

			if (pressed && !this.numberDown[i] && i < this.items.size()) {
				this.selected = i;
				apply(i, false, false);
				close();
				return;
			}

			this.numberDown[i] = pressed;
		}
	}

	// ------------------------------------------------------------------ 绘制

	public void renderInScreen(AbstractContainerScreen<?> containerScreen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!this.open || this.screen != containerScreen) {
			return;
		}

		layoutForScreen(containerScreen);
		render(graphics, mouseX, mouseY);
	}

	public void renderInHud(GuiGraphicsExtractor graphics) {
		if (!this.open || this.screen != null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gui.screen() != null || minecraft.player == null) {
			close();
			return;
		}

		double scaleX = (double) graphics.guiWidth() / minecraft.getWindow().getWidth();
		double scaleY = (double) graphics.guiHeight() / minecraft.getWindow().getHeight();
		int mouseX = (int) (minecraft.mouseHandler.xpos() * scaleX);
		int mouseY = (int) (minecraft.mouseHandler.ypos() * scaleY);
		layoutForHud(graphics.guiWidth(), graphics.guiHeight());
		render(graphics, mouseX, mouseY);
	}

	private void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int cells = visibleCells();
		int cellX = this.x + (this.width - CELL) / 2;
		int cellTop = this.y + HEADER;

		// 原版容器面板：1px 黑描边 + #C6C6C6 底 + 白/深灰立体边框
		graphics.fill(this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, PANEL_OUTLINE);
		graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, PANEL_FILL);
		graphics.fill(this.x, this.y, this.x + this.width - 1, this.y + 1, PANEL_LIGHT);
		graphics.fill(this.x, this.y, this.x + 1, this.y + this.height - 1, PANEL_LIGHT);
		graphics.fill(this.x + 1, this.y + this.height - 1, this.x + this.width, this.y + this.height, PANEL_DARK);
		graphics.fill(this.x + this.width - 1, this.y + 1, this.x + this.width, this.y + this.height, PANEL_DARK);

		// 标题 = 方块集合的名字，用原版容器标题的深灰、无描边
		graphics.text(font, this.title, this.x + 5, this.y + 4, TITLE_COLOR, false);

		if (maxScroll() > 0) {
			Component position = Component.literal((this.selected + 1) + "/" + this.items.size());
			graphics.text(font, position, this.x + this.width - 5 - font.width(position), this.y + 4, TITLE_COLOR, false);
		}

		// 选中项：原版“槽位背面高亮”，画在物品下面
		int selectedRow = this.selected - this.scroll;
		boolean selectedVisible = selectedRow >= 0 && selectedRow < cells;

		if (selectedVisible) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK,
					cellX - 4, cellTop + selectedRow * CELL - 4, 24, 24);
		}

		// 第一遍：所有槽底
		for (int i = 0; i < cells; i++) {
			if (this.scroll + i >= this.items.size()) {
				break;
			}

			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, cellX, cellTop + i * CELL, CELL, CELL);
		}

		// 选中格的加粗外框：就是原版快捷栏滚动时那个 hud/hotbar_selection
		if (selectedVisible) {
			int frameX = cellX - (FRAME_W - CELL) / 2;
			int frameY = cellTop + selectedRow * CELL - (FRAME_H - CELL) / 2;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, frameX, frameY, FRAME_W, FRAME_H);

			// 刚切换过的那一瞬间再叠一层会淡出的同款外框，滚动时更醒目
			long elapsed = Util.getMillis() - this.selectionPulseAt;

			if (this.selectionPulseAt > 0L && elapsed >= 0L && elapsed < PULSE_MS) {
				int alpha = (int) ((1.0F - (float) elapsed / PULSE_MS) * 150.0F);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE,
						frameX, frameY, FRAME_W, FRAME_H, (alpha << 24) | 0xFFFFFF);
			}
		}

		// 第二遍：物品与角标画在外框上面（外框中间是镂空的）
		for (int i = 0; i < cells; i++) {
			int index = this.scroll + i;

			if (index >= this.items.size()) {
				break;
			}

			int cellY = cellTop + i * CELL;
			ItemStack stack = new ItemStack(this.items.get(index));
			graphics.item(stack, cellX + 1, cellY + 1);
			graphics.itemDecorations(font, stack, cellX + 1, cellY + 1);

			if (!isAvailable(minecraft, this.items.get(index))) {
				graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1, cellY + CELL - 1, UNAVAILABLE);
			}
		}

		// 鼠标悬停：原版“槽位正面高亮”，画在物品上面
		int hoverRow = rowAt(mouseY);

		if (hoverRow >= 0 && mouseX >= cellX && mouseX < cellX + CELL) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT,
					cellX - 4, cellTop + hoverRow * CELL - 4, 24, 24);
		}

		// 悬停/选中那一项的名字，浮在栏旁边
		int labelIndex = indexAt(mouseX, mouseY);

		if (labelIndex < 0) {
			labelIndex = this.selected;
		}

		if (labelIndex >= 0 && labelIndex < this.items.size()) {
			Component name = new ItemStack(this.items.get(labelIndex)).getHoverName();
			int textWidth = font.width(name);
			int textX = this.x + this.width + 4;

			if (textX + textWidth > graphics.guiWidth() - 2) {
				textX = this.x - 4 - textWidth;
			}

			graphics.fill(textX - 2, this.y - 1, textX + textWidth + 2, this.y + 10, 0xB0000000);
			graphics.text(font, name, textX, this.y + 1, 0xFFFFFFFF, true);
		}

		// 操作提示
		Component hint = Component.translatable(this.screen == null
				? "hint.block_zip.world"
				: "hint.block_zip.screen");
		int hintWidth = font.width(hint);
		int hintX = Mth.clamp(this.x + (this.width - hintWidth) / 2, 2, Math.max(2, graphics.guiWidth() - hintWidth - 2));
		int hintY = this.screen == null ? this.y - 12 : this.y + this.height + 3;

		if (hintY < 2 || hintY + 10 > graphics.guiHeight() - 2) {
			hintY = this.y + this.height + 3;
		}

		if (hintY + 10 > graphics.guiHeight() - 2) {
			hintY = Math.max(2, this.y - 12);
		}

		graphics.fill(hintX - 2, hintY - 1, hintX + hintWidth + 2, hintY + 9, 0xA0000000);
		graphics.text(font, hint, hintX, hintY, 0xFFD8D8D8, false);
	}

	// ------------------------------------------------------------------ 布局

	/** 世界里的折叠栏：直接长在当前快捷栏格子正上方，和它同一条竖线。 */
	private void layoutForHud(int guiWidth, int guiHeight) {
		refreshSize();
		int hotbar = Mth.clamp(this.hotbarIndex, 0, 8);
		int slotLeft = guiWidth / 2 - 91 + hotbar * 20;
		int hotbarTop = guiHeight - 22;
		this.x = Mth.clamp(slotLeft + 2 - (this.width - CELL) / 2, 1, Math.max(1, guiWidth - this.width - 1));
		this.y = Mth.clamp(hotbarTop - this.height - 1, 1, Math.max(1, guiHeight - this.height - 1));
	}

	/** 界面里的折叠栏：贴着手底下那个格子往上长，放不下就翻到下面。 */
	private void layoutForScreen(AbstractContainerScreen<?> containerScreen) {
		refreshSize();
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
		Slot anchor = this.anchorSlot;

		// 打开时鼠标没停在格子上（比如停在界面空白处）的话，
		// 退回贴“快捷栏当前格”，免得面板孤零零跑到屏幕左上角
		if (anchor == null) {
			Minecraft minecraft = Minecraft.getInstance();

			if (minecraft.player != null) {
				int hotbar = this.hotbarIndex >= 0 ? this.hotbarIndex : minecraft.player.getInventory().getSelectedSlot();
				anchor = findHotbarSlot(containerScreen.getMenu(), minecraft.player, hotbar);
			}
		}

		int slotFrameX = 8;
		int slotFrameY = 8;

		if (anchor != null) {
			slotFrameX = accessor.getLeftPos() + anchor.x - 1;
			slotFrameY = accessor.getTopPos() + anchor.y - 1;
		}

		this.x = Mth.clamp(slotFrameX - (this.width - CELL) / 2, 1, Math.max(1, containerScreen.width - this.width - 1));
		this.y = slotFrameY - this.height;

		if (this.y < 1) {
			this.y = slotFrameY + CELL + 1;
		}

		this.y = Mth.clamp(this.y, 1, Math.max(1, containerScreen.height - this.height - 1));
	}

	private void refreshSize() {
		int titleWidth = Minecraft.getInstance().font.width(this.title);
		// 面板宽度要装得下选中格那圈外框（24 宽），否则外框会压到面板描边上
		this.width = Math.max(FRAME_W + 2, titleWidth + 10);
		this.height = HEADER + visibleCells() * CELL + 3;
	}

	private int visibleCells() {
		Minecraft minecraft = Minecraft.getInstance();
		int guiHeight = minecraft.getWindow().getGuiScaledHeight();
		int max = Math.max(1, (guiHeight - 16 - HEADER - 3) / CELL);
		return Math.min(this.items.size(), max);
	}

	private int maxScroll() {
		return Math.max(0, this.items.size() - visibleCells());
	}

	private void move(int delta) {
		if (this.items.isEmpty()) {
			return;
		}

		int before = this.selected;
		this.selected = Mth.clamp(this.selected + delta, 0, this.items.size() - 1);

		if (this.selected != before) {
			this.selectionChanged = true;
			this.selectionPulseAt = Util.getMillis();
		}

		if (this.selected < this.scroll) {
			this.scroll = this.selected;
		}

		int visible = visibleCells();

		if (this.selected >= this.scroll + visible) {
			this.scroll = this.selected - visible + 1;
		}

		this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
	}

	private boolean inside(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
	}

	private int indexAt(double mouseX, double mouseY) {
		if (!inside(mouseX, mouseY)) {
			return -1;
		}

		int row = rowAt(mouseY);

		if (row < 0) {
			return -1;
		}

		int index = this.scroll + row;
		return index < this.items.size() ? index : -1;
	}

	/** 鼠标落在第几个槽位上（标题不算）。 */
	private int rowAt(double mouseY) {
		int row = (int) ((mouseY - (this.y + HEADER)) / CELL);
		return row < 0 || row >= visibleCells() ? -1 : row;
	}

	// ------------------------------------------------------------------ 取出

	private int numberKey(int key) {
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			return key - InputConstants.KEY_1;
		}

		if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
			return key - InputConstants.KEY_NUMPAD1;
		}

		return -1;
	}

	private boolean isAvailable(Minecraft minecraft, Item item) {
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return false;
		}

		if (player.isCreative()) {
			return true;
		}

		return player.getInventory().findSlotMatchingItem(new ItemStack(item)) >= 0;
	}

	/**
	 * 取出一个变种。
	 *
	 * @param fromClick 是不是鼠标点选来的。点选 = 照原版创造物品栏，把物品贴到鼠标上
	 * @param fullStack 点选时只拿一个还是拿一整组（Shift = 一整组）
	 */
	private void apply(int index, boolean fromClick, boolean fullStack) {
		if (index < 0 || index >= this.items.size()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.gameMode == null) {
			return;
		}

		Item item = this.items.get(index);
		ItemStack stack = new ItemStack(item);
		AbstractContainerMenu menu = this.screen != null
				? this.screen.getMenu()
				: player.inventoryMenu;
		Slot target = resolveTargetSlot(menu, player);

		// “目标格里已经是这个变种”的短路只对“松开确认”有意义；
		// 鼠标点选必须能真的拿起来，否则就会觉得面板里拿不出东西
		if (!fromClick && target != null && ItemStack.isSameItemSameComponents(target.getItem(), stack)) {
			return;
		}

		if (player.isCreative()) {
			// 鼠标点选：完全照原版创造物品栏点物品槽的做法 —— 只改本地 setCarried，把物品“贴”到鼠标上。
			// 关键：绝不能发 slotNum < 0 的包，服务端 handleSetCreativeModeSlot 会把它当成
			// player.drop(...) —— 那就是之前“点一下面板就有一个物品被丢到世界里”的原因。
			if (fromClick && this.screen != null) {
				grabToCursor(menu, stack, fullStack);
				return;
			}

			// 松开确认（世界里滚轮选完松手）：放进面板所贴的那一格 / 快捷栏
			Slot dest = target;

			// index < 1 的槽（比如合成结果格）服务端不接受，退回快捷栏
			if (dest == null || dest.index < 1) {
				int hotbar = this.hotbarIndex >= 0 ? this.hotbarIndex : player.getInventory().getSelectedSlot();
				dest = findHotbarSlot(menu, player, hotbar);
			}

			if (dest == null || dest.index < 1) {
				return;
			}

			if (dest.container == player.getInventory()) {
				player.getInventory().setItem(dest.getContainerSlot(), stack.copy());
				player.inventoryMenu.broadcastChanges();
			}

			minecraft.gameMode.handleCreativeModeItemAdd(stack, dest.index);
			debug("put into slot " + dest.index + ": " + stack);
			return;
		}

		if (target == null || !menu.getCarried().isEmpty()) {
			reportMissing(player, stack);
			return;
		}

		int targetId = menu.slots.indexOf(target);
		int sourceId = findPlayerSlot(menu, player, stack);

		if (targetId < 0 || sourceId < 0 || targetId == sourceId) {
			reportMissing(player, stack);
			return;
		}

		// 三次普通点击完成对调：拿起 -> 放到目标格 -> 把换出来的那格放回原处
		minecraft.gameMode.handleContainerInput(menu.containerId, sourceId, 0, ContainerInput.PICKUP, player);
		minecraft.gameMode.handleContainerInput(menu.containerId, targetId, 0, ContainerInput.PICKUP, player);
		minecraft.gameMode.handleContainerInput(menu.containerId, sourceId, 0, ContainerInput.PICKUP, player);
	}

	private Slot resolveTargetSlot(AbstractContainerMenu menu, LocalPlayer player) {
		if (this.anchorSlot != null && !this.anchorSlot.isFake() && menu.slots.contains(this.anchorSlot)) {
			return this.anchorSlot;
		}

		int hotbar = this.hotbarIndex >= 0 ? this.hotbarIndex : player.getInventory().getSelectedSlot();
		return findHotbarSlot(menu, player, hotbar);
	}

	private int findPlayerSlot(AbstractContainerMenu menu, LocalPlayer player, ItemStack stack) {
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);

			if (slot.container != player.getInventory() || slot.isFake()) {
				continue;
			}

			ItemStack inSlot = slot.getItem();

			if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, stack)) {
				return i;
			}
		}

		return -1;
	}

	private void reportMissing(LocalPlayer player, ItemStack stack) {
		player.sendSystemMessage(Component.translatable("message.block_zip.missing", stack.getHoverName()));
	}
}
