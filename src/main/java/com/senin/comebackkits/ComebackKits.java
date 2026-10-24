package com.senin.comebackkits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public class ComebackKits extends JavaPlugin implements Listener {

    // ---------- BÜYÜ İSİMLERİ ----------
    private static final Enchantment KORUMA = Enchantment.PROTECTION;
    private static final Enchantment KIRILMAZLIK = Enchantment.UNBREAKING;
    private static final Enchantment ONARIM = Enchantment.MENDING;
    private static final Enchantment KESKINLIK = Enchantment.SHARPNESS;
    private static final Enchantment SAVURMA = Enchantment.SWEEPING_EDGE;
    private static final Enchantment ALEV = Enchantment.FIRE_ASPECT;
    private static final Enchantment VERIMLILIK = Enchantment.EFFICIENCY;
    private static final Enchantment SERVET = Enchantment.FORTUNE;
    private static final Enchantment YAGMA = Enchantment.LOOTING;
    private static final Enchantment GERI_TEPME = Enchantment.KNOCKBACK;
    private static final Enchantment DIKEN = Enchantment.THORNS;
    private static final Enchantment SOLUNUM = Enchantment.RESPIRATION;
    private static final Enchantment SU_ALTI = Enchantment.AQUA_AFFINITY;
    private static final Enchantment DUSME = Enchantment.FEATHER_FALLING;
    private static final Enchantment SU_YURUYUS = Enchantment.DEPTH_STRIDER;

    private static final Material[] ELMAS = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
    private static final Material[] NETHERITE = {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};

    // ---------- RÜTBELER ----------
    enum Rutbe {
        YOK("Yok", 0), VIP("VIP", 1), VIP_PLUS("VIP+", 2), MVIP("MVIP", 3), WVIP("WVIP", 4);

        final String ad;
        final int seviye;

        Rutbe(String ad, int seviye) {
            this.ad = ad;
            this.seviye = seviye;
        }

        static Rutbe bul(String s) {
            for (Rutbe r : values()) {
                if (r.ad.equalsIgnoreCase(s)) return r;
            }
            return null;
        }
    }

    record Kit(String ad, Material ikon, NamedTextColor renk, Rutbe gereken,
               long beklemeMs, Supplier<List<ItemStack>> esyalar) {}

    static class KitMenu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] SLOTLAR = {11, 12, 13, 14, 15};
    private final List<Kit> kitler = new ArrayList<>();
    private final Map<UUID, Map<String, Long>> beklemeler = new HashMap<>();
    private File rutbeDosya;
    private YamlConfiguration rutbeler;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        rutbeDosya = new File(getDataFolder(), "rutbeler.yml");
        rutbeler = YamlConfiguration.loadConfiguration(rutbeDosya);

        long dk = 60 * 1000L;
        kitler.add(new Kit("Başlangıç", Material.CHEST, NamedTextColor.GREEN, Rutbe.YOK, 5 * dk, this::baslangicEsyalari));
        kitler.add(new Kit("VIP", Material.GOLD_INGOT, NamedTextColor.GOLD, Rutbe.VIP, 30 * dk, this::vipEsyalari));
        kitler.add(new Kit("VIP+", Material.DIAMOND, NamedTextColor.AQUA, Rutbe.VIP_PLUS, 20 * dk, this::vipPlusEsyalari));
        kitler.add(new Kit("MVIP", Material.NETHERITE_INGOT, NamedTextColor.LIGHT_PURPLE, Rutbe.MVIP, 15 * dk, this::mvipEsyalari));
        kitler.add(new Kit("WVIP", Material.NETHERITE_BLOCK, NamedTextColor.RED, Rutbe.WVIP, 10 * dk, this::wvipEsyalari));

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("comebackKits açıldı!");
    }

    // ---------- KOMUTLAR ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("rutbever")) {
            return rutbeVerKomutu(sender, args);
        }
        if (sender instanceof Player p) {
            openMenu(p);
        } else {
            sender.sendMessage("Bu komut sadece oyuncular içindir.");
        }
        return true;
    }

    private boolean rutbeVerKomutu(CommandSender sender, String[] args) {
        if (!sender.hasPermission("comebackkits.admin")) {
            sender.sendMessage(text("Bunun için yetkin yok.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(text("Kullanım: /rutbever <vip|vip+|mvip|wvip|yok> <oyuncu>", NamedTextColor.RED));
            return true;
        }
        Rutbe r = Rutbe.bul(args[0]);
        if (r == null) {
            sender.sendMessage(text("Böyle bir rütbe yok. (vip, vip+, mvip, wvip, yok)", NamedTextColor.RED));
            return true;
        }
        Player hedef = Bukkit.getPlayerExact(args[1]);
        if (hedef == null) {
            sender.sendMessage(text("Oyuncu çevrimiçi değil.", NamedTextColor.RED));
            return true;
        }
        rutbeVer(hedef, r);
        sender.sendMessage(text(hedef.getName() + " oyuncusuna " + r.ad + " rütbesi verildi.", NamedTextColor.GREEN));
        hedef.sendMessage(text("Rütben güncellendi: " + r.ad, NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (!c.getName().equalsIgnoreCase("rutbever")) return List.of();
        if (args.length == 1) return List.of("vip", "vip+", "mvip", "wvip", "yok");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }

    // ---------- RÜTBE KAYDI ----------

    private Rutbe rutbeAl(Player p) {
        String s = rutbeler.getString(p.getUniqueId().toString(), "YOK");
        try {
            return Rutbe.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Rutbe.YOK;
        }
    }

    private void rutbeVer(Player p, Rutbe r) {
        rutbeler.set(p.getUniqueId().toString(), r.name());
        try {
            rutbeler.save(rutbeDosya);
        } catch (IOException e) {
            getLogger().warning("Rütbe kaydedilemedi: " + e.getMessage());
        }
    }

    // ---------- MENÜ ----------

    private void openMenu(Player p) {
        KitMenu holder = new KitMenu();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Kitler"));
        holder.inv = inv;

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(Component.text(" "));
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        for (int i = 0; i < kitler.size() && i < SLOTLAR.length; i++) {
            inv.setItem(SLOTLAR[i], kitIkon(p, kitler.get(i)));
        }
        p.openInventory(inv);
    }

    private ItemStack kitIkon(Player p, Kit k) {
        ItemStack icon = new ItemStack(k.ikon());
        ItemMeta m = icon.getItemMeta();
        m.displayName(text(k.ad() + " Kiti", k.renk()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("Bekleme süresi: " + (k.beklemeMs() / 60000) + " dk", NamedTextColor.GRAY));
        lore.add(text(" ", NamedTextColor.GRAY));
        if (rutbeAl(p).seviye < k.gereken().seviye) {
            lore.add(text("Gerekli rütbe: " + k.gereken().ad, NamedTextColor.RED));
        } else {
            long left = kalan(p, k);
            if (left > 0) {
                long sn = left / 1000;
                lore.add(text("Bekleme: " + (sn / 60) + " dk " + (sn % 60) + " sn", NamedTextColor.RED));
            } else {
                lore.add(text("Almak için tıkla!", NamedTextColor.YELLOW));
            }
        }
        m.lore(lore);
        icon.setItemMeta(m);
        return icon;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory ust = e.getView().getTopInventory();
        if (!(ust.getHolder() instanceof KitMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != ust) return;
        for (int i = 0; i < SLOTLAR.length && i < kitler.size(); i++) {
            if (e.getSlot() == SLOTLAR[i]) {
                kitAl(p, kitler.get(i));
                p.closeInventory();
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof KitMenu) {
            e.setCancelled(true);
        }
    }

    // ---------- KİT VERME ----------

    private long kalan(Player p, Kit k) {
        Map<String, Long> m = beklemeler.get(p.getUniqueId());
        if (m == null) return 0;
        long son = m.getOrDefault(k.ad(), 0L);
        return Math.max(0, son + k.beklemeMs() - System.currentTimeMillis());
    }

    private void kitAl(Player p, Kit k) {
        if (rutbeAl(p).seviye < k.gereken().seviye) {
            p.sendMessage(text("Bu kit için " + k.gereken().ad + " rütbesi gerekir.", NamedTextColor.RED));
            return;
        }
        long left = kalan(p, k);
        if (left > 0) {
            long sn = left / 1000;
            p.sendMessage(text("Bu kiti " + (sn / 60) + " dk " + (sn % 60) + " sn sonra alabilirsin.", NamedTextColor.RED));
            return;
        }
        for (ItemStack item : k.esyalar().get()) {
            ver(p, item);
        }
        beklemeler.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>())
                .put(k.ad(), System.currentTimeMillis());
        p.sendMessage(text(k.ad() + " kitini aldın!", NamedTextColor.GREEN));
    }

    // Zırh boşsa giyer, değilse envantere koyar, envanter doluysa yere düşer
    private void ver(Player p, ItemStack item) {
        String ad = item.getType().name();
        PlayerInventory inv = p.getInventory();
        if (ad.endsWith("_HELMET") && inv.getHelmet() == null) inv.setHelmet(item);
        else if (ad.endsWith("_CHESTPLATE") && inv.getChestplate() == null) inv.setChestplate(item);
        else if (ad.endsWith("_LEGGINGS") && inv.getLeggings() == null) inv.setLeggings(item);
        else if (ad.endsWith("_BOOTS") && inv.getBoots() == null) inv.setBoots(item);
        else inv.addItem(item).values()
                    .forEach(kalan -> p.getWorld().dropItemNaturally(p.getLocation(), kalan));
    }

    // ---------- KİT İÇERİKLERİ ----------

    private List<ItemStack> baslangicEsyalari() {
        List<ItemStack> l = new ArrayList<>();
        l.add(new ItemStack(Material.ELYTRA));

        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET, 3);
        FireworkMeta fm = (FireworkMeta) rocket.getItemMeta();
        fm.setPower(1);
        rocket.setItemMeta(fm);
        l.add(rocket);

        ekle(l, Material.GOLDEN_APPLE, 16);
        ekle(l, Material.COOKED_BEEF, 32);
        for (Material m : new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS}) {
            l.add(new ItemStack(m));
        }
        l.add(adli(buyulu(Material.IRON_SWORD, KESKINLIK, 2), "Başlangıç Kılıcı"));
        l.add(adli(buyulu(Material.IRON_PICKAXE, VERIMLILIK, 2), "Başlangıç Kazması"));
        return l;
    }

    private List<ItemStack> vipEsyalari() {
        List<ItemStack> l = new ArrayList<>();
        elmasTakim(l);
        ekle(l, Material.GOLDEN_APPLE, 64);
        ekle(l, Material.ENCHANTED_GOLDEN_APPLE, 3);
        ekle(l, Material.COOKED_BEEF, 64);
        return l;
    }

    private List<ItemStack> vipPlusEsyalari() {
        List<ItemStack> l = new ArrayList<>();
        elmasTakim(l);
        ekle(l, Material.GOLDEN_APPLE, 192);
        ekle(l, Material.ENCHANTED_GOLDEN_APPLE, 8);
        ekle(l, Material.COOKED_BEEF, 64);
        ekle(l, Material.ENDER_PEARL, 16);
        l.add(zumrut(4));
        return l;
    }

    private List<ItemStack> mvipEsyalari() {
        List<ItemStack> l = new ArrayList<>(zirhSeti(NETHERITE, false));
        l.add(buyulu(Material.NETHERITE_SWORD, KESKINLIK, 5, SAVURMA, 3, ALEV, 2, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.NETHERITE_PICKAXE, VERIMLILIK, 5, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.NETHERITE_AXE, VERIMLILIK, 5, KESKINLIK, 5, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(kalkan());
        ekle(l, Material.EXPERIENCE_BOTTLE, 64);
        ekle(l, Material.COOKED_BEEF, 128);
        ekle(l, Material.GOLDEN_APPLE, 320);
        ekle(l, Material.ENCHANTED_GOLDEN_APPLE, 16);
        ekle(l, Material.ENDER_PEARL, 16);
        l.add(zumrut(8));
        return l;
    }

    private List<ItemStack> wvipEsyalari() {
        List<ItemStack> l = new ArrayList<>(zirhSeti(NETHERITE, true));
        // Kılıçta savurma yok, diğer büyülerin hepsi var
        l.add(buyulu(Material.NETHERITE_SWORD, KESKINLIK, 5, ALEV, 2, YAGMA, 3, GERI_TEPME, 2, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.NETHERITE_PICKAXE, VERIMLILIK, 5, SERVET, 3, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.NETHERITE_AXE, VERIMLILIK, 5, KESKINLIK, 5, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.NETHERITE_SHOVEL, VERIMLILIK, 5, SERVET, 3, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(kalkan());
        ekle(l, Material.EXPERIENCE_BOTTLE, 128);
        ekle(l, Material.COOKED_BEEF, 192);
        ekle(l, Material.GOLDEN_APPLE, 512);
        ekle(l, Material.ENCHANTED_GOLDEN_APPLE, 32);
        ekle(l, Material.ENDER_PEARL, 32);
        l.add(zumrut(16));
        return l;
    }

    // VIP ve VIP+ için ortak: elmas set + kılıç + kazma + balta + kalkan
    private void elmasTakim(List<ItemStack> l) {
        l.addAll(zirhSeti(ELMAS, false));
        l.add(buyulu(Material.DIAMOND_SWORD, KESKINLIK, 5, SAVURMA, 3, ALEV, 2, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.DIAMOND_PICKAXE, VERIMLILIK, 4, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(buyulu(Material.DIAMOND_AXE, VERIMLILIK, 4, KIRILMAZLIK, 3, ONARIM, 1));
        l.add(kalkan());
    }

    private List<ItemStack> zirhSeti(Material[] parcalar, boolean tam) {
        List<ItemStack> l = new ArrayList<>();
        for (Material m : parcalar) {
            String ad = m.name();
            if (!tam) {
                l.add(buyulu(m, KORUMA, 4, KIRILMAZLIK, 3, ONARIM, 1));
            } else if (ad.endsWith("_HELMET")) {
                l.add(buyulu(m, KORUMA, 4, KIRILMAZLIK, 3, ONARIM, 1, DIKEN, 3, SOLUNUM, 3, SU_ALTI, 1));
            } else if (ad.endsWith("_BOOTS")) {
                l.add(buyulu(m, KORUMA, 4, KIRILMAZLIK, 3, ONARIM, 1, DIKEN, 3, DUSME, 4, SU_YURUYUS, 3));
            } else {
                l.add(buyulu(m, KORUMA, 4, KIRILMAZLIK, 3, ONARIM, 1, DIKEN, 3));
            }
        }
        return l;
    }

    private ItemStack kalkan() {
        return buyulu(Material.SHIELD, KIRILMAZLIK, 3, ONARIM, 1);
    }

    private ItemStack zumrut(int adet) {
        ItemStack d = new ItemStack(Material.GREEN_DYE, adet);
        ItemMeta m = d.getItemMeta();
        m.displayName(Component.text("Zümrüt Kristali", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Zümrüt Kristali sayesinde", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("özel kasa anahtarı alabilirsin.", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false)));
        d.setItemMeta(m);
        return d;
    }

    // ---------- YARDIMCILAR ----------

    // buyulu(Material.DIAMOND_SWORD, KESKINLIK, 5, ONARIM, 1) gibi kullanılır
    private ItemStack buyulu(Material m, Object... p) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        for (int i = 0; i < p.length; i += 2) {
            meta.addEnchant((Enchantment) p[i], (Integer) p[i + 1], true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack adli(ItemStack item, String ad) {
        ItemMeta m = item.getItemMeta();
        m.displayName(text(ad, NamedTextColor.AQUA));
        item.setItemMeta(m);
        return item;
    }

    // 64'ten fazlasını stack stack böler
    private void ekle(List<ItemStack> l, Material m, int toplam) {
        while (toplam > 0) {
            int n = Math.min(64, toplam);
            l.add(new ItemStack(m, n));
            toplam -= n;
        }
    }

    private Component text(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
}
