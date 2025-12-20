package com.yourname.tyrantboss;

import org.bukkit.entity.WitherSkeleton;

public class TyrantNPC {
    
    private final WitherSkeleton npc;
    private final TyrantBossPlugin plugin;
    
    public TyrantNPC(WitherSkeleton npc, TyrantBossPlugin plugin) {
        this.npc = npc;
        this.plugin = plugin;
    }
    
    public WitherSkeleton getNPC() {
        return npc;
    }
    
    public void cleanup() {
        if (npc != null && npc.isValid()) {
            npc.remove();
        }
    }
}
