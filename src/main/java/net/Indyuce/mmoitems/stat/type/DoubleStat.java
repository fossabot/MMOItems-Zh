package net.Indyuce.mmoitems.stat.type;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.SupportedNBTTagValues;
import io.lumine.mythic.lib.api.util.AltChar;
import io.lumine.mythic.lib.api.util.ui.FriendlyFeedbackCategory;
import io.lumine.mythic.lib.api.util.ui.FriendlyFeedbackProvider;
import io.lumine.mythic.lib.api.util.ui.PlusMinusPercent;
import io.lumine.mythic.lib.api.util.ui.SilentNumbers;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.MMOUtils;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.edition.StatEdition;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.api.util.NumericStatFormula;
import net.Indyuce.mmoitems.api.util.StatFormat;
import net.Indyuce.mmoitems.api.util.message.FFPMMOItems;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.data.type.UpgradeInfo;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;


public class DoubleStat extends ItemStat implements Upgradable, Previewable {
	private static final DecimalFormat digit = new DecimalFormat("0.####");

	public DoubleStat(String id, Material mat, String name, String[] lore) {
		super(id, mat, name, lore, new String[] { "!miscellaneous", "!block", "all" });
	}

	public DoubleStat(String id, Material mat, String name, String[] lore, String[] types, Material... materials) {
		super(id, mat, name, lore, types, materials);
	}

	/**
	 * @return If this stat supports negatives stat values
	 */
	public boolean handleNegativeStats() {
		return true;
	}

	/**
	 * Usually, a greater magnitude of stat benefits the player (more health, more attack damage).
	 * <p>However, its not impossible for a stat to be evil instead, who knows?
	 */
	public boolean moreIsBetter() { return true; }

	@Override
	public RandomStatData whenInitialized(Object object) {

		if (object instanceof Number)
			return new NumericStatFormula(Double.parseDouble(object.toString()), 0, 0, 0);

		if (object instanceof ConfigurationSection)
			return new NumericStatFormula(object);

		throw new IllegalArgumentException("Must specify a number or a config section");
	}

	@Override
	public void whenApplied(@NotNull ItemStackBuilder item, @NotNull StatData data) {

		// Get Value
		double value = ((DoubleData) data).getValue();

		// Cancel if it its NEGATIVE and this doesn't support negative stats.
		if (value < 0 && !handleNegativeStats()) { return; }

		// Identify the upgrade amount
		double upgradeShift = 0;

		// Displaying upgrades?
		if (UpgradeTemplate.isDisplayingUpgrades() && item.getMMOItem().getUpgradeLevel() != 0) {

			// Get stat history
			StatHistory hist = item.getMMOItem().getStatHistory(this);
			if (hist != null) {

				// Get as if it had never been upgraded
				DoubleData uData = (DoubleData) hist.recalculateUnupgraded();

				// Calculate Difference
				upgradeShift = value - uData.getValue();
			}

		}

		// Display if not ZERO
		if (value != 0 || upgradeShift != 0) {

			// Displaying upgrades?
			if (upgradeShift != 0) {

				item.getLore().insert(getPath(), formatNumericStat(value, "#", new StatFormat("##").format(value))
						+ MythicLib.plugin.parseColors(UpgradeTemplate.getUpgradeChangeSuffix(plus(upgradeShift) + (new StatFormat("##").format(upgradeShift)), !isGood(upgradeShift))));

			} else {

				// Just display normally
				item.getLore().insert(getPath(), formatNumericStat(value, "#", new StatFormat("##").format(value)));
			}
		}

		// Add NBT Path
		item.addItemTag(getAppliedNBT(data));
	}

	@Override
	public void whenPreviewed(@NotNull ItemStackBuilder item, @NotNull StatData currentData, @NotNull RandomStatData templateData) throws IllegalArgumentException {
		Validate.isTrue(currentData instanceof DoubleData, "Current Data is not Double Data");
		Validate.isTrue(templateData instanceof NumericStatFormula, "Template Data is not Numeric Stat Formula");

		// Get Value
		double techMinimum = ((NumericStatFormula) templateData).calculate(0, -2.5);
		double techMaximum = ((NumericStatFormula) templateData).calculate(0, 2.5);

		// Cancel if it its NEGATIVE and this doesn't support negative stats.
		if (techMaximum < 0 && !handleNegativeStats()) { return; }
		if (techMinimum < 0 && !handleNegativeStats()) { techMinimum = 0; }
		if (techMinimum < ((NumericStatFormula) templateData).getBase() - ((NumericStatFormula) templateData).getMaxSpread()) { techMinimum = ((NumericStatFormula) templateData).getBase() - ((NumericStatFormula) templateData).getMaxSpread(); }
		if (techMaximum > ((NumericStatFormula) templateData).getBase() + ((NumericStatFormula) templateData).getMaxSpread()) { techMaximum = ((NumericStatFormula) templateData).getBase() + ((NumericStatFormula) templateData).getMaxSpread(); }

		// Add NBT Path
		item.addItemTag(getAppliedNBT(currentData));

		// Display if not ZERO
		if (techMinimum != 0 || techMaximum != 0) {

			String builtRange;
			if (SilentNumbers.round(techMinimum, 2) == SilentNumbers.round(techMaximum, 2)) { builtRange = new StatFormat("##").format(techMinimum); }
			else { builtRange = new StatFormat("##").format(techMinimum) + "-" + new StatFormat("##").format(techMaximum); }

			// Just display normally
			item.getLore().insert(getPath(), formatNumericStat(techMinimum, "#", builtRange));
		}
	}

