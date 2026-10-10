package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class ResearchAgeChecks {
    private Settlement town() { return new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Research",BlockPos.ZERO,240,List.of(),List.of(),"balanced"); }
    @Test void paidProgressAndOldEquipmentSurviveSaving() {
        Settlement town=town();
        assertEquals("Stone Age",Research.age(town));
        town.progress.project="bronze_age"; town.progress.projectTicks=1430;
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement loaded=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        assertEquals("bronze_age",loaded.progress.project); assertEquals(1430,loaded.progress.projectTicks);
        assertFalse(Research.has(loaded,"bronze_age")); assertEquals(0,loaded.progress.legacyGearTier);
        var old=json.deepCopy().getAsJsonObject();
        old.getAsJsonObject("progress").remove("legacy_gear_tier");
        Settlement legacy=Settlement.CODEC.parse(JsonOps.INSTANCE,old).getOrThrow();
        assertTrue(Research.has(legacy,"netherite_smithing"));
        assertEquals(1430,legacy.progress.projectTicks);
        old.remove("progress");
        assertEquals("Iron Age",Research.age(Settlement.CODEC.parse(JsonOps.INSTANCE,old).getOrThrow()));
    }
    @Test void onlyAcceptedMembersShareResearch() {
        Settlement town=town(); UUID member=UUID.randomUUID(),invitee=UUID.randomUUID(),ally=UUID.randomUUID();
        town.campaign.members.put(member,"builder"); town.campaign.invitations.put(invitee,"steward"); town.campaign.allies.add(ally);
        assertTrue(AgeProgression.member(town,town.owner)); assertTrue(AgeProgression.member(town,member));
        assertFalse(AgeProgression.member(town,invitee)); assertFalse(AgeProgression.member(town,ally));
        town.campaign.members.remove(member); assertFalse(AgeProgression.member(town,member));
        town.trading.npc=true; assertFalse(AgeProgression.member(town,town.owner));
    }
    @Test void childhoodAndLoadedBirthCooldownSurviveOldAndNewSaves() {
        Settlement town=town(); UUID child=UUID.randomUUID(); town.citizens.add(child);
        town.progress.children.add(child); town.progress.birthWaitTicks=430; town.progress.growthEnabled=false;
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement loaded=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        assertEquals(Set.of(child),loaded.progress.children); assertEquals(430,loaded.progress.birthWaitTicks); assertFalse(loaded.progress.growthEnabled);
        var old=json.deepCopy().getAsJsonObject(); var progress=old.getAsJsonObject("progress");
        progress.remove("children"); progress.remove("birth_wait_ticks"); progress.remove("growth_enabled");
        Settlement legacy=Settlement.CODEC.parse(JsonOps.INSTANCE,old).getOrThrow();
        assertTrue(legacy.progress.children.isEmpty()); assertTrue(legacy.progress.growthEnabled); assertEquals(1200,legacy.progress.birthWaitTicks);
    }
}
