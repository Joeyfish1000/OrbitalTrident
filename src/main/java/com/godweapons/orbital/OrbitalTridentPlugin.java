package com.godweapons.orbital;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class OrbitalTridentPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey weaponKey;
    private NamespacedKey thrownKey;
    private NamespacedKey rainKey;

    @Override
    public void onEnable() {
        this.weaponKey = new NamespacedKey(this, "weapon_id");
        this.thrownKey = new NamespacedKey(this, "is_orbital_thrown");
        this.rainKey = new NamespacedKey(this, "is_orbital_rain");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("orbitalcannon") != null) {
            getCommand("orbitalcannon").setExecutor(this);
        }
        getLogger().info("⚡ Orbital Trident Cannon V2 loaded! Vanilla aesthetic locked.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && player.hasPermission("godweapons.admin")) {
            ItemStack trident = new ItemStack(Material.TRIDENT);
            ItemMeta meta = trident.getItemMeta();
            
            // Plain vanilla text, no custom colors
            meta.displayName(Component.text("Orbital Trident Cannon"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Throw to call down a celestial bombardment."));
            meta.lore(lore);
            
            meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "orbital_cannon");
            meta.setUnbreakable(true);
            trident.setItemMeta(meta);
            
            player.getInventory().addItem(trident);
            player.sendMessage(Component.text("Equipped the Orbital Trident Cannon."));
        }
        return true;
    }

    @EventHandler
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player) {
            ItemStack item = trident.getItem();
            if (item != null && item.hasItemMeta()) {
                String id = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
                if ("orbital_cannon".equals(id)) {
                    trident.getPersistentDataContainer().set(thrownKey, PersistentDataType.BYTE, (byte) 1);
                }
            }
        }
    }

    @EventHandler
    public void onTridentLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;

        // Sequence 1: The original thrown trident lands
        if (trident.getPersistentDataContainer().has(thrownKey, PersistentDataType.BYTE)) {
            Location hitLoc = null;
            if (event.getHitBlock() != null) hitLoc = event.getHitBlock().getLocation();
            else if (event.getHitEntity() != null) hitLoc = event.getHitEntity().getLocation();
            
            if (hitLoc == null) return;
            
            // Instantly despawn the original trident
            trident.remove();

            Location finalHitLoc = hitLoc.clone();
            Player shooter = trident.getShooter() instanceof Player ? (Player) trident.getShooter() : null;

            // Initiate strike delay without any flashy particles
            new BukkitRunnable() {
                @Override
                public void run() {
                    launchOrbitalStrike(finalHitLoc, shooter);
                }
            }.runTaskLater(this, 15); 
        }
        
        // Sequence 2: The raining tridents from the sky hit the ground
        else if (trident.getPersistentDataContainer().has(rainKey, PersistentDataType.BYTE)) {
            Location hitLoc = trident.getLocation();
            
            for (Entity e : hitLoc.getWorld().getNearbyEntities(hitLoc, 2.0, 2.0, 2.0)) {
                if (e instanceof LivingEntity le && !e.equals(trident.getShooter())) {
                    le.damage(8.0, (Player) trident.getShooter());
                }
            }
            
            // Clean them up after a few seconds without smoke particles
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (trident.isValid()) {
                        trident.remove();
                    }
                }
            }.runTaskLater(this, 50); 
        }
    }

    private void launchOrbitalStrike(Location target, Player shooter) {
        double startY = Math.min(target.getWorld().getMaxHeight() - 5, target.getY() + 60);

        int[] ringCounts = {1, 8, 16, 24};
        double[] ringRadii = {0.0, 3.0, 6.0, 9.0};

        // Create the vanilla Channeling Item profile to apply to the falling entities
        ItemStack channeledTrident = new ItemStack(Material.TRIDENT);
        channeledTrident.addEnchantment(Enchantment.CHANNELING, 1);

        for (int i = 0; i < ringCounts.length; i++) {
            int count = ringCounts[i];
            double radius = ringRadii[i];

            // Generates a perfect trigonometric circle of tridents
            for (int j = 0; j < count; j++) {
                double angle = 2 * Math.PI * j / count;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location spawnLoc = target.clone().add(x, 0, z);
                spawnLoc.setY(startY);

                Trident rainingTrident = target.getWorld().spawn(spawnLoc, Trident.class);
                
                rainingTrident.getPersistentDataContainer().set(rainKey, PersistentDataType.BYTE, (byte) 1);
                if (shooter != null) rainingTrident.setShooter(shooter);
                
                rainingTrident.setVelocity(new Vector(0, -4.0, 0));
                
                // Vanilla Overrides
                rainingTrident.setItem(channeledTrident); // Grants Channeling enchant physically
                rainingTrident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); // Prevents item harvesting
            }
        }
    }
}
