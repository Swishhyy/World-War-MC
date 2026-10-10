package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.List;

/**
 * Experience a citizen earns at each job, from finished work rather than time. Bonuses stay modest: at most 12% faster
 * ordinary work (16% for cooks and smelters), a fifth less tool wear for miners, quarry workers, lumberjacks, hunters,
 * fishermen and butchers, and for guards up to 20% more damage, 12% less damage taken and quicker swings. Experience is
 * kept per job, so a citizen moved to another job keeps what it learned at the first.
 */
public final class CitizenSkill {
    public static final List<String> TITLES=List.of("Novice","Trained","Skilled","Expert","Master");
    /** Experience needed for each level. */
    private static final int[] THRESHOLDS={0,25,75,175,375};
    public static final int MAX_LEVEL=THRESHOLDS.length-1;
    /** Experience stops counting here, far beyond the top level. */
    public static final int CAP=100_000;
    private CitizenSkill() {}
    public static int level(int experience) {
        int level=0;
        for(int n=1;n<THRESHOLDS.length;n++) if(experience>=THRESHOLDS[n]) level=n;
        return level;
    }
    /** Experience needed for the next level, or -1 at the top. */
    public static int next(int level) { return level>=MAX_LEVEL ? -1 : THRESHOLDS[level+1]; }
    public static String title(int level) { return TITLES.get(Math.clamp(level,0,MAX_LEVEL)); }
    private static boolean processes(StructureRole role) { return role==StructureRole.COOK || role==StructureRole.SMELTERY; }
    /** Jobs whose tools wear out with every block, catch or cut. */
    public static boolean wearsTools(StructureRole role) {
        return role.excavates() || role==StructureRole.FARM || role==StructureRole.GATHERER || role==StructureRole.LUMBER || role==StructureRole.HUNTER || role==StructureRole.FISHERMAN || role==StructureRole.BUTCHER;
    }
    /** Extra work speed, in percent. */
    public static int speed(StructureRole role,int level) {
        if(role==null || role==StructureRole.GUARD) return 0;
        return (processes(role) ? 4 : 3)*Math.clamp(level,0,MAX_LEVEL);
    }
    /** Chance, in percent, that a use of a tool costs no durability. */
    public static int toolSaving(StructureRole role,int level) { return role!=null && wearsTools(role) ? 5*Math.clamp(level,0,MAX_LEVEL) : 0; }
    public static int guardDamage(int level) { return 5*Math.clamp(level,0,MAX_LEVEL); }
    public static int guardProtection(int level) { return 3*Math.clamp(level,0,MAX_LEVEL); }
    /**
     * Ticks before a guard's next swing or shot, given a roll from 0 to 99. Guards act every ten ticks, so experience
     * is a chance of the quick ten-tick recovery: 10% per level.
     */
    public static int guardCooldown(int level,int roll) { return roll<10*Math.clamp(level,0,MAX_LEVEL) ? 10 : 20; }
    /** What the level brings at this job, for the citizen screen. */
    public static String perk(StructureRole role,int level) {
        if(level<=0) return "No bonus yet: finished work earns experience";
        if(role==StructureRole.GUARD) return guardDamage(level)+"% more damage, "+guardProtection(level)+"% less damage taken, quicker swings";
        String text=speed(role,level)+"% faster work";
        if(toolSaving(role,level)>0) text+=", "+toolSaving(role,level)+"% less tool wear";
        return text;
    }
}
