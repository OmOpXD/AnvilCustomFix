package com.custom.anvilfix;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;

public class AnvilFixPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);

        if (first == null || second == null || first.getType().isAir() || second.getType().isAir()) {
            return;
        }

        if (first.getAmount() > 1 || second.getAmount() > 1) {
            return;
        }

        boolean firstIsBook = first.getType() == Material.ENCHANTED_BOOK;
        boolean secondIsBook = second.getType() == Material.ENCHANTED_BOOK;

        if (firstIsBook && secondIsBook) {
            handleBookMerge(event, first, second);
        } else if (!firstIsBook && secondIsBook) {
            handleItemAndBook(event, first, second);
        } else if (!firstIsBook && first.getType() == second.getType()) {
            handleItemMerge(event, first, second);
        }
    }

    private void handleBookMerge(PrepareAnvilEvent event, ItemStack first, ItemStack second) {
        if (!(first.getItemMeta() instanceof EnchantmentStorageMeta firstMeta)
                || !(second.getItemMeta() instanceof EnchantmentStorageMeta secondMeta)) {
            return;
        }

        Map<Enchantment, Integer> firstEnchants = firstMeta.getStoredEnchants();
        Map<Enchantment, Integer> secondEnchants = secondMeta.getStoredEnchants();
        if (secondEnchants.isEmpty()) {
            return;
        }

        boolean vanillaProduced = event.getResult() != null && !event.getResult().getType().isAir();
        ItemStack result = vanillaProduced ? event.getResult().clone() : first.clone();
        if (!(result.getItemMeta() instanceof EnchantmentStorageMeta resultMeta)) {
            return;
        }

        int addedCost = 0;
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int secondLevel = entry.getValue();

            if (hasConflict(ench, resultMeta.getStoredEnchants().keySet())) {
                continue;
            }

            int firstLevel = firstEnchants.getOrDefault(ench, 0);
            int targetLevel = calculateTargetLevel(ench, firstLevel, secondLevel);

            if (targetLevel > resultMeta.getStoredEnchantLevel(ench)) {
                resultMeta.addStoredEnchant(ench, targetLevel, true);
                addedCost += Math.max(1, targetLevel);
                changed = true;
            }
        }

        if (changed) {
            result.setItemMeta(resultMeta);
            applyResult(event, result, addedCost);
        }
    }

    private void handleItemAndBook(PrepareAnvilEvent event, ItemStack first, ItemStack second) {
        if (!(second.getItemMeta() instanceof EnchantmentStorageMeta secondMeta)) {
            return;
        }

        Map<Enchantment, Integer> secondEnchants = secondMeta.getStoredEnchants();
        if (secondEnchants.isEmpty()) {
            return;
        }

        Map<Enchantment, Integer> firstEnchants = first.getEnchantments();
        boolean vanillaProduced = event.getResult() != null && !event.getResult().getType().isAir();
        ItemStack result = vanillaProduced ? event.getResult().clone() : first.clone();

        int addedCost = 0;
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int secondLevel = entry.getValue();

            if (!ench.canEnchantItem(first)) {
                continue;
            }

            if (hasConflict(ench, result.getEnchantments().keySet())) {
                continue;
            }

            int firstLevel = firstEnchants.getOrDefault(ench, 0);
            int targetLevel = calculateTargetLevel(ench, firstLevel, secondLevel);

            if (targetLevel > result.getEnchantmentLevel(ench)) {
                result.addUnsafeEnchantment(ench, targetLevel);
                addedCost += Math.max(1, targetLevel);
                changed = true;
            }
        }

        if (changed) {
            applyResult(event, result, addedCost);
        }
    }

    private void handleItemMerge(PrepareAnvilEvent event, ItemStack first, ItemStack second) {
        Map<Enchantment, Integer> secondEnchants = second.getEnchantments();
        if (secondEnchants.isEmpty()) {
            return;
        }

        Map<Enchantment, Integer> firstEnchants = first.getEnchantments();
        boolean vanillaProduced = event.getResult() != null && !event.getResult().getType().isAir();
        ItemStack result = vanillaProduced ? event.getResult().clone() : first.clone();

        int addedCost = 0;
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int secondLevel = entry.getValue();

            if (!ench.canEnchantItem(first)) {
                continue;
            }

            if (hasConflict(ench, result.getEnchantments().keySet())) {
                continue;
            }

            int firstLevel = firstEnchants.getOrDefault(ench, 0);
            int targetLevel = calculateTargetLevel(ench, firstLevel, secondLevel);

            if (targetLevel > result.getEnchantmentLevel(ench)) {
                result.addUnsafeEnchantment(ench, targetLevel);
                addedCost += Math.max(1, targetLevel);
                changed = true;
            }
        }

        if (changed) {
            if (!vanillaProduced) {
                combineDurability(first, second, result);
            }
            applyResult(event, result, addedCost);
        }
    }

    private void combineDurability(ItemStack first, ItemStack second, ItemStack result) {
        int maxDurability = first.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        if (first.getItemMeta() instanceof Damageable d1
                && second.getItemMeta() instanceof Damageable d2
                && result.getItemMeta() instanceof Damageable resDmg) {
            int rem1 = maxDurability - d1.getDamage();
            int rem2 = maxDurability - d2.getDamage();
            int combined = Math.min(maxDurability, rem1 + rem2 + (maxDurability * 12 / 100));
            resDmg.setDamage(Math.max(0, maxDurability - combined));
            result.setItemMeta(resDmg);
        }
    }

    private int calculateTargetLevel(Enchantment ench, int firstLevel, int secondLevel) {
        if (firstLevel == secondLevel && firstLevel < ench.getMaxLevel()) {
            return firstLevel + 1;
        }
        return Math.max(firstLevel, secondLevel);
    }

    private boolean hasConflict(Enchantment candidate, Set<Enchantment> existingEnchants) {
        for (Enchantment existing : existingEnchants) {
            if (!existing.equals(candidate) && existing.conflictsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void applyResult(PrepareAnvilEvent event, ItemStack result, int addedCost) {
        AnvilView view = event.getView();
        int baseCost = Math.max(0, view.getRepairCost());
        int finalCost = Math.min(39, Math.max(1, baseCost + addedCost));
        view.setRepairCost(finalCost);
        event.setResult(result);
    }
}
