package com.blockzip.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 只用来读取原版容器界面的几个受保护字段（鼠标下的格子、界面左上角坐标），
 * 不改动任何原版逻辑。菜单本身用原版公开的 {@code getMenu()} 即可。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("hoveredSlot")
	Slot getHoveredSlot();

	@Accessor("leftPos")
	int getLeftPos();

	@Accessor("topPos")
	int getTopPos();
}
