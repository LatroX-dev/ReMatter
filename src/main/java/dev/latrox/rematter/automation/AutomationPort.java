package dev.latrox.rematter.automation;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Optional network adapters and standalone automation share this authoritative, server-thread contract. */
public interface AutomationPort {
    enum Action { SIMULATE, EXECUTE }
    enum Kind { ITEM, FLUID, MATTER }
    /** Item and fluid keys describe plain resources only; components are never silently discarded. */
    record Resource(Kind kind, String id, String phase) implements Comparable<Resource> {
        public Resource {
            Objects.requireNonNull(kind); Objects.requireNonNull(phase); SubstanceId.parse(id);
            if(id.length()>512)throw new IllegalArgumentException("Resource id too long");
            if(kind==Kind.MATTER) {
                if(!java.util.Set.of("solid","liquid","gas").contains(phase))throw new IllegalArgumentException("Invalid matter phase");
            } else if(!phase.isEmpty())throw new IllegalArgumentException("Only matter has a phase");
        }
        public static Resource item(String id) { return new Resource(Kind.ITEM,id,""); }
        public static Resource fluid(String id) { return new Resource(Kind.FLUID,id,""); }
        public static Resource matter(SubstanceKey key) { return new Resource(Kind.MATTER,key.id().toString(),key.phase().name().toLowerCase(java.util.Locale.ROOT)); }
        public SubstanceKey matterKey() {
            if(kind!=Kind.MATTER)throw new IllegalStateException("Not matter");
            return SubstanceKey.of(id,Phase.valueOf(phase.toUpperCase(java.util.Locale.ROOT)));
        }
        @Override public int compareTo(Resource other) {
            int result=kind.compareTo(other.kind);if(result==0)result=id.compareTo(other.id);return result==0?phase.compareTo(other.phase):result;
        }
    }
    record Recipe(String id, String revision, String workerKind, Map<Resource,Long> inputs,
                  Map<Resource,Long> outputs, long energy, int ticks) {
        public Recipe {
            SubstanceId.parse(id);
            if(id.length()>512 || revision==null || revision.isBlank() || revision.length()>128
                    || workerKind==null || workerKind.isBlank() || workerKind.length()>128 || energy<0 || ticks<1)
                throw new IllegalArgumentException("Invalid automation recipe");
            inputs=ResourceAmounts.copy(inputs); outputs=ResourceAmounts.copy(outputs);
            if(inputs.isEmpty()||outputs.isEmpty())throw new IllegalArgumentException("Recipe must have inputs and outputs");
        }
    }
    record Delivery(UUID orderId, Resource resource, long amount) {
        public Delivery { Objects.requireNonNull(orderId);Objects.requireNonNull(resource);if(amount<=0)throw new IllegalArgumentException("Invalid delivery amount"); }
    }
    UUID ownerId();
    UUID bridgeId();
    /** Includes server thread, actual loaded host, permissions, network validity and any required exclusive lease. */
    boolean active();
    Map<SubstanceKey,Long> availableEntries();
    long insert(SubstanceKey key,long maximum,Action action);
    long extract(SubstanceKey key,long maximum,Action action);
    List<Recipe> recipes();
    boolean busy();
    /**
     * Exact inputs already belong to the caller after extraction. EXECUTE transfers that ownership
     * to this persisted queue; never withdraw them from shared storage a second time. The host must
     * resolve id and revision against its current recipe catalogue before accepting a cached pattern.
     */
    boolean acceptTransferred(String id,String revision,int batches,Map<Resource,Long> inputs,Action action);
    List<Delivery> pendingOutputs();
    /** Persist a retry guard before entering external code that can save or remove this interface. */
    boolean beginDelivery(UUID orderId,Resource resource,long offered);
    /** Finish the current live attempt; zero acceptance also releases its guard. Detached copies must stay locked. */
    void ackOutput(UUID orderId,Resource resource,long accepted);
    /** Transferred inputs of cancelled orders. They return to the native network through the same guard as outputs. */
    List<Delivery> pendingRefunds();
    boolean beginRefund(UUID orderId,Resource resource,long offered);
    void ackRefund(UUID orderId,Resource resource,long accepted);
    void quarantineDelivery(UUID orderId,String reason);
    void changed();
}
