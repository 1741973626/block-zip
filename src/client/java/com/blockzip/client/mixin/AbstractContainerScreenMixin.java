package com.blockzip.client.mixin;

import com.blockzip.client.ui.VariantPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 容器界面的鼠标事件必须在 AbstractContainerScreen 这一层拦。
 *
 * <p>原因：Fabric 的 {@code ScreenMouseEvents} 打在 {@code Screen} 上——
 * 点击事件那里取消的只是 {@code super.mouseClicked} 的返回值，子类照样继续处理，
 * 于是点击会穿透到面板下面的格子；而 {@code mouseScrolled} 更极端，
 * {@code AbstractContainerScreen} 压根不调用 super，那个事件在背包里永远不会触发。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
	private void blockzip$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> callbackInfo) {
		VariantPanel.debug("mouseClicked button=" + event.button()
				+ " at=" + (int) event.x() + "," + (int) event.y()
				+ " open=" + VariantPanel.get().isOpen());

		if (VariantPanel.get().onMouseClicked((AbstractContainerScreen<?>) (Object) this, event)) {
			callbackInfo.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void blockzip$mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> callbackInfo) {
		if (VariantPanel.get().onMouseScrolled((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, scrollY)) {
			callbackInfo.setReturnValue(true);
		}
	}

	/**
	 * 松开左键也要拦：原版 {@code mouseReleased} 看到鼠标上拿着东西就会把它“放”进光标下的格子，
	 * 那样玩家从面板里点出来的变种会立刻被塞回背包，表现为“拿不起来”。
	 */
	@Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
	private void blockzip$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> callbackInfo) {
		boolean wasOpen = VariantPanel.get().isOpen();
		boolean consumed = VariantPanel.get().onMouseReleased((AbstractContainerScreen<?>) (Object) this, event);

		if (wasOpen || consumed) {
			VariantPanel.debug("mouseReleased button=" + event.button()
					+ " wasOpen=" + wasOpen + " consumed=" + consumed);
		}

		if (consumed) {
			callbackInfo.setReturnValue(true);
		}
	}
}
