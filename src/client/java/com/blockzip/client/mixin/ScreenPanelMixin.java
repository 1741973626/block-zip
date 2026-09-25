package com.blockzip.client.mixin;

import com.blockzip.client.ui.VariantPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 把变种面板画在**屏幕渲染的最外层**，这样它才盖得住原版内容。
 *
 * <p>为什么不能用 Fabric 的 {@code ScreenEvents.afterRender}：它挂在 {@code Screen#render} 那一层，
 * 而容器界面是先 {@code super.render}、之后才画槽位/物品/标签/tooltip
 * （{@code CreativeModeInventoryScreen#render} 更是 super 之后才画分类栏和垃圾槽 tooltip）。</p>
 *
 * <p>为什么不能注入 {@code AbstractContainerScreen#render}：那只是子类 render 里的中间一步，
 * 子类之后画的东西照样压在面板上（实测就是这么复发的）。</p>
 *
 * <p>{@code Screen#renderWithTooltip} 是最外面那一层：它先调 {@code render(...)}（含所有子类内容），
 * 再把延迟的 tooltip 画掉，然后才返回 —— 在它的 RETURN 处画，面板才是真正的顶层。</p>
 */
@Mixin(Screen.class)
public abstract class ScreenPanelMixin {
	@Inject(method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
	private void blockzip$renderPanelOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		// 只有容器界面（背包 / 创造物品栏 / 各种箱子）才需要这条折叠栏
		if ((Object) this instanceof AbstractContainerScreen<?> containerScreen) {
			// 关键：1.21.1 的 GuiGraphics 是按 RenderType 排队、由 flush()/endBatch() 统一提交的，
			// 提交顺序**不按画图顺序**（BufferSource 按 RenderType 分组）。所以即使我在最后画，
			// 面板底（GUI 类型）也可能被先入队的格子物品（item 类型）盖住 —— 在用户装着
			// sodium/iris/axiom/owo 等模组的实例里就会这样。
			// 解法：画之前先 flush（把原版已入队的内容，包括物品，真正画出去），
			//       画完再 flush（把面板自己的内容立刻画出去），前后各一次。
			graphics.flush();
			VariantPanel.get().renderInScreen(containerScreen, graphics, mouseX, mouseY);
			graphics.flush();
		}
	}
}