	@NotNull String plus(double amount) { if (amount >= 0) { return "+"; } else return ""; }

	/**
	 * Usually, a greater magnitude of stat benefits the player (more health, more attack damage).
	 * <p>However, its not impossible for a stat to be evil instead, who knows?
	 * <p></p>
	 * This will return true if:
	 * <p> > The amount is positive, and more benefits the player
	 * </p> > The amount is negative, and more hurts the player
	 */
	public boolean isGood(double amount) {
		if (moreIsBetter()) {
			return amount >= 0;
		} else {
			return  amount <= 0;
		}
	}

	@Override
	public @NotNull ArrayList<ItemTag> getAppliedNBT(@NotNull StatData data) {

		// Create Fresh
		ArrayList<ItemTag> ret = new ArrayList<>();

		// Add sole tag
		ret.add(new ItemTag(getNBTPath(), ((DoubleData) data).getValue()));

		// Return thay
		return ret;
	}

	@Override
	public void whenLoaded(@NotNull ReadMMOItem mmoitem) {

		// Get tags
		ArrayList<ItemTag> relevantTags = new ArrayList<>();

		// Add sole tag
		if (mmoitem.getNBT().hasTag(getNBTPath()))
			relevantTags.add(ItemTag.getTagAtPath(getNBTPath(), mmoitem.getNBT(), SupportedNBTTagValues.DOUBLE));

		// Use that
		DoubleData bakedData = (DoubleData) getLoadedNBT(relevantTags);

		// Valid?
		if (bakedData != null) {

			// Set
			mmoitem.setData(this, bakedData);
		}
	}
	@Override
	public @Nullable StatData getLoadedNBT(@NotNull ArrayList<ItemTag> storedTags) {

		// You got a double righ
		ItemTag tg = ItemTag.getTagAtPath(getNBTPath(), storedTags);

		// Found righ
		if (tg != null) {

			// Get number
			Double value = (Double) tg.getValue();

			// Thats it
			return new DoubleData(value);
		}

		// Fail
		return null;
	}

