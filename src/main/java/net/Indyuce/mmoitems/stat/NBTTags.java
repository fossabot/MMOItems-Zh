package net.Indyuce.mmoitems.stat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.edition.StatEdition;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.StringListStat;
import net.mmogroup.mmolib.api.item.ItemTag;
import net.mmogroup.mmolib.api.util.AltChar;

public class NBTTags extends StringListStat {
	public NBTTags() {
		super("CUSTOM_NBT", Material.NAME_TAG, "NBT Tags", new String[] { "Custom NBT Tags." }, new String[] { "all" });
	}

	@Override
	@SuppressWarnings("unchecked")
	public StringListData whenInitialized(Object object) {
		Validate.isTrue(object instanceof List<?>, "Must specify a string list");
		return new StringListData((List<String>) object);
	}

	@Override
	public void whenClicked(EditionInventory inv, InventoryClickEvent event) {
		if (event.getAction() == InventoryAction.PICKUP_ALL)
			new StatEdition(inv, ItemStats.NBT_TAGS).enable("Write in the chat the NBT tag you want to add.",
					ChatColor.AQUA + "Format: {Tag Name} {Tag Value}");

		if (event.getAction() == InventoryAction.PICKUP_HALF) {
			if (inv.getEditedSection().contains("custom-nbt")) {
				List<String> nbtTags = inv.getEditedSection().getStringList("custom-nbt");
				if (nbtTags.size() < 1)
					return;

				String last = nbtTags.get(nbtTags.size() - 1);
				nbtTags.remove(last);
				inv.getEditedSection().set("custom-nbt", nbtTags);
				inv.registerTemplateEdition();
				inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "Successfully removed '" + last + "'.");
			}
		}
	}

	@Override
	public void whenInput(EditionInventory inv, String message, Object... info) {
		Validate.isTrue(message.split(" ").length > 1, "Use this format: {Tag Name} {Tag Value}");
		List<String> customNbt = inv.getEditedSection().contains("custom-nbt") ? inv.getEditedSection().getStringList("custom-nbt")
				: new ArrayList<>();
		customNbt.add(message);

		inv.getEditedSection().set("custom-nbt", customNbt);
		inv.registerTemplateEdition();
		inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "StringListStat successfully added.");
	}

	@Override
	public void whenDisplayed(List<String> lore, Optional<RandomStatData> statData) {
		if (statData.isPresent()) {
			lore.add(ChatColor.GRAY + "Current Value:");
			StringListData data = (StringListData) statData.get();
			data.getList().forEach(str -> lore.add(ChatColor.GRAY + str));

		} else
			lore.add(ChatColor.GRAY + "Current Value: " + ChatColor.RED + "None");

		lore.add("");
		lore.add(ChatColor.YELLOW + AltChar.listDash + " Click to add a tag.");
		lore.add(ChatColor.YELLOW + AltChar.listDash + " Right click to remove the last tag.");
	}

	@Override
	public void whenApplied(ItemStackBuilder item, StatData data) {
		JsonArray array = new JsonArray();
		((StringListData) data).getList().forEach(tag -> {
			array.add(tag);

			item.addItemTag(new ItemTag(tag.substring(0, tag.indexOf(' ')), calculateObjectType(tag.substring(tag.indexOf(' ') + 1))));
		});
		item.addItemTag(new ItemTag("MMOITEMS_NBTTAGS", array.toString()));
	}

	@Override
	public void whenLoaded(ReadMMOItem mmoitem) {
		if (mmoitem.getNBT().hasTag("MMOITEMS_NBTTAGS"))
			mmoitem.setData(ItemStats.NBT_TAGS,
					new StringListData(new JsonParser().parse(mmoitem.getNBT().getString("MMOITEMS_NBTTAGS")).getAsJsonArray()));
	}

	public Object calculateObjectType(String input) {
		if (input.equalsIgnoreCase("true"))
			return true;
		if (input.equalsIgnoreCase("false"))
			return false;
		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException ignored) {}
		try {
			return Double.parseDouble(input);
		} catch (NumberFormatException ignored) {}
		if (input.contains("[") && input.contains("]")) {
			List<String> entries = new ArrayList<>();
			for (String s : input.replace("[", "").replace("]", "").split(","))
				entries.add(s.replace("\"", ""));
			return entries;
		}
		return input;
	}
}
