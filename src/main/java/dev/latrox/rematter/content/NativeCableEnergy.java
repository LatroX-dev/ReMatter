package dev.latrox.rematter.content;

import java.util.function.IntSupplier;

/** Server-only lexical context set by the native cable adapter; clients never select a transfer class. */
final class NativeCableEnergy {
    private static final ThreadLocal<Integer> RATE=ThreadLocal.withInitial(()->0);
    static int rate(){return RATE.get();}
    static int transfer(int rate,IntSupplier action){int previous=RATE.get();RATE.set(rate);try{return action.getAsInt();}finally{if(previous==0)RATE.remove();else RATE.set(previous);}}
}
