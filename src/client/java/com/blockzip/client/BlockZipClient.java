package com.blockzip.client;

import com.blockzip.client.debug.SelfTest;
import com.blockzip.client.ui.VariantPanel;
import com.blockzip.client.variant.VariantIndex;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/**
 * Block_zip —— 把方块变种聚合成一个集合。
 *
 * <p>选中一个方块时按下波浪键（可在“选项 → 控制 → 按键绑定”中改成任意键），
 * 就会在它旁边竖向展开这个方块的全部变种（半砖、楼梯、氧化/打蜡等随时间变化的状态），
 * 滚轮或方向键切换，点击或回车取出。</p>
 */
public class BlockZipClient implements ClientModInitializer {
	public static final String MOD_ID = "block_zip";

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(id("main"));

	/** 波浪键 / 反引号键。默认是键盘左上角 Esc 下面那个键，可自由改键。 */
	public static final KeyMapping OPEN_VARIANTS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.block_zip.open_variants",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_GRAVE,
			CATEGORY));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** 上一 tick 波浪键是否已经按下，用来过滤长按自动重复。 */
	private static boolean openKeyWasDown;

	@Override
	public void onInitializeClient() {
		// 变种集合的索引要等方块注册表就绪后再建，这里只做标记，第一次用到时才真正构建。
		VariantIndex.warmUp();

		// ---------- 物品栏 / 创造模式物品栏 / 任意容器界面 ----------
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}

			ScreenKeyboardEvents.allowKeyPress(screen).register(
					(s, keyEvent) -> !VariantPanel.get().onKeyPressed(containerScreen, keyEvent));
			ScreenKeyboardEvents.allowKeyRelease(screen).register(
					(s, keyEvent) -> !VariantPanel.get().onKeyReleased(keyEvent));
			// 鼠标的点击/滚轮不在这里挂：Fabric 的这两个事件在容器界面上语义不对
			// （点击取消不掉子类的处理、滚轮压根不触发），改用 AbstractContainerScreenMixin。
			ScreenEvents.afterForeground(screen).register(
					(s, graphics, mouseX, mouseY, tickDelta) -> VariantPanel.get().renderInScreen(containerScreen, graphics, mouseX, mouseY));
			ScreenEvents.remove(screen).register(s -> VariantPanel.get().onScreenClosed());
		});

		// ---------- 世界里（手持方块、没开界面时）：画在快捷栏上方 ----------
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id("variant_panel"),
				(graphics, tickDelta) -> VariantPanel.get().renderInHud(graphics));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 长按会连续产生 click（系统自动重复），只把“刚按下”那一下当打开/收起，
			// 否则面板会疯狂开合。
			boolean down = OPEN_VARIANTS.isDown();
			boolean clicked = false;

			while (OPEN_VARIANTS.consumeClick()) {
				clicked = true;
			}

			if (clicked && !openKeyWasDown && client.gui.screen() == null) {
				VariantPanel.get().toggleForHeldItem(client);
			}

			openKeyWasDown = down;

			VariantPanel.get().tickInWorld(client);
			// 只有设置 BLOCKZIP_SELFTEST=1 时才会做事，平时是空转
			SelfTest.tick(client);
		});
	}
}
