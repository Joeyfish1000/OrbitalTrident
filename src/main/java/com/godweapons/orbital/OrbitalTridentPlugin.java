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
    
    // Command Parameters
    private NamespacedKey radiusKey;
    private NamespacedKey countKey;
    private NamespacedKey wavesKey;
    private NamespacedKey delayKey;

    @Override
    public void onEnable() {
        this.weaponKey = new NamespacedKey(this, "weapon_id");
        this.thrownKey = new NamespacedKey(this, "is_orbital_thrown");
        this.rainKey = new NamespacedKey(this, "is_orbital_rain");
        
        this.radiusKey = new NamespacedKey(this, "orbital_radius");
        this.countKey = new NamespacedKey(this, "orbital_count");
        this.wavesKey = new NamespacedKey(this, "orbital_waves");
        this.delayKey = new NamespacedKey(this, "orbital_delay");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("orbitalcannon") != null) {
            getCommand("orbitalcannon").setExecutor(this);
        }
        getLogger().info("⚡ Orbital Trident Cannon V5 loaded! Multi-wave celestial bombardment active.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && player.hasPermission("godweapons.admin")) {
            
            // Default Values
            double radius = 9.0;
            int count = 241;
            int waves = 1;
            long delay = 20; // 1 second between waves
            
            if (args.length >= 2) {
                try {
                    radius = Double.parseDouble(args[0]);
                    count = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid numbers. Usage: /orbitalcannon <radius> <count> [waves] [delay_ticks]"));
                    return true;
                }
            }
            if (args.length >= 4) {
                try {
                    waves = Integer.parseInt(args[2]);
                    delay = Long.parseLong(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid numbers. Usage: /orbitalcannon <radius> <count> <waves> <delay_ticks>"));
                    return true;
                }
            }
            
            ItemStack trident = new ItemStack(Material.TRIDENT);
            ItemMeta meta = trident.getItemMeta();
            
            meta.displayName(Component.text("Orbital Trident Cannon"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Radius: " + radius));
            lore.add(Component.text("Payload: " + count + " Tridents/Wave"));
            lore.add(Component.text("Waves: " + waves + " (Delay: " + delay + "t)"));
            meta.lore(lore);
            
            meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "orbital_cannon");
            
            // Stamp the geometry and wave data onto the item
            meta.getPersistentDataContainer().set(radiusKey, PersistentDataType.DOUBLE, radius);
            meta.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, count);
            meta.getPersistentDataContainer().set(wavesKey, PersistentDataType.INTEGER, waves);
            meta.getPersistentDataContainer().set(delayKey, PersistentDataType.LONG, delay);
            
            meta.setUnbreakable(true);
            trident.setItemMeta(meta);
            
            player.getInventory().addItem(trident);
            player.sendMessage(Component.text("Equipped Parametric Orbital Cannon (R: " + radius + ", C: " + count + ", W: " + waves + ")."));
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
                    
                    // Transfer all parameter data to the thrown entity
                    if (item.getItemMeta().getPersistentDataContainer().has(radiusKey, PersistentDataType.DOUBLE)) {
                        double r = item.getItemMeta().getPersistentDataContainer().get(radiusKey, PersistentDataType.DOUBLE);
                        int c = item.getItemMeta().getPersistentDataContainer().get(countKey, PersistentDataType.INTEGER);
                        int w = item.getItemMeta().getPersistentDataContainer().getOrDefault(wavesKey, PersistentDataType.INTEGER, 1);
                        long d = item.getItemMeta().getPersistentDataContainer().getOrDefault(delayKey, PersistentDataType.LONG, 20L);
                        
                        trident.getPersistentDataContainer().set(radiusKey, PersistentDataType.DOUBLE, r);
                        trident.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, c);
                        trident.getPersistentDataContainer().set(wavesKey, PersistentDataType.INTEGER, w);
                        trident.getPersistentDataContainer().set(delayKey, PersistentDataType.LONG, d);
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
            
            // Extract geometry and wave data before deletion
            double radius = trident.getPersistentDataContainer().getOrDefault(radiusKey, PersistentDataType.DOUBLE, 9.0);
            int count = trident.getPersistentDataContainer().getOrDefault(countKey, PersistentDataType.INTEGER, 241);
            int waves = trident.getPersistentDataContainer().getOrDefault(wavesKey, PersistentDataType.INTEGER, 1);
            long delay = trident.getPersistentDataContainer().getOrDefault(delayKey, PersistentDataType.LONG, 20L);
            
            trident.remove();

            Location finalHitLoc = hitLoc.clone();
            Player shooter = trident.getShooter() instanceof Player ? (Player) trident.getShooter() : null;

            // Initiate the staggered strike
            new BukkitRunnable() {
                @Override
                public void run() {
                    launchOrbitalStrike(finalHitLoc, shooter, radius, count, waves, delay);
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

    private void launchOrbitalStrike(Location target, Player shooter, double maxRadius, int tridentsPerWave, int totalWaves, long delayTicks) {
        double startY = Math.min(target.getWorld().getMaxHeight() - 5, target.getY() + 60);

        ItemStack channeledTrident = new ItemStack(Material.TRIDENT);
        channeledTrident.addEnchantment(Enchantment.CHANNELING, 1);

        new BukkitRunnable() {
            int currentWave = 0;
            Random random = new Random();

            @Override
            public void run() {
                if (currentWave >= totalWaves) {
                    cancel();
                    return;
                }

                for (int i = 0; i < tridentsPerWave; i++) {
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
                
                currentWave++;
            }
        }.runTaskTimer(this, 0, delayTicks);
    }
}
