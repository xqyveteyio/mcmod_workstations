package dev.keyboard.breederscarecrow.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Adds the config button to the Mod Menu list. Only loaded when Mod Menu itself is installed,
 * and the Cloth Config screen is only touched once that mod is confirmed present as well.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return parent -> null;
		}

		return parent -> ClothConfigScreenFactory.create(parent);
	}
}
