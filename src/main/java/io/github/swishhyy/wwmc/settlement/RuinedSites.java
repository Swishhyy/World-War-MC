package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Finite expedition ruins double as examples of real station/furniture layouts. Never rebuilt after discovery. */
public final class RuinedSites {
    private RuinedSites() {}
    public static int radius(String kind) { return kind.equals("fort") ? 10 : kind.equals("townhall") ? 8 : kind.equals("mine") ? 7 : 5; }
    private static void put(ServerLevel level,BlockPos pos,BlockState state) {
        level.setBlock(pos,state,3); WorldWorkData.get(level).protect(pos);
    }
    private static void station(ServerLevel level,BlockPos c,int x,int z,StructureRole role) {
        put(level,c.offset(x,0,z),WWMC.STATIONS.get(role).get().defaultBlockState());
    }
    private static void bed(ServerLevel level,BlockPos foot) {
        var state=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
        put(level,foot,state.setValue(BedBlock.PART,BedPart.FOOT)); put(level,foot.north(),state.setValue(BedBlock.PART,BedPart.HEAD));
    }
    public static void build(ServerLevel level,ExpeditionData.Site site) {
        BlockPos c=site.pos; int radius=radius(site.kind);
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            put(level,c.offset(x,-1,z),(Math.floorMod(x*7+z*13,5)==0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
            for(int y=0;y<=6;y++) level.setBlock(c.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        if(site.kind.equals("fort")) castle(level,c); else if(site.kind.equals("townhall")) townhall(level,c); else workshop(level,c);
    }
    /** A broken civic hall shows how support, research and production rooms fit together. */
    private static void townhall(ServerLevel level,BlockPos c) {
        for(int x=-7;x<=7;x++) for(int z=-7;z<=7;z++) {
            if(Math.abs(x)!=7 && Math.abs(z)!=7 || z==7 && Math.abs(x)<=2) continue;
            int height=2+Math.floorMod(x*3+z*11,3);
            for(int y=0;y<height;y++) if(y!=1 || Math.floorMod(x+z,4)!=0)
                put(level,c.offset(x,y,z),(y==0 ? Blocks.STONE_BRICKS : Blocks.OAK_PLANKS).defaultBlockState());
        }
        for(int x=-7;x<=7;x++) for(int z=-7;z<=0;z++) if(Math.floorMod(x*7+z*11,5)>1)
            put(level,c.offset(x,4,z),Blocks.OAK_PLANKS.defaultBlockState());
        put(level,c.north(3),WWMC.BANNER.get().defaultBlockState());
        station(level,c,0,-5,StructureRole.RESEARCHER); put(level,c.north(4),Blocks.LECTERN.defaultBlockState());
        station(level,c,-5,1,StructureRole.HOUSING); bed(level,c.offset(-6,0,3)); bed(level,c.offset(-5,0,3));
        station(level,c,5,1,StructureRole.WAREHOUSE); put(level,c.offset(6,0,2),Blocks.BARREL.defaultBlockState());
        station(level,c,-4,-4,StructureRole.CRAFTSMAN); put(level,c.offset(-5,0,-4),Blocks.CRAFTING_TABLE.defaultBlockState());
        put(level,c.offset(-4,0,-6),Blocks.BARREL.defaultBlockState());
        station(level,c,4,-4,StructureRole.BLACKSMITH); put(level,c.offset(5,0,-4),WWMC.BRONZE_ANVIL.get().defaultBlockState());
        put(level,c.offset(5,0,-6),Blocks.FURNACE.defaultBlockState()); put(level,c.offset(3,0,-6),Blocks.BARREL.defaultBlockState());
        station(level,c,5,4,StructureRole.GATHERER); put(level,c.offset(6,0,5),Blocks.BARREL.defaultBlockState());
        for(BlockPos p:java.util.List.of(c.offset(-2,0,5),c.offset(2,0,5),c.offset(-2,0,-1),c.offset(2,0,-1)))
            put(level,p,Blocks.TORCH.defaultBlockState());
    }
    private static void castle(ServerLevel level,BlockPos c) {
        // An open south gate and broken parapets keep the courtyard accessible without demolishing the example rooms.
        for(int x=-9;x<=9;x++) for(int z=-9;z<=9;z++) {
            if(Math.abs(x)!=9 && Math.abs(z)!=9 || z==9 && Math.abs(x)<=2) continue;
            int height=2+Math.floorMod(x*11+z*7,3);
            for(int y=0;y<height;y++) put(level,c.offset(x,y,z),(y==height-1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
        }
        for(int tx:new int[]{-8,8}) for(int tz:new int[]{-8,8}) {
            for(int x=tx-2;x<=tx+2;x++) for(int z=tz-2;z<=tz+2;z++) {
                boolean wall=Math.abs(x-tx)==2 || Math.abs(z-tz)==2;
                for(int y=0;y<6;y++) {
                    boolean door=z==tz && x==tx-(tx>0 ? 2 : -2) && y<3;
                    boolean window=y==3 && (x==tx || z==tz);
                    if(wall && !door && !window) put(level,c.offset(x,y,z),(y==5 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
                    else if(!wall && y==5 && Math.floorMod(x*3+z,5)!=0) put(level,c.offset(x,y,z),Blocks.OAK_PLANKS.defaultBlockState());
                    else if(!wall && y<5 || door || window) level.setBlock(c.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
                }
            }
        }
        station(level,c,-8,-7,StructureRole.HOUSING); bed(level,c.offset(-8,0,-8)); bed(level,c.offset(-7,0,-8));
        station(level,c,8,-7,StructureRole.RESEARCHER); put(level,c.offset(8,0,-9),Blocks.LECTERN.defaultBlockState());
        put(level,c.offset(7,0,-8),Blocks.BARREL.defaultBlockState());
        station(level,c,8,7,StructureRole.WAREHOUSE); put(level,c.offset(8,0,9),Blocks.BARREL.defaultBlockState()); put(level,c.offset(7,0,8),Blocks.BARREL.defaultBlockState());
        station(level,c,-8,7,StructureRole.MINE); put(level,c.offset(-8,0,9),WWMC.TIN_ORE.get().defaultBlockState()); put(level,c.offset(-7,0,8),Blocks.BARREL.defaultBlockState());
        station(level,c,-4,0,StructureRole.COOK); put(level,c.offset(-5,0,0),Blocks.SMOKER.defaultBlockState()); put(level,c.offset(-4,0,2),Blocks.BARREL.defaultBlockState());
        station(level,c,4,0,StructureRole.CRAFTSMAN); put(level,c.offset(5,0,0),Blocks.CRAFTING_TABLE.defaultBlockState()); put(level,c.offset(4,0,2),Blocks.BARREL.defaultBlockState());
        station(level,c,0,-4,StructureRole.GUARD);
        for(BlockPos light:java.util.List.of(c.offset(-5,0,-3),c.offset(5,0,-3),c.offset(-5,0,4),c.offset(5,0,4))) put(level,light,Blocks.TORCH.defaultBlockState());
    }
    private static void workshop(ServerLevel level,BlockPos c) {
        for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++) {
            if(Math.abs(x)!=6 && Math.abs(z)!=6 || z==6 && Math.abs(x)<=1) continue;
            for(int y=0;y<3;y++) if(!(y==1 && Math.floorMod(x+z,4)==0))
                put(level,c.offset(x,y,z),(y==0 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).defaultBlockState());
        }
        for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++) if(Math.floorMod(x*13+z*3,7)>1)
            put(level,c.offset(x,3,z),Blocks.OAK_PLANKS.defaultBlockState());
        station(level,c,-4,0,StructureRole.MINE); put(level,c.offset(-5,0,0),WWMC.TIN_ORE.get().defaultBlockState()); put(level,c.offset(-4,0,2),Blocks.BARREL.defaultBlockState());
        station(level,c,4,0,StructureRole.WAREHOUSE); put(level,c.offset(4,0,2),Blocks.BARREL.defaultBlockState());
        station(level,c,0,-4,StructureRole.RESEARCHER); put(level,c.offset(0,0,-5),Blocks.LECTERN.defaultBlockState());
        station(level,c,0,4,StructureRole.HOUSING); bed(level,c.offset(1,0,4)); bed(level,c.offset(2,0,4));
        bed(level,c.offset(-1,0,4)); bed(level,c.offset(3,0,4));
    }
}
