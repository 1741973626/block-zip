package com.blockzip.client.mixin;

import com.blockzip.client.ui.VariantPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

/**
 * 世界里（没开界面时）原版把滚轮用来切快捷栏，本模组要借用它来选择变种：
 * 面板展开期间吃掉滚轮事件，其余情况完全交还原版。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void blockzip$onScroll(long handle, double xOffset, double yOffset, CallbackInfo callbackInfo) {
		if (VariantPanel.get().onWorldScroll(yOffset)) {
			callbackInfo.cancel();
		}
	}
}
