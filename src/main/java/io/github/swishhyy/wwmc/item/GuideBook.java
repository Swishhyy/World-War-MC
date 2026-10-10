package io.github.swishhyy.wwmc.item;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

/** Short, illustrated reference data. The guide item opens a real mod screen rather than paginating these into a book. */
public final class GuideBook {
    public record Card(String icon,String title,String text,String link) {
        public Card(String icon,String title,String text) { this(icon,title,text,""); }
    }
    public record Topic(String id,String title,String subtitle,List<Card> cards,List<String> flow,String tip) {}
    public record StationHelp(StructureRole role,String ingredient,String furniture,String supplies,String result) {}
    private GuideBook() {}
    public static final List<Topic> TOPICS=List.of(
        new Topic("start","Start here","Your first town in six steps",List.of(
            new Card("wwmc:settlement_banner","1. Found a town","Craft a flag with blue wool and 8 planks. Place it and right-click with an empty hand.","stations"),
            new Card("minecraft:red_bed","2. Add beds","Place Housing beside complete beds. Both halves must fit inside its range.","stations"),
            new Card("minecraft:chest","3. Stock a warehouse","Place Warehouse beside chests. Add ready-to-eat food, tools and fuel.","food"),
            new Card("minecraft:bell","4. Recruit citizens","Open the town flag, select People and press Recruit. Each citizen needs a housing bed.","people"),
            new Card("wwmc:farm_station","5. Give them work","Build a Farm and a Courier. Put a separate job barrel beside the farm.","food"),
            new Card("minecraft:writable_book","6. Check the town","Open Needs at the flag. Press Show to find a station with a problem.","help")
        ),List.of("Flag","Beds","Warehouse","Recruit","Jobs"),"Press L for Advancements. The Villager Colonies branch tracks your real progress."),
        new Topic("stations","Stations & recipes","Pick a station to see its recipe and setup",List.of(),List.of(),"Any kind of planks works. Most stations start with a 7 x 7 x 7 range."),
        new Topic("people","Citizens","Recognize their job at a glance",List.of(
            new Card("minecraft:leather_chestplate","Job outfits","Each job has its own outfit. Armor hides the clothing underneath it; uncovered clothes and real tools remain visible."),
            new Card("wwmc:courier_station","One worker per job","Every job block has one worker. Quarries can have a crew. Add stations to add workers."),
            new Card("minecraft:experience_bottle","Skills & meals","Finished jobs build experience. Better axes, hoes and shovels speed up suitable work; pickaxes already affect mining. Varied meals improve work speed and happiness."),
            new Card("minecraft:apple","Happy homes","Check People > Wellbeing. Meals they actually eat, enough housing, safety, flowers, bells, lit campfires and books affect happiness. Each amenity type counts once."),
            new Card("minecraft:red_bed","Grow your population","Two happy, healthy adults with varied meals may have a baby. A spare housing bed, room under the cap and 6 meals plus a food reserve are needed. Babies play near home and take no job until adulthood after 20 loaded minutes. Pause growth at the banner."),
            new Card("wwmc:hospital_station","Hospital recovery","Injured citizens rest in hospital beds until fully healed. These beds never count as housing.","defense")
        ),List.of(),"Empty-hand right-click: open a citizen's bag and status. Sneak-right-click: release their job."),
        new Topic("food","Food & hauling","Keep the supply chain moving",List.of(
            new Card("wwmc:farm_station","Start with crops","Farmers harvest and replant. Carrots and potatoes can start the food chain; cooks turn wheat into bread."),
            new Card("wwmc:courier_station","Build a courier","Couriers carry warehouse supplies to job barrels and bring finished goods back. Keep job barrels outside warehouse range."),
            new Card("wwmc:cook_station","Set up a kitchen","Put a smoker, furnace or lit campfire beside Cook. Stock raw food, wheat and fuel in the warehouse."),
            new Card("wwmc:butcher_station","Add meat later","Hunters, fishermen and animal keepers supply carcasses. Butchers cut them into raw portions; cooks make meals.")
        ),List.of("Job barrel","Courier","Warehouse","Courier","Kitchen"),"Workers need clear paths, storage space and ticking chunks. A full barrel stops production."),
        new Topic("industry","Mining & workshops","Real tools, real supplies, visible work",List.of(
            new Card("wwmc:mine_station","Mine or quarry","A Mine near exposed ore harvests a replenishing vein; otherwise it digs tunnels. A Quarry digs the chunk its arrow points toward."),
            new Card("minecraft:iron_pickaxe","Tools & upgrades","Better pickaxes dig and replenish faster. Mine and Farm yield upgrades give extra output. Supply floor blocks for tunnels."),
            new Card("wwmc:craftsman_station","Craft & repair","Set stock targets in Production > Workshop for job blocks and supplies, or teach a recipe by example. Use Production > Metalwork for smelter alloy targets and forged equipment; blacksmith repairs run first."),
            new Card("minecraft:enchanting_table","Enchant gear","Put an enchanting table and bookshelves near Enchanter. Supply lapis and plain gear or books. Runes and hand motions show active work.")
        ),List.of(),"No swings or work particles? The worker may be walking, resting or waiting. Open Needs to see why."),
        new Topic("defense","Defense & recovery","Prepare before the next wave",List.of(
            new Card("wwmc:guard_station","Equip a guard","Place usable armor and shields on nearby stands. Put weapons, shields and arrows in the guard barrel. Archers shoot real arrows; shield guards raise a shield between attacks. One post has one guard."),
            new Card("minecraft:shield","Posts & patrols","Choose a swordsman, shield guard or archer role at Guard. Use the Inspector to mark posts and patrol points."),
            new Card("minecraft:bell","Sound the alarm","Ring a bell to call guards. Keep some home when borrowing guards for a squad or expedition."),
            new Card("wwmc:hospital_station","Make patient beds","Put beds beside Hospital. They heal slowly. Fund Field Hospital and stock meals plus paper to let a medic assist."),
            new Card("wwmc:wooden_spikes","Build trap defenses","Stone Age spikes damage and slow; nets strongly slow. Bronze unlocks snares and caltrops; Iron unlocks spring traps. Place them on solid ground inside your town."),
            new Card("wwmc:bronze_snare","Maintain your traps","Only hostile monsters trigger traps. Each activation spends a use. Nets and snares need string to rearm; broken traps need planks, string or their metal. Moving a trap retains wear."),
            new Card("wwmc:craftsman_station","Repair after combat","Craftsmen carry courier-delivered repair materials to nearby traps after the alarm ends. Check Overview and Needs at the banner; Show locates worn defenses.")
        ),List.of(),"Housing beds and food do not heal injuries. Hospital patients stay until their health is full."),
        new Topic("relationships","Town & friends","Manage access and colors at the flag",List.of(
            new Card("minecraft:iron_door","Invite your friends","Open Relationships > Players. Invite a Builder or Steward. They accept under Invitations. Outsiders have no access."),
            new Card("minecraft:blue_banner","Choose a town color","Open Relationships > Town and press Next color. Your flag and four border corners use the same saved color."),
            new Card("minecraft:map","Share the map","Open Campaign > Map to see claims, routes and expedition sites. Click to ping your town and allies."),
            new Card("wwmc:trader_station","Connect settlements","Set exports at Trader. Your own towns link immediately; other players must agree. Warehouses and a walkable route are required.")
        ),List.of(),"Alliances share cooperation, not building access. Only the owner changes town permissions and color."),
        new Topic("research","Ages & research","Stone > Bronze > Iron",List.of(
            new Card("wwmc:researcher_station","Hire a researcher","Place Researcher beside a lectern. Give a citizen this job in People > Jobs and keep ready-to-eat food available."),
            new Card("wwmc:research_scroll","Write research scrolls","Researchers spend 2 paper and 1 ink sac or charcoal per scroll, working at a real lectern for 30 seconds. Set their stock target in Research. Scrolls can be traded."),
            new Card("minecraft:book","Unlock a discovery","Open Research at the flag. Spend earned scrolls and supplies once. Bronze Age needs 3 citizens; Iron Age needs a staffed, furnished smithy. All settlement members share the unlock."),
            new Card("wwmc:tin_ore","Find tin","Tin occurs from Y -32 to 64 in new Overworld terrain. A stone pickaxe can mine it. Smelt raw tin into ingots."),
            new Card("wwmc:alloy_furnace","Alloy bronze directly","After Bronze Age, build an Alloy Furnace from a furnace, 6 cobblestone and 2 copper ingots. Put 3 copper and 1 tin in the two inputs, plus separate fuel. It yields 4 bronze ingots in 20 seconds. Ingots, raw metals and ores work. Smelters and couriers automate it."),
            new Card("wwmc:steel_ingot","Discover steel","After Iron Age, staff a smeltery with an Alloy Furnace and research Steelworking. Alloy 1 iron + 1 coal or charcoal, with separate fuel, into 1 steel ingot in 30 seconds. Steel equipment is forged by the blacksmith; it sits between iron and diamond."),
            new Card("wwmc:bronze_anvil","Build the first smithy","Bronze Age unlocks a copper-based Blacksmith Station and a bronze anvil. Add a furnace and a job barrel. Bronze anvils work 65% slower than iron, with normal anvil wear."),
            new Card("minecraft:iron_sword","Order forged equipment","Bronze, copper, iron and later equipment must be made by your blacksmith. Set targets in Production > Metalwork. The smith repairs first, then uses real materials and coal/charcoal to forge. Found equipment can be stored until its age is researched."),
            new Card("minecraft:blue_banner","Share progress","The owner and accepted members share research everywhere, including other dimensions. Allies and invitations alone do not grant it. Existing towns retain their old gear access.")
        ),List.of("Stone","Researcher","Tin + copper","Bronze","Iron"),"Research pauses during sleep, danger or blocked access. Check the banner's Research status for the reason. Progress and paid supplies survive saving."),
        new Topic("multiplayer","Settlement neighbours","Trade, supply requests and town news",List.of(
            new Card("minecraft:compass","Meet your neighbours","Open Neighbours at your main banner. Nearby towns show what they need, their relationships and their trade status. Explore to discover NPC towns."),
            new Card("minecraft:emerald","Post a supply contract","Hold a plain example at your main banner. Choose an amount and emerald payment; Post reserves real emeralds from your inventory for the supplying settlement."),
            new Card("minecraft:chest","Send a caravan","Accept another town's contract and connect a trade route. Assign your Trader Block and put plain requested goods in your warehouse. Your trader carries surplus after home reserves; manual delivery also works."),
            new Card("minecraft:carrot","Help an NPC neighbour","Accept its request on Neighbours, then connect trade. The trader carries the reserved reward home. NPC towns also arrange routes with each other while keeping a checkpoint open for player trade."),
            new Card("minecraft:paper","Follow town news","The News tab records neighbour trade, arrivals, requests and NPC growth. Ordinary shipments stay at the banner. Earned payments and refunds remain saved until Collect payment can fit them."),
            new Card("minecraft:red_banner","Agree an outpost battle","Both main settlement owners must agree and stay online. Assemble for one minute; then hold the flag for 60 seconds with no nearby defender. Normal PvP death and drops apply.")
        ),List.of("Neighbour","Trade route","Real delivery","Growing towns"),"Towns keep their own storage and research permissions. Build roads and bridges to help caravans reach each other."),
        new Topic("frontier","Projects & exploration","Expand after the town is stable",List.of(
            new Card("minecraft:iron_ingot","Fund projects","Open Campaign > Projects. Furnish the required stations, then pay with real warehouse goods."),
            new Card("minecraft:compass","Explore together","Camps have captives or stolen supplies. Ruined castles have captains and schematics; town halls contain salvageable settlement blocks, scrolls and a bronze smithy. Ruins show real station and furniture setups. Clear sites with equipped guards."),
            new Card("minecraft:emerald","Claim regional outposts","After Frontier Charter, claim a cleared site. Ship its regional ore back to your home town."),
            new Card("minecraft:book","Research upgrades","Research at the flag spends researcher-made scrolls to unlock equipment, population, production, medicine and defenses.","research")
        ),List.of("Stable town","Projects","Expedition","Regional ore","Research"),"Open Campaign > Journal to review deliveries, projects, losses and victories."),
        new Topic("help","Work stopped?","Check these in order",List.of(
            new Card("minecraft:writable_book","1. Read Needs","Open the flag, select Needs, then Show. The station is outlined in the world."),
            new Card("minecraft:barrel","2. Check storage","Every production station needs a job barrel. Give it free space and keep it outside warehouse range."),
            new Card("minecraft:iron_pickaxe","3. Check supplies","Stock real tools, ingredients and fuel. Hire couriers to move them. Broken tools need a blacksmith."),
            new Card("minecraft:oak_door","4. Check the route","Leave reachable ground beside work blocks and storage. Both bed halves and furniture must fit the range.")
        ),List.of(),"Towns work while their chunks tick on a running server. Distant or unloaded jobs wait.")
    );
    public static final List<StationHelp> STATIONS=List.of(
        new StationHelp(StructureRole.HOUSING,"minecraft:oak_door","Complete beds in range","Ready-to-eat food in warehouse","Housing for recruits"),
        new StationHelp(StructureRole.WAREHOUSE,"minecraft:chest","Chests or barrels within 3 blocks","Food, tools, fuel and materials","Communal town stock"),
        new StationHelp(StructureRole.FARM,"minecraft:wheat_seeds","Crops on farmland + job barrel","Seeds or crops to replant","Produce for meals and cooks"),
        new StationHelp(StructureRole.COURIER,"minecraft:barrel","Warehouse + job barrels + clear paths","Goods in reachable storage","Supplies delivered; goods collected"),
        new StationHelp(StructureRole.COOK,"minecraft:smoker","Smoker, furnace or lit campfire + barrel","Raw food, wheat and fuel","Ready-to-eat meals"),
        new StationHelp(StructureRole.LUMBER,"minecraft:stone_axe","Natural trees, clear soil + barrel","Axes and saplings","Logs; trees replanted"),
        new StationHelp(StructureRole.MINE,"minecraft:stone_pickaxe","Exposed ore within 2 blocks, or tunnel space + barrel","Pickaxes and floor blocks","Minerals; better picks work faster"),
        new StationHelp(StructureRole.QUARRY,"wwmc:bronze_pickaxe","Facing chunk completely inside claim + barrel","Pickaxes and floor blocks","Excavated blocks; up to 8 workers before upgrades"),
        new StationHelp(StructureRole.SMELTERY,"minecraft:furnace","Furnace or blast furnace + barrel","Raw ores and fuel","Smelted metal"),
        new StationHelp(StructureRole.CRAFTSMAN,"minecraft:crafting_table","Job barrel beside the station","Teach an item; deliver its materials","Keeps learned items in stock"),
        new StationHelp(StructureRole.BLACKSMITH,"minecraft:copper_ingot","Bronze/iron anvil + furnace + job barrel","Repair materials, forge ingredients and coal/charcoal","Repaired and forged equipment; alloys"),
        new StationHelp(StructureRole.ENCHANTER,"minecraft:book","Enchanting table + bookshelves + barrel","Lapis and unenchanted gear or books","Enchanted gear; takes several minutes"),
        new StationHelp(StructureRole.GUARD,"minecraft:wooden_sword","Equipped armor stands + barrel","Weapons, arrows and armor","One guard per post"),
        new StationHelp(StructureRole.BARRACKS,"minecraft:stone_sword","Complete beds in range","Equipped guards at their own Guard Stations","More housing; required by Armory"),
        new StationHelp(StructureRole.HOSPITAL,"minecraft:paper","Complete patient beds + barrel","Meals and paper after Field Hospital","Hospital beds heal; medic assists"),
        new StationHelp(StructureRole.HUNTER,"minecraft:leather","Unprotected adult game + barrel","Sword or axe","Carcasses for the butcher"),
        new StationHelp(StructureRole.FISHERMAN,"minecraft:fishing_rod","Dry bank, open water 2 blocks deep + barrel","Fishing rod","Whole fish carcasses"),
        new StationHelp(StructureRole.ANIMAL_KEEPER,"minecraft:hay_block","Fenced pen with breeding pairs + barrel","Normal breeding feed and sword or axe","Keeps breeders; harvests surplus adults"),
        new StationHelp(StructureRole.BUTCHER,"minecraft:wooden_axe","Job barrel; station is cutting table","Axe and carcasses","Raw meat for the cook"),
        new StationHelp(StructureRole.RESEARCHER,"minecraft:lectern","Lectern within station range","Paper and ink sac/charcoal in warehouse; meals","Research scrolls for shared discoveries"),
        new StationHelp(StructureRole.GATHERER,"minecraft:stone_shovel","Cane/bamboo or dry exposed sand, gravel, clay + barrel","Shovel and meals","Paper crops and building materials"),
        new StationHelp(StructureRole.TRADER,"minecraft:compass","One per town, warehouse and walkable route","Choose exports and reserves in its screen","Real shipments between towns")
    );
    public static Topic topic(String id) { return TOPICS.stream().filter(t -> t.id().equals(id)).findFirst().orElse(TOPICS.getFirst()); }
    /** A single fallback page preserves existing book components; normal use opens the illustrated guide. */
    public static List<String> pages() { return List.of("Villager Colonies\nGuide\n\nRight-click this\nguide to open\nvisual topics,\nstation recipes\nand quick help.\n\nPress L to see\ntown progress."); }
    public static WrittenBookContent content() {
        return new WrittenBookContent(Filterable.passThrough("Settlement Guide"),"Villager Colonies",0,
                pages().stream().map(s -> Filterable.<Component>passThrough(Component.literal(s))).toList(),true);
    }
}
