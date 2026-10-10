package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.CitizenSkill;
import io.github.swishhyy.wwmc.settlement.MealVariety;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Citizen experience levels and their modest bonuses, and how meals fill and cheer citizens. */
public final class ProgressChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }

    @Test void experience() {
        check(CitizenSkill.level(0)==0 && CitizenSkill.level(24)==0 && CitizenSkill.level(25)==1,"The first level takes 25 finished jobs");
        check(CitizenSkill.level(374)==3 && CitizenSkill.level(375)==CitizenSkill.MAX_LEVEL && CitizenSkill.level(CitizenSkill.CAP)==CitizenSkill.MAX_LEVEL,"Levels stop at Master");
        check(CitizenSkill.next(0)==25 && CitizenSkill.next(CitizenSkill.MAX_LEVEL)==-1,"Each level names the experience for the next");
        check(CitizenSkill.title(0).equals("Novice") && CitizenSkill.title(4).equals("Master"),"Levels have titles");
        check(CitizenSkill.speed(StructureRole.FARM,4)==12 && CitizenSkill.speed(StructureRole.COOK,4)==16 && CitizenSkill.speed(StructureRole.GUARD,4)==0,
                "Work speed rises modestly; cooks and smelters a little more; guards fight better instead");
        check(CitizenSkill.toolSaving(StructureRole.MINE,4)==20 && CitizenSkill.toolSaving(StructureRole.LUMBER,2)==10 && CitizenSkill.toolSaving(StructureRole.FARM,4)==20,
                "Experienced miners, lumberjacks and farmers reduce wear on the tools they use");
        check(CitizenSkill.guardDamage(4)==20 && CitizenSkill.guardProtection(4)==12,"A master guard hits a fifth harder and takes 12% less damage");
        check(CitizenSkill.guardCooldown(4,39)==10 && CitizenSkill.guardCooldown(4,40)==20 && CitizenSkill.guardCooldown(0,0)==20,
                "A master guard recovers early from four swings in ten; a novice never does");
        check(CitizenSkill.perk(StructureRole.MINE,0).startsWith("No bonus") && CitizenSkill.perk(StructureRole.MINE,2).contains("10% less tool wear"),"The citizen screen explains each level");
        System.out.println("Passed "+checks+" experience checks.");
    }

    @Test void meals() {
        check(Math.abs(MealVariety.fullness(5,6.0F)-1F)<1.0E-4,"Bread keeps the configured meal interval");
        check(MealVariety.fullness(8,12.8F)==1.75F && MealVariety.fullness(1,1.2F)==0.5F,"Steak lasts longest and beetroot shortest, within bounds");
        check(MealVariety.fullness(3,3.6F)<MealVariety.fullness(6,7.2F),"Heartier meals last longer");
        List<String> meals=new ArrayList<>();
        for(int n=0;n<8;n++) meals=MealVariety.remember(meals,"minecraft:bread");
        check(meals.size()==MealVariety.REMEMBERED && MealVariety.bonus(meals)==0 && MealVariety.mood(meals).startsWith("Bored"),"Only bread, every meal, earns no bonus");
        meals=MealVariety.remember(MealVariety.remember(meals,"minecraft:cooked_beef"),"minecraft:carrot");
        check(MealVariety.distinct(meals)==3 && MealVariety.bonus(meals)==5 && MealVariety.mood(meals).equals("Content"),"Three kinds of food lately lift morale");
        meals=MealVariety.remember(meals,"minecraft:baked_potato");
        check(MealVariety.bonus(meals)==8 && MealVariety.mood(meals).equals("Delighted"),"Four or more kinds lift it most");
        check(MealVariety.mood(List.of()).equals("Settling in"),"A new citizen has no history yet");
        System.out.println("Passed "+checks+" meal checks.");
    }
}