	@Override
	public void whenClicked(@NotNull EditionInventory inv, @NotNull InventoryClickEvent event) {
		if (event.getAction() == InventoryAction.PICKUP_HALF) {
			inv.getEditedSection().set(getPath(), null);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "Successfully removed " + getName() + ChatColor.GRAY + ".");
			return;
		}
		new StatEdition(inv, this).enable("Write in the chat the numeric value you want.",
				"Second Format: {Base} {Scaling Value} {Spread} {Max Spread}", "Third Format: {Min Value} -> {Max Value}");
	}

	@Override
	public void whenInput(@NotNull EditionInventory inv, @NotNull String message, Object... info) {
		double base, scale, spread, maxSpread;

		/*
		 * Supports the old RANGE formula with a minimum and a maximum value and
		 * automatically makes the conversion to the newest system. This way
		 * users can keep using the old system if they don't want to adapt to
		 * the complex gaussian stat calculation
		 */
		if (message.contains("->")) {
			String[] split = message.replace(" ", "").split(Pattern.quote("->"));
			Validate.isTrue(split.length > 1, "You must specif two (both min and max) values");

			double min = Double.parseDouble(split[0]), max = Double.parseDouble(split[1]);
			Validate.isTrue(max > min, "Max value must be greater than min value");

			base = MMOUtils.truncation(min == -max ? (max - min) * .05 : (min + max) / 2, 3);
			scale = 0; // No scale
			maxSpread = MMOUtils.truncation((max - min) / (2 * base), 3);
			spread = MMOUtils.truncation(.8 * maxSpread, 3);
		}

		/*
		 * Newest system with gaussian values calculation
		 */
		else {
			String[] split = message.split(" ");
			base = MMOUtils.parseDouble(split[0]);
			scale = split.length > 1 ? MMOUtils.parseDouble(split[1]) : 0;
			spread = split.length > 2 ? MMOUtils.parseDouble(split[2]) : 0;
			maxSpread = split.length > 3 ? MMOUtils.parseDouble(split[3]) : 0;
		}

		// Save as a flat formula
		if (scale == 0 && spread == 0 && maxSpread == 0)
			inv.getEditedSection().set(getPath(), base);

		else {
			inv.getEditedSection().set(getPath() + ".base", base);
			inv.getEditedSection().set(getPath() + ".scale", scale == 0 ? null : scale);
			inv.getEditedSection().set(getPath() + ".spread", spread == 0 ? null : spread);
			inv.getEditedSection().set(getPath() + ".max-spread", maxSpread == 0 ? null : maxSpread);
		}

		inv.registerTemplateEdition();
		inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + getName() + " successfully changed to {" + base + " - " + scale + " - " + spread
				+ " - " + maxSpread + "}");
	}

	@Override
	public void whenDisplayed(List<String> lore, Optional<RandomStatData> statData) {
		if (statData.isPresent()) {
			NumericStatFormula data = (NumericStatFormula) statData.get();
			lore.add(ChatColor.GRAY + "Base Value: " + ChatColor.GREEN + digit.format(data.getBase())
					+ (data.getScale() != 0 ? ChatColor.GRAY + " (+" + ChatColor.GREEN + digit.format(data.getScale()) + ChatColor.GRAY + ")" : ""));
			if (data.getSpread() > 0)
				lore.add(ChatColor.GRAY + "Spread: " + ChatColor.GREEN + digit.format(data.getSpread() * 100) + "%" + ChatColor.GRAY + " (Max: "
						+ ChatColor.GREEN + digit.format(data.getMaxSpread() * 100) + "%" + ChatColor.GRAY + ")");

		} else
			lore.add(ChatColor.GRAY + "Current Value: " + ChatColor.GREEN + "---");

		lore.add("");
		lore.add(ChatColor.YELLOW + AltChar.listDash + " Left click to change this value.");
		lore.add(ChatColor.YELLOW + AltChar.listDash + " Right click to remove this value.");
	}

	@Override
	public @NotNull StatData getClearStatData() {
		return new DoubleData(0D);
	}

	@NotNull
	@Override
	public UpgradeInfo loadUpgradeInfo(@Nullable Object obj) throws IllegalArgumentException {

		// Return result of thay
		return DoubleUpgradeInfo.GetFrom(obj);
	}

	@NotNull
	@Override
	public StatData apply(@NotNull StatData original, @NotNull UpgradeInfo info, int level) {

		// Must be DoubleData
		if (original instanceof DoubleData && info instanceof DoubleUpgradeInfo) {

			// Get value
			double value = ((DoubleData) original).getValue();

			// If leveling up
			if (level > 0) {

				// While still positive
				while (level > 0) {

					// Apply PMP Operation Positively
					value = ((DoubleUpgradeInfo) info).getPMP().apply(value);

					// Decrease
					level--;
				}

			// Degrading the item
			} else if (level < 0) {

				// While still negative
				while (level < 0) {

					// Apply PMP Operation Reversibly
					value = ((DoubleUpgradeInfo) info).getPMP().reverse(value);

					// Decrease
					level++;
				}
			}

			// Update
			((DoubleData) original).setValue(value);
		}

		// Upgraded
		return original;
	}

	public static class DoubleUpgradeInfo implements UpgradeInfo {
		@NotNull PlusMinusPercent pmp;

		/**
		 * Generate a <code>DoubleUpgradeInfo</code> from this <code><b>String</b></code>
		 * that represents a {@link PlusMinusPercent}.
		 * <p></p>
		 * To keep older MMOItems versions working the same way, instead of having no prefix
		 * to use the <i>set</i> function of the PMP, one must use an <b><code>s</code></b> prefix.
		 * @param obj A <code><u>String</u></code> that encodes for a PMP.
		 * @throws IllegalArgumentException If any part of the operation goes wrong (including reading the PMP).
		 */
		@NotNull public static DoubleUpgradeInfo GetFrom(@Nullable Object obj) throws IllegalArgumentException {

			// Shall not be null
			Validate.notNull(obj, FriendlyFeedbackProvider.quickForConsole(FFPMMOItems.get(), "Upgrade operation must not be null"));

			// Does the string exist?
			String str = obj.toString();
			if (str.isEmpty()) {
				throw new IllegalArgumentException(
						FriendlyFeedbackProvider.quickForConsole(FFPMMOItems.get(), "Upgrade operation is empty"));
			}

			// Adapt to PMP format
			char c = str.charAt(0); if (c == 's') { str = str.substring(1); } else if (c != '+' && c != '-' && c != 'n') { str = '+' + str; }

			// Is it a valid plus minus percent?
			FriendlyFeedbackProvider ffp = new FriendlyFeedbackProvider(FFPMMOItems.get());
			PlusMinusPercent pmpRead = PlusMinusPercent.getFromString(str, ffp);
			if (pmpRead == null) {
				throw new IllegalArgumentException(
						ffp.getFeedbackOf(FriendlyFeedbackCategory.ERROR).get(0).forConsole(ffp.getPalette()));
			}

			// Success
			return new DoubleUpgradeInfo(pmpRead);
		}

		public DoubleUpgradeInfo(@NotNull PlusMinusPercent pmp) { this.pmp = pmp; }

		/**
		 * The operation every level will perform.
		 * @see PlusMinusPercent
		 */
		@NotNull public PlusMinusPercent getPMP() { return pmp; }
	}
}
