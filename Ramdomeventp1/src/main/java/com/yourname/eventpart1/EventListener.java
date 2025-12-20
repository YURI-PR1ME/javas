// EventListener.java
package com.yourname.eventpart1;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class EventListener implements Listener {
    
    private final EventManager eventManager = EventPart1.getInstance().getEventManager();
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        // 处理社会净化演习
        if (killer != null && killer != victim) {
            eventManager.handleSocialPurificationKill(killer, victim);
        }
    }
}
