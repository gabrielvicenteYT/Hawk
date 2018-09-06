package me.islandscout.hawk.checks.combat;

import me.islandscout.hawk.HawkPlayer;
import me.islandscout.hawk.checks.AsyncCustomCheck;
import me.islandscout.hawk.checks.Cancelless;
import me.islandscout.hawk.events.ArmSwingEvent;
import me.islandscout.hawk.events.Event;
import me.islandscout.hawk.events.InteractAction;
import me.islandscout.hawk.events.InteractEntityEvent;
import me.islandscout.hawk.utils.Debug;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;

public class FightAccuracy extends AsyncCustomCheck implements Listener, Cancelless {

    //Theoretically should also detect noswing after some time.

    //Hits done to dealt ratio (5.66)
    //Accuracy: 0.8 (for every attacker, per entity)
    //Victim must be moving and trying to fight back with at least 3APS.

    //TODO: To be worked on

    private Map<UUID, Ratio> hddr;
    private Map<UUID, Map<UUID, Ratio>> accuracy;
    private Map<UUID, Float> attacksPerSec;
    private Map<UUID, UUID> tryingToAttack;
    private Map<UUID, Player> lastAttacked;
    private Map<UUID, Long> swingTick;

    public FightAccuracy() {
        super("fightaccuracy", "&7%player% may be using killaura (ACCURACY). Accuracy: %accuracy%, VL: %vl%");
        hddr = new HashMap<>();
        accuracy = new HashMap<>();
        attacksPerSec = new HashMap<>();
        tryingToAttack = new HashMap<>();
        lastAttacked = new HashMap<>();
        swingTick = new HashMap<>();
    }

    public void check(Event e) {
        if(e instanceof InteractEntityEvent) {
            hitProcessor((InteractEntityEvent)e);
        }
        else if(e instanceof ArmSwingEvent) {
            swingProcessor((ArmSwingEvent)e);
        }
    }

    private void hitProcessor(InteractEntityEvent e) {
        if(e.getInteractAction() == InteractAction.INTERACT || !(e.getEntity() instanceof Player))
            return;
        UUID victim = e.getEntity().getUniqueId();
        UUID uuid = e.getPlayer().getUniqueId();

        HawkPlayer att = e.getHawkPlayer();
        Player pVictim = Bukkit.getPlayer(victim);
        if(att.getCurrentTick() != swingTick.getOrDefault(uuid, 0L))
            return;
        if(pVictim == null || att.getLocation().distanceSquared(pVictim.getLocation()) > 9)
            return;

        Map<UUID, Ratio> accuracyToVictim = accuracy.getOrDefault(uuid, new HashMap<>());
        Ratio ratio = accuracyToVictim.getOrDefault(victim, new Ratio());
        if(ratio.denominator == 0)
            return;
        ratio.numerator++;

        //now it's time to check accuracy
        if(ratio.denominator >= 20) {
            Debug.sendToPlayer(e.getPlayer(), "aim: " + ratio.getRatio());
            ratio.normalize();
        }


        accuracyToVictim.put(victim, ratio);
        accuracy.put(uuid, accuracyToVictim);
    }

    private void swingProcessor(ArmSwingEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if(!lastAttacked.containsKey(uuid))
            return;

        HawkPlayer att = e.getHawkPlayer();
        Player victim = lastAttacked.get(uuid);
        if(victim == null)
            return;
        long lastSwingTick = swingTick.getOrDefault(uuid, 0L);

        //proceed if victim's invulnerability is gone
        if(att.getCurrentTick() - lastSwingTick >= victim.getMaximumNoDamageTicks() / 2) {
            if(att.getLocation().distanceSquared(victim.getLocation()) > 9)
                return;

            Map<UUID, Ratio> accuracyToVictim = accuracy.getOrDefault(uuid, new HashMap<>());
            Ratio ratio = accuracyToVictim.getOrDefault(victim.getUniqueId(), new Ratio());
            ratio.denominator++;
            accuracyToVictim.put(victim.getUniqueId(), ratio);

            accuracy.put(uuid, accuracyToVictim);
            swingTick.put(uuid, att.getCurrentTick());
        }
    }




    @EventHandler
    public void damageDealt(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player && e.getEntity() instanceof Player))
            return;
        Player attacker = (Player)e.getDamager();
        Player victim = (Player)e.getEntity();
        lastAttacked.put(attacker.getUniqueId(), victim);


    }

    private class Ratio {

        private float numerator;
        private float denominator;

        private Ratio() {
            numerator = 0;
            denominator = 0;
        }

        private float getRatio() {
            return denominator == 0 ? Float.NaN : (numerator / denominator);
        }

        private void normalize() {
            numerator /= denominator;
            denominator = 1;
        }

    }

    public void removeData(Player p) {
        UUID uuid = p.getUniqueId();
        hddr.remove(uuid);
        accuracy.remove(uuid);
        attacksPerSec.remove(uuid);
        tryingToAttack.remove(uuid);
        lastAttacked.remove(uuid);
    }
}
