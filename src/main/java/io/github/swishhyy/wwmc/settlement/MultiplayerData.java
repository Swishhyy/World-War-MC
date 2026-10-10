package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Real payments are reserved before an offer is saved. Undelivered payments survive full inventories and logout. */
public final class MultiplayerData extends SavedData {
    public static final class Contract {
        public static final Codec<Contract> CODEC=RecordCodecBuilder.create(i -> i.group(
                Settlement.UUID_CODEC.fieldOf("id").forGetter(c -> c.id),Settlement.UUID_CODEC.fieldOf("issuer").forGetter(c -> c.issuer),
                Settlement.UUID_CODEC.fieldOf("publisher").forGetter(c -> c.publisher),Codec.STRING.fieldOf("item").forGetter(c -> c.item),
                Codec.intRange(1,256).fieldOf("amount").forGetter(c -> c.amount),Codec.intRange(0,256).fieldOf("delivered").forGetter(c -> c.delivered),
                Codec.intRange(1,64).fieldOf("payment").forGetter(c -> c.payment),
                Settlement.UUID_CODEC.optionalFieldOf("supplier").forGetter(c -> Optional.ofNullable(c.supplier)),
                Settlement.UUID_CODEC.optionalFieldOf("recipient").forGetter(c -> Optional.ofNullable(c.recipient))
        ).apply(i,Contract::new));
        public final UUID id,issuer,publisher;
        public final String item;
        public final int amount,payment;
        public int delivered;
        public UUID supplier,recipient;
        public Contract(UUID id,UUID issuer,UUID publisher,String item,int amount,int delivered,int payment,Optional<UUID> supplier,Optional<UUID> recipient) {
            this.id=id; this.issuer=issuer; this.publisher=publisher; this.item=item; this.amount=amount;
            this.delivered=Math.clamp(delivered,0,amount); this.payment=payment; this.supplier=supplier.orElse(null); this.recipient=recipient.orElse(null);
        }
        public int remaining() { return amount-delivered; }
    }
    /** Decode old preview saves only, so removing duels refunds paid stakes. */
    public static final class LegacyDuel {
        public static final Codec<LegacyDuel> CODEC=RecordCodecBuilder.create(i -> i.group(
                Settlement.UUID_CODEC.fieldOf("id").forGetter(d -> d.id),Settlement.UUID_CODEC.fieldOf("challenger").forGetter(d -> d.challenger),
                Settlement.UUID_CODEC.fieldOf("opponent").forGetter(d -> d.opponent),Codec.intRange(0,64).fieldOf("stake").forGetter(d -> d.stake),
                BlockPos.CODEC.fieldOf("arena").forGetter(d -> d.arena),Codec.BOOL.fieldOf("accepted").forGetter(d -> d.accepted),
                Codec.LONG.fieldOf("starts").forGetter(d -> d.starts),Codec.LONG.fieldOf("deadline").forGetter(d -> d.deadline)
        ).apply(i,LegacyDuel::new));
        public final UUID id,challenger,opponent;
        public final int stake;
        public final BlockPos arena;
        public boolean accepted;
        public long starts,deadline;
        public LegacyDuel(UUID id,UUID challenger,UUID opponent,int stake,BlockPos arena,boolean accepted,long starts,long deadline) {
            this.id=id; this.challenger=challenger; this.opponent=opponent; this.stake=stake; this.arena=arena.immutable();
            this.accepted=accepted; this.starts=starts; this.deadline=deadline;
        }
        public boolean includes(UUID player) { return challenger.equals(player) || opponent.equals(player); }
        public UUID other(UUID player) { return challenger.equals(player) ? opponent : challenger; }
    }
    public static final class Contest {
        public static final Codec<Contest> CODEC=RecordCodecBuilder.create(i -> i.group(
                Settlement.UUID_CODEC.fieldOf("id").forGetter(c -> c.id),Settlement.UUID_CODEC.fieldOf("outpost").forGetter(c -> c.outpost),
                Settlement.UUID_CODEC.fieldOf("defender").forGetter(c -> c.defender),Settlement.UUID_CODEC.fieldOf("challenger").forGetter(c -> c.challenger),
                Codec.LONG.fieldOf("starts").forGetter(c -> c.starts),Codec.LONG.fieldOf("deadline").forGetter(c -> c.deadline),
                Codec.BOOL.fieldOf("accepted").forGetter(c -> c.accepted),Codec.intRange(0,1200).fieldOf("progress").forGetter(c -> c.progress)
        ).apply(i,Contest::new));
        public final UUID id,outpost,defender,challenger;
        public long starts,deadline;
        public boolean accepted;
        public int progress;
        public Contest(UUID id,UUID outpost,UUID defender,UUID challenger,long starts,long deadline,boolean accepted,int progress) {
            this.id=id; this.outpost=outpost; this.defender=defender; this.challenger=challenger;
            this.starts=starts; this.deadline=deadline; this.accepted=accepted; this.progress=progress;
        }
    }
    public static final Codec<MultiplayerData> CODEC=RecordCodecBuilder.create(i -> i.group(
            Contract.CODEC.listOf().optionalFieldOf("contracts",List.of()).forGetter(d -> d.contracts),
            LegacyDuel.CODEC.listOf().optionalFieldOf("duels",List.of()).forGetter(d -> d.retiredDuels),
            Contest.CODEC.listOf().optionalFieldOf("contests",List.of()).forGetter(d -> d.contests),
            Codec.unboundedMap(Settlement.UUID_CODEC,Codec.LONG).optionalFieldOf("payments",Map.of()).forGetter(d -> d.payments)
    ).apply(i,MultiplayerData::new));
    private static final SavedDataType<MultiplayerData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"multiplayer"),MultiplayerData::new,CODEC);
    public final List<Contract> contracts;
    public final List<LegacyDuel> retiredDuels;
    public final List<Contest> contests;
    public final Map<UUID,Long> payments;
    private boolean initialized;
    public MultiplayerData() { this(List.of(),List.of(),List.of(),Map.of()); }
    public MultiplayerData(List<Contract> contracts,List<LegacyDuel> duels,List<Contest> contests,Map<UUID,Long> payments) {
        this.contracts=new ArrayList<>(contracts); this.retiredDuels=new ArrayList<>(duels); this.contests=new ArrayList<>(contests); this.payments=new HashMap<>(payments);
    }
    public void pay(UUID player,long emeralds) {
        if(player!=null && emeralds>0) { payments.merge(player,emeralds,Math::addExact); setDirty(); }
    }
    /** Retired duel payments are refunded once; a restart cannot transfer an unattended outpost. */
    public void recover() {
        if(initialized) return;
        initialized=true;
        if(retiredDuels.isEmpty() && contests.isEmpty()) return;
        for(LegacyDuel duel:retiredDuels) { pay(duel.challenger,duel.stake); if(duel.accepted) pay(duel.opponent,duel.stake); }
        WWMC.LOGGER.info("[WWMC] [multiplayer] Restored reserved stakes for {} retired preview duels; cancelled {} outpost challenges",retiredDuels.size(),contests.size());
        retiredDuels.clear(); contests.clear(); setDirty();
    }
    public static MultiplayerData get(ServerLevel level) {
        MultiplayerData data=level.getDataStorage().computeIfAbsent(TYPE); data.recover(); return data;
    }
    public Contract contract(UUID id) { return contracts.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null); }
    public Contest contest(UUID id) { return contests.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null); }
}
