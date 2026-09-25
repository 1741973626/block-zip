package com.blockzip.client.mixin;

import com.blockzip.client.ui.VariantPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 容器界面的鼠标按下/松开必须在 AbstractContainerScreen 这一层拦。
 *
 * <p>原因：Fabric 的 {@code ScreenMouseEvents} 打在 {@code Screen} 上——点击事件那里
 * 取消的只是 {@code super.mouseClicked} 的返回值，子类（AbstractContainerScreen）
 * 自己覆写的处理照样会跑，于是点击会穿透到面板下面的格子。</p>
 *
 * <p>滚轮不在这里：1.21.1 的 {@code AbstractContainerScreen} 根本没有覆写
 * {@code mouseScrolled}（26.3 才有），所以没有可注入的目标，改由
 * {@link MouseHandlerMixin} 在 {@code MouseHandler#onScroll} 处统一处理。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	// 绘制**不在这里**：容器界面（尤其创造物品栏）会在 super.render 之后继续画分类栏/物品/tooltip，
	// 注在这里的 RETURN 也压不住它们。面板改由 ScreenPanelMixin 注入
	// Screen#renderWithTooltip 的 RETURN（屏幕渲染最外层）来画。

	/**
	 * 面板打开时，让原版的格子 tooltip 让位。
	 *
	 * <p>面板盖住了鼠标下面的格子，但原版照样会为那个格子画 tooltip（内容还会带上创造模式分类名），
	 * 于是它浮在面板上、和面板自己显示的物品名打架。面板展开期间直接取消掉即可。</p>
	 */
	@Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
	private void blockzip$hideVanillaTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callbackInfo) {
		if (VariantPanel.get().isOpen()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
	private void blockzip$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo) {
		VariantPanel.debug("mouseClicked button=" + button
				+ " at=" + (int) mouseX + "," + (int) mouseY
				+ " open=" + VariantPanel.get().isOpen());

		// 1.21.1 的 mouseClicked 没有修饰键参数，Shift 只能自己问键盘状态
		if (VariantPanel.get().onMouseClicked((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, button,
				Screen.hasShiftDown())) {
			callbackInfo.setReturnValue(true);
		}
	}

	/**
	 * 松开左键也要拦：原版 {@code mouseReleased} 看到鼠标上拿着东西就会把它“放”进光标下的格子，
	 * 那样玩家从面板里点出来的变种会立刻被塞回背包，表现为“拿不起来”。
	 */
	@Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true)
	private void blockzip$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo) {
		boolean wasOpen = VariantPanel.get().isOpen();
		boolean consumed = VariantPanel.get().onMouseReleased((AbstractContainerScreen<?>) (Object) this, button);

		if (wasOpen || consumed) {
			VariantPanel.debug("mouseReleased button=" + button
					+ " wasOpen=" + wasOpen + " consumed=" + consumed);
		}

		if (consumed) {
			callbackInfo.setReturnValue(true);
		}
	}
}
