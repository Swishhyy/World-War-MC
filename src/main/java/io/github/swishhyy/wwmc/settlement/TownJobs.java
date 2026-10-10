package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Job places and citizens are counted separately from support stations and loaded working crews. */
public record TownJobs(int assigned,int unassigned,int squadOnly,int places,int open,int off,int waiting) {
    public static TownJobs assess(ServerLevel level,Settlement town) {
        int assigned=0,unassigned=0,squadOnly=0,places=0,open=0,off=0,waiting=0;
        for(UUID id:town.citizens) {
            if(town.progress.children.contains(id)) continue;
            var home=town.jobs.home(id);
            Station station=home==null ? null : town.station(home);
            if(station!=null && town.jobs.level(station.role())!=JobBoard.OFF && town.jobs.holdsPlace(id,station,SettlementService.workerLimit(town,station))) assigned++;
            else if(SquadService.assigned(town,id)) squadOnly++;
            else unassigned++;
        }
        for(Station station:town.stations) {
            int capacity=SettlementService.workerLimit(town,station);
            if(town.jobs.level(station.role())==JobBoard.OFF) { off+=capacity; continue; }
            places+=capacity;
            int filled=(int)town.jobs.crew(station.position()).stream().filter(id -> town.citizens.contains(id) && !town.progress.children.contains(id)
                    && town.jobs.holdsPlace(id,station,capacity)).count();
            int free=capacity-filled;
            boolean available=SettlementService.active(level,station) && (station.role()!=StructureRole.TRADER
                    || TradeRoutes.canDepart(level,town) && town.trading.runner==null);
            if(available) open+=free; else waiting+=free;
        }
        return new TownJobs(assigned,unassigned,squadOnly,places,open,off,waiting);
    }
    public String summary() {
        return assigned+"/"+places+" enabled job places filled; "+unassigned+" unassigned"+(squadOnly>0 ? ", "+squadOnly+" on squad duty" : "");
    }
    public String noJobAdvice() {
        String counts=assigned+"/"+places+" enabled job places filled. ";
        if(open>0) return counts+open+(open==1 ? " loaded place is" : " loaded places are")+" open; citizens take jobs when ready for work. Check the Citizens tab for their status.";
        if(off>0) return counts+off+(off==1 ? " place is" : " places are")+" switched off: enable jobs on the Jobs tab, or add job stations.";
        if(waiting>0) return counts+waiting+(waiting==1 ? " unfilled place is" : " unfilled places are")+" unloaded or waiting for a trade route. Load those stations or connect and unpause trade.";
        return counts+"Add job stations or expand a quarry crew. Housing, barracks and warehouses do not add jobs.";
    }
}
