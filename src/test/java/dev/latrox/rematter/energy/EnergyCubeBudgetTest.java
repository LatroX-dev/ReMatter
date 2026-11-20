package dev.latrox.rematter.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyCubeBudgetTest {
    @Test void facesAndSlotsCannotMultiplyThePhysicalRate(){
        for(var tier:EnergyCubeTier.values()){
            var budget=new EnergyCubeBudget(tier.transferRate());
            try(var tx=Transaction.openRoot()){
                assertEquals(1,budget.transfer(7,1,tx,n->n));
                assertEquals(tier.transferRate()-1,budget.transfer(7,Integer.MAX_VALUE,tx,n->n));
                assertEquals(0,budget.transfer(7,1,tx,n->n));tx.commit();
            }
            try(var tx=Transaction.openRoot()){assertEquals(tier.transferRate(),budget.transfer(8,Integer.MAX_VALUE,tx,n->n));tx.commit();}
        }
    }
    @Test void simulationAndNestedAbortReturnTheirAllowances(){
        var budget=new EnergyCubeBudget(1000);
        try(var simulation=Transaction.openRoot()){assertEquals(1000,budget.transfer(1,1000,simulation,n->n));}
        try(var outer=Transaction.openRoot()){
            assertEquals(700,budget.transfer(1,700,outer,n->n));
            try(var nested=Transaction.open(outer)){assertEquals(300,budget.transfer(1,500,nested,n->n));}
            assertEquals(300,budget.transfer(1,500,outer,n->n));outer.commit();
        }
        assertEquals(0,budget.remaining(1));
    }
    @Test void callbackReentrySeesTheReservationAndRejectedEnergyIsReturned(){
        var budget=new EnergyCubeBudget(1000);
        try(var tx=Transaction.openRoot()){
            assertEquals(100,budget.transfer(1,700,tx,allowed->{
                assertEquals(300,budget.transfer(1,900,tx,n->n));
                return 100;
            }));
            assertEquals(600,budget.remaining(1));
            assertEquals(0,budget.transfer(1,500,tx,n->0));
            assertEquals(600,budget.remaining(1));tx.commit();
        }
    }
    @Test void inputAndOutputAreIndependentWhileEachDirectionIsGloballyBounded(){
        var input=new EnergyCubeBudget(2048);var output=new EnergyCubeBudget(2048);
        try(var tx=Transaction.openRoot()){
            assertEquals(2048,input.transfer(1,99999,tx,n->n));
            assertEquals(2048,output.transfer(1,99999,tx,n->n));
            assertEquals(0,input.transfer(1,1,tx,n->n));assertEquals(0,output.transfer(1,1,tx,n->n));tx.commit();
        }
    }
    @Test void invalidOffersFailAndAbortDoesNotLeaveAReservation(){
        var budget=new EnergyCubeBudget(1000);
        try(var tx=Transaction.openRoot()){
            assertThrows(IllegalArgumentException.class,()->budget.transfer(1,-1,tx,n->n));
            assertThrows(IllegalStateException.class,()->budget.transfer(1,100,tx,n->101));
        }
        assertEquals(1000,budget.remaining(1));
    }
}
