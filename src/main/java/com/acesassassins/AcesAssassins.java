package com.acesassassins;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AcesAssassins extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Stats> stats = new LinkedHashMap<>();
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, UUID> assassins = new HashMap<>();
    private final Map<UUID, UUID> revengePaid = new HashMap<>();
    private final Set<UUID> pendingRule = ConcurrentHashMap.newKeySet();
    private final List<String> rules = new ArrayList<>();
    private boolean active;
    private NamespacedKey compassKey, voucherKey, aceKey;
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig();
        compassKey = new NamespacedKey(this, "assassin_compass");
        voucherKey = new NamespacedKey(this, "rule_voucher");
        aceKey = new NamespacedKey(this, "ace_of_spades");
        dataFile = new File(getDataFolder(), "event-data.yml");
        loadData();
        Objects.requireNonNull(getCommand("acesassassins")).setExecutor(this);
        Objects.requireNonNull(getCommand("acesassassins")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("leaderboard")).setExecutor(this);
        Objects.requireNonNull(getCommand("leaderboard")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ACE'S ASSASSINS enabled.");
    }
    @Override public void onDisable() { saveData(); }

    private static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private Stats getStats(UUID id) { return stats.computeIfAbsent(id, k -> new Stats()); }
    private void msg(CommandSender s, String m) { s.sendMessage(color("&c&lACE'S ASSASSINS &8» &f" + m)); }
    private void broadcast(String m) { Bukkit.broadcastMessage(color("&c&l♠ ACE'S ASSASSINS ♠ &8» &f" + m)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("leaderboard")) {
            if (args.length > 0 && sender.hasPermission("acesassassins.admin")) {
                if (args[0].equalsIgnoreCase("place") && sender instanceof Player p) { placeDisplay(p); return true; }
                if (args[0].equalsIgnoreCase("remove") && sender instanceof Player p) { removeDisplay(p); return true; }
                if (args[0].equalsIgnoreCase("refresh")) { refreshDisplays(); msg(sender,"Leaderboard displays refreshed."); return true; }
            }
            if (sender instanceof Player p) openLeaderboard(p); else msg(sender,"Players only.");
            return true;
        }
        if (args.length == 0) { msg(sender,"Commands: start, end, cancel, status, points, shop, rules, config"); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("points") && sender instanceof Player p) { Stats st=getStats(p.getUniqueId()); msg(p,"Points: &e"+st.points+" &7| Assassinations: &e"+st.kills+" &7| Revenge Kills: &e"+st.revenge); return true; }
        if (sub.equals("shop") && sender instanceof Player p) { openShop(p); return true; }
        if (sub.equals("rules") && sender instanceof Player p) { openRules(p); return true; }
        if (sub.equals("config")) { if (!sender.hasPermission("acesassassins.admin")) return noPerm(sender); if(sender instanceof Player p) openConfig(p); else msg(sender,"Edit plugins/AcesAssassins/config.yml and reload."); return true; }
        if (!sender.hasPermission("acesassassins.admin")) return noPerm(sender);
        switch (sub) {
            case "start" -> startEvent();
            case "end" -> finishEvent();
            case "cancel" -> cancelEvent();
            case "status" -> msg(sender, active ? "Event ACTIVE. Players remaining: &e"+aliveCount() : "No active event.");
            case "rule" -> { if(args.length>=3 && args[1].equalsIgnoreCase("remove")) { try { int i=Integer.parseInt(args[2])-1; if(i>=0&&i<rules.size()){String removed=rules.remove(i);broadcast("Rule #"+(i+1)+" was removed: "+removed);saveData();}else msg(sender,"Invalid rule number.");}catch(NumberFormatException ex){msg(sender,"Use /acesassassins rule remove <id>");} } }
            default -> msg(sender,"Unknown command. Use /acesassassins for help.");
        }
        return true;
    }
    private boolean noPerm(CommandSender s){msg(s,"You do not have permission.");return true;}
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if(args.length==1) return Arrays.asList("start","end","cancel","status","points","shop","rules","config","rule");
        if(args.length==2 && args[0].equalsIgnoreCase("rule")) return List.of("remove","list");
        return Collections.emptyList();
    }

    private void startEvent() {
        if(active){broadcast("An event is already running.");return;}
        List<Player> ps=new ArrayList<>(Bukkit.getOnlinePlayers());
        ps.removeIf(p->!p.hasPermission("acesassassins.play"));
        if(ps.size()<getConfig().getInt("event.minimum-players",3)){broadcast("Not enough players to start. Need "+getConfig().getInt("event.minimum-players",3)+".");return;}
        Collections.shuffle(ps); active=true; participants.clear(); eliminated.clear(); targets.clear(); assassins.clear(); revengePaid.clear();
        for(Player p:ps){participants.add(p.getUniqueId());Stats st=getStats(p.getUniqueId());st.points=0;st.kills=0;st.revenge=0;}
        for(int i=0;i<ps.size();i++){UUID a=ps.get(i).getUniqueId(),t=ps.get((i+1)%ps.size()).getUniqueId();targets.put(a,t);assassins.put(t,a);giveCompass(ps.get(i));}
        broadcast("The hunt has begun! Your target is secret. Check your compass.");
        for(Player p:ps) animateTargetReveal(p, ps);
        saveData();
    }
    private void animateTargetReveal(Player player,List<Player> pool){UUID finalTarget=targets.get(player.getUniqueId());new BukkitRunnable(){int tick=0;public void run(){if(!player.isOnline()||tick>=60){Player target=Bukkit.getPlayer(finalTarget);player.sendTitle(color("&c&l♠ TARGET SELECTED ♠"),color("&f"+(target==null?"Unknown":target.getName())),10,60,20);player.playSound(player,Sound.BLOCK_NOTE_BLOCK_PLING,1f,1.4f);cancel();return;}Player random=pool.get((int)(Math.random()*pool.size()));player.sendTitle(color("&c&lSELECTING TARGET"),color("&f"+random.getName()),0,8,0);if(tick%5==0)player.playSound(player,Sound.UI_BUTTON_CLICK,0.5f,1.6f);tick+=Math.max(1,tick/12+1);}}.runTaskTimer(this,0L,2L);}
    private void finishEvent(){if(!active){broadcast("There is no active event.");return;} UUID winner=participants.stream().filter(id->!eliminated.contains(id)).findFirst().orElse(null); if(winner!=null){Player p=Bukkit.getPlayer(winner); if(p!=null){broadcast("THE LAST ACE IS "+p.getName()+"!");p.getInventory().addItem(makeAce());winnerAnimation(p);}} active=false;broadcast("Event completed.");saveData();}
    private void cancelEvent(){active=false;participants.clear();targets.clear();assassins.clear();eliminated.clear();for(Player p:Bukkit.getOnlinePlayers())removeCompasses(p);broadcast("The event was cancelled.");saveData();}
    private int aliveCount(){return (int)participants.stream().filter(id->!eliminated.contains(id)).count();}

    @EventHandler public void onDeath(PlayerDeathEvent e){if(!active)return;Player victim=e.getEntity();Player killer=victim.getKiller();UUID v=victim.getUniqueId();if(!participants.contains(v)||eliminated.contains(v))return;
        if(killer==null||!participants.contains(killer.getUniqueId())||eliminated.contains(killer.getUniqueId())){Bukkit.getScheduler().runTask(this,()->victim.spigot().respawn());return;}
        UUID k=killer.getUniqueId();
        if(v.equals(targets.get(k))){Stats st=getStats(k);st.points+=getConfig().getDouble("ranking.points-per-kill",5);st.kills++; eliminate(v,k,"ASSASSINATION"); UUID next=targets.remove(v);if(next!=null&&!eliminated.contains(next)){targets.put(k,next);assassins.put(next,k);}giveCompass(killer);}
        else if(v.equals(assassins.get(k))){Stats st=getStats(k);st.points+=getConfig().getDouble("ranking.points-per-revenge-kill",2.5);st.revenge++;eliminate(v,k,"REVENGE KILL");}
        else {Bukkit.getScheduler().runTask(this,()->victim.spigot().respawn());return;}
        Bukkit.getScheduler().runTask(this,()->{victim.spigot().respawn();victim.setGameMode(GameMode.SPECTATOR);});
        if(aliveCount()<=1) Bukkit.getScheduler().runTaskLater(this,this::finishEvent,40L);saveData();
    }
    private void eliminate(UUID victim,UUID killer,String type){eliminated.add(victim);Player v=Bukkit.getPlayer(victim),k=Bukkit.getPlayer(killer);if(v!=null)removeCompasses(v);if(k!=null){k.sendMessage(color("&b+"+(type.equals("REVENGE KILL")?"2.5":"5")+" points"));particleBurst(k.getLocation().add(0,1,0),false);}broadcast((v==null?"A player":v.getName())+" was eliminated by "+(k==null?"a hunter":k.getName())+"! &b"+type);}

    private void giveCompass(Player p){ItemStack item=new ItemStack(Material.COMPASS);ItemMeta m=item.getItemMeta();m.setDisplayName(color(getConfig().getString("event.compass-name","&c&lASSASSIN'S COMPASS")));m.getPersistentDataContainer().set(compassKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(m);p.getInventory().addItem(item);updateCompass(p);}
    private void updateCompass(Player p){UUID t=targets.get(p.getUniqueId());Player target=t==null?null:Bukkit.getPlayer(t);if(target!=null&&target.isOnline()&&target.getWorld().equals(p.getWorld()))p.setCompassTarget(target.getLocation());}
    private void removeCompasses(Player p){for(int i=0;i<p.getInventory().getSize();i++){ItemStack it=p.getInventory().getItem(i);if(isTagged(it,compassKey))p.getInventory().setItem(i,null);}}
    @EventHandler public void onMove(PlayerMoveEvent e){if(active&&e.getTo()!=null&&!e.getFrom().getBlock().equals(e.getTo().getBlock()))updateCompass(e.getPlayer());}
    @EventHandler public void onJoin(PlayerJoinEvent e){if(active&&participants.contains(e.getPlayer().getUniqueId())&&!eliminated.contains(e.getPlayer().getUniqueId()))giveCompass(e.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent e){saveData();}
    @EventHandler public void onDrop(PlayerDropItemEvent e){if(isTagged(e.getItemDrop().getItemStack(),compassKey)||isTagged(e.getItemDrop().getItemStack(),voucherKey)||isTagged(e.getItemDrop().getItemStack(),aceKey))e.setCancelled(true);}
    @EventHandler public void onChat(AsyncPlayerChatEvent e){UUID id=e.getPlayer().getUniqueId();if(!pendingRule.contains(id))return;e.setCancelled(true);String proposal=e.getMessage();pendingRule.remove(id);Bukkit.getScheduler().runTask(this,()->submitRule(e.getPlayer(),proposal));}
    @EventHandler public void onInteract(PlayerInteractEvent e){ItemStack it=e.getItem();if(it==null)return;if(isTagged(it,voucherKey)){e.setCancelled(true);Player p=e.getPlayer();pendingRule.add(p.getUniqueId());p.sendMessage(color("&c&lRULE SUBMISSION &7» Type your proposed rule in chat, or CANCEL."));} }

    private void submitRule(Player p,String proposal){if(proposal.equalsIgnoreCase("cancel")){msg(p,"Submission cancelled.");return;}if(!active){msg(p,"Rules can only be proposed during an active event.");return;}Player op=Bukkit.getOnlinePlayers().stream().filter(x->x.hasPermission("acesassassins.operator")).min(Comparator.comparingDouble(x->x.getLocation().distanceSquared(p.getLocation()))).orElse(null);if(op==null){msg(p,"No Operator is online. Your proposal was not submitted.");return;}p.teleport(op.getLocation());consumeOneVoucher(p);openApproval(op,p.getName(),proposal);msg(p,"Your proposal was sent to "+op.getName()+".");}

    private void openLeaderboard(Player p){Inventory inv=Bukkit.createInventory(null,54,color("&8♠ &cASSASSINS LEADERBOARD"));List<UUID> ids=new ArrayList<>(participants);ids.sort((a,b)->Double.compare(getStats(b).points,getStats(a).points));int slot=0;for(UUID id:ids){if(slot>=45)break;OfflinePlayer op=Bukkit.getOfflinePlayer(id);Stats s=getStats(id);ItemStack head=new ItemStack(Material.PLAYER_HEAD);var m=(org.bukkit.inventory.meta.SkullMeta)head.getItemMeta();m.setOwningPlayer(op);m.setDisplayName(color((eliminated.contains(id)?"&c":"&a")+op.getName()));m.setLore(List.of(color("&7Points: &e"+s.points),color("&7Assassinations: &e"+s.kills),color("&7Revenge Kills: &e"+s.revenge),color(eliminated.contains(id)?"&cELIMINATED":"&aACTIVE HUNTER")));head.setItemMeta(m);inv.setItem(slot++,head);}p.openInventory(inv);}
    private void openShop(Player p){Inventory inv=Bukkit.createInventory(null,27,color("&8♠ &cASSASSINS SHOP"));ItemStack voucher=new ItemStack(Material.PAPER);ItemMeta m=voucher.getItemMeta();m.setDisplayName(color("&c&lRULE VOUCHER"));m.setLore(List.of(color("&7Cost: &e2 points"),color("&7Click to purchase.")));voucher.setItemMeta(m);inv.setItem(13,voucher);p.openInventory(inv);}
    private void openRules(Player p){Inventory inv=Bukkit.createInventory(null,54,color("&8♠ &cOFFICIAL RULES"));for(int i=0;i<rules.size()&&i<54;i++){ItemStack b=new ItemStack(Material.BOOK);ItemMeta m=b.getItemMeta();m.setDisplayName(color("&cRule #"+(i+1)));m.setLore(List.of(color("&f"+rules.get(i))));b.setItemMeta(m);inv.setItem(i,b);}p.openInventory(inv);}
    private void openConfig(Player p){Inventory inv=Bukkit.createInventory(null,27,color("&8♠ &cEVENT CONFIG"));inv.setItem(11,button(Material.REDSTONE,"&cParticle effects", "&7Enabled: "+getConfig().getBoolean("particles.enabled")));inv.setItem(15,button(Material.NOTE_BLOCK,"&eSound effects","&7Enabled: "+getConfig().getBoolean("sounds.enabled")));p.openInventory(inv);}
    private ItemStack button(Material mat,String name,String lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(color(name));m.setLore(List.of(color(lore)));i.setItemMeta(m);return i;}
    @EventHandler public void onInventory(InventoryClickEvent e){if(e.getView().getTitle().contains("ASSASSINS SHOP")){e.setCancelled(true);if(e.getRawSlot()==13&&e.getWhoClicked() instanceof Player p){Stats s=getStats(p.getUniqueId());double cost=getConfig().getDouble("ranking.rule-voucher-cost",2);if(s.points<cost){msg(p,"You need "+cost+" points.");return;}s.points-=cost;ItemStack v=new ItemStack(Material.PAPER);ItemMeta m=v.getItemMeta();m.setDisplayName(color("&c&lRULE VOUCHER"));m.getPersistentDataContainer().set(voucherKey,PersistentDataType.BYTE,(byte)1);v.setItemMeta(m);p.getInventory().addItem(v);msg(p,"Purchased a Rule Voucher.");saveData();}}
        else if(e.getView().getTitle().contains("RULE APPROVAL")){e.setCancelled(true);if(e.getWhoClicked() instanceof Player op){String proposal=approvalProposal.remove(op.getUniqueId());String proposer=approvalProposer.remove(op.getUniqueId());if(proposal!=null&&e.getRawSlot()==11){rules.add(proposal);broadcast("NEW OFFICIAL RULE #"+rules.size()+" proposed by "+proposer+": \""+proposal+"\" — approved by "+op.getName());saveData();}else if(proposal!=null&&e.getRawSlot()==15)msg(op,"Rule rejected.");op.closeInventory();}}
    }
    private final Map<UUID,String> approvalProposal=new HashMap<>(),approvalProposer=new HashMap<>();
    private void consumeOneVoucher(Player p){for(int i=0;i<p.getInventory().getSize();i++){ItemStack it=p.getInventory().getItem(i);if(isTagged(it,voucherKey)){if(it.getAmount()>1)it.setAmount(it.getAmount()-1);else p.getInventory().setItem(i,null);return;}}}
    private void openApproval(Player op,String proposer,String proposal){approvalProposal.put(op.getUniqueId(),proposal);approvalProposer.put(op.getUniqueId(),proposer);Inventory inv=Bukkit.createInventory(null,27,color("&8♠ &cRULE APPROVAL"));ItemStack book=button(Material.BOOK,"&fProposed by "+proposer,"&7"+proposal);inv.setItem(13,book);inv.setItem(11,button(Material.LIME_WOOL,"&aAPPROVE","&7Make this rule official"));inv.setItem(15,button(Material.RED_WOOL,"&cREJECT","&7Reject this proposal"));op.openInventory(inv);}

    private ItemStack makeAce(){Material mat=Material.matchMaterial(getConfig().getString("winner.reward-material","PAPER"));if(mat==null)mat=Material.PAPER;ItemStack item=new ItemStack(mat);ItemMeta m=item.getItemMeta();m.setDisplayName(color(getConfig().getString("winner.reward-name","&c&lACE OF SPADES")));m.setCustomModelData(getConfig().getInt("winner.custom-model-data",2000));m.getPersistentDataContainer().set(aceKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(m);return item;}
    private void winnerAnimation(Player p){int duration=getConfig().getInt("particles.winner-duration-ticks",200);new BukkitRunnable(){int tick=0;public void run(){if(!p.isOnline()||tick++>duration){cancel();return;}particleBurst(p.getLocation().add(0,1,0),true);if(tick%10==0)p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,1.2f);}}.runTaskTimer(this,0L,1L);}

    // Reference-inspired effect: rising cyan/blue vortex, dense cloud, white diamond sparkles, central flash.
    private void particleBurst(Location center,boolean winner){if(!getConfig().getBoolean("particles.enabled",true))return;World w=center.getWorld();if(w==null)return;double radius=getConfig().getDouble("particles.radius",2.8);int density=getConfig().getInt("particles.density",36);double phase=(System.currentTimeMillis()%5000)/5000.0*Math.PI*2;
        Particle.DustOptions blue=new Particle.DustOptions(Color.fromRGB(20,95,255),(float)getConfig().getDouble("particles.blue-dust-size",1.35));Particle.DustOptions cyan=new Particle.DustOptions(Color.fromRGB(0,225,255),(float)getConfig().getDouble("particles.cyan-dust-size",1.1));
        for(int i=0;i<density;i++){double a=phase+i*(Math.PI*2/density);double r=radius*(0.45+0.55*((i%7)/6.0));double y=0.15+(i%12)*0.17;Location at=center.clone().add(Math.cos(a)*r,y,Math.sin(a)*r);w.spawnParticle(Particle.DUST,at,2,0.12,0.10,0.12,0, i%2==0?cyan:blue);if(i%3==0)w.spawnParticle(Particle.END_ROD,at,1,0.04,0.08,0.04,0);}
        double trail=getConfig().getDouble("particles.trail-height",3.5);for(int j=0;j<12;j++){double y=(j/12.0)*trail;double a=phase*1.8+j*0.52;Location at=center.clone().add(Math.cos(a)*0.65,y,Math.sin(a)*0.65);w.spawnParticle(Particle.DUST,at,3,0.08,0.08,0.08,0,cyan);}
        w.spawnParticle(Particle.CLOUD,center, winner?18:8,0.55,0.25,0.55,0.03);w.spawnParticle(Particle.FIREWORK,center, winner?16:6,0.45,0.35,0.45,0.08);
        if(getConfig().getBoolean("particles.flash",true)){w.spawnParticle(Particle.END_ROD,center, winner?45:18,0.7,0.55,0.7,0.12);if(winner)w.spawnParticle(Particle.FLASH,center,1,0,0,0,0,Color.WHITE);}
        if(getConfig().getBoolean("sounds.enabled",true)&&winner&&System.currentTimeMillis()%3==0)w.playSound(center,Sound.BLOCK_BEACON_AMBIENT,0.25f,1.7f);
    }

    private boolean isTagged(ItemStack item,NamespacedKey key){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.BYTE);}
    private void placeDisplay(Player p){Location l=p.getLocation().add(0,2,0);TextDisplay d=(TextDisplay)p.getWorld().spawnEntity(l,EntityType.TEXT_DISPLAY);d.setPersistent(true);d.setBillboard(Display.Billboard.CENTER);d.setText(color("&c&l♠ ACE'S ASSASSINS ♠\n&fUse /leaderboard to view rankings"));msg(p,"Physical leaderboard placed.");}
    private void removeDisplay(Player p){TextDisplay nearest=p.getNearbyEntities(8,8,8).stream().filter(x->x instanceof TextDisplay).map(x->(TextDisplay)x).min(Comparator.comparingDouble(x->x.getLocation().distanceSquared(p.getLocation()))).orElse(null);if(nearest!=null){nearest.remove();msg(p,"Nearest text display removed.");}else msg(p,"No nearby leaderboard display found.");}
    private void refreshDisplays(){for(World w:Bukkit.getWorlds())for(TextDisplay d:w.getEntitiesByClass(TextDisplay.class))if(d.getText().contains("ACE'S ASSASSINS"))d.setText(color("&c&l♠ ACE'S ASSASSINS ♠\n&fPlayers remaining: "+aliveCount()+"\n&7Use /leaderboard for full rankings"));}

    private void loadData(){if(!dataFile.exists()){getDataFolder().mkdirs();data=new YamlConfiguration();return;}data=YamlConfiguration.loadConfiguration(dataFile);active=data.getBoolean("active",false);for(String s:data.getStringList("rules"))rules.add(s);ConfigurationSection sec=data.getConfigurationSection("players");if(sec!=null)for(String key:sec.getKeys(false)){try{UUID id=UUID.fromString(key);Stats st=new Stats();st.points=sec.getDouble(key+".points");st.kills=sec.getInt(key+".kills");st.revenge=sec.getInt(key+".revenge");stats.put(id,st);if(sec.getBoolean(key+".participant"))participants.add(id);if(sec.getBoolean(key+".eliminated"))eliminated.add(id);String t=sec.getString(key+".target");if(t!=null)targets.put(id,UUID.fromString(t));}catch(IllegalArgumentException ignored){}}
        if(active){getLogger().warning("An event was saved as active. Participants and stats were restored; target compass items are restored when players rejoin.");for(UUID id:targets.keySet()){UUID t=targets.get(id);assassins.put(t,id);}}}
    private void saveData(){if(data==null)data=new YamlConfiguration();data.set("active",active);data.set("rules",rules);data.set("players",null);for(UUID id:stats.keySet()){String k="players."+id;Stats st=stats.get(id);data.set(k+".points",st.points);data.set(k+".kills",st.kills);data.set(k+".revenge",st.revenge);data.set(k+".participant",participants.contains(id));data.set(k+".eliminated",eliminated.contains(id));if(targets.containsKey(id))data.set(k+".target",targets.get(id).toString());}try{data.save(dataFile);}catch(IOException ex){getLogger().severe("Could not save event data: "+ex.getMessage());}}
    private static final class Stats{double points;int kills;int revenge;}
}
