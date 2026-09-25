package com.blockzip.client.mixin;

import com.blockzip.client.ui.VariantPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 滚轮事件在这里统一拦：
 *
 * <ul>
 *   <li>世界里（没开界面时）原版把滚轮用来切快捷栏，本模组要借用它来选择变种；</li>
 *   <li>界面里 1.21.1 的 {@code AbstractContainerScreen} 没有覆写 {@code mouseScrolled}
 *       （26.3 才有），Fabric 的 {@code ScreenMouseEvents.allowMouseScroll} 又只能在
 *       {@code Screen} 这一层取消，拦不住子类，所以在 {@code MouseHandler#onScroll}
 *       里直接判定，面板展开期间滚轮只用来切换选中项。</li>
 * </ul>
 *
 * 其余情况完全交还原版。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void blockzip$onScroll(long handle, double xOffset, double yOffset, CallbackInfo callbackInfo) {
		Minecraft minecraft = Minecraft.getInstance();

		if (handle != minecraft.getWindow().getWindow()) {
			return;
		}

		if (minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) {
			// 面板只认自己打开时那个界面；鼠标坐标只用于日志，顺手按原版算法换算一下
			Window window = minecraft.getWindow();
			double guiX = minecraft.mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
			double guiY = minecraft.mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();

			if (VariantPanel.get().onMouseScrolled(containerScreen, guiX, guiY, yOffset)) {
				callbackInfo.cancel();
			}

			return;
		}

		if (VariantPanel.get().onWorldScroll(yOffset)) {
			callbackInfo.cancel();
		}
	}
}
