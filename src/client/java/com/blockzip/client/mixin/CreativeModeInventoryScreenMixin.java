package com.blockzip.client.mixin;

import com.blockzip.client.ui.VariantPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

/**
 * 创造物品栏在 {@code super.mouseClicked / super.mouseReleased} **之前**先处理顶部分类栏和右侧滚动条：
 *
 * <pre>
 * if (event.button() == 1) {
 *     for (CreativeModeTab tab : CreativeModeTabs.tabs())
 *         if (this.checkTabClicked(tab, xm, ym)) return true;   // ← 在这里就返回了，轮不到面板
 *     if (... this.insideScrollbar(...)) { this.scrolling = ...; return true; }
 * }
 * return super.mouseClicked(event, doubleClick);
 * </pre>
 *
 * 所以折叠栏一旦长到盖住分类栏（面板贴的格子靠上时就会），点重叠部分就变成"点了分类栏"。
 * 这里让这两个判定在面板范围内直接返回 false，事件就会正常落到我们的鼠标处理里。
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	@Inject(method = "checkTabClicked", at = @At("HEAD"), cancellable = true)
	private void blockzip$checkTabClicked(CreativeModeTab tab, double xm, double ym, CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreenAccessor self = (AbstractContainerScreenAccessor) (Object) this;
		// checkTabClicked 收到的是相对 leftPos/topPos 的坐标
		if (VariantPanel.get().containsGui(xm + self.getLeftPos(), ym + self.getTopPos())) {
			VariantPanel.debug("panel covers a tab -> tab check skipped");
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "insideScrollbar", at = @At("HEAD"), cancellable = true)
	private void blockzip$insideScrollbar(double xm, double ym, CallbackInfoReturnable<Boolean> cir) {
		// insideScrollbar 收到的是绝对 GUI 坐标。这个方法渲染时也会被问到，所以不打日志免得刷屏
		if (VariantPanel.get().containsGui(xm, ym)) {
			cir.setReturnValue(false);
		}
	}
}
