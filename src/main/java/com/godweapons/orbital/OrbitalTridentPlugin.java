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
import java.util.Random;

public final class OrbitalTridentPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey weaponKey;
    private NamespacedKey thrownKey;
    private NamespacedKey rainKey;
    private NamespacedKey radiusKey;
    private NamespacedKey countKey;

    @Override
    public void onEnable() {
        this.weaponKey = new NamespacedKey(this, "weapon_id");
        this.thrownKey = new NamespacedKey(this, "is_orbital_thrown");
        this.rainKey = new NamespacedKey(this, "is_orbital_rain");
        
        // V4: Parameter Keys
        this.radiusKey = new NamespacedKey(this, "orbital_radius");
        this.countKey = new NamespacedKey(this, "orbital_count");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("orbitalcannon") != null) {
            getCommand("orbitalcannon").setExecutor(this);
        }
        getLogger().info("⚡ Orbital Trident Cannon V4 loaded! Parametric arguments enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && player.hasPermission("godweapons.admin")) {
            
            double radius = 9.0;
            int count = 241;
            
            // Parse custom parameters if provided
            if (args.length == 2) {
                try {
                    radius = Double.parseDouble(args[0]);
                    count = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid numbers. Usage: /orbitalcannon <radius> <count>"));
                    return true;
                }
            }
            
            ItemStack trident = new ItemStack(Material.TRIDENT);
            ItemMeta meta = trident.getItemMeta();
            
            meta.displayName(Component.text("Orbital Trident Cannon"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Radius: " + radius));
            lore.add(Component.text("Payload: " + count + " Tridents"));
            meta.lore(lore);
            
            meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "orbital_cannon");
            
            // Stamp the geometry data onto the item
            meta.getPersistentDataContainer().set(radiusKey, PersistentDataType.DOUBLE, radius);
            meta.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, count);
            
            meta.setUnbreakable(true);
            trident.setItemMeta(meta);
            
            player.getInventory().addItem(trident);
            player.sendMessage(Component.text("Equipped Parametric Orbital Cannon (R: " + radius + ", C: " + count + ")."));
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
                    
                    // Transfer parameter data to the thrown entity
                    if (item.getItemMeta().getPersistentDataContainer().has(radiusKey, PersistentDataType.DOUBLE)) {
                        double r = item.getItemMeta().getPersistentDataContainer().get(radiusKey, PersistentDataType.DOUBLE);
                        int c = item.getItemMeta().getPersistentDataContainer().get(countKey, PersistentDataType.INTEGER);
                        trident.getPersistentDataContainer().set(radiusKey, PersistentDataType.DOUBLE, r);
                        trident.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, c);
                    }
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
            
            // Extract geometry data before deletion
            double radius = trident.getPersistentDataContainer().getOrDefault(radiusKey, PersistentDataType.DOUBLE, 9.0);
            int count = trident.getPersistentDataContainer().getOrDefault(countKey, PersistentDataType.INTEGER, 241);
            
            // Instantly despawn the original trident
            trident.remove();

            Location finalHitLoc = hitLoc.clone();
            Player shooter = trident.getShooter() instanceof Player ? (Player) trident.getShooter() : null;

            // Initiate strike delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    launchOrbitalStrike(finalHitLoc, shooter, radius, count);
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
            
            // Clean them up after a few seconds
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

    private void launchOrbitalStrike(Location target, Player shooter, double maxRadius, int totalCount) {
        double startY = Math.min(target.getWorld().getMaxHeight() - 5, target.getY() + 60);

        ItemStack channeledTrident = new ItemStack(Material.TRIDENT);
        channeledTrident.addEnchantment(Enchantment.CHANNELING, 1);
        Random random = new Random();

        for (int i = 0; i < totalCount; i++) {
            // V4: Uniform Disk Distribution Math
            double r = maxRadius * Math.sqrt(random.nextDouble());
            double theta = random.nextDouble() * 2 * Math.PI;
            
            double x = r * Math.cos(theta);
            double z = r * Math.sin(theta);

            Location spawnLoc = target.clone().add(x, 0, z);
            spawnLoc.setY(startY);

            Trident rainingTrident = target.getWorld().spawn(spawnLoc, Trident.class);
            
            rainingTrident.getPersistentDataContainer().set(rainKey, PersistentDataType.BYTE, (byte) 1);
            if (shooter != null) rainingTrident.setShooter(shooter);
            
            rainingTrident.setVelocity(new Vector(0, -4.0, 0));
            rainingTrident.setItem(channeledTrident); 
            rainingTrident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }
}
