package com.blockzip;

import net.fabricmc.api.ModInitializer;

/**
 * Block_zip 是纯客户端模组，公共侧不需要做任何事。
 * 保留这个入口只是为了让公共源集不为空（否则开发环境启动时会提示类路径缺失）。
 */
public class BlockZip implements ModInitializer {
	@Override
	public void onInitialize() {
	}
}
