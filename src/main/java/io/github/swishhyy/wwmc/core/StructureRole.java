package io.github.swishhyy.wwmc.core;

/** Stations declare purpose; furniture supplies capacity. */
public enum StructureRole {
    HOUSING("housing", true, false), BARRACKS("barracks", true, false),
    HOSPITAL("hospital", false, true), WAREHOUSE("warehouse", false, false),
    FARM("farm", false, true), LUMBER("lumber", false, true), MINE("mine", false, true), QUARRY("quarry", false, true), GUARD("guard", false, true),
    CRAFTSMAN("craftsman", false, true), SMELTERY("smeltery", false, true), COOK("cook", false, true), BLACKSMITH("blacksmith", false, true),
    COURIER("courier", false, true), ENCHANTER("enchanter", false, true), TRADER("trader", false, true), HUNTER("hunter", false, true), FISHERMAN("fisherman", false, true),
    ANIMAL_KEEPER("animal_keeper", false, true), BUTCHER("butcher", false, true), RESEARCHER("researcher", false, true), GATHERER("gatherer",false,true);
    private final String id;
    private final boolean residential;
    private final boolean worker;
    StructureRole(String id, boolean residential, boolean worker) {
        this.id = id; this.residential = residential; this.worker = worker;
    }
    public String id() { return id; }
    public boolean providesHousing() { return residential; }
    public boolean detectsBeds() { return residential || this==HOSPITAL; }
    public boolean providesWork() { return worker; }
    public boolean excavates() { return this==MINE || this==QUARRY; }
    public boolean processes() { return this==SMELTERY || this==COOK; }
    /** Jobs whose barrels in range hold their tools, supplies and finished goods; couriers move goods between them and the warehouse. */
    public boolean keepsJobStorage() { return worker && this!=COURIER && this!=TRADER && this!=RESEARCHER; }
    public boolean animalJob() { return this==HUNTER || this==FISHERMAN || this==ANIMAL_KEEPER || this==BUTCHER; }
    public boolean foodJob() { return this==FARM || this==COOK || animalJob(); }
    /** Display name such as "Craftsman". */
    public String title() { return this==ANIMAL_KEEPER ? "Animal Keeper" : Character.toUpperCase(id.charAt(0))+id.substring(1); }
}
